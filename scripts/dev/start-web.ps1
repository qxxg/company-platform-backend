[CmdletBinding()]
param(
    [switch]$Install,
    [switch]$Restart,
    [int]$Port = 5173
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..')).Path
# 前端是独立仓库，默认与后端同级放在 company-platform-web；
# 保留仓库内 platform-web 作为兼容回退，便于单仓开发。
$webRoot = Join-Path $workspaceRoot 'company-platform-web'
if (-not (Test-Path $webRoot)) {
    $webRoot = Join-Path $projectRoot 'platform-web'
}
if (-not (Test-Path $webRoot)) {
    throw "前端目录不存在：$webRoot（请克隆 company-platform-frontend 仓库）"
}
$runDir = Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$pidPath = Join-Path $runDir 'web.pid'
$outLog = Join-Path $runDir 'web.log'
$errLog = Join-Path $runDir 'web.err.log'

function Stop-WebProcess {
    if (-not (Test-Path $pidPath)) {
        return
    }
    $existingPid = Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingPid) {
        $process = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $existingPid -Force -ErrorAction SilentlyContinue
            Write-Host "[web] stopped (PID $existingPid)"
        }
    }
    Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
}

if ($Restart) {
    Stop-WebProcess
}

if (Test-Path $pidPath) {
    $existingPid = Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
        Write-Host "[web] already running (PID $existingPid, port $Port): http://localhost:$Port"
        exit 0
    }
    Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
}

$nodeModules = Join-Path $webRoot 'node_modules'
if ($Install -or -not (Test-Path $nodeModules)) {
    $npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $npm) {
        throw 'npm.cmd was not found. Install Node.js 24+ and add it to PATH.'
    }
    Push-Location $webRoot
    try {
        & $npm install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $node) {
    throw 'node.exe was not found. Install Node.js 24+ and add it to PATH.'
}

$viteEntry = Join-Path $webRoot 'node_modules\vite\bin\vite.js'
if (-not (Test-Path $viteEntry)) {
    throw "Vite entry not found: $viteEntry. Run this script with -Install."
}

# 直接启动 node，而不是 npm.cmd：这样可以拿到真正的 Vite 进程 PID，
# 停止脚本不会只杀掉 cmd 包装进程而留下后台 node。
$process = Start-Process -FilePath $node `
    -ArgumentList @(
        "`"$viteEntry`"",
        '--host', '0.0.0.0',
        '--port', "$Port",
        '--strictPort'
    ) `
    -WorkingDirectory $webRoot `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -WindowStyle Hidden `
    -PassThru

Set-Content -Path $pidPath -Value $process.Id

$url = "http://127.0.0.1:$Port/"
$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    if ($process.HasExited) {
        Write-Host '---- web log tail ----'
        Get-Content $outLog -Tail 40 -ErrorAction SilentlyContinue
        Get-Content $errLog -Tail 40 -ErrorAction SilentlyContinue
        throw "[web] Vite exited with code $($process.ExitCode)"
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Host "[web] started (PID $($process.Id)): http://localhost:$Port/"
            Write-Host "[web] login: http://localhost:$Port/login"
            Write-Host '[web] OAuth callback must be registered for the current origin on pc-web.'
            exit 0
        }
    } catch {
    }
    Start-Sleep -Seconds 1
}

Stop-WebProcess
throw "[web] Vite did not become healthy within 60 seconds. See $outLog and $errLog."
