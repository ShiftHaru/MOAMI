#Requires -Version 5.1
<#
.SYNOPSIS
Build a locally available Git tag or commit without switching the working tree.
.EXAMPLE
.\tools\build-version.ps1 -Version c0662f5
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Version,
    [ValidateSet('Release', 'Debug')][string]$Configuration = 'Release',
    [string]$SigningProperties = $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$utf8 = New-Object System.Text.UTF8Encoding($false)
$originalSdk = $env:ANDROID_HOME
$originalSigning = $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES
$run = $null
$report = $null

try {
    $resolved = & git -C $repo rev-parse --verify --end-of-options "$Version^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0 -or "$resolved" -notmatch '^[0-9a-f]{40}$') {
        throw 'Version is not a locally available Git tag or commit. No fetch was performed.'
    }
    $commit = "$resolved"
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        throw 'Set JAVA_HOME to a compatible JDK installation (current project: JDK 21 launcher).'
    }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $sdk = Get-Item -LiteralPath $env:ANDROID_HOME
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk.FullName 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Install Android SDK Build Tools before building.' }
    $aapt = Join-Path $buildTools.FullName 'aapt.exe'
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if (-not (Test-Path $aapt) -or -not (Test-Path $apksigner)) { throw 'APK verification tools are missing.' }
    if ($Configuration -eq 'Release') {
        if (-not $SigningProperties) { $SigningProperties = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'keyStore/signing.properties' }
        $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES = (Get-Item -LiteralPath $SigningProperties).FullName
    }

    # A unique directory prevents stale APKs and collisions between repeated builds.
    $run = Join-Path $repo ('.local/version-builds/' + $commit.Substring(0, 12) + '/' +
        $Configuration.ToLowerInvariant() + '-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $run -Force | Out-Null
    $report = [ordered]@{ status = 'building'; commit = $commit; configuration = $Configuration;
        startedUtc = [DateTime]::UtcNow.ToString('o') }
    [IO.File]::WriteAllText((Join-Path $run 'build-info.json'), ($report | ConvertTo-Json), $utf8)
    $archive = Join-Path $run 'source.zip'
    & git -C $repo archive --format=zip "--output=$archive" $commit
    if ($LASTEXITCODE -ne 0) { throw 'Git source export failed.' }
    $source = Join-Path $run 'source'
    Expand-Archive -LiteralPath $archive -DestinationPath $source
    $gradle = Join-Path $source 'gradlew.bat'
    $appBuild = Join-Path $source 'app/build.gradle.kts'
    if (-not (Test-Path $gradle) -or -not (Test-Path $appBuild)) { throw 'This revision does not contain the supported Android app layout.' }
    if ($Configuration -eq 'Release' -and -not ([IO.File]::ReadAllText($appBuild).Contains('BROWSERDOWNLOADER_SIGNING_PROPERTIES'))) {
        throw 'This revision does not support external release signing. Use Debug or select a compatible revision.'
    }
    $log = Join-Path $run 'build.log'
    Write-Host "Building $commit ($Configuration). Log: $log"
    Push-Location $source
    try {
        # Windows PowerShell can turn native stderr into terminating errors; use the exit code.
        $ErrorActionPreference = 'Continue'
        & $gradle ":app:assemble$Configuration" --no-configuration-cache *> $log
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($buildExit -ne 0) { throw "Gradle failed (exit $buildExit). See build.log." }
    } finally { $ErrorActionPreference = 'Stop'; Pop-Location }
    $apkDir = Join-Path $source ('app/build/outputs/apk/' + $Configuration.ToLowerInvariant())
    $metadata = Get-Content -LiteralPath (Join-Path $apkDir 'output-metadata.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($elements.Count -ne 1) { throw 'Expected one APK; split APK builds are not supported.' }
    $element = $elements[0]
    if ([IO.Path]::GetFileName($element.outputFile) -ne $element.outputFile) { throw 'Invalid APK metadata path.' }
    $apk = Join-Path $apkDir $element.outputFile
    & $apksigner verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $badging = @(& $aapt dump badging $apk)
    if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed.' }
    $packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if ($packageLine -notmatch "versionCode='([0-9]+)' versionName='([^']*)'") { throw 'APK version fields are missing.' }
    $versionCode = [int]$Matches[1]; $versionName = $Matches[2]
    if ($versionCode -ne $element.versionCode -or $versionName -ne $element.versionName) { throw 'APK version disagrees with build metadata.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($apk)
    try { $abis = @($zip.Entries.FullName | Where-Object { $_ -match '^lib/[^/]+/[^/]+$' } |
        ForEach-Object { $_.Split('/')[1] } | Sort-Object -Unique) } finally { $zip.Dispose() }
    if ($abis.Count -eq 0) { throw 'APK contains no native runtime.' }
    $outputName = 'MOAMI-' + $commit.Substring(0, 12) + '-' + $Configuration.ToLowerInvariant() + '.apk'
    $output = Join-Path $run $outputName
    Copy-Item -LiteralPath $apk -Destination $output
    $report.status = 'success'
    $report.versionName = $versionName; $report.versionCode = $versionCode
    $report.applicationId = $metadata.applicationId; $report.abis = $abis
    $report.apk = $outputName; $report.sha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    $report.bytes = (Get-Item -LiteralPath $output).Length
    $report.signatureVerified = $true; $report.finishedUtc = [DateTime]::UtcNow.ToString('o')
    [IO.File]::WriteAllText((Join-Path $run 'build-info.json'), ($report | ConvertTo-Json -Depth 4), $utf8)
    Write-Host "SUCCESS: $versionName ($versionCode), $($abis -join ', ')"
    Write-Host "APK: $output"
    Write-Host "SHA-256: $($report.sha256)"
} catch {
    if ($null -ne $report) {
        $report.status = 'failed'; $report.finishedUtc = [DateTime]::UtcNow.ToString('o')
        [IO.File]::WriteAllText((Join-Path $run 'build-info.json'), ($report | ConvertTo-Json -Depth 4), $utf8)
    }
    throw
} finally {
    $env:ANDROID_HOME = $originalSdk
    $env:BROWSERDOWNLOADER_SIGNING_PROPERTIES = $originalSigning
}
