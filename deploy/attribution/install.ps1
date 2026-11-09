[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User,
    [string]$Server = '127.0.0.1',
    [int]$Port = 3306,
    [string]$MySql = 'mysql',
    [string]$RoleId
)

$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskSchema = Join-Path $taskRoot 'upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-attribution/src/main/resources/db/attribution/001-schema.sql'
$taskMenu = Join-Path $PSScriptRoot '002-menu.sql'
if (-not $env:MYSQL_PWD) { throw 'Set MYSQL_PWD in this process before importing. Do not pass passwords as arguments.' }
if ($RoleId -and $RoleId -notmatch '^[A-Za-z0-9_-]{1,64}$') { throw 'RoleId must be an existing role ID containing only letters, digits, underscore or hyphen.' }
foreach ($taskSqlFile in @($taskSchema, $taskMenu)) {
    if (-not (Test-Path -LiteralPath $taskSqlFile)) { throw "SQL file missing: $taskSqlFile" }
}
$taskMysqlArguments = @('--default-character-set=utf8mb4', '--protocol=TCP', '-h', $Server, '-P', "$Port", '-u', $User, $Database)
function Invoke-AttributionSql([string]$Sql) {
    $taskPreviousEncoding = $OutputEncoding
    try {
        # Windows PowerShell 5.1 defaults to ASCII when piping to native executables.
        $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $Sql | & $MySql @taskMysqlArguments
        if ($LASTEXITCODE -ne 0) { throw 'MySQL import failed. Existing committed data is retained; inspect the error before retrying.' }
    } finally { $OutputEncoding = $taskPreviousEncoding }
}
foreach ($taskSqlFile in @($taskSchema, $taskMenu)) {
    Invoke-AttributionSql (Get-Content -LiteralPath $taskSqlFile -Raw -Encoding utf8)
    Write-Host "Imported: $([IO.Path]::GetFileName($taskSqlFile))"
}
if ($RoleId) {
    $taskRoleCount = & $MySql @taskMysqlArguments '-Nse' "SELECT COUNT(*) FROM sys_role WHERE id='$RoleId'"
    if ($LASTEXITCODE -ne 0 -or "$taskRoleCount".Trim() -ne '1') { throw 'RoleId does not identify an existing role. No role grants were changed.' }
    Invoke-AttributionSql @"
INSERT INTO sys_role_permission (id,role_id,permission_id,data_rule_ids,operate_date,operate_ip)
SELECT REPLACE(UUID(),'-',''),'$RoleId',p.id,NULL,NOW(),NULL
FROM sys_permission p
WHERE (p.id='attr_menu_root' OR p.parent_id='attr_menu_root')
AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id='$RoleId' AND rp.permission_id=p.id);
"@
    Write-Host 'Granted the attribution menu and its six permissions to the specified role. Sign in again to refresh permissions.'
}
Invoke-AttributionSql "SELECT id,name,url,component,perms FROM sys_permission WHERE id='attr_menu_root' OR parent_id='attr_menu_root' ORDER BY sort_no;"
