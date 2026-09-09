#Requires -Version 5.1
# Integration check: requires the same SDK/JDK/signing setup as build-version.ps1.
param([string]$Version = 'HEAD')
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$script = Join-Path $PSScriptRoot 'build-version.ps1'
$head = & git -C $repo rev-parse HEAD
$status = (& git -C $repo status --porcelain) -join "`n"
$diff = (& git -C $repo diff --binary HEAD) -join "`n"
$sdk = $env:ANDROID_HOME
$signing = $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES
$currentApk = Join-Path $repo 'app/build/outputs/apk/release/app-release.apk'
$oldHash = if (Test-Path $currentApk) { (Get-FileHash $currentApk).Hash } else { $null }
$rejected = $false
try { & $script -Version ('missing-' + [guid]::NewGuid().ToString('N')) } catch { $rejected = $true }
if (-not $rejected) { throw 'Invalid revision was accepted.' }
$rejected = $false
try { & $script -Version $Version -SigningProperties (Join-Path $repo '.local/nonexistent-signing.properties') } catch { $rejected = $true }
if (-not $rejected) { throw 'Missing signing configuration was accepted.' }
& $script -Version $Version
if ((& git -C $repo rev-parse HEAD) -ne $head -or
    ((& git -C $repo status --porcelain) -join "`n") -ne $status -or
    ((& git -C $repo diff --binary HEAD) -join "`n") -ne $diff) { throw 'Working tree or HEAD changed.' }
if ($env:ANDROID_HOME -ne $sdk -or $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES -ne $signing) { throw 'Caller environment changed.' }
if ($null -ne $oldHash -and (Get-FileHash $currentApk).Hash -ne $oldHash) { throw 'Existing APK was overwritten.' }
Write-Host 'PASS: rejection checks, signed build, preserved HEAD/working tree/environment/existing APK.'
