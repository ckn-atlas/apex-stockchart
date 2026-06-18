$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $Root ".tools\jdk-21.0.11+10"
$env:ANDROID_HOME = Join-Path $Root ".tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Set-Location $Root
.\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease

Write-Host ""
Write-Host "Release APK:"
Write-Host (Join-Path $Root "app\build\outputs\apk\release\app-release.apk")
Write-Host ""
Write-Host "Release AAB:"
Write-Host (Join-Path $Root "app\build\outputs\bundle\release\app-release.aab")
