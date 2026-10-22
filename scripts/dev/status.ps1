[CmdletBinding()]
param(
    [switch]$Json,
    [string]$NacosServer = 'http://192.168.3.44:8848'
)

$ErrorActionPreference = 'Continue'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runDir = Join-Path $projectRoot '.run'

$services = @(
    [pscustomobject]@{ Name = 'web'; Port = 5173; Health = 'http://127.0.0.1:5173/' },
    [pscustomobject]@{ Name = 'auth'; Port = 9000; Health = 'http://localhost:9000/actuator/health' },
    [pscustomobject]@{ Name = 'system'; Port = 9101; Health = 'http://localhost:9101/actuator/health' },
    [pscustomobject]@{ Name = 'customer'; Port = 9103; Health = 'http://localhost:9103/actuator/health' },
    [pscustomobject]@{ Name = 'order'; Port = 9102; Health = 'http://localhost:9102/actuator/health' },
    [pscustomobject]@{ Name = 'approval'; Port = 9104; Health = 'http://localhost:9104/actuator/health' },
    [pscustomobject]@{ Name = 'bi'; Port = 9105; Health = 'http://localhost:9105/actuator/health' },
    [pscustomobject]@{ Name = 'ai'; Port = 9106; Health = 'http://localhost:9106/actuator/health' },
    [pscustomobject]@{ Name = 'gateway'; Port = 8080; Health = 'http://localhost:8080/actuator/health' }
)

$nacosHealthy = $false
if (-not $NacosServer.StartsWith('http://') -and -not $NacosServer.StartsWith('https://')) {
    $NacosServer = "http://$NacosServer"
}
try {
    $nacos = Invoke-RestMethod -Uri "$NacosServer/nacos/actuator/health" -TimeoutSec 2
    $nacosHealthy = $nacos.status -eq 'UP'
} catch {
    $nacosHealthy = $false
}

$result = @()
$result += [pscustomobject]@{
    Service = 'nacos'
    Port = 8848
    PID = 'remote'
    Running = $nacosHealthy
    Healthy = $nacosHealthy
}

$result += foreach ($service in $services) {
    $pidPath = Join-Path $runDir "$($service.Name).pid"
    $servicePid = if (Test-Path $pidPath) { Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1 } else { $null }
    $running = $false
    if ($servicePid) {
        $running = [bool](Get-Process -Id $servicePid -ErrorAction SilentlyContinue)
    }

    $healthy = $false
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $service.Health -TimeoutSec 2
        $healthy = $response.StatusCode -eq 200
    } catch {
        $healthy = $false
    }

    [pscustomobject]@{
        Service = $service.Name
        Port = $service.Port
        PID = $servicePid
        Running = $running
        Healthy = $healthy
    }
}

if ($Json) {
    $result | ConvertTo-Json
} else {
    $result | Format-Table -AutoSize
}
