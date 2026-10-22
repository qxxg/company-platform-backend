[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..')).Path

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

function Resolve-Maven {
    $mavenHome = Join-Path $workspaceRoot '.tools\apache-maven-3.10.0'
    $candidate = Join-Path $mavenHome 'bin\mvn.cmd'
    if (Test-Path $candidate) {
        return $candidate
    }
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw 'Maven was not found. Install it under .tools\apache-maven-3.10.0 or add mvn.cmd to PATH.'
}

$java = Resolve-Java
$env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
$maven = Resolve-Maven

Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host "Maven     = $maven"
Write-Host "Project   = $projectRoot"

Push-Location $projectRoot
try {
    & $maven -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
