<#
.SYNOPSIS
  Verifies flavor isolation of the built debug APKs.

.DESCRIPTION
  Checks that:
   - the Offline APK has no INTERNET permission and the Exchange APK has it,
   - the Offline APK contains no exchange classes, resources or provider strings,
   - neither APK contains Material, Google Play Services, Firebase, Play Core / Play Integrity,
     Play Billing, ML Kit or other proprietary Google SDK classes,
   - both APKs have distinct application IDs and the expected labels.
  Requires ANDROID_HOME (build-tools 37.0.0, cmdline-tools/latest) and JAVA_HOME (for apkanalyzer).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\verify-flavors.ps1            # build, then verify
  powershell -ExecutionPolicy Bypass -File scripts\verify-flavors.ps1 -SkipBuild # verify existing APKs
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:ANDROID_HOME) { throw 'ANDROID_HOME is not set' }
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set' }

$aapt2 = Join-Path $env:ANDROID_HOME 'build-tools\37.0.0\aapt2.exe'
$apkanalyzer = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\apkanalyzer.bat'
foreach ($tool in @($aapt2, $apkanalyzer)) { if (-not (Test-Path $tool)) { throw "Missing tool: $tool" } }

Push-Location $root
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat assembleOfflineDebug assembleExchangeDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
    }

    $offline = Join-Path $root 'app\build\outputs\apk\offline\debug\app-offline-debug.apk'
    $exchange = Join-Path $root 'app\build\outputs\apk\exchange\debug\app-exchange-debug.apk'
    foreach ($apk in @($offline, $exchange)) { if (-not (Test-Path $apk)) { throw "APK not found: $apk" } }

    $script:failures = 0
    function Check([bool]$condition, [string]$message) {
        if ($condition) { Write-Host "PASS  $message" -ForegroundColor Green }
        else { Write-Host "FAIL  $message" -ForegroundColor Red; $script:failures++ }
    }
    function HasMatch($lines, [string]$pattern) { return @($lines | Where-Object { $_ -match $pattern }).Count -gt 0 }

    # Raw byte search inside dex files and resources.arsc (UTF-8/ASCII and UTF-16LE).
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    function ApkContainsText([string]$apk, [string[]]$needles) {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
        try {
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -notmatch '^(classes\d*\.dex|resources\.arsc)$') { continue }
                $stream = $entry.Open()
                $ms = New-Object System.IO.MemoryStream
                $stream.CopyTo($ms); $stream.Dispose()
                $bytes = $ms.ToArray()
                $latin = [System.Text.Encoding]::GetEncoding(28591).GetString($bytes)
                $utf16 = [System.Text.Encoding]::Unicode.GetString($bytes)
                foreach ($needle in $needles) {
                    if ($latin.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                        $utf16.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                        return "$needle in $($entry.FullName)"
                    }
                }
            }
        } finally { $zip.Dispose() }
        return $null
    }

    Write-Host "`n== Identity"
    $offBadging = & $aapt2 dump badging $offline
    $exBadging = & $aapt2 dump badging $exchange
    $offId = ([regex]::Match(($offBadging -join "`n"), "package: name='([^']+)'")).Groups[1].Value
    $exId = ([regex]::Match(($exBadging -join "`n"), "package: name='([^']+)'")).Groups[1].Value
    Write-Host "      offline  applicationId = $offId"
    Write-Host "      exchange applicationId = $exId"
    Check ($offId -ne '' -and $exId -ne '' -and $offId -ne $exId) 'Application IDs are distinct'
    Check (HasMatch $offBadging "application-label:'Calculator Offline'") 'Offline label is "Calculator Offline"'
    Check (HasMatch $exBadging "application-label:'Calculator Exchange'") 'Exchange label is "Calculator Exchange"'
    Check (HasMatch $offBadging "targetSdkVersion:'37'") 'Offline targetSdkVersion 37'
    Check (HasMatch $offBadging "minSdkVersion:'24'") 'Offline minSdkVersion 24'
    Check (HasMatch $exBadging "targetSdkVersion:'37'") 'Exchange targetSdkVersion 37'
    Check (HasMatch $exBadging "minSdkVersion:'24'") 'Exchange minSdkVersion 24'

    Write-Host "`n== Permissions"
    $offPerms = & $aapt2 dump permissions $offline
    $exPerms = & $aapt2 dump permissions $exchange
    $offPerms | ForEach-Object { Write-Host "      offline : $_" }
    $exPerms | ForEach-Object { Write-Host "      exchange: $_" }
    Check (-not (HasMatch $offPerms 'android\.permission\.INTERNET')) 'Offline APK has NO INTERNET permission'
    Check (-not (HasMatch $offPerms 'ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE')) 'Offline APK has no network-state permissions'
    Check (HasMatch $exPerms 'android\.permission\.INTERNET') 'Exchange APK has INTERNET permission'

    Write-Host "`n== Classes"
    $offPkgs = & $apkanalyzer dex packages --defined-only $offline
    $exPkgs = & $apkanalyzer dex packages --defined-only $exchange
    Check (-not (HasMatch $offPkgs 'com\.scalc\.calculator\.exchange')) 'Offline APK contains no com.scalc.calculator.exchange classes'
    Check (HasMatch $exPkgs 'com\.scalc\.calculator\.exchange') 'Exchange APK contains com.scalc.calculator.exchange classes'
    # Material, Play Services, Firebase, Play Core / Play Integrity, Play Billing, ML Kit, GMS/Firebase
    # transport, other proprietary Google client libraries and ads SDKs.
    $forbidden = 'androidx\.compose\.material|com\.google\.android\.material|com\.google\.android\.gms|com\.google\.firebase|com\.google\.android\.play|com\.android\.billingclient|com\.google\.mlkit|com\.google\.android\.datatransport|com\.google\.android\.libraries|com\.google\.ads'
    # Self-check of the pattern: it must catch the prohibited SDKs and must not flag the legitimate
    # androidx ListenableFuture package (com.google.common.util.concurrent) present in both APKs.
    foreach ($n in @('com.google.android.play.core.integrity', 'com.android.billingclient.api', 'com.google.mlkit.common',
                     'com.google.android.datatransport.runtime', 'com.google.android.gms.common', 'com.google.firebase.analytics')) {
        if ($n -notmatch $forbidden) { throw "Forbidden-class pattern does not match $n" }
    }
    if ('com.google.common.util.concurrent' -match $forbidden) { throw 'Forbidden-class pattern wrongly matches com.google.common.util.concurrent' }
    Check (-not (HasMatch $offPkgs $forbidden)) 'Offline APK: no Material / Play Services / Firebase / Play Core / Billing / ML Kit classes'
    Check (-not (HasMatch $exPkgs $forbidden)) 'Exchange APK: no Material / Play Services / Firebase / Play Core / Billing / ML Kit classes'

    Write-Host "`n== Resources and strings"
    $offRes = & $aapt2 dump resources $offline
    Check (-not (HasMatch $offRes 'string/(exch_|tab_exchange)')) 'Offline APK has no exchange string resources'
    $hit = ApkContainsText $offline @('frankfurter', 'eurofxref', 'ecb.europa', 'exchange_cache', 'com/scalc/calculator/exchange')
    Check ($null -eq $hit) ("Offline APK contains no exchange provider strings" + $(if ($hit) { " (found: $hit)" } else { '' }))
    $exHit = ApkContainsText $exchange @('api.frankfurter.dev')
    Check ($null -ne $exHit) 'Exchange APK references the exchange-rate provider'

    Write-Host ''
    if ($script:failures -gt 0) { Write-Host "$script:failures check(s) FAILED" -ForegroundColor Red; exit 1 }
    Write-Host 'All flavor isolation checks passed.' -ForegroundColor Green
} finally {
    Pop-Location
}
