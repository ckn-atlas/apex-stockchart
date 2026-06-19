package com.apex.stockchart

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.stockchart.alerts.scheduleLineAlerts
import com.apex.stockchart.data.AppSettings
import com.apex.stockchart.data.Candle
import com.apex.stockchart.data.OrientationMode
import com.apex.stockchart.data.SettingsRepository
import com.apex.stockchart.data.StockDataRepository
import com.apex.stockchart.data.TickerSuggestion
import com.apex.stockchart.ui.ChartScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleLineAlerts(applicationContext)

        setContent {
            val repository = remember { SettingsRepository(applicationContext) }
            val stockDataRepository = remember { StockDataRepository() }
            val scope = rememberCoroutineScope()
            val settingsState = repository.settings.collectAsStateWithLifecycle(AppSettings())
            val linesState = repository.lines.collectAsStateWithLifecycle(emptyList())
            val settings = settingsState.value
            var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
            var isLoading by remember { mutableStateOf(false) }
            var dataError by remember { mutableStateOf<String?>(null) }
            var tickerSearchText by remember { mutableStateOf("") }
            var tickerSuggestions by remember { mutableStateOf<List<TickerSuggestion>>(emptyList()) }

            LaunchedEffect(settings.orientationMode) {
                requestedOrientation = when (settings.orientationMode) {
                    OrientationMode.System -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    OrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    OrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {},
                )
                LaunchedEffect(Unit) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(settings.ticker, settings.timeframe) {
                isLoading = true
                dataError = null
                if (settings.ticker.equals("GUIDE", ignoreCase = true)) {
                    repository.saveTicker("TSLA")
                    candles = emptyList()
                    isLoading = false
                    return@LaunchedEffect
                }
                Log.d("StockChartData", "Loading ${settings.ticker} ${settings.timeframe}")
                runCatching {
                    stockDataRepository.fetchCandles(settings.ticker, settings.timeframe)
                }.onSuccess {
                    candles = it
                    dataError = if (it.isEmpty()) "No chart data for ${settings.ticker}" else null
                    Log.d("StockChartData", "Loaded ${it.size} candles for ${settings.ticker} ${settings.timeframe}")
                }.onFailure {
                    if (it is CancellationException) throw it
                    dataError = it.message ?: "Failed to load chart data"
                    Log.e("StockChartData", "Failed to load ${settings.ticker} ${settings.timeframe}", it)
                }
                isLoading = false
            }

            LaunchedEffect(tickerSearchText) {
                val query = tickerSearchText.trim()
                tickerSuggestions = if (query.length >= 1 && query != settings.ticker) {
                    runCatching { stockDataRepository.searchTickers(query) }.getOrDefault(emptyList()).take(8)
                } else {
                    emptyList()
                }
            }

            fun saveTicker(nextTicker: String) {
                val normalized = nextTicker.trim().uppercase()
                scope.launch {
                    repository.saveTicker(if (normalized == "GUIDE") "TSLA" else normalized)
                }
            }

            ChartScreen(
                settings = settings,
                candles = candles,
                isLoading = isLoading,
                dataError = dataError,
                tickerSuggestions = tickerSuggestions,
                lines = linesState.value,
                onTickerSearchTextChanged = { tickerSearchText = it },
                onSaveLine = { line -> scope.launch { repository.saveLine(line) } },
                onReplaceLines = { nextLines -> scope.launch { repository.replaceLines(nextLines) } },
                onClearLines = { scope.launch { repository.clearLines() } },
                onSaveOrientation = { mode -> scope.launch { repository.saveOrientation(mode) } },
                onSaveTicker = ::saveTicker,
                onSaveTimeframe = { timeframe -> scope.launch { repository.saveTimeframe(timeframe) } },
                onSaveLogScale = { enabled -> scope.launch { repository.saveLogScale(enabled) } },
                onSaveTheme = { theme, background, grid, text, candle ->
                    scope.launch { repository.saveTheme(theme, background, grid, text, candle) }
                },
                onSaveCandleMode = { mode -> scope.launch { repository.saveCandleMode(mode) } },
                onSaveCandleColor = { color -> scope.launch { repository.saveCandleColor(color) } },
                onSaveBackgroundColor = { color -> scope.launch { repository.saveBackgroundColor(color) } },
                onSaveGridColor = { color -> scope.launch { repository.saveGridColor(color) } },
                onSaveTextColor = { color -> scope.launch { repository.saveTextColor(color) } },
                onSaveFontFamily = { font -> scope.launch { repository.saveFontFamily(font) } },
                onSaveFontSize = { value -> scope.launch { repository.saveFontSize(value) } },
                onSaveLineWidth = { value -> scope.launch { repository.saveLineWidth(value) } },
                onSavePointSize = { value -> scope.launch { repository.savePointSize(value) } },
                onSaveStarSize = { value -> scope.launch { repository.saveStarSize(value) } },
                onSaveRange = { start, end -> scope.launch { repository.saveRange(start, end) } },
            )
        }
    }
}
