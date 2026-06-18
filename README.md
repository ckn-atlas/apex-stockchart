# StockChart Android

Android version of the stock chart tool.

## Current MVP

- Kotlin + Jetpack Compose Android app
- Native candlestick chart canvas
- Bottom range selector
  - endpoint drag adjusts the endpoint
  - non-endpoint drag moves the selected window
- Manual line drawing
  - tap candle high/low anchors
  - two anchors create one saved line
- User options persistence with DataStore
  - orientation mode
  - chart range
  - saved user lines
- Background line-touch alert skeleton with WorkManager

## Build

This project keeps portable build tools under `.tools` so it does not depend on system-wide Java or Android Studio.

```powershell
cd D:\0_PycharmProject\pythonProject\039_StockChartAndroid
.\build-debug.ps1
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- The current chart uses generated sample candles. The next milestone is connecting the real stock data provider.
- Line-touch alert logic is implemented, but the latest candle provider is currently a placeholder.
- Android WorkManager periodic checks are designed for roughly 15-minute background intervals. Real-time alerts should use a server-side watcher plus push notifications.
