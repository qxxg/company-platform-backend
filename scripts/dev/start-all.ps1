[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$Restart,
    [string]$NacosServer = 'http://192.168.3.44:8848',
    [string]$NacosNamespace = 'public',
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..')).Path
$runDir = Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

function Resolve-Java {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $zuluRoot = Join-Path $workspaceRoot '.tools\zulu27-extract'
    $candidate = Get-ChildItem -Path $zuluRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if (-not $candidate) {
        throw 'JDK 27 was not found. Set JAVA_HOME or install it under .tools\zulu27-extract.'
    }
    return $candidate
}

function Start-PlatformService {
    param(
        [string]$Name,
        [string]$Jar,
        [int]$Port,
        [string]$HealthUrl
    )

    $pidPath = Join-Path $runDir "$Name.pid"
    if (Test-Path $pidPath) {
        $existingPid = Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
            Write-Host "[$Name] already running (PID $existingPid, port $Port)"
            return $null
        }
        Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
    }

    $jarPath = Join-Path $projectRoot $Jar
    if (-not (Test-Path $jarPath)) {
        throw "[$Name] jar not found: $jarPath. Run this script with -Build first."
    }

    $outLog = Join-Path $runDir "$Name.log"
    $errLog = Join-Path $runDir "$Name.err.log"
    $process = Start-Process -FilePath $java `
        -ArgumentList @('-jar', "`"$jarPath`"") `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $pidPath -Value $process.Id
    Write-Host "[$Name] starting (PID $($process.Id), port $Port)"
    return $process
}

function Wait-PlatformService {
    param(
        [string]$Name,
        [string]$HealthUrl,
        $Process,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process -and $Process.HasExited) {
            Write-Host "---- $Name log tail ----"
            Get-Content (Join-Path $runDir "$Name.log") -Tail 40 -ErrorAction SilentlyContinue
            throw "[$Name] exited with code $($Process.ExitCode)"
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $HealthUrl -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "[$Name] healthy: $HealthUrl"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    Write-Host "---- $Name log tail ----"
    Get-Content (Join-Path $runDir "$Name.log") -Tail 60 -ErrorAction SilentlyContinue
    throw "[$Name] did not become healthy within $TimeoutSeconds seconds"
}

function Wait-GatewayDiscovery {
    param(
        [string]$NacosBaseUrl,
        [string]$Namespace,
        [string[]]$ExpectedServices,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $listUrl = "$NacosBaseUrl/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=$Namespace"
            $list = Invoke-RestMethod -Uri $listUrl -TimeoutSec 3
            $services = @($list.doms)
            $missing = @($ExpectedServices | Where-Object { $services -notcontains $_ })
            if ($missing.Count -eq 0) {
                Write-Host "[nacos] services registered: $($ExpectedServices -join ', ')"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    throw "Nacos did not report all services within $TimeoutSeconds seconds"
}

function Wait-Nacos {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds
    )

    $healthUrl = "$BaseUrl/nacos/actuator/health"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
            if ($health.status -eq 'UP') {
                Write-Host "[nacos] healthy: $healthUrl"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    throw "Nacos is not reachable at $healthUrl. Start Nacos 3.0.3 on 192.168.3.44 or pass -NacosServer."
}

$java = Resolve-Java

if ($Restart) {
    & (Join-Path $PSScriptRoot 'stop-all.ps1')
}
if ($Build) {
    & (Join-Path $PSScriptRoot 'build.ps1')
}

$services = @(
    [pscustomobject]@{
        Name = 'auth'
        Jar = 'platform-auth\target\platform-auth-1.0.0-SNAPSHOT.jar'
        Port = 9000
        Health = 'http://localhost:9000/actuator/health'
    },
    [pscustomobject]@{
        Name = 'system'
        Jar = 'platform-system\target\platform-system-1.0.0-SNAPSHOT.jar'
        Port = 9101
        Health = 'http://localhost:9101/actuator/health'
    },
    [pscustomobject]@{
        Name = 'customer'
        Jar = 'platform-customer\target\platform-customer-1.0.0-SNAPSHOT.jar'
        Port = 9103
        Health = 'http://localhost:9103/actuator/health'
    },
    [pscustomobject]@{
        Name = 'order'
        Jar = 'platform-order\target\platform-order-1.0.0-SNAPSHOT.jar'
        Port = 9102
        Health = 'http://localhost:9102/actuator/health'
    },
    [pscustomobject]@{
        Name = 'approval'
        Jar = 'platform-approval\target\platform-approval-1.0.0-SNAPSHOT.jar'
        Port = 9104
        Health = 'http://localhost:9104/actuator/health'
    },
    [pscustomobject]@{
        Name = 'bi'
        Jar = 'platform-bi\target\platform-bi-1.0.0-SNAPSHOT.jar'
        Port = 9105
        Health = 'http://localhost:9105/actuator/health'
    },
    [pscustomobject]@{
        Name = 'ai'
        Jar = 'platform-ai\target\platform-ai-1.0.0-SNAPSHOT.jar'
        Port = 9106
        Health = 'http://localhost:9106/actuator/health'
    },
    [pscustomobject]@{
        Name = 'gateway'
        Jar = 'platform-gateway\target\platform-gateway-1.0.0-SNAPSHOT.jar'
        Port = 8080
        Health = 'http://localhost:8080/actuator/health'
    }
)

# Nacos must be reachable before the services register themselves.
if (-not $NacosServer.StartsWith('http://') -and -not $NacosServer.StartsWith('https://')) {
    $NacosServer = "http://$NacosServer"
}
$nacosAddress = ([uri]$NacosServer).Authority
$env:NACOS_SERVER_ADDR = $nacosAddress
Wait-Nacos -BaseUrl $NacosServer -TimeoutSeconds $TimeoutSeconds

$authServices = $services | Where-Object { $_.Name -in @('auth', 'system', 'customer', 'order', 'approval', 'bi', 'ai') }
$processes = @{}
foreach ($service in $authServices) {
    $processes[$service.Name] = Start-PlatformService -Name $service.Name -Jar $service.Jar -Port $service.Port -HealthUrl $service.Health
}
foreach ($service in $authServices) {
    Wait-PlatformService -Name $service.Name -HealthUrl $service.Health -Process $processes[$service.Name] -TimeoutSeconds $TimeoutSeconds
}

$gateway = $services | Where-Object { $_.Name -eq 'gateway' }
$gatewayProcess = Start-PlatformService -Name $gateway.Name -Jar $gateway.Jar -Port $gateway.Port -HealthUrl $gateway.Health
Wait-PlatformService -Name $gateway.Name -HealthUrl $gateway.Health -Process $gatewayProcess -TimeoutSeconds $TimeoutSeconds
Wait-GatewayDiscovery -NacosBaseUrl $NacosServer -Namespace $NacosNamespace -ExpectedServices @(
    'platform-auth',
    'platform-system',
    'platform-customer',
    'platform-order',
    'platform-approval',
    'platform-bi',
    'platform-ai',
    'platform-gateway'
) -TimeoutSeconds $TimeoutSeconds

Write-Host ''
& (Join-Path $PSScriptRoot 'status.ps1')
