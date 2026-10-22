[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runDir = Join-Path $projectRoot '.run'

# Stop in reverse dependency order. Nacos runs on the remote middleware host and is not managed here.
$names = @('web', 'gateway', 'ai', 'bi', 'approval', 'order', 'customer', 'system', 'auth')

foreach ($name in $names) {
    $pidPath = Join-Path $runDir "$name.pid"
    if (-not (Test-Path $pidPath)) {
        Write-Host "[$name] no pid file"
        continue
    }

    $processId = Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($processId) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            Write-Host "[$name] stopped (PID $processId)"
        } else {
            Write-Host "[$name] process $processId is not running"
        }
    }
    Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
}

Write-Host 'All platform services have been stopped.'
