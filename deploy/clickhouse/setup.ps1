#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Setup', 'Prepare', 'Verify', 'Start', 'Stop', 'Status')]
    [string]$Action = 'Setup'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$runtimeDir = Join-Path $PSScriptRoot '.runtime'
$envFile = Join-Path $runtimeDir 'clickhouse.env'
$composeArgs = @('compose', '--project-name', 'jeecg-analytics', '--file', $composeFile)

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'ClickHouse Compose command failed. No credentials were printed by this script.' }
}

function Protect-Runtime {
    if ($IsWindows) {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = [System.Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @($identity, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
            $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
                $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        [IO.FileSystemAclExtensions]::SetAccessControl([IO.DirectoryInfo]::new($runtimeDir), $acl)
        if (Test-Path -LiteralPath $envFile) {
            $fileAcl = [System.Security.AccessControl.FileSecurity]::new()
            $fileAcl.SetAccessRuleProtection($true, $false)
            foreach ($sid in @($identity, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
                $fileAcl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'Allow'))
            }
            [IO.FileSystemAclExtensions]::SetAccessControl([IO.FileInfo]::new($envFile), $fileAcl)
        }
    } else {
        & chmod 700 $runtimeDir
        if ($LASTEXITCODE -ne 0) { throw 'Cannot protect runtime directory.' }
        if (Test-Path -LiteralPath $envFile) {
            & chmod 600 $envFile
            if ($LASTEXITCODE -ne 0) { throw 'Cannot protect credential file.' }
        }
    }
}

function Read-Credentials {
    if (!(Test-Path -LiteralPath $envFile)) { throw 'Run setup.ps1 -Action Prepare first.' }
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($envFile)) {
        if ($line -match '^([A-Z_0-9]+)=(.*)$') { $values[$Matches[1]] = $Matches[2] }
    }
    foreach ($key in @('CLICKHOUSE_ADMIN_PASSWORD', 'CLICKHOUSE_ADMIN_SHA256', 'CLICKHOUSE_WRITER_PASSWORD', 'CLICKHOUSE_READER_PASSWORD')) {
        if (!$values.ContainsKey($key) -or $values[$key] -cnotmatch '^[a-f0-9]{64}$') {
            throw "Invalid generated credential format for $key; restore the protected file, do not silently regenerate it."
        }
    }
    if ($values['CLICKHOUSE_WRITER_USER'] -cne 'jeecg_writer' -or $values['CLICKHOUSE_READER_USER'] -cne 'jeecg_reader') {
        throw 'Account names must match the initialization contract.'
    }
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($values['CLICKHOUSE_ADMIN_PASSWORD']))).ToLowerInvariant()
    if ($hash -cne $values['CLICKHOUSE_ADMIN_SHA256']) { throw 'Bootstrap password and hash do not match.' }
    return $values
}

function Initialize-Credentials {
    $null = New-Item -ItemType Directory -Path $runtimeDir -Force
    Protect-Runtime
    if (!(Test-Path -LiteralPath $envFile)) {
        # Missing credentials plus an existing volume requires recovery, never automatic rotation.
        $existing = & docker volume ls --quiet --filter 'name=^jeecg-analytics_clickhouse-data$'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect ClickHouse volume.' }
        if ($existing) { throw 'Existing ClickHouse volume without credentials: restore the original protected env file.' }
        $admin = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        $writer = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        $reader = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($admin))).ToLowerInvariant()
        $content = @(
            'CLICKHOUSE_ENABLED=true', 'CLICKHOUSE_ENDPOINT=http://127.0.0.1:18123',
            'CLICKHOUSE_WRITER_USER=jeecg_writer', "CLICKHOUSE_WRITER_PASSWORD=$writer",
            'CLICKHOUSE_READER_USER=jeecg_reader', "CLICKHOUSE_READER_PASSWORD=$reader",
            "CLICKHOUSE_ADMIN_PASSWORD=$admin", "CLICKHOUSE_ADMIN_SHA256=$hash"
        ) -join "`n"
        # CreateNew fails closed on a concurrent setup instead of overwriting its credentials.
        $stream = [IO.File]::Open($envFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($content + "`n")
            $stream.Write($bytes, 0, $bytes.Length)
        } finally { $stream.Dispose() }
        Protect-Runtime
    }
    $null = Read-Credentials
    Write-Host 'Protected ClickHouse credentials ready (values are never printed).'
}

