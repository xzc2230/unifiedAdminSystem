[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status')]
    [string]$Action = 'Status'
)

$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRuntime = Join-Path $taskRoot '.runtime'
$taskStateFile = Join-Path $taskRuntime 'services.json'
New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
$taskState = @{}
if (Test-Path -LiteralPath $taskStateFile) {
    $taskState = Get-Content -LiteralPath $taskStateFile -Raw | ConvertFrom-Json -AsHashtable
}

function Get-ManagedProcess([string]$Name) {
    if (-not $taskState.ContainsKey($Name)) { return $null }
    $record = $taskState[$Name]
    $process = Get-Process -Id $record.Id -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $record.StartTicks) {
        throw "PID reused for $Name; refusing to manage it."
    }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.Id)"
    if (-not $details.CommandLine -or -not $details.CommandLine.Contains($record.Marker)) {
        throw "Process identity mismatch for $Name; refusing to manage it."
    }
    return $process
}

function Start-ManagedProcess([string]$Name, [int]$Port, [string]$Executable,
    [string[]]$Arguments, [string]$Directory, [string]$Marker) {
    $existing = Get-ManagedProcess $Name
    if ($existing) { Write-Host "$Name already running: PID $($existing.Id)"; return }
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $Port is occupied by an unmanaged process; inspect it before restarting." }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $stdout = Join-Path $taskRuntime "$Name-$stamp.stdout.log"
    $stderr = Join-Path $taskRuntime "$Name-$stamp.stderr.log"
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $taskState[$Name] = @{
        Id = $process.Id
        StartTicks = $process.StartTime.ToUniversalTime().Ticks.ToString()
        Marker = $Marker
        Stdout = $stdout
        Stderr = $stderr
    }
    $taskState | ConvertTo-Json | Set-Content -LiteralPath $taskStateFile -Encoding utf8
    Write-Host "$Name started: PID $($process.Id)"
}

function Get-Readiness {
    $results = foreach ($check in @(
        @{ Name = 'Frontend'; Url = 'http://127.0.0.1:13100/' },
        @{ Name = 'Backend'; Url = 'http://127.0.0.1:18080/jeecg-boot/sys/randomImage/local-service-check' },
        @{ Name = 'Proxy'; Url = 'http://127.0.0.1:13100/jeecgboot/sys/randomImage/local-proxy-check' }
    )) {
        try {
            $response = Invoke-WebRequest -Uri $check.Url -TimeoutSec 3 -SkipHttpErrorCheck
            $ready = [int]$response.StatusCode -eq 200
            if ($ready -and $check.Name -ne 'Frontend') {
                $ready = ($response.Content | ConvertFrom-Json).success -eq $true
            }
            [PSCustomObject]@{ Service = $check.Name; Ready = $ready; HTTP = [int]$response.StatusCode }
        } catch {
            [PSCustomObject]@{ Service = $check.Name; Ready = $false; HTTP = 0 }
        }
    }
    return $results
}

if ($Action -eq 'Stop') {
    foreach ($name in @('frontend', 'backend')) {
        $process = Get-ManagedProcess $name
        if ($process) { Stop-Process -Id $process.Id; Write-Host "$name stopped: PID $($process.Id)" }
        $taskState.Remove($name)
    }
    $taskState | ConvertTo-Json | Set-Content -LiteralPath $taskStateFile -Encoding utf8
    Write-Host 'Database and Redis containers retained.'
    exit 0
}

if ($Action -eq 'Start') {
    $taskJava = 'C:\Program Files\Java\jdk-21\bin\java.exe'
    $taskJar = Join-Path $taskRoot 'upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar'
    $taskFrontend = Join-Path $taskRoot 'upstream/JeecgBoot/jeecgboot-vue3'
    $taskVite = Join-Path $taskFrontend 'node_modules/vite/bin/vite.js'
    $taskConfig = Join-Path $PSScriptRoot 'application-local.yml'
    $taskEnvFile = Join-Path $taskRuntime 'local.env'
    foreach ($path in @($taskJava, $taskJar, $taskVite, $taskConfig, $taskEnvFile)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Required file missing: $path" }
    }
    $taskNode = (Get-Command node -ErrorAction Stop).Source
    & docker compose --env-file $taskEnvFile -f (Join-Path $PSScriptRoot 'compose.yaml') up -d --wait --wait-timeout 150
    if ($LASTEXITCODE -ne 0) { throw 'Database/Redis startup failed.' }
    $taskTmp = Join-Path $taskRuntime 'tmp'
    New-Item -ItemType Directory -Force -Path $taskTmp | Out-Null
    $taskPasswordLine = @(Get-Content -LiteralPath $taskEnvFile | Where-Object { $_.StartsWith('JEECG_LOCAL_DB_PASSWORD=') })
    if ($taskPasswordLine.Count -ne 1) { throw 'Local database password entry missing or duplicated.' }
    $taskPreviousPassword = $env:JEECG_LOCAL_DB_PASSWORD
    try {
        $env:JEECG_LOCAL_DB_PASSWORD = $taskPasswordLine[0].Substring('JEECG_LOCAL_DB_PASSWORD='.Length)
        Start-ManagedProcess 'backend' 18080 $taskJava @(
            ('"-Djdk.net.unixdomain.tmpdir=' + $taskTmp + '"'), '-jar', ('"' + $taskJar + '"'),
            '--spring.profiles.active=dev', ('"--spring.config.additional-location=file:' + $taskConfig + '"')
        ) $taskRoot $taskJar
    } finally { $env:JEECG_LOCAL_DB_PASSWORD = $taskPreviousPassword }
    # Match pnpm's generated Vite launcher without leaving a wrapper process running.
    $taskPreviousNodePath = $env:NODE_PATH
    try {
        $env:NODE_PATH = (Join-Path $taskFrontend 'node_modules/.pnpm/node_modules')
        if ($taskPreviousNodePath) { $env:NODE_PATH += [IO.Path]::PathSeparator + $taskPreviousNodePath }
        Start-ManagedProcess 'frontend' 13100 $taskNode @(
            ('"' + $taskVite + '"'), '--host', '127.0.0.1', '--port', '13100', '--strictPort'
        ) $taskFrontend $taskVite
    } finally { $env:NODE_PATH = $taskPreviousNodePath }
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(180)
    do {
        foreach ($name in @('backend', 'frontend')) {
            if (-not (Get-ManagedProcess $name)) { throw "$name exited; inspect logs listed in $taskStateFile" }
        }
        $taskHealth = @(Get-Readiness)
        if (@($taskHealth | Where-Object { -not $_.Ready }).Count -eq 0) {
            $taskHealth | Format-Table
            exit 0
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    $taskHealth | Format-Table
    throw "Readiness timed out; inspect logs listed in $taskStateFile"
}

$taskHealth = @(Get-Readiness)
$taskHealth | Format-Table
if (@($taskHealth | Where-Object { -not $_.Ready }).Count -gt 0) { exit 1 }
