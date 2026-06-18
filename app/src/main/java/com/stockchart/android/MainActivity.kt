package com.stockchart.android

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
import com.stockchart.android.alerts.scheduleLineAlerts
import com.stockchart.android.data.AppSettings
import com.stockchart.android.data.Candle
import com.stockchart.android.data.OrientationMode
import com.stockchart.android.data.SettingsRepository
import com.stockchart.android.data.StockDataRepository
import com.stockchart.android.data.TickerSuggestion
import com.stockchart.android.ui.ChartScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
                    candles = guideCandles(settings.timeframe)
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
                    val guideSuggestion = guideTickerSuggestion(query)
                    val marketSuggestions = if (query.equals("GUIDE", ignoreCase = true)) {
                        emptyList()
                    } else {
                        runCatching { stockDataRepository.searchTickers(query) }.getOrDefault(emptyList())
                    }
                    (listOfNotNull(guideSuggestion) + marketSuggestions).distinctBy { it.symbol }.take(8)
                } else {
                    emptyList()
                }
            }

            fun saveTicker(nextTicker: String) {
                val normalized = nextTicker.trim().uppercase()
                scope.launch {
                    if (normalized == "GUIDE" && !settings.ticker.equals("GUIDE", ignoreCase = true)) {
                        repository.saveGuideSnapshot(settings, linesState.value)
                        repository.saveGuideReturnTicker(settings.ticker)
                    }
                    repository.saveTicker(normalized)
                }
            }

            ChartScreen(
                settings = settings,
                candles = candles,
                isLoading = isLoading,
                dataError = dataError,
                tickerSuggestions = tickerSuggestions,
                isGuideMode = settings.ticker.equals("GUIDE", ignoreCase = true),
                lines = linesState.value,
                onTickerSearchTextChanged = { tickerSearchText = it },
                onSaveLine = { line -> scope.launch { repository.saveLine(line) } },
                onReplaceLines = { nextLines -> scope.launch { repository.replaceLines(nextLines) } },
                onClearLines = { scope.launch { repository.clearLines() } },
                onSaveOrientation = { mode -> scope.launch { repository.saveOrientation(mode) } },
                onSaveTicker = ::saveTicker,
                onExitGuide = {
                    scope.launch {
                        repository.restoreGuideSnapshot(settings.guideReturnTicker ?: "TSLA")
                    }
                },
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

private fun guideTickerSuggestion(query: String): TickerSuggestion? {
    val q = query.trim().uppercase()
    if (q.isBlank()) return null
    val searchable = "GUIDE INTERACTIVE WALKTHROUGH"
    return if ("GUIDE".startsWith(q) || searchable.contains(q)) {
        TickerSuggestion(symbol = "GUIDE", name = "Interactive Walkthrough", exchange = "")
    } else {
        null
    }
}

private fun guideCandles(timeframe: String): List<Candle> {
    val dayMillis = 86_400_000L
    val stepDays = when (timeframe) {
        "W" -> 7L
        "M" -> 30L
        else -> 1L
    }
    val count = when (timeframe) {
        "W" -> 120
        "M" -> 72
        else -> 220
    }
    val end = System.currentTimeMillis()
    var previousClose = 118f
    return List(count) { index ->
        val phase = index.toDouble()
        val timeMillis = end - (count - 1L - index) * stepDays * dayMillis
        val trend = index * 0.72f
        val wave = (sin(phase / 8.0) * 9.5 + cos(phase / 19.0) * 13.0).toFloat()
        val breakout = if (index > count * 0.72f) (index - count * 0.72f) * 1.15f else 0f
        val close = (118f + trend + wave + breakout).coerceAtLeast(12f)
        val open = previousClose + (sin(phase / 3.0) * 2.2).toFloat()
        val high = maxOf(open, close) + 4.2f + (abs(sin(phase / 5.0)) * 5.5).toFloat()
        val low = minOf(open, close) - 4.2f - (abs(cos(phase / 6.0)) * 4.5).toFloat()
        previousClose = close
        Candle(
            timeMillis = timeMillis,
            open = open,
            high = high,
            low = low.coerceAtLeast(1f),
            close = close,
        )
    }
}
