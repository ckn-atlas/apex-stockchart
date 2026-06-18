$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $Root ".tools\jdk-21.0.11+10"
$env:ANDROID_HOME = Join-Path $Root ".tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Set-Location $Root
.\gradlew.bat --no-daemon :app:assembleDebug

Write-Host ""
Write-Host "APK:"
Write-Host (Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk")