function Invoke-Query {
    param([string]$Sql, [string]$Role, [hashtable]$Credentials, [string]$QueryParameters = '')
    $client = [Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    try {
        if ($Role) {
            $user = $Credentials["CLICKHOUSE_${Role}_USER"]
            $password = $Credentials["CLICKHOUSE_${Role}_PASSWORD"]
            $token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${user}:$password"))
            $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Basic', $token)
        }
        $body = [Net.Http.StringContent]::new($Sql, [Text.Encoding]::UTF8, 'text/plain')
        try {
            $response = $client.PostAsync("http://127.0.0.1:18123/?$QueryParameters", $body).GetAwaiter().GetResult()
            try {
                return @{ Code = [int]$response.StatusCode; Body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() }
            } finally { $response.Dispose() }
        } finally { $body.Dispose() }
    } finally { $client.Dispose() }
}

function Test-Deployment {
    $credentials = Read-Credentials
    $eventId = [Guid]::NewGuid().ToString()
    $apiId = "__deployment_probe__/$eventId"
    $insert = "INSERT INTO jeecg_analytics.api_call (event_id,event_time,api_id,method,status,duration_ms) VALUES ('$eventId',now64(3),'$apiId','GET',200,1)"
    $result = Invoke-Query $insert 'WRITER' $credentials
    if ($result.Code -ne 200) { throw "Writer INSERT failed (HTTP $($result.Code)). Inspect server locally." }
    $result = Invoke-Query "SELECT count() FROM jeecg_analytics.api_call WHERE event_id='$eventId'" 'READER' $credentials
    if ($result.Code -ne 200 -or $result.Body.Trim() -ne '1') { throw 'Reader could not read the synchronous writer probe.' }
    $schema = Invoke-Query 'SHOW CREATE TABLE jeecg_analytics.api_call' 'READER' $credentials
    # JSON output avoids SQL literal escaping in TSV SHOW CREATE output.
    $columns = Invoke-Query 'DESCRIBE TABLE jeecg_analytics.api_call FORMAT JSON' 'READER' $credentials
    if ($columns.Code -ne 200) { throw 'Cannot inspect table columns.' }
    $actual = @((ConvertFrom-Json $columns.Body).data | ForEach-Object { "$($_.name):$($_.type)" }) -join '|'
    $expected = "event_id:UUID|event_time:DateTime64(3, 'UTC')|api_id:String|method:LowCardinality(String)|status:UInt16|duration_ms:UInt32"
    if ($actual -cne $expected) { throw 'Table column contract mismatch; no automatic schema migration is performed.' }
    foreach ($fragment in @('ENGINE = MergeTree', 'PARTITION BY toYYYYMM(event_time)', 'ORDER BY (event_time, api_id, event_id)', 'TTL event_time + toIntervalDay(7)')) {
        if ($schema.Code -ne 200 -or !$schema.Body.Contains($fragment)) { throw "Table engine/key/TTL contract mismatch: $fragment" }
    }
    foreach ($case in @(
        @{ Role = 'WRITER'; Sql = 'SELECT count() FROM jeecg_analytics.api_call' },
        @{ Role = 'READER'; Sql = $insert },
        @{ Role = 'WRITER'; Sql = 'CREATE TABLE jeecg_analytics.__denied (x UInt8) ENGINE=Memory' },
        @{ Role = 'READER'; Sql = 'CREATE TABLE jeecg_analytics.__denied (x UInt8) ENGINE=Memory' },
        @{ Role = 'WRITER'; Sql = 'SELECT count() FROM system.users' },
        @{ Role = 'READER'; Sql = 'SELECT count() FROM system.users' },
        @{ Role = ''; Sql = 'SELECT count() FROM jeecg_analytics.api_call' }
    )) {
        $denied = Invoke-Query $case.Sql $case.Role $credentials
        # Require ClickHouse's ACCESS_DENIED/READONLY or authentication errors, not generic 500s.
        if ($denied.Code -eq 200 -or $denied.Body -notmatch 'Code: (164|192|193|194|195|497|516)\b') {
            throw "Expected ClickHouse authorization denial for role '$($case.Role)'."
        }
    }
    $version = Invoke-Query 'SELECT version()' 'READER' $credentials
    if ($version.Code -ne 200 -or $version.Body.Trim() -ne '25.8.24.21') { throw 'Unexpected server version.' }
    $limits = Invoke-Query "SELECT getSetting('readonly') AS readonly, getSetting('max_execution_time') AS seconds, getSetting('max_threads') AS threads, getSetting('max_memory_usage') AS memory, getSetting('max_rows_to_read') AS rows, getSetting('read_overflow_mode') AS read_mode, getSetting('max_result_rows') AS results, getSetting('result_overflow_mode') AS result_mode, getSetting('max_concurrent_queries_for_user') AS concurrent FORMAT JSONEachRow" 'READER' $credentials
    if ($limits.Code -ne 200) { throw 'Cannot verify reader settings.' }
    $limit = ConvertFrom-Json $limits.Body
    if ($limit.readonly -ne 1 -or $limit.seconds -ne 3 -or $limit.threads -ne 2 -or $limit.memory -ne 268435456 -or $limit.rows -ne 10000000 -or $limit.read_mode -ne 'throw' -or $limit.results -ne 1000 -or $limit.result_mode -ne 'throw' -or $limit.concurrent -ne 1) { throw 'Reader profile limits mismatch.' }
    # Same parameterized SQL as ClickHouseStore.summary; no SQL SETTINGS with readonly=1.
    $summarySql = 'SELECT api_id, method, count() AS calls, countIf(status >= 400) AS errors, ' +
        'countIf(status >= 500) AS server_errors, avg(duration_ms) AS avg_ms, ' +
        'quantileTDigest(0.95)(duration_ms) AS p95_ms, quantileTDigest(0.99)(duration_ms) AS p99_ms ' +
        'FROM jeecg_analytics.api_call WHERE event_time >= fromUnixTimestamp64Milli({start:Int64}) ' +
        'AND event_time < fromUnixTimestamp64Milli({end:Int64}) ' +
        "AND ({api:String} = '' OR api_id = {api:String}) GROUP BY api_id, method " +
        'ORDER BY calls DESC LIMIT 1000 FORMAT JSONEachRow'
    $end = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + 1000
    $start = $end - 86400000
    $parameters = "param_start=$start&param_end=$end&param_api=$([Uri]::EscapeDataString($apiId))"
    $summary = Invoke-Query $summarySql 'READER' $credentials $parameters
    if ($summary.Code -ne 200) { throw "Summary SQL failed (HTTP $($summary.Code))." }
    $row = ConvertFrom-Json $summary.Body
    if ($row.api_id -ne $apiId -or $row.calls -ne 1 -or $row.errors -ne 0 -or $row.server_errors -ne 0 -or $row.avg_ms -ne 1 -or $row.p95_ms -ne 1 -or $row.p99_ms -ne 1) { throw 'Summary result mismatch.' }
    $global = Invoke-Query $summarySql 'READER' $credentials "param_start=$start&param_end=$end&param_api="
    if ($global.Code -ne 200) { throw "Global summary SQL failed (HTTP $($global.Code))." }
    $globalRows = @($global.Body -split "`n" | Where-Object { $_.Trim() } | ForEach-Object { ConvertFrom-Json $_ })
    if (!($globalRows | Where-Object api_id -EQ $apiId)) { throw 'Global summary did not contain the probe.' }
    Write-Host 'PASS: version, schema, synchronous HTTP write/read, scoped denials, reader limits, and parameterized 24h summary JSONEachRow (filtered/global).'
    Write-Host 'A synthetic __deployment_probe__/UUID row remains until TTL cleanup.'
}

switch ($Action) {
    'Prepare' { Initialize-Credentials }
    'Setup' {
        Initialize-Credentials
        Invoke-Compose -Arguments @('config', '--quiet')
        Invoke-Compose -Arguments @('pull', 'clickhouse')
        Invoke-Compose -Arguments @('up', '-d', '--wait', '--wait-timeout', '180', 'clickhouse')
        Invoke-Compose -Arguments @('exec', '-T', 'clickhouse', 'bash', '/opt/jeecg-init/01-schema.sh')
        Test-Deployment
    }
    'Verify' { Test-Deployment }
    'Start' {
        $null = Read-Credentials
        Invoke-Compose -Arguments @('up', '-d', '--wait', '--wait-timeout', '180', 'clickhouse')
        Test-Deployment
    }
    'Stop' { Invoke-Compose -Arguments @('stop', 'clickhouse') }
    'Status' { Invoke-Compose -Arguments @('ps', 'clickhouse') }
}
