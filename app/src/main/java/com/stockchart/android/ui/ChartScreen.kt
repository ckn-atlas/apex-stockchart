package com.stockchart.android.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockchart.android.data.AppSettings
import com.stockchart.android.data.Candle
import com.stockchart.android.data.OrientationMode
import com.stockchart.android.data.TickerSuggestion
import com.stockchart.android.data.UserLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class ChartMode {
    None,
    BoxSelect,
    DrawLine,
    ConnectLines,
    DeleteLine,
    ForecastLine,
    LowHighMeasure,
}

private data class AnchorPoint(
    val timeMillis: Long,
    val price: Float,
)

private data class BoxSelection(
    val start: Offset,
    val end: Offset,
) {
    val rect: Rect
        get() = Rect(
            left = min(start.x, end.x),
            top = min(start.y, end.y),
            right = max(start.x, end.x),
            bottom = max(start.y, end.y),
        )
}

private data class LineProjection(
    val first: UserLine,
    val second: UserLine,
    val timeMillis: Long,
    val price: Float,
)

private data class MeasureSelection(
    val start: AnchorPoint,
    val end: AnchorPoint,
)

private data class PatternPoint(
    val timeMillis: Long,
    val price: Float,
)

private data class PatternCandidate(
    val label: String,
    val color: Long,
    val upperA: PatternPoint,
    val upperB: PatternPoint,
    val lowerA: PatternPoint,
    val lowerB: PatternPoint,
    val convergence: PatternPoint,
)

private data class SavedPattern(
    val id: String,
    val ticker: String,
    val timeframe: String,
    val pattern: PatternCandidate,
)

private data class DisplayPattern(
    val id: String,
    val pattern: PatternCandidate,
    val saved: Boolean,
)

private data class LineForecast(
    val line: UserLine,
    val queryTimeMillis: Long,
    val queryPrice: Float,
)

private data class LineTooltip(
    val label: String,
    val timeMillis: Long,
    val price: Float,
    val point: Offset,
    val color: Long,
)

private data class PriceLineCandidate(
    val label: String,
    val start: Offset,
    val end: Offset,
    val color: Long,
)

private data class CandleTooltip(
    val index: Int,
    val candle: Candle,
    val touch: Offset,
)

private data class GuideStep(
    val title: String,
    val body: String,
    val arrow: String,
)

@Composable
fun ChartScreen(
    settings: AppSettings,
    candles: List<Candle>,
    isLoading: Boolean,
    dataError: String?,
    tickerSuggestions: List<TickerSuggestion>,
    isGuideMode: Boolean,
    lines: List<UserLine>,
    onTickerSearchTextChanged: (String) -> Unit,
    onSaveLine: (UserLine) -> Unit,
    onReplaceLines: (List<UserLine>) -> Unit,
    onClearLines: () -> Unit,
    onSaveOrientation: (OrientationMode) -> Unit,
    onSaveTicker: (String) -> Unit,
    onSaveTimeframe: (String) -> Unit,
    onSaveLogScale: (Boolean) -> Unit,
    onSaveTheme: (String, Long, Long, Long, Long) -> Unit,
    onSaveCandleMode: (String) -> Unit,
    onSaveCandleColor: (Long) -> Unit,
    onSaveBackgroundColor: (Long) -> Unit,
    onSaveGridColor: (Long) -> Unit,
    onSaveTextColor: (Long) -> Unit,
    onSaveFontFamily: (String) -> Unit,
    onSaveFontSize: (Float) -> Unit,
    onSaveLineWidth: (Float) -> Unit,
    onSavePointSize: (Float) -> Unit,
    onSaveStarSize: (Float) -> Unit,
    onSaveRange: (Float, Float) -> Unit,
    onExitGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chartMode by remember { mutableStateOf(ChartMode.None) }
    var pendingAnchor by remember { mutableStateOf<AnchorPoint?>(null) }
    var activeBox by remember { mutableStateOf<BoxSelection?>(null) }
    var committedBox by remember { mutableStateOf<BoxSelection?>(null) }
    var firstConnectLine by remember { mutableStateOf<UserLine?>(null) }
    var projections by remember { mutableStateOf<List<LineProjection>>(emptyList()) }
    var patterns by remember { mutableStateOf<List<PatternCandidate>>(emptyList()) }
    var activePatternIndex by remember { mutableStateOf<Int?>(null) }
    var savedPatterns by remember { mutableStateOf<List<SavedPattern>>(emptyList()) }
    var activeSavedPatternId by remember { mutableStateOf<String?>(null) }
    var forecastLine by remember { mutableStateOf<UserLine?>(null) }
    var lineForecast by remember { mutableStateOf<LineForecast?>(null) }
    var measureStart by remember { mutableStateOf<AnchorPoint?>(null) }
    var measureSelection by remember { mutableStateOf<MeasureSelection?>(null) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var undoStack by remember { mutableStateOf<List<List<UserLine>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<UserLine>>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var freeMoveZoom by remember { mutableStateOf(false) }
    var guideStepIndex by remember { mutableStateOf(0) }
    var rangeStart by remember(settings.rangeStartPercent) { mutableStateOf(settings.rangeStartPercent) }
    var rangeEnd by remember(settings.rangeEndPercent) { mutableStateOf(settings.rangeEndPercent) }
    fun UserLine.isCurrentSymbolLine(): Boolean =
        ticker.equals(settings.ticker, ignoreCase = true)

    fun LineProjection.isCurrentSymbolProjection(): Boolean =
        first.isCurrentSymbolLine() && second.isCurrentSymbolLine()

    fun SavedPattern.isCurrentSymbolPattern(): Boolean =
        ticker.equals(settings.ticker, ignoreCase = true)

    val visibleLines = lines.filter { it.isCurrentSymbolLine() }
    val visibleForecastLines = visibleLines.filter { it.forecastTimeMillis != null }
    val visibleProjections = projections.filter { it.isCurrentSymbolProjection() }
    val visibleSavedPatterns = savedPatterns.filter { it.isCurrentSymbolPattern() }
    val displayPatterns = visibleSavedPatterns.map { DisplayPattern(it.id, it.pattern, saved = true) } +
        if (patterns.isNotEmpty()) {
            patterns.mapIndexed { index, pattern -> DisplayPattern("D-$index", pattern, saved = false) }
        } else {
            emptyList()
        }
    val visiblePatterns = displayPatterns.map { it.pattern }
    val activePatternIndexForCanvas = when {
        activeSavedPatternId != null -> displayPatterns.indexOfFirst { it.saved && it.id == activeSavedPatternId }.takeIf { it >= 0 }
        activePatternIndex != null -> displayPatterns.indexOfFirst { !it.saved && it.id == "D-$activePatternIndex" }.takeIf { it >= 0 }
        else -> null
    }
    val guideMessage = when {
        chartMode == ChartMode.ConnectLines && firstConnectLine == null -> "수렴하는 두선을 선택해주세요."
        chartMode == ChartMode.ConnectLines -> "하나 더 선택해주세요."
        chartMode == ChartMode.LowHighMeasure && measureStart == null && measureSelection == null -> "상승/하락률 첫 번째 점을 선택해주세요."
        chartMode == ChartMode.LowHighMeasure && measureStart != null -> "두 번째 점을 선택해주세요."
        else -> transientMessage
    }
    val latestCandle = candles.lastOrNull()
    val previousCandle = if (candles.size >= 2) candles[candles.lastIndex - 1] else null
    val latestChange = if (latestCandle != null && previousCandle != null) latestCandle.close - previousCandle.close else null
    val latestChangePercent = if (latestChange != null && previousCandle != null && previousCandle.close != 0f) {
        latestChange / previousCandle.close * 100f
    } else {
        null
    }
    val guideSteps = remember {
        listOf(
            GuideStep(
                "Ticker Search",
                "Type a symbol or company name. Choose GUIDE any time to replay this walkthrough.",
                "↑",
            ),
            GuideStep(
                "Timeframes",
                "Switch daily, weekly, or monthly while keeping the chart range you are working in.",
                "↑",
            ),
            GuideStep(
                "Analysis Area",
                "Drag across the chart to scan that time window for converging triangle and wedge patterns.",
                "↗",
            ),
            GuideStep(
                "Drawing Tools",
                "Draw trend lines, connect two lines for an intersection, delete selected lines, or forecast price.",
                "↑",
            ),
            GuideStep(
                "Free Move + Zoom",
                "Use one finger to pan. Pinch with two fingers to zoom. Future space stays open for projections.",
                "→",
            ),
            GuideStep(
                "Range Slider",
                "Use quick range buttons or the lower slider to control exactly which section is visible.",
                "↓",
            ),
        )
    }

    fun commitLines(nextLines: List<UserLine>) {
        undoStack = undoStack + listOf(lines)
        redoStack = emptyList()
        onReplaceLines(nextLines)
    }

    fun selectMode(next: ChartMode) {
        freeMoveZoom = false
        val wasBoxMode = chartMode == ChartMode.BoxSelect
        val nextMode = if (chartMode == next) ChartMode.None else next
        if (wasBoxMode && nextMode != ChartMode.BoxSelect) {
            activeBox = null
            committedBox = null
            patterns = emptyList()
            activePatternIndex = null
        }
        chartMode = nextMode
        pendingAnchor = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        if (nextMode == ChartMode.LowHighMeasure) {
            measureStart = null
            measureSelection = null
        }
        transientMessage = null
    }

    fun clearAnalysisState() {
        pendingAnchor = null
        activeBox = null
        committedBox = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        measureStart = null
        measureSelection = null
        projections = projections.filterNot { it.isCurrentSymbolProjection() }
        patterns = emptyList()
        activePatternIndex = null
        activeSavedPatternId = null
        transientMessage = null
    }

    LaunchedEffect(isGuideMode) {
        if (isGuideMode) {
            guideStepIndex = 0
            clearAnalysisState()
            chartMode = ChartMode.None
            freeMoveZoom = false
        }
    }

    LaunchedEffect(settings.ticker, settings.timeframe) {
        pendingAnchor = null
        activeBox = null
        committedBox = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        measureStart = null
        patterns = emptyList()
        activePatternIndex = null
        activeSavedPatternId = null
        transientMessage = null
        chartMode = ChartMode.None
        freeMoveZoom = false
    }

    LaunchedEffect(candles, settings.hasSavedRange) {
        if (!settings.hasSavedRange && candles.isNotEmpty()) {
            onPickRecentRange(candles, 30) { start, end ->
                rangeStart = start
                rangeEnd = end
                onSaveRange(start, end)
            }
        }
    }

    LaunchedEffect(isGuideMode, guideStepIndex) {
        if (!isGuideMode) return@LaunchedEffect
        pendingAnchor = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        measureStart = null
        transientMessage = null
        chartMode = when (guideStepIndex) {
            2 -> ChartMode.BoxSelect
            3 -> ChartMode.DrawLine
            else -> ChartMode.None
        }
        freeMoveZoom = guideStepIndex == 4
    }

    @Composable
    fun ToolbarPanel(modifier: Modifier = Modifier, compactVertical: Boolean = false) {
        Box(modifier = modifier) {
            TopToolbar(
                ticker = settings.ticker,
                timeframe = settings.timeframe,
                lastPrice = latestCandle?.close,
                lastChange = latestChange,
                lastChangePercent = latestChangePercent,
                accentColor = settings.candleColor,
                backgroundColor = settings.backgroundColor,
                mode = chartMode,
                canConnectLines = visibleLines.size >= 2,
                canForecastLine = visibleLines.isNotEmpty(),
                onTickerChanged = onSaveTicker,
                onTickerSearchTextChanged = onTickerSearchTextChanged,
                tickerSuggestions = tickerSuggestions,
                onPickTimeframe = {
                    freeMoveZoom = false
                    onSaveTimeframe(it)
                },
                onPickMode = ::selectMode,
                onShowMessage = { transientMessage = it },
                onClearLines = {
                    pendingAnchor = null
                    undoStack = undoStack + listOf(lines)
                    redoStack = emptyList()
                    projections = projections.filterNot { it.isCurrentSymbolProjection() }
                    activeBox = null
                    committedBox = null
                    firstConnectLine = null
                    forecastLine = null
                    lineForecast = null
                    measureStart = null
                    measureSelection = null
                    patterns = emptyList()
                    activePatternIndex = null
                    activeSavedPatternId = null
                    transientMessage = null
                    onReplaceLines(lines.filterNot { it.isCurrentSymbolLine() })
                },
                onOpenSettings = { showSettings = true },
                compactVertical = compactVertical,
            )
        }
    }

    @Composable
    fun ChartPanel(modifier: Modifier = Modifier) {
            Box(
                modifier = modifier,
            ) {
                StockChartCanvas(
                    candles = candles,
                    settings = settings,
                    lines = visibleLines,
                    projections = visibleProjections,
                    patterns = visiblePatterns,
                    activePatternIndex = activePatternIndexForCanvas,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    mode = chartMode,
                    pendingAnchor = pendingAnchor,
                    activeBox = activeBox,
                    committedBox = committedBox,
                    freeMoveZoom = freeMoveZoom,
                    firstConnectLine = firstConnectLine,
                    forecastLine = forecastLine,
                    lineForecast = lineForecast,
                    measureStart = measureStart,
                    measureSelection = measureSelection,
                    onAnchorPicked = { anchor ->
                        val first = pendingAnchor
                        if (first == null) {
                            pendingAnchor = anchor
                        } else {
                            undoStack = undoStack + listOf(lines)
                            redoStack = emptyList()
                            onSaveLine(
                                UserLine(
                                    id = "L-${System.currentTimeMillis()}",
                                    ticker = settings.ticker,
                                    timeframe = settings.timeframe,
                                    startTimeMillis = first.timeMillis,
                                    startPrice = first.price,
                                    endTimeMillis = anchor.timeMillis,
                                    endPrice = anchor.price,
                                    color = contrastLineColors(settings.backgroundColor)[visibleLines.size % contrastLineColors(settings.backgroundColor).size],
                                ),
                            )
                            pendingAnchor = null
                        }
                    },
                    onLinePicked = { line ->
                        val first = firstConnectLine
                        if (first == null || first.id == line.id) {
                            firstConnectLine = line
                            transientMessage = null
                        } else {
                            val projection = intersectFutureLines(first, line)
                            if (projection == null) {
                                transientMessage = "수렴되지 않음"
                            } else {
                                projections = projections + projection
                                transientMessage = null
                            }
                            firstConnectLine = null
                        }
                    },
                    onForecastLinePicked = {
                        forecastLine = it
                        lineForecast = null
                        transientMessage = null
                    },
                    onForecastChanged = {
                        lineForecast = it
                    },
                    onMeasurePointPicked = { anchor ->
                        val first = measureStart
                        if (first == null) {
                            measureStart = anchor
                            measureSelection = null
                        } else {
                            measureSelection = MeasureSelection(first, anchor)
                            measureStart = null
                            transientMessage = null
                        }
                    },
                    onDeleteLinePicked = { line ->
                        commitLines(lines.filterNot { it.id == line.id })
                        projections = projections.filterNot { it.first.id == line.id || it.second.id == line.id }
                        if (firstConnectLine?.id == line.id) firstConnectLine = null
                        if (forecastLine?.id == line.id) {
                            forecastLine = null
                            lineForecast = null
                        }
                        transientMessage = "선 삭제됨"
                    },
                    onBoxChanged = {
                        activeBox = it
                        if (it != null) {
                            activePatternIndex = null
                        }
                    },
                    onBoxSelectionStarted = ::clearAnalysisState,
                    onBoxCommitted = {
                        activeBox = null
                        committedBox = it
                    },
                    onPatternsDetected = {
                        patterns = it
                        activePatternIndex = null
                    },
                    onPanRange = { start, end ->
                        rangeStart = start
                        rangeEnd = end
                    },
                    onPanFinished = { start, end ->
                        onSaveRange(start, end)
                    },
                    onToggleLogScale = {
                        freeMoveZoom = false
                        onSaveLogScale(!settings.logScale)
                    },
                    onToggleFreeMoveZoom = {
                        freeMoveZoom = !freeMoveZoom
                        if (!freeMoveZoom) {
                            chartMode = ChartMode.None
                            } else {
                                pendingAnchor = null
                                firstConnectLine = null
                                activeBox = null
                                committedBox = null
                                transientMessage = null
                                freeMoveZoom = true
                            }
                    },
                    onToggleMeasureMode = { selectMode(ChartMode.LowHighMeasure) },
                    onPickCandleMode = onSaveCandleMode,
                    modifier = Modifier.fillMaxSize(),
                )
                if (chartMode == ChartMode.DrawLine || chartMode == ChartMode.DeleteLine) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MiniChartButton("↶", undoStack.isNotEmpty()) {
                            val previous = undoStack.lastOrNull() ?: return@MiniChartButton
                            undoStack = undoStack.dropLast(1)
                            redoStack = redoStack + listOf(lines)
                            onReplaceLines(previous)
                        }
                        MiniChartButton("↷", redoStack.isNotEmpty()) {
                            val next = redoStack.lastOrNull() ?: return@MiniChartButton
                            redoStack = redoStack.dropLast(1)
                            undoStack = undoStack + listOf(lines)
                            onReplaceLines(next)
                        }
                    }
                }
                if (chartMode == ChartMode.ForecastLine && lineForecast != null) {
                    ForecastSaveButton(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 84.dp),
                    ) {
                        val forecast = lineForecast ?: return@ForecastSaveButton
                        val updated = forecast.line.copy(
                            forecastTimeMillis = forecast.queryTimeMillis,
                            forecastPrice = forecast.queryPrice,
                        )
                        undoStack = undoStack + listOf(lines)
                        redoStack = emptyList()
                        onReplaceLines(lines.map { if (it.id == updated.id) updated else it })
                        forecastLine = updated
                        lineForecast = LineForecast(updated, forecast.queryTimeMillis, forecast.queryPrice)
                        transientMessage = "가격예측 저장됨"
                    }
                }
                if (patterns.isNotEmpty() || visibleSavedPatterns.isNotEmpty() || visibleForecastLines.isNotEmpty()) {
                    PatternPanel(
                        detectedPatterns = patterns,
                        savedPatterns = visibleSavedPatterns,
                        savedForecastLines = visibleForecastLines,
                        activeDetectedIndex = activePatternIndex,
                        activeSavedId = activeSavedPatternId,
                        activeForecastId = forecastLine?.id,
                        onPickDetected = {
                            activePatternIndex = it
                            activeSavedPatternId = null
                            forecastLine = null
                            lineForecast = null
                        },
                        onPickSaved = {
                            activeSavedPatternId = it
                            activePatternIndex = null
                            forecastLine = null
                            lineForecast = null
                        },
                        onPickForecast = {
                            forecastLine = it
                            lineForecast = null
                            activeSavedPatternId = null
                            activePatternIndex = null
                        },
                        onSavePattern = { patternIndex ->
                            patterns.getOrNull(patternIndex)?.let { pattern ->
                                val now = System.currentTimeMillis()
                                savedPatterns = savedPatterns +
                                    SavedPattern(
                                        id = "P-${now}-${patternIndex}",
                                        ticker = settings.ticker,
                                        timeframe = settings.timeframe,
                                        pattern = pattern,
                                    )
                            }
                        },
                        onDeleteSaved = {
                            activeSavedPatternId?.let { id ->
                                savedPatterns = savedPatterns.filterNot { it.id == id }
                                activeSavedPatternId = null
                            }
                        },
                        onDeleteForecast = {
                            val targetId = forecastLine?.id
                            if (targetId != null) {
                                undoStack = undoStack + listOf(lines)
                                redoStack = emptyList()
                                onReplaceLines(
                                    lines.map { line ->
                                        if (line.id == targetId) {
                                            line.copy(forecastTimeMillis = null, forecastPrice = null)
                                        } else {
                                            line
                                        }
                                    }
                                )
                                forecastLine = null
                                lineForecast = null
                                transientMessage = "예측 삭제됨"
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 44.dp),
                    )
                }
                when {
                    isLoading && candles.isEmpty() -> StatusOverlay("${settings.ticker} 데이터 로딩 중...")
                    dataError != null && candles.isEmpty() -> StatusOverlay(dataError)
                }
                guideMessage?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 52.dp)
                            .background(Color(0xDD252538), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF45475A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(it, color = Color(0xFFE5E7FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
    }

    @Composable
    fun RangeControls(rangeSelectorModifier: Modifier = Modifier.height(118.dp)) {
        QuickRangeButtons(
            candles = candles,
            onPick = { start, end ->
                freeMoveZoom = false
                rangeStart = start
                rangeEnd = end
                onSaveRange(start, end)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        RangeSelector(
            candles = candles,
            start = rangeStart,
            end = rangeEnd,
            onChange = { start, end ->
                rangeStart = start
                rangeEnd = end
            },
            onChangeFinished = { start, end -> onSaveRange(start, end) },
            modifier = rangeSelectorModifier.fillMaxWidth(),
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = argbColor(settings.backgroundColor),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ToolbarPanel(Modifier.fillMaxWidth(), compactVertical = true)
                    }
                    Column(
                        modifier = Modifier
                            .weight(4f)
                            .fillMaxHeight(),
                    ) {
                        ChartPanel(
                            Modifier
                                .weight(4f)
                                .fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            RangeControls(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ToolbarPanel(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ChartPanel(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    RangeControls(
                        Modifier
                            .fillMaxWidth()
                            .height(118.dp),
                    )
                }
            }

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettings = false },
                onPickOrientation = onSaveOrientation,
                onSaveTheme = onSaveTheme,
                onSaveCandleMode = onSaveCandleMode,
                onSaveCandleColor = onSaveCandleColor,
                onSaveBackgroundColor = onSaveBackgroundColor,
                onSaveGridColor = onSaveGridColor,
                onSaveTextColor = onSaveTextColor,
                onSaveFontFamily = onSaveFontFamily,
                onSaveFontSize = onSaveFontSize,
                onSaveLineWidth = onSaveLineWidth,
                onSavePointSize = onSavePointSize,
                onSaveStarSize = onSaveStarSize,
                onGuideUpdating = {
                    showSettings = false
                    transientMessage = "가이드는 업데이트 중입니다."
                },
            )
        }

        if (isGuideMode) {
            GuideOverlay(
                step = guideSteps[guideStepIndex.coerceIn(0, guideSteps.lastIndex)],
                index = guideStepIndex,
                total = guideSteps.size,
                onPrevious = { guideStepIndex = (guideStepIndex - 1).coerceAtLeast(0) },
                onNext = {
                    if (guideStepIndex >= guideSteps.lastIndex) {
                        clearAnalysisState()
                        chartMode = ChartMode.None
                        freeMoveZoom = false
                        onExitGuide()
                    } else {
                        guideStepIndex += 1
                    }
                },
                onFinish = {
                    clearAnalysisState()
                    chartMode = ChartMode.None
                    freeMoveZoom = false
                    onExitGuide()
                },
            )
        }
        }
    }
}

@Composable
private fun GuideOverlay(
    step: GuideStep,
    index: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xF0212132),
        titleContentColor = Color(0xFFE5E7FF),
        textContentColor = Color(0xFFD7DBF4),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = step.arrow,
                    color = Color(0xFF89B4FA),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${index + 1} / $total", color = Color(0xFFA9B1D6), fontSize = 12.sp)
                }
            }
        },
        text = {
            Text(step.body, fontSize = 13.sp, lineHeight = 19.sp)
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = index > 0,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Back", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onFinish,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Finish", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(if (index >= total - 1) "Start" else "Next", fontSize = 12.sp)
            }
        },
    )
}

@Composable
private fun TopToolbar(
    ticker: String,
    timeframe: String,
    lastPrice: Float?,
    lastChange: Float?,
    lastChangePercent: Float?,
    accentColor: Long,
    backgroundColor: Long,
    mode: ChartMode,
    canConnectLines: Boolean,
    canForecastLine: Boolean,
    onTickerChanged: (String) -> Unit,
    onTickerSearchTextChanged: (String) -> Unit,
    tickerSuggestions: List<TickerSuggestion>,
    onPickTimeframe: (String) -> Unit,
    onPickMode: (ChartMode) -> Unit,
    onShowMessage: (String) -> Unit,
    onClearLines: () -> Unit,
    onOpenSettings: () -> Unit,
    compactVertical: Boolean = false,
) {
    var tickerField by remember(ticker) {
        mutableStateOf(TextFieldValue(ticker, selection = TextRange(ticker.length)))
    }
    val tickerAccent = argbColor(accentColor)
    val tickerTextColor = argbColor(readableAccentColor(accentColor, backgroundColor))
    val tickerAccentSoft = tickerAccent.copy(alpha = 0.68f)

    fun commitTicker() {
        val next = tickerField.text.trim().uppercase()
        if (next.isNotBlank() && next != ticker) {
            tickerField = TextFieldValue(next, selection = TextRange(next.length))
            onTickerChanged(next)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (compactVertical) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TickerInputField(
                    value = tickerField,
                    onValueChange = {
                        tickerField = it.copy(text = it.text.uppercase())
                        onTickerSearchTextChanged(it.text)
                    },
                    onCommit = { commitTicker() },
                    textColor = tickerTextColor,
                    accentColor = tickerAccent,
                    accentSoftColor = tickerAccentSoft,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                )
                GlowActionButton(
                    text = "검색",
                    icon = Icons.Filled.Search,
                    onClick = { commitTicker() },
                    modifier = Modifier
                        .height(44.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            TickerInputField(
                value = tickerField,
                onValueChange = {
                    tickerField = it.copy(text = it.text.uppercase())
                    onTickerSearchTextChanged(it.text)
                },
                onCommit = { commitTicker() },
                textColor = tickerTextColor,
                accentColor = tickerAccent,
                accentSoftColor = tickerAccentSoft,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
            GlowActionButton(
                text = "검색",
                icon = Icons.Filled.Search,
                onClick = { commitTicker() },
                modifier = Modifier.height(44.dp),
            )
            }
        }
        if (tickerSuggestions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tickerSuggestions.take(5).forEach { suggestion ->
                    TickerSuggestionButton(
                        suggestion = suggestion,
                        accentColor = accentColor,
                        onClick = {
                            tickerField = TextFieldValue(
                                suggestion.symbol,
                                selection = TextRange(suggestion.symbol.length),
                            )
                            onTickerSearchTextChanged("")
                            onTickerChanged(suggestion.symbol)
                        },
                    )
                }
            }
        }
        StockSummaryRow(
            ticker = ticker,
            lastPrice = lastPrice,
            lastChange = lastChange,
            lastChangePercent = lastChangePercent,
            compactVertical = compactVertical,
            accentColor = accentColor,
        )
        if (compactVertical) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TimeframeSegment("일", timeframe == "D", Modifier.weight(1f)) { onPickTimeframe("D") }
                TimeframeSegment("주", timeframe == "W", Modifier.weight(1f)) { onPickTimeframe("W") }
                TimeframeSegment("월", timeframe == "M", Modifier.weight(1f)) { onPickTimeframe("M") }
                ToolIconButton(Icons.Filled.Settings, onOpenSettings)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .background(Color(0xCC1B1E2C), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF252A3D), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeframeSegment("일봉", timeframe == "D", Modifier.weight(1f)) { onPickTimeframe("D") }
                    TimeframeSegment("주봉", timeframe == "W", Modifier.weight(1f)) { onPickTimeframe("W") }
                    TimeframeSegment("월봉", timeframe == "M", Modifier.weight(1f)) { onPickTimeframe("M") }
                }
                ToolIconButton(Icons.Filled.Settings, onOpenSettings)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (compactVertical) {
                ToolbarButton(Icons.Filled.CropFree, "분석영역", mode == ChartMode.BoxSelect, { onPickMode(ChartMode.BoxSelect) }, Modifier.fillMaxWidth())
                ToolbarButton(Icons.Filled.Edit, "선그리기", mode == ChartMode.DrawLine, { onPickMode(ChartMode.DrawLine) }, Modifier.fillMaxWidth())
                ToolbarButton(
                    Icons.Filled.Timeline,
                    "선잇기",
                    mode == ChartMode.ConnectLines,
                    {
                        if (canConnectLines) {
                            onPickMode(ChartMode.ConnectLines)
                        } else {
                            onShowMessage("선그리기로 최소 추세선 2개를 그려주세요.")
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
                ToolbarButton(Icons.Filled.Delete, "선삭제", mode == ChartMode.DeleteLine, { onPickMode(ChartMode.DeleteLine) }, Modifier.fillMaxWidth())
                ToolbarButton(
                    Icons.Filled.ShowChart,
                    "가격예측",
                    mode == ChartMode.ForecastLine,
                    {
                        if (canForecastLine || mode == ChartMode.ForecastLine) {
                            onPickMode(ChartMode.ForecastLine)
                        } else {
                            onShowMessage("선그리기로 추세선 1개를 그려주세요.")
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
                ToolbarButton(Icons.Filled.Refresh, "초기화", false, onClearLines, Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ToolbarButton(Icons.Filled.CropFree, "분석영역", mode == ChartMode.BoxSelect, { onPickMode(ChartMode.BoxSelect) }, Modifier.weight(1f))
                    ToolbarButton(Icons.Filled.Edit, "선그리기", mode == ChartMode.DrawLine, { onPickMode(ChartMode.DrawLine) }, Modifier.weight(1f))
                    ToolbarButton(
                        Icons.Filled.Timeline,
                        "선잇기",
                        mode == ChartMode.ConnectLines,
                        {
                            if (canConnectLines) {
                                onPickMode(ChartMode.ConnectLines)
                            } else {
                                onShowMessage("선그리기로 최소 추세선 2개를 그려주세요.")
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ToolbarButton(Icons.Filled.Delete, "선삭제", mode == ChartMode.DeleteLine, { onPickMode(ChartMode.DeleteLine) }, Modifier.weight(1f))
                    ToolbarButton(
                        Icons.Filled.ShowChart,
                        "가격예측",
                        mode == ChartMode.ForecastLine,
                        {
                            if (canForecastLine || mode == ChartMode.ForecastLine) {
                                onPickMode(ChartMode.ForecastLine)
                            } else {
                                onShowMessage("선그리기로 추세선 1개를 그려주세요.")
                            }
                        },
                        Modifier.weight(1f),
                    )
                    ToolbarButton(Icons.Filled.Refresh, "초기화", false, onClearLines, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GlowActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .shadow(14.dp, RoundedCornerShape(9.dp), clip = false, ambientColor = Color(0xAA1468FF), spotColor = Color(0xAA1468FF))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B7CFF), Color(0xFF0F4FE4)),
                ),
                RoundedCornerShape(9.dp),
            )
            .border(1.dp, Color(0xFF5FA1FF), RoundedCornerShape(9.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(Color(0xCC202435), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF343A52), RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFE5E7FF), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StockSummaryRow(
    ticker: String,
    lastPrice: Float?,
    lastChange: Float?,
    lastChangePercent: Float?,
    compactVertical: Boolean = false,
    accentColor: Long,
) {
    val accent = argbColor(accentColor)
    val accentSoft = accent.copy(alpha = 0.72f)
    if (compactVertical) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (ticker == "GUIDE") "Interactive Walkthrough" else "$ticker  •  US Stock",
                color = accentSoft,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lastPrice?.let { price ->
                    Text(
                        text = "$${"%.2f".format(price)}",
                        color = Color(0xFFE5E7FF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (lastChange != null && lastChangePercent != null) {
                    val positive = lastChange >= 0f
                    Text(
                        text = "${if (positive) "+" else ""}${"%.2f".format(lastChange)} (${if (positive) "+" else ""}${"%.2f".format(lastChangePercent)}%)",
                        color = if (positive) Color(0xFF1E88FF) else Color(0xFFEF5350),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (ticker == "GUIDE") "Interactive Walkthrough" else "$ticker  •  US Stock",
            color = accentSoft,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        lastPrice?.let { price ->
            Text(
                text = "$${"%.2f".format(price)}",
                color = Color(0xFFE5E7FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (lastChange != null && lastChangePercent != null) {
            val positive = lastChange >= 0f
            Text(
                text = "${if (positive) "+" else ""}${"%.2f".format(lastChange)} (${if (positive) "+" else ""}${"%.2f".format(lastChangePercent)}%)",
                color = if (positive) Color(0xFF1E88FF) else Color(0xFFEF5350),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TickerInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCommit: () -> Unit,
    textColor: Color,
    accentColor: Color,
    accentSoftColor: Color,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(accentColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onCommit() }),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                }
            }
            .onPreviewKeyEvent {
                if (it.key == Key.Enter) {
                    onCommit()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) accentColor else accentSoftColor,
                        shape = RoundedCornerShape(7.dp),
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isBlank()) {
                    Text(
                        text = "티커",
                        color = accentSoftColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun TimeframeButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (active) Color(0xFF3D4866) else Color(0xFF252538), RoundedCornerShape(6.dp))
            .border(1.dp, if (active) Color(0xFF89B4FA) else Color(0xFF45475A), RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .height(32.dp)
            .padding(horizontal = 12.dp, vertical = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFFE5E7FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimeframeSegment(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (active) {
                    Modifier.shadow(
                        10.dp,
                        RoundedCornerShape(7.dp),
                        clip = false,
                        ambientColor = Color(0x991468FF),
                        spotColor = Color(0x991468FF),
                    )
                } else {
                    Modifier
                },
            )
            .background(
                if (active) {
                    Brush.verticalGradient(listOf(Color(0xFF2A74FF), Color(0xFF134ED8)))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
                RoundedCornerShape(7.dp),
            )
            .border(1.dp, if (active) Color(0xFF67A5FF) else Color.Transparent, RoundedCornerShape(7.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFFE5E7FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TickerSuggestionButton(
    suggestion: TickerSuggestion,
    accentColor: Long,
    onClick: () -> Unit,
) {
    val accent = argbColor(accentColor)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEE252538), RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
            .pointerInput(suggestion.symbol) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = suggestion.label,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .then(
                if (active) {
                    Modifier.shadow(
                        8.dp,
                        RoundedCornerShape(7.dp),
                        clip = false,
                        ambientColor = Color(0x661468FF),
                        spotColor = Color(0x661468FF),
                    )
                } else {
                    Modifier
                },
            )
            .background(
                if (active) {
                    Brush.verticalGradient(listOf(Color(0xFF243E78), Color(0xFF172C58)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF202435), Color(0xFF1B1E2C)))
                },
                RoundedCornerShape(7.dp),
            )
            .border(1.dp, if (active) Color(0xFF4D85F0) else Color(0xFF2B3044), RoundedCornerShape(7.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) Color(0xFF9EC5FF) else Color(0xFF8EA0C8),
                modifier = Modifier.size(14.dp),
            )
            Text(text = text, color = Color(0xFFE5E7FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun MiniChartButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xCC252538),
            contentColor = Color(0xFFE5E7FF),
            disabledContainerColor = Color(0x66252538),
            disabledContentColor = Color(0x88E5E7FF),
        ),
        modifier = modifier.height(30.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ForecastSaveButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(Color(0xEE123A43), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF2DD4BF), RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("예측 저장", color = Color(0xFFE6FFFA), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QuickRangeButtons(
    candles: List<Candle>,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickRangeButton("1M", candles.isNotEmpty()) { onPickRecentRange(candles, 30, onPick) }
        QuickRangeButton("3M", candles.isNotEmpty()) { onPickRecentRange(candles, 90, onPick) }
        QuickRangeButton("6M", candles.isNotEmpty()) { onPickRecentRange(candles, 180, onPick) }
        QuickRangeButton("1Y", candles.isNotEmpty()) { onPickRecentRange(candles, 365, onPick) }
        QuickRangeButton("3Y", candles.isNotEmpty()) { onPickRecentRange(candles, 365 * 3, onPick) }
    }
}

@Composable
private fun QuickRangeButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(5.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xCC252538),
            contentColor = Color(0xFFE5E7FF),
            disabledContainerColor = Color(0x55252538),
            disabledContentColor = Color(0x77E5E7FF),
        ),
        modifier = Modifier.height(30.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatusOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA171724)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFFE5E7FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PatternButtons(
    patterns: List<PatternCandidate>,
    activeIndex: Int?,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        PatternSelectButton("전체", activeIndex == null, { onPick(null) })
        patterns.forEachIndexed { index, pattern ->
            PatternSelectButton(
                text = "${index + 1}: ${pattern.label} $${"%.0f".format(pattern.convergence.price)}",
                active = activeIndex == index,
                onClick = { onPick(index) },
            )
        }
    }
}

@Composable
private fun PatternSelectButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(if (active) Color(0xFF243E78) else Color(0xDD202435), RoundedCornerShape(6.dp))
            .border(1.dp, if (active) Color(0xFF67A5FF) else Color(0xFF343A52), RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFE5E7FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PatternPanel(
    detectedPatterns: List<PatternCandidate>,
    savedPatterns: List<SavedPattern>,
    savedForecastLines: List<UserLine>,
    activeDetectedIndex: Int?,
    activeSavedId: String?,
    activeForecastId: String?,
    onPickDetected: (Int?) -> Unit,
    onPickSaved: (String) -> Unit,
    onPickForecast: (UserLine) -> Unit,
    onSavePattern: (Int) -> Unit,
    onDeleteSaved: () -> Unit,
    onDeleteForecast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        savedPatterns.forEach { saved ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                PatternSelectButton(
                    text = savedPatternLabel(saved.pattern),
                    active = activeSavedId == saved.id,
                    onClick = { onPickSaved(saved.id) },
                )
                if (activeSavedId == saved.id) {
                    PatternSelectButton("삭제", false, onDeleteSaved)
                }
            }
        }
        savedForecastLines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                PatternSelectButton(
                    text = forecastShortLabel(line),
                    active = activeForecastId == line.id,
                    onClick = { onPickForecast(line) },
                )
                if (activeForecastId == line.id) {
                    PatternSelectButton("삭제", false, onDeleteForecast)
                }
            }
        }
        if (detectedPatterns.isNotEmpty()) {
            PatternSelectButton("전체", activeSavedId == null && activeForecastId == null && activeDetectedIndex == null, { onPickDetected(null) })
            detectedPatterns.forEachIndexed { index, pattern ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    PatternSelectButton(
                        text = "${index + 1}: ${patternShortLabel(pattern)}",
                        active = activeSavedId == null && activeDetectedIndex == index,
                        onClick = { onPickDetected(index) },
                    )
                    PatternSelectButton("저장", false) { onSavePattern(index) }
                }
            }
        }
    }
}

private fun savedPatternLabel(pattern: PatternCandidate): String = "패턴${patternShortLabel(pattern)}"

private fun forecastShortLabel(line: UserLine): String {
    val type = if (line.endPrice >= line.startPrice) "지지" else "저항"
    return "예측[$type]"
}

private fun patternShortLabel(pattern: PatternCandidate): String {
    return when {
        pattern.label.contains("대칭") -> "[대칭]"
        pattern.label.contains("하락") -> "[하락]"
        pattern.label.contains("상승") -> "[상승]"
        pattern.label.contains("채널") -> "[채널]"
        else -> "[패턴]"
    }
}

@Composable
private fun StockChartCanvas(
    candles: List<Candle>,
    settings: AppSettings,
    lines: List<UserLine>,
    projections: List<LineProjection>,
    patterns: List<PatternCandidate>,
    activePatternIndex: Int?,
    rangeStart: Float,
    rangeEnd: Float,
    mode: ChartMode,
    pendingAnchor: AnchorPoint?,
    activeBox: BoxSelection?,
    committedBox: BoxSelection?,
    freeMoveZoom: Boolean,
    firstConnectLine: UserLine?,
    forecastLine: UserLine?,
    lineForecast: LineForecast?,
    measureStart: AnchorPoint?,
    measureSelection: MeasureSelection?,
    onAnchorPicked: (AnchorPoint) -> Unit,
    onLinePicked: (UserLine) -> Unit,
    onForecastLinePicked: (UserLine) -> Unit,
    onForecastChanged: (LineForecast?) -> Unit,
    onMeasurePointPicked: (AnchorPoint) -> Unit,
    onDeleteLinePicked: (UserLine) -> Unit,
    onBoxChanged: (BoxSelection?) -> Unit,
    onBoxSelectionStarted: () -> Unit,
    onBoxCommitted: (BoxSelection) -> Unit,
    onPatternsDetected: (List<PatternCandidate>) -> Unit,
    onPanRange: (Float, Float) -> Unit,
    onPanFinished: (Float, Float) -> Unit,
    onToggleLogScale: () -> Unit,
    onToggleFreeMoveZoom: () -> Unit,
    onToggleMeasureMode: () -> Unit,
    onPickCandleMode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isPanning by remember { mutableStateOf(false) }
    var liveRangeStart by remember { mutableStateOf(rangeStart) }
    var liveRangeEnd by remember { mutableStateOf(rangeEnd) }
    var freeYLow by remember { mutableStateOf<Float?>(null) }
    var freeYHigh by remember { mutableStateOf<Float?>(null) }
    var candleTooltip by remember { mutableStateOf<CandleTooltip?>(null) }
    var lineTooltip by remember { mutableStateOf<LineTooltip?>(null) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(rangeStart, rangeEnd, isPanning) {
        if (!isPanning) {
            liveRangeStart = rangeStart
            liveRangeEnd = rangeEnd
        }
    }

    val currentRangeStart = rememberUpdatedState(liveRangeStart)
    val currentRangeEnd = rememberUpdatedState(liveRangeEnd)
    val currentFreeYLow = rememberUpdatedState(freeYLow)
    val currentFreeYHigh = rememberUpdatedState(freeYHigh)
    val visible = remember(candles, liveRangeStart, liveRangeEnd) {
        visibleCandles(candles, liveRangeStart, liveRangeEnd)
    }
    val visibleFirstIndex = remember(candles, liveRangeStart) {
        visibleFirstIndex(candles, liveRangeStart)
    }
    val currentVisible = rememberUpdatedState(visible)
    val currentCandles = rememberUpdatedState(candles)
    val currentVisibleFirstIndex = rememberUpdatedState(visibleFirstIndex)
    val currentLogScale = rememberUpdatedState(settings.logScale)
    LaunchedEffect(mode, freeMoveZoom, visible) {
        if (mode != ChartMode.None || freeMoveZoom || visible.isEmpty()) {
            candleTooltip = null
            lineTooltip = null
        }
    }
    LaunchedEffect(freeMoveZoom, visible) {
        if (freeMoveZoom && visible.isNotEmpty() && (freeYLow == null || freeYHigh == null)) {
            val low = visible.minOf { it.low }
            val high = visible.maxOf { it.high }
            val padding = (high - low).coerceAtLeast(1f) * 0.1f
            freeYLow = (low - padding).coerceAtLeast(0.0001f)
            freeYHigh = high + padding
        }
        if (!freeMoveZoom) {
            freeYLow = null
            freeYHigh = null
        }
    }

    Box(modifier = modifier.background(argbColor(settings.backgroundColor))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(freeMoveZoom, canvasSize) {
                    if (!freeMoveZoom) return@pointerInput
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2 && canvasSize.width > 0 && canvasSize.height > 0) {
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom().coerceIn(0.2f, 5f)
                                val xSpan = (liveRangeEnd - liveRangeStart).coerceAtLeast(0.5f)
                                val zoomedXSpan = (xSpan / zoom).coerceIn(0.3f, 100f)
                                val xFocus = (centroid.x / canvasSize.width.toFloat()).coerceIn(0f, 1f)
                                val xFocusValue = liveRangeStart + xSpan * xFocus
                                var nextStart = xFocusValue - zoomedXSpan * xFocus
                                nextStart += -pan.x / canvasSize.width.toFloat() * zoomedXSpan
                                nextStart = nextStart.coerceIn(0f, FUTURE_RANGE_START_LIMIT)
                                liveRangeStart = nextStart
                                liveRangeEnd = nextStart + zoomedXSpan
                                onPanRange(liveRangeStart, liveRangeEnd)

                                val currentLow = freeYLow ?: visible.minOfOrNull { it.low } ?: 0f
                                val currentHigh = freeYHigh ?: visible.maxOfOrNull { it.high } ?: 1f
                                val ySpan = (currentHigh - currentLow).coerceAtLeast(0.0001f)
                                val zoomedYSpan = (ySpan / zoom).coerceAtLeast(0.0001f)
                                val yFocus = (1f - centroid.y / canvasSize.height.toFloat()).coerceIn(0f, 1f)
                                val yFocusValue = currentLow + ySpan * yFocus
                                var nextLow = yFocusValue - zoomedYSpan * yFocus
                                nextLow += pan.y / canvasSize.height.toFloat() * zoomedYSpan
                                freeYLow = nextLow.coerceAtLeast(0.0001f)
                                freeYHigh = freeYLow!! + zoomedYSpan
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(
                    mode,
                    freeMoveZoom,
                    visible,
                    candles,
                    lines,
                    patterns,
                    canvasSize,
                    settings.logScale,
                    forecastLine,
                    liveRangeStart,
                    liveRangeEnd,
                    visibleFirstIndex,
                ) {
                    detectTapGestures { tap ->
                        if (freeMoveZoom) return@detectTapGestures
                        if (visible.isEmpty() || canvasSize.width == 0) return@detectTapGestures
                        val mapper = ChartMapper(
                            visible,
                            canvasSize.width.toFloat(),
                            canvasSize.height.toFloat(),
                            settings.logScale,
                            if (freeMoveZoom) freeYLow else null,
                            if (freeMoveZoom) freeYHigh else null,
                            liveRangeStart,
                            liveRangeEnd,
                            visibleFirstIndex,
                            candles,
                        )
                        when (mode) {
                            ChartMode.None -> {
                                lineTooltip = mapper.nearestLineTooltip(tap, lines, patterns)
                                candleTooltip = if (lineTooltip == null) {
                                    mapper.nearestTooltipCandleIndex(tap)?.let { index ->
                                        CandleTooltip(index, visible[index], tap)
                                    }
                                } else {
                                    null
                                }
                            }
                            ChartMode.DrawLine -> onAnchorPicked(mapper.nearestAnchor(tap))
                            ChartMode.ConnectLines -> mapper.nearestLine(tap, lines, excludeId = firstConnectLine?.id)?.let(onLinePicked)
                            ChartMode.DeleteLine -> mapper.nearestLine(tap, lines)?.let(onDeleteLinePicked)
                            ChartMode.ForecastLine -> {
                                val picked = mapper.nearestLine(tap, lines)
                                if (picked != null && picked.id != forecastLine?.id) {
                                    onForecastLinePicked(picked)
                                } else {
                                    forecastLine?.let { line ->
                                        onForecastChanged(mapper.forecastAt(line, tap))
                                    }
                                }
                            }
                            ChartMode.LowHighMeasure -> onMeasurePointPicked(mapper.nearestAnchor(tap))
                            else -> Unit
                        }
                    }
                }
                .pointerInput(
                    mode,
                    freeMoveZoom,
                    canvasSize,
                ) {
                    var boxStart: Offset? = null
                    var currentBox: BoxSelection? = null
                    var panAccumulatedPx = 0f
                    var panAccumulatedYPx = 0f
                    var panStartRange = 0f
                    var panEndRange = 100f
                    var panStartYLow = 0f
                    var panStartYHigh = 1f
                    var panLiveStart = rangeStart
                    var panLiveEnd = rangeEnd
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (freeMoveZoom) {
                                panAccumulatedPx = 0f
                                panAccumulatedYPx = 0f
                                panStartRange = currentRangeStart.value
                                panEndRange = currentRangeEnd.value
                                val dragVisible = currentVisible.value
                                val fallbackLow = dragVisible.minOfOrNull { it.low } ?: 0f
                                val fallbackHigh = dragVisible.maxOfOrNull { it.high } ?: 1f
                                panStartYLow = currentFreeYLow.value ?: fallbackLow
                                panStartYHigh = currentFreeYHigh.value ?: fallbackHigh
                                if (freeYLow == null || freeYHigh == null) {
                                    val padding = (fallbackHigh - fallbackLow).coerceAtLeast(1f) * 0.1f
                                    freeYLow = (fallbackLow - padding).coerceAtLeast(0.0001f)
                                    freeYHigh = fallbackHigh + padding
                                    panStartYLow = freeYLow ?: panStartYLow
                                    panStartYHigh = freeYHigh ?: panStartYHigh
                                }
                            } else if (mode == ChartMode.BoxSelect) {
                                onBoxSelectionStarted()
                                boxStart = offset
                                currentBox = analysisSelection(offset, offset, canvasSize.height.toFloat())
                                onBoxChanged(currentBox)
                            } else if (mode == ChartMode.None) {
                                isPanning = true
                                candleTooltip = null
                                lineTooltip = null
                                panAccumulatedPx = 0f
                                panStartRange = currentRangeStart.value
                                panEndRange = currentRangeEnd.value
                                panLiveStart = panStartRange
                                panLiveEnd = panEndRange
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (freeMoveZoom && canvasSize.width > 0 && canvasSize.height > 0) {
                                panAccumulatedPx += dragAmount.x
                                panAccumulatedYPx += dragAmount.y
                                val xSpan = (panEndRange - panStartRange).coerceAtLeast(0.5f)
                                panLiveStart = (panStartRange - panAccumulatedPx / canvasSize.width.toFloat() * xSpan)
                                    .coerceIn(0f, FUTURE_RANGE_START_LIMIT)
                                panLiveEnd = panLiveStart + xSpan
                                liveRangeStart = panLiveStart
                                liveRangeEnd = panLiveEnd
                                onPanRange(panLiveStart, panLiveEnd)

                                val ySpan = (panStartYHigh - panStartYLow).coerceAtLeast(0.0001f)
                                val yDelta = panAccumulatedYPx / canvasSize.height.toFloat() * ySpan
                                val nextLow = (panStartYLow + yDelta).coerceAtLeast(0.0001f)
                                freeYLow = nextLow
                                freeYHigh = nextLow + ySpan
                            } else if (mode == ChartMode.BoxSelect) {
                                val start = boxStart ?: change.position
                                currentBox = analysisSelection(start, change.position, canvasSize.height.toFloat())
                                onBoxChanged(currentBox)
                            } else if (mode == ChartMode.None && canvasSize.width > 0) {
                                panAccumulatedPx += dragAmount.x
                                val span = (panEndRange - panStartRange).coerceAtLeast(1f)
                                val delta = -panAccumulatedPx / canvasSize.width.toFloat() * span
                                panLiveStart = (panStartRange + delta).coerceIn(0f, FUTURE_RANGE_START_LIMIT)
                                panLiveEnd = panLiveStart + span
                                liveRangeStart = panLiveStart
                                liveRangeEnd = panLiveEnd
                                onPanRange(panLiveStart, panLiveEnd)
                            }
                        },
                        onDragEnd = {
                            if (freeMoveZoom) {
                                onPanFinished(panLiveStart, panLiveEnd)
                            } else if (mode == ChartMode.BoxSelect) {
                                currentBox?.let { box ->
                                    onBoxCommitted(box)
                                    val dragVisible = currentVisible.value
                                    val dragCandles = currentCandles.value
                                    if (dragVisible.isNotEmpty() && canvasSize.width > 0) {
                                        val mapper = ChartMapper(
                                            dragVisible,
                                            canvasSize.width.toFloat(),
                                            canvasSize.height.toFloat(),
                                            currentLogScale.value,
                                            rangeStartPercent = liveRangeStart,
                                            rangeEndPercent = liveRangeEnd,
                                            firstVisibleIndex = currentVisibleFirstIndex.value,
                                            allCandles = dragCandles,
                                        )
                                        onPatternsDetected(
                                            detectConvergencePatterns(
                                                dragVisible,
                                                mapper,
                                                box,
                                                settings.timeframe,
                                                settings.backgroundColor,
                                            )
                                        )
                                    }
                                }
                            } else if (mode == ChartMode.None) {
                                isPanning = false
                                onPanRange(panLiveStart, panLiveEnd)
                                onPanFinished(panLiveStart, panLiveEnd)
                            }
                            boxStart = null
                            currentBox = null
                        },
                        onDragCancel = {
                            boxStart = null
                            currentBox = null
                            isPanning = false
                            liveRangeStart = rangeStart
                            liveRangeEnd = rangeEnd
                            onBoxChanged(null)
                        },
                    )
                },
        ) {
            if (visible.isEmpty()) return@Canvas
            val mapper = ChartMapper(
                visible,
                size.width,
                size.height,
                settings.logScale,
                if (freeMoveZoom) freeYLow else null,
                if (freeMoveZoom) freeYHigh else null,
                liveRangeStart,
                liveRangeEnd,
                visibleFirstIndex,
                candles,
            )
            val gridColor = argbColor(settings.gridColor)
            val candleColor = argbColor(settings.candleColor)
            val upColor = Color(0xFF26A69A)
            val downColor = Color(0xFFEF5350)

            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            repeat(7) { index ->
                val x = size.width * index / 6f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            drawAxisLabels(visible, mapper, settings)

            val visibleSlotCount = if (candles.size > 1) {
                (candles.lastIndex * (liveRangeEnd - liveRangeStart) / 100f).coerceAtLeast(1f)
            } else {
                visible.size.toFloat().coerceAtLeast(1f)
            }
            val candleWidth = (size.width / visibleSlotCount * 0.45f).coerceIn(2f, 18f)
            if (settings.candleMode == "close") {
                val closePath = Path()
                visible.forEachIndexed { index, candle ->
                    val x = mapper.x(index)
                    val closeY = mapper.y(candle.close)
                    if (index == 0) closePath.moveTo(x, closeY) else closePath.lineTo(x, closeY)
                    drawCircle(Color.White.copy(alpha = 0.72f), radius = 2.2f, center = Offset(x, closeY))
                    drawCircle(candleColor, radius = 1.5f, center = Offset(x, closeY))
                    if (candleTooltip?.index == index) {
                        drawCircle(Color(0xFFF9E2AF), radius = 7f, center = Offset(x, closeY), style = Stroke(width = 2f))
                    }
                }
                drawPath(closePath, candleColor, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
            } else {
                visible.forEachIndexed { index, candle ->
                    val selectedCandle = candleTooltip?.index == index
                    val bodyColor = if (settings.candleMode == "color") {
                        if (candle.close >= candle.open) upColor else downColor
                    } else {
                        candleColor
                    }
                    val x = mapper.x(index)
                    val highY = mapper.y(candle.high)
                    val lowY = mapper.y(candle.low)
                    val openY = mapper.y(candle.open)
                    val closeY = mapper.y(candle.close)
                    drawLine(bodyColor, Offset(x, highY), Offset(x, lowY), strokeWidth = 1.2f)
                    val wickPointRadius = (candleWidth * 0.18f).coerceIn(1.1f, 2.2f)
                    drawCircle(Color.White.copy(alpha = 0.82f), radius = wickPointRadius + 0.7f, center = Offset(x, highY))
                    drawCircle(Color.White.copy(alpha = 0.82f), radius = wickPointRadius + 0.7f, center = Offset(x, lowY))
                    drawCircle(bodyColor.copy(alpha = 0.92f), radius = wickPointRadius, center = Offset(x, highY))
                    drawCircle(bodyColor.copy(alpha = 0.92f), radius = wickPointRadius, center = Offset(x, lowY))
                    drawRect(
                        color = bodyColor,
                        topLeft = Offset(x - candleWidth / 2f, min(openY, closeY)),
                        size = Size(candleWidth, max(2f, abs(closeY - openY))),
                    )
                    if (selectedCandle) {
                        val highlight = Color(0xFFF9E2AF)
                        drawCircle(highlight.copy(alpha = 0.28f), radius = wickPointRadius + 7f, center = Offset(x, highY))
                        drawCircle(highlight.copy(alpha = 0.28f), radius = wickPointRadius + 7f, center = Offset(x, lowY))
                        drawCircle(highlight, radius = wickPointRadius + 4f, center = Offset(x, highY), style = Stroke(width = 2f))
                        drawCircle(highlight, radius = wickPointRadius + 4f, center = Offset(x, lowY), style = Stroke(width = 2f))
                    }
                }
            }

            lines.forEach { line ->
                val selected = firstConnectLine?.id == line.id || forecastLine?.id == line.id
                val x1 = mapper.xForTime(line.startTimeMillis)
                val x2 = mapper.xForTime(line.endTimeMillis)
                val y1 = mapper.y(line.startPrice)
                val y2 = mapper.y(line.endPrice)
                drawLine(
                    color = if (selected) Color(0xFFA6E3A1) else argbColor(line.color),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (selected) settings.lineWidth + 1.2f else settings.lineWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(Color.White, radius = settings.pointSize / 2f, center = Offset(x1, y1))
                drawCircle(Color.White, radius = settings.pointSize / 2f, center = Offset(x2, y2))
            }

            patterns.forEachIndexed { index, pattern ->
                val active = activePatternIndex == null || activePatternIndex == index
                drawPattern(pattern, mapper, settings, active)
            }

            projections.forEach { projection ->
                drawProjection(projection, mapper, settings)
            }

            lines.forEach { line ->
                if (line.id != forecastLine?.id) {
                    mapper.savedForecast(line)?.let { forecast ->
                        drawLineForecast(line, forecast, mapper, settings)
                    }
                }
            }

            forecastLine?.let { line ->
                drawLineForecast(line, lineForecast ?: mapper.savedForecast(line), mapper, settings)
            }

            candleTooltip?.let {
                drawCandleTooltip(it, mapper, settings)
            }

            lineTooltip?.let {
                drawLineTooltip(it, settings)
            }

            pendingAnchor?.let {
                drawCircle(
                    color = Color(0xFFA6E3A1),
                    radius = settings.pointSize,
                    center = Offset(mapper.xForTime(it.timeMillis), mapper.y(it.price)),
                    style = Stroke(width = 2f),
                )
            }

            measureSelection?.let { selection ->
                drawMeasureSelection(selection, mapper, settings)
            }
            measureStart?.let { start ->
                drawCircle(
                    color = Color(0xFF2DD4BF),
                    radius = settings.pointSize,
                    center = Offset(mapper.xForTime(start.timeMillis), mapper.y(start.price)),
                    style = Stroke(width = 2f),
                )
            }

            committedBox?.let { drawBoxSelection(it, Color(0x6689B4FA), Color(0x2289B4FA)) }
            activeBox?.let { drawBoxSelection(it, Color(0xFFE5C890), Color(0x22E5C890)) }

            drawVisibleRangePrices(visible, settings)
        }

        VisibleRangeSummaryPanel(
            visible = visible,
            active = mode == ChartMode.LowHighMeasure,
            onMeasureClick = onToggleMeasureMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 34.dp),
        )

        ChartChipButton(
            text = "LOG",
            active = settings.logScale,
            onClick = onToggleLogScale,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
        )
        ChartChipButton(
            text = "PAN",
            active = freeMoveZoom,
            onClick = onToggleFreeMoveZoom,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp, end = 8.dp),
        )
        ChartChipButton(
            text = "보기",
            active = showViewModeMenu,
            onClick = { showViewModeMenu = !showViewModeMenu },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = 8.dp),
        )
        if (showViewModeMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 110.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ChartViewModeButton("현재", settings.candleMode == "mono") {
                    onPickCandleMode("mono")
                    showViewModeMenu = false
                }
                ChartViewModeButton("전통", settings.candleMode == "color") {
                    onPickCandleMode("color")
                    showViewModeMenu = false
                }
                ChartViewModeButton("종가선", settings.candleMode == "close") {
                    onPickCandleMode("close")
                    showViewModeMenu = false
                }
            }
        }
    }
}

@Composable
private fun ChartChipButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF2D3B58) else Color(0x77202534),
            contentColor = Color(0xFFE5E7FF),
        ),
        modifier = modifier.height(28.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ChartViewModeButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .background(if (active) Color(0xEE2D3B58) else Color(0xDD202534), RoundedCornerShape(7.dp))
            .border(1.dp, if (active) Color(0xFF89B4FA) else Color(0xFF343A52), RoundedCornerShape(7.dp))
            .pointerInput(text) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFE5E7FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun VisibleRangeSummaryPanel(
    visible: List<Candle>,
    active: Boolean,
    onMeasureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (visible.isEmpty()) return
    val startOpen = visible.first().open
    val endClose = visible.last().close
    val percent = if (startOpen != 0f) (endClose - startOpen) / startOpen * 100f else 0f
    val valueColor = if (percent >= 0f) Color(0xFF22C55E) else Color(0xFFF87171)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onMeasureClick,
            shape = RoundedCornerShape(7.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Color(0xFF2563EB) else Color(0xEE1F2A44),
                contentColor = Color(0xFFEAF2FF),
            ),
            modifier = Modifier.height(28.dp),
        ) {
            Text("상승/하락률", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(
            text = "시작/종가 : ${"%+.1f".format(percent)}%",
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoxSelection(
    box: BoxSelection,
    strokeColor: Color,
    fillColor: Color,
) {
    val rect = box.rect
    drawRect(
        color = fillColor,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 2f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisibleRangePrices(
    visible: List<Candle>,
    settings: AppSettings,
) {
    if (visible.isEmpty()) return
    val values = listOf(
        visible.first().open to android.graphics.Color.rgb(229, 231, 255),
        visible.minOf { it.low } to android.graphics.Color.rgb(96, 165, 250),
        visible.maxOf { it.high } to android.graphics.Color.rgb(248, 113, 113),
        visible.last().close to android.graphics.Color.rgb(250, 204, 21),
    )
    val paint = Paint().apply {
        textSize = chartTextPx(settings, 2.35f)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    var x = 12f
    val y = 36f
    values.forEach { (price, color) ->
        val text = "$${"%.1f".format(price)}"
        paint.color = color
        drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
        x += paint.measureText(text) + 18f
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeasureSelection(
    selection: MeasureSelection,
    mapper: ChartMapper,
    settings: AppSettings,
) {
    val start = Offset(mapper.xForTime(selection.start.timeMillis), mapper.y(selection.start.price))
    val end = Offset(mapper.xForTime(selection.end.timeMillis), mapper.y(selection.end.price))
    val pastPoint = if (selection.start.timeMillis <= selection.end.timeMillis) selection.start else selection.end
    val futurePoint = if (selection.start.timeMillis <= selection.end.timeMillis) selection.end else selection.start
    val percent = if (pastPoint.price != 0f) {
        (futurePoint.price - pastPoint.price) / pastPoint.price * 100f
    } else {
        0f
    }
    val positive = percent >= 0f
    val color = if (positive) Color(0xFF22C55E) else Color(0xFFF87171)
    val label = if (positive) "상승율" else "하락율"
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = settings.lineWidth + 0.8f,
        cap = StrokeCap.Round,
    )
    drawCircle(Color.White, radius = settings.pointSize / 2f, center = start)
    drawCircle(Color.White, radius = settings.pointSize / 2f, center = end)
    drawCircle(color.copy(alpha = 0.24f), radius = settings.pointSize + 4f, center = end)

    val text = "$label : ${"%+.1f".format(percent)}%"
    val textSize = chartTextPx(settings, 2.45f)
    val paint = Paint().apply {
        this.color = color.toArgb()
        this.textSize = textSize
        isAntiAlias = true
    }
    val labelMaxX = (size.width - paint.measureText(text) - 10f).coerceAtLeast(10f)
    val labelMaxY = (size.height - 10f).coerceAtLeast(textSize + 10f)
    val labelX = (end.x + 10f).coerceIn(10f, labelMaxX)
    val labelY = (end.y - 10f).coerceIn(textSize + 10f, labelMaxY)
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisibleRangePercentBox(
    visible: List<Candle>,
    settings: AppSettings,
) {
    if (visible.isEmpty()) return
    val startOpen = visible.first().open
    val endClose = visible.last().close
    val openClosePercent = if (startOpen != 0f) (endClose - startOpen) / startOpen * 100f else 0f
    val text = "시작/종가 : ${"%+.1f".format(openClosePercent)}%"
    val textSize = chartTextPx(settings, 2.15f)
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(229, 231, 255)
        this.textSize = textSize
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    val boxWidth = paint.measureText(text) + 30f
    val boxHeight = textSize + 26f
    val left = (size.width - boxWidth - 12f).coerceAtLeast(12f)
    val top = (size.height - boxHeight - 36f).coerceAtLeast(48f)
    drawRoundRect(
        color = Color(0xDD111827),
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = Color(0xFF3B82F6),
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.2f),
    )
    paint.color = if (openClosePercent >= 0f) android.graphics.Color.rgb(34, 197, 94) else android.graphics.Color.rgb(248, 113, 113)
    drawContext.canvas.nativeCanvas.drawText(
        text,
        left + 15f,
        top + textSize + 9f,
        paint,
    )
}

private fun analysisSelection(start: Offset, end: Offset, height: Float): BoxSelection {
    val safeHeight = height.coerceAtLeast(1f)
    return BoxSelection(
        start = Offset(start.x, 0f),
        end = Offset(end.x, safeHeight),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabels(
    visible: List<Candle>,
    mapper: ChartMapper,
    settings: AppSettings,
) {
    if (visible.isEmpty()) return
    if (size.width < 96f || size.height < 48f) return
    val paint = Paint().apply {
        color = settings.textColor.toInt()
        textSize = chartTextPx(settings, 1.65f)
        isAntiAlias = true
    }
    repeat(5) { index ->
        val y = size.height * index / 4f
        val price = mapper.priceAtY(y)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            "$${"%.0f".format(price)}",
            size.width - 8f,
            (y + 18f).coerceIn(18f, (size.height - 8f).coerceAtLeast(18f)),
            paint,
        )
    }
    val dateLabelIndices = (0..3)
        .map { step -> ((visible.lastIndex) * step / 3f).roundToInt().coerceIn(0, visible.lastIndex) }
        .distinct()
    var lastLabelX = -1000f
    dateLabelIndices.forEach { index ->
        val x = mapper.x(index)
        if (x - lastLabelX < 64f) return@forEach
        lastLabelX = x
        paint.textAlign = Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(
            AXIS_DATE_FORMAT.format(Date(visible[index].timeMillis)),
            x.coerceIn(42f, (size.width - 42f).coerceAtLeast(42f)),
            size.height - 10f,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProjection(
    projection: LineProjection,
    mapper: ChartMapper,
    settings: AppSettings,
) {
    val projectionColor = contrastProjectionColor(settings.backgroundColor)
    val color = argbColor(projectionColor)
    val halo = if (relativeLuminance(settings.backgroundColor) > 0.46f) {
        Color.Black.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    val star = Offset(mapper.xForTime(projection.timeMillis), mapper.y(projection.price))
    listOf(projection.first, projection.second).forEach { line ->
        val anchor = line.futureAnchor()
        val start = Offset(mapper.xForTime(anchor.first), mapper.y(anchor.second))
        drawLine(halo, start, star, strokeWidth = settings.lineWidth + 2.2f, cap = StrokeCap.Round)
        drawLine(color, start, star, strokeWidth = settings.lineWidth + 0.8f, cap = StrokeCap.Round)
    }
    drawStar(star, settings.starSize, color)
    val paint = Paint().apply {
        this.color = projectionColor.toInt()
        textSize = chartTextPx(settings, 2.0f)
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(DATE_FORMAT.format(Date(projection.timeMillis)), star.x + 12f, star.y - 4f, paint)
    drawContext.canvas.nativeCanvas.drawText("$${"%.1f".format(projection.price)}", star.x + 12f, star.y + 24f, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineForecast(
    line: UserLine,
    forecast: LineForecast?,
    mapper: ChartMapper,
    settings: AppSettings,
) {
    val color = Color(0xFFB4F8C8)
    val anchor = line.futureAnchor()
    val start = Offset(mapper.xForTime(anchor.first), mapper.y(anchor.second))
    val endX = mapper.forecastDrawEndX(line, forecast?.queryTimeMillis)
    val endY = mapper.yForVisualLineAtX(line, endX) ?: return
    drawLine(color, start, Offset(endX, endY), strokeWidth = settings.lineWidth, cap = StrokeCap.Round)

    forecast?.let {
        val point = Offset(mapper.xForTime(it.queryTimeMillis), mapper.y(it.queryPrice))
        drawCircle(Color.White, radius = settings.pointSize / 2f, center = point)
        drawLine(Color(0x88B4F8C8), Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = 1f)
        val paint = Paint().apply {
            this.color = settings.textColor.toInt()
            textSize = chartTextPx(settings, 2.0f)
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            DATE_FORMAT.format(Date(it.queryTimeMillis)),
            point.x + 12f,
            point.y - 4f,
            paint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "$${"%.1f".format(it.queryPrice)}",
            point.x + 12f,
            point.y + chartTextPx(settings, 2.0f) + 2f,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCandleTooltip(
    tooltip: CandleTooltip,
    mapper: ChartMapper,
    settings: AppSettings,
) {
    val candle = tooltip.candle
    val x = mapper.x(tooltip.index)
    val highY = mapper.y(candle.high)
    val lowY = mapper.y(candle.low)
    val tooltipTextSize = chartTextPx(settings, 1.9f)
    val lineStep = tooltipTextSize + 7f
    val boxWidth = 196f
    val boxHeight = lineStep * 5f + 24f
    val left = 12f
    val boxY = 56f.coerceAtMost((size.height - boxHeight - 10f).coerceAtLeast(10f))

    drawLine(
        color = Color(0x99F9E2AF),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.5f,
    )
    drawCircle(Color(0xFFF9E2AF), radius = 7f, center = Offset(x, highY), style = Stroke(width = 2f))
    drawCircle(Color(0xFFF9E2AF), radius = 7f, center = Offset(x, lowY), style = Stroke(width = 2f))
    drawRoundRect(
        color = Color(0xEE111827),
        topLeft = Offset(left, boxY),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = Color(0xFF3B82F6),
        topLeft = Offset(left, boxY),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.2f),
    )

    val labelPaint = Paint().apply {
        color = android.graphics.Color.argb(235, 229, 231, 255)
        textSize = tooltipTextSize
        isAntiAlias = true
    }
    val valuePaint = Paint().apply {
        color = android.graphics.Color.argb(245, 137, 180, 250)
        textSize = tooltipTextSize
        isAntiAlias = true
    }
    val lines = listOf(
        TOOLTIP_DATE_FORMAT.format(Date(candle.timeMillis)),
        "시작  $${"%.2f".format(candle.open)}",
        "종료  $${"%.2f".format(candle.close)}",
        "최고  $${"%.2f".format(candle.high)}",
        "최저  $${"%.2f".format(candle.low)}",
    )
    lines.forEachIndexed { index, text ->
        val paint = if (index == 0) valuePaint else labelPaint
        drawContext.canvas.nativeCanvas.drawText(
            text,
            left + 12f,
            boxY + tooltipTextSize + 8f + index * lineStep,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineTooltip(
    tooltip: LineTooltip,
    settings: AppSettings,
) {
    val accent = argbColor(tooltip.color)
    val point = tooltip.point
    val textSize = chartTextPx(settings, 1.85f)
    val boxWidth = 178f
    val boxHeight = textSize * 2f + 34f
    val left = (point.x + 12f).coerceIn(10f, (size.width - boxWidth - 10f).coerceAtLeast(10f))
    val top = (point.y - boxHeight - 12f).coerceIn(10f, (size.height - boxHeight - 10f).coerceAtLeast(10f))

    drawLine(
        color = accent.copy(alpha = 0.58f),
        start = Offset(point.x, 0f),
        end = Offset(point.x, size.height),
        strokeWidth = 1.1f,
    )
    drawCircle(accent.copy(alpha = 0.24f), radius = 11f, center = point)
    drawCircle(Color.White, radius = 4.5f, center = point)
    drawRoundRect(
        color = Color(0xEE111827),
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.2f),
    )
    val labelPaint = Paint().apply {
        color = android.graphics.Color.argb(235, 229, 231, 255)
        this.textSize = textSize
        isAntiAlias = true
    }
    val valuePaint = Paint().apply {
        color = tooltip.color.toInt()
        this.textSize = textSize
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        "${tooltip.label} ${TOOLTIP_DATE_FORMAT.format(Date(tooltip.timeMillis))}",
        left + 10f,
        top + textSize + 8f,
        labelPaint,
    )
    drawContext.canvas.nativeCanvas.drawText(
        "$${"%.1f".format(tooltip.price)}",
        left + 10f,
        top + textSize * 2f + 18f,
        valuePaint,
    )
}

private fun chartTextPx(settings: AppSettings, multiplier: Float): Float {
    return max(21f, settings.fontSize.coerceAtLeast(13f) * multiplier)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPattern(
    pattern: PatternCandidate,
    mapper: ChartMapper,
    settings: AppSettings,
    active: Boolean,
) {
    val color = argbColor(pattern.color)
    val alphaColor = if (active) color else color.copy(alpha = 0.18f)
    val lineWidth = if (active) settings.lineWidth else 1f
    val pointRadius = if (active) settings.pointSize / 2f else 0f
    val conv = Offset(mapper.xForTime(pattern.convergence.timeMillis), mapper.y(pattern.convergence.price))
    val points = listOf(pattern.upperA, pattern.upperB, pattern.lowerA, pattern.lowerB)

    drawLine(
        alphaColor,
        Offset(mapper.xForTime(pattern.upperA.timeMillis), mapper.y(pattern.upperA.price)),
        conv,
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        alphaColor,
        Offset(mapper.xForTime(pattern.lowerA.timeMillis), mapper.y(pattern.lowerA.price)),
        conv,
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
    )
    if (active) {
        points.forEach { point ->
            drawCircle(
                Color.White,
                radius = pointRadius,
                center = Offset(mapper.xForTime(point.timeMillis), mapper.y(point.price)),
            )
        }
        drawStar(conv, settings.starSize, Color.White)
        val paint = Paint().apply {
            this.color = settings.textColor.toInt()
            textSize = chartTextPx(settings, 2.0f)
            isAntiAlias = true
        }
        val dateText = DATE_FORMAT.format(Date(pattern.convergence.timeMillis))
        drawContext.canvas.nativeCanvas.drawText(
            dateText,
            conv.x + 12f,
            conv.y - 4f,
            paint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "$${"%.1f".format(pattern.convergence.price)}",
            conv.x + 12f,
            conv.y + chartTextPx(settings, 2.0f) + 2f,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val path = Path()
    val inner = radius * 0.45f
    repeat(10) { index ->
        val angle = -Math.PI / 2.0 + index * Math.PI / 5.0
        val r = if (index % 2 == 0) radius else inner
        val p = Offset(
            x = center.x + (kotlin.math.cos(angle) * r).toFloat(),
            y = center.y + (kotlin.math.sin(angle) * r).toFloat(),
        )
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
private fun RangeSelector(
    candles: List<Candle>,
    start: Float,
    end: Float,
    onChange: (Float, Float) -> Unit,
    onChangeFinished: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var dragAccumulatedPx by remember { mutableStateOf(0f) }
    var startAtDrag by remember { mutableStateOf(start) }
    var endAtDrag by remember { mutableStateOf(end) }
    var liveStart by remember { mutableStateOf(start) }
    var liveEnd by remember { mutableStateOf(end) }
    val overview = remember(candles) { overviewCandles(candles, maxPoints = 120) }

    LaunchedEffect(start, end, dragMode) {
        if (dragMode == null) {
            liveStart = start
            liveEnd = end
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val leftPad = 64f
                        val rightPad = 64f
                        val trackWidth = (size.width - leftPad - rightPad).toFloat().coerceAtLeast(1f)
                        val currentStart = liveStart
                        val currentEnd = liveEnd
                        val startX = leftPad + trackWidth * currentStart / 100f
                        val endX = leftPad + trackWidth * currentEnd / 100f
                        val trackY = size.height * 0.82f
                        val handleTouchRadius = 18f
                        val handleBand = abs(offset.y - trackY) <= 20f
                        dragMode = when {
                            handleBand && abs(offset.x - startX) <= handleTouchRadius -> DragMode.Start
                            handleBand && abs(offset.x - endX) <= handleTouchRadius -> DragMode.End
                            offset.x in startX..endX -> DragMode.Window
                            offset.x < startX -> DragMode.Start
                            else -> DragMode.End
                        }
                        dragAccumulatedPx = 0f
                        startAtDrag = currentStart
                        endAtDrag = currentEnd
                    },
                    onDrag = { change, dragAmount ->
                        dragAccumulatedPx += dragAmount.x
                        val leftPad = 64f
                        val rightPad = 64f
                        val trackWidth = (size.width - leftPad - rightPad).toFloat().coerceAtLeast(1f)
                        val deltaPercent = dragAccumulatedPx / trackWidth * 100f
                        val pointerPercent = (change.position.x - leftPad) / trackWidth * 100f
                        when (dragMode) {
                            DragMode.Start -> {
                                val maxStart = (liveEnd - 1f).coerceAtLeast(0f)
                                liveStart = pointerPercent.coerceIn(0f, maxStart)
                                onChange(liveStart, liveEnd)
                            }
                            DragMode.End -> {
                                liveEnd = pointerPercent.coerceAtLeast(liveStart + 1f)
                                onChange(liveStart, liveEnd)
                            }
                            DragMode.Window -> {
                                val span = endAtDrag - startAtDrag
                                liveStart = (startAtDrag + deltaPercent).coerceAtLeast(0f)
                                liveEnd = liveStart + span
                                onChange(liveStart, liveEnd)
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        dragMode = null
                        onChangeFinished(liveStart, liveEnd)
                    },
                    onDragCancel = { dragMode = null },
                )
            },
    ) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val chartTop = 4f
        val chartBottom = height * 0.62f
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val leftPad = 64f
        val rightPad = 64f
        val trackWidth = (width - leftPad - rightPad).coerceAtLeast(1f)
        val displayStart = liveStart
        val displayEnd = liveEnd
        val startX = leftPad + trackWidth * displayStart / 100f
        val endX = leftPad + trackWidth * displayEnd / 100f

        if (overview.size > 1) {
            val low = overview.minOf { it.low }
            val high = overview.maxOf { it.high }
            val span = (high - low).coerceAtLeast(1f)
            fun point(index: Int, price: Float): Offset {
                val x = index / (overview.size - 1).toFloat() * width
                val y = chartBottom - (price - low) / span * chartHeight
                return Offset(x, y)
            }

            val area = Path()
            val line = Path()
            overview.forEachIndexed { index, candle ->
                val p = point(index, candle.close)
                if (index == 0) {
                    area.moveTo(p.x, chartBottom)
                    area.lineTo(p.x, p.y)
                    line.moveTo(p.x, p.y)
                } else {
                    area.lineTo(p.x, p.y)
                    line.lineTo(p.x, p.y)
                }
            }
            area.lineTo(width, chartBottom)
            area.close()

            drawPath(area, Color(0x5535597E))
            drawPath(line, Color(0xFF1E88FF), style = Stroke(width = 2f))
            val visibleLeft = startX.coerceIn(0f, width)
            val visibleRight = endX.coerceIn(0f, width)
            if (visibleLeft > 0f) {
                drawRect(
                    color = Color(0x99101422),
                    topLeft = Offset(0f, chartTop),
                    size = Size(visibleLeft, chartHeight),
                )
            }
            if (visibleRight < width) {
                drawRect(
                    color = Color(0x99101422),
                    topLeft = Offset(visibleRight, chartTop),
                    size = Size(width - visibleRight, chartHeight),
                )
            }
            drawLine(Color(0xAA89B4FA), Offset(visibleLeft, chartTop), Offset(visibleLeft, chartBottom), strokeWidth = 1.5f)
            drawLine(Color(0xAA89B4FA), Offset(visibleRight, chartTop), Offset(visibleRight, chartBottom), strokeWidth = 1.5f)
        }

        val trackY = height * 0.82f
        drawLine(Color(0xFF3A3A4C), Offset(leftPad, trackY), Offset(width - rightPad, trackY), 5f)
        drawLine(Color(0xFF9B5DE5), Offset(startX, trackY), Offset(endX, trackY), 5f, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 5f, center = Offset(startX, trackY))
        drawCircle(Color.White, radius = 5f, center = Offset(endX, trackY))
        drawCircle(Color(0xFF9B5DE5), radius = 3.5f, center = Offset(startX, trackY))
        drawCircle(Color(0xFF9B5DE5), radius = 3.5f, center = Offset(endX, trackY))

    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onPickOrientation: (OrientationMode) -> Unit,
    onSaveTheme: (String, Long, Long, Long, Long) -> Unit,
    onSaveCandleMode: (String) -> Unit,
    onSaveCandleColor: (Long) -> Unit,
    onSaveBackgroundColor: (Long) -> Unit,
    onSaveGridColor: (Long) -> Unit,
    onSaveTextColor: (Long) -> Unit,
    onSaveFontFamily: (String) -> Unit,
    onSaveFontSize: (Float) -> Unit,
    onSaveLineWidth: (Float) -> Unit,
    onSavePointSize: (Float) -> Unit,
    onSaveStarSize: (Float) -> Unit,
    onGuideUpdating: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("템플릿", fontWeight = FontWeight.Bold)
                ChoiceRows(THEMES, columns = 3) { theme ->
                    SmallChoiceButton(
                        text = theme.label,
                        active = settings.theme == theme.id,
                    ) {
                        onSaveTheme(theme.id, theme.background, theme.grid, theme.text, theme.candle)
                    }
                }

                Text("캔들 색상", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChoiceButton("단색", settings.candleMode == "mono") { onSaveCandleMode("mono") }
                    SmallChoiceButton("상승/하락", settings.candleMode == "color") { onSaveCandleMode("color") }
                    SmallChoiceButton("종가선", settings.candleMode == "close") { onSaveCandleMode("close") }
                }
                ColorRow("캔들", CANDLE_SWATCHES, settings.candleColor, onSaveCandleColor)
                ColorRow("배경", BACKGROUND_SWATCHES, settings.backgroundColor, onSaveBackgroundColor)
                ColorRow("축/격자", GRID_SWATCHES, settings.gridColor, onSaveGridColor)
                ColorRow("글자", TEXT_SWATCHES, settings.textColor, onSaveTextColor)

                Button(
                    onClick = onGuideUpdating,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                ) {
                    Text("가이드 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Text("화면 방향", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE7E2EA), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFD2CAD8), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    OrientationMode.entries.forEach { mode ->
                        OrientationChoiceButton(
                            text = mode.label(),
                            active = mode == settings.orientationMode,
                            onClick = { onPickOrientation(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SettingSlider("글자크기", settings.fontSize.coerceAtLeast(12f), 12f..22f, onSaveFontSize)
                SettingSlider("선 굵기", settings.lineWidth, 1f..8f, onSaveLineWidth)
                SettingSlider("점 크기", settings.pointSize, 3f..18f, onSavePointSize)
                SettingSlider("별 크기", settings.starSize, 6f..24f, onSaveStarSize)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("닫기") }
        },
    )
}

@Composable
private fun <T> ChoiceRows(
    items: List<T>,
    columns: Int,
    itemContent: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { itemContent(it) }
            }
        }
    }
}

@Composable
private fun SmallChoiceButton(text: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF3D4866) else Color(0xFF252538),
            contentColor = Color(0xFFE5E7FF),
        ),
        modifier = Modifier.height(32.dp),
    ) {
        Text(text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun OrientationChoiceButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF2563EB) else Color.Transparent,
            contentColor = if (active) Color.White else Color(0xFF3B3748),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (active) 2.dp else 0.dp,
            pressedElevation = 0.dp,
        ),
        modifier = modifier.height(34.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ColorRow(
    label: String,
    colors: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .weight(0.45f)
                    .background(argbColor(color), RoundedCornerShape(4.dp))
                    .border(
                        width = if (selected == color) 2.dp else 1.dp,
                        color = if (selected == color) Color.White else Color(0x66555566),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .pointerInput(color) { detectTapGestures { onPick(color) } },
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text("$label ${"%.1f".format(value)}", fontSize = 13.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

private enum class DragMode {
    Start,
    End,
    Window,
}

private class ChartMapper(
    private val candles: List<Candle>,
    private val width: Float,
    private val height: Float,
    private val logScale: Boolean,
    yLowOverride: Float? = null,
    yHighOverride: Float? = null,
    private val rangeStartPercent: Float? = null,
    private val rangeEndPercent: Float? = null,
    private val firstVisibleIndex: Int = 0,
    private val allCandles: List<Candle> = candles,
) {
    private val fullCandleCount = allCandles.size
    private val low = yLowOverride ?: candles.minOf { it.low }
    private val high = yHighOverride ?: candles.maxOf { it.high }
    private val paddedLow = yLowOverride ?: (low * 0.9f)
    private val paddedHigh = yHighOverride ?: (high + (high - low).coerceAtLeast(1f) * 0.1f)
    private val mappedLow = mapPrice(paddedLow)
    private val mappedHigh = mapPrice(paddedHigh)

    fun x(index: Int): Float {
        return xForLocalIndex(index.toFloat())
    }

    private fun xForLocalIndex(index: Float): Float {
        return xForGlobalIndex(firstVisibleIndex + index)
    }

    private fun xForGlobalIndex(globalIndex: Float): Float {
        val startPercent = rangeStartPercent
        val endPercent = rangeEndPercent
        if (startPercent != null && endPercent != null && fullCandleCount > 1) {
            val fullLastIndex = (fullCandleCount - 1).toFloat()
            val startIndex = fullLastIndex * startPercent / 100f
            val endIndex = fullLastIndex * endPercent / 100f
            val span = (endIndex - startIndex).coerceAtLeast(0.0001f)
            return (globalIndex - startIndex) / span * width
        }
        if (candles.size <= 1) return width / 2f
        return (globalIndex - firstVisibleIndex) / (candles.size - 1).toFloat() * width
    }

    fun xForTime(timeMillis: Long): Float {
        if (allCandles.size <= 1) return width / 2f

        val exactIndex = allCandles.indexOfFirst { it.timeMillis == timeMillis }
        if (exactIndex >= 0) return xForGlobalIndex(exactIndex.toFloat())

        val firstTime = allCandles.first().timeMillis
        val lastTime = allCandles.last().timeMillis
        val globalIndex = when {
            timeMillis < firstTime -> (timeMillis - firstTime) / leadingTimeStep()
            timeMillis > lastTime -> allCandles.lastIndex + (timeMillis - lastTime) / trailingTimeStep()
            else -> {
                val right = allCandles.indexOfFirst { it.timeMillis > timeMillis }
                val left = (right - 1).coerceAtLeast(0)
                if (right <= 0) {
                    0.0
                } else {
                    val leftTime = allCandles[left].timeMillis
                    val rightTime = allCandles[right].timeMillis
                    left + (timeMillis - leftTime).toDouble() / (rightTime - leftTime).toDouble().coerceAtLeast(1.0)
                }
            }
        }
        return xForGlobalIndex(globalIndex.toFloat())
    }

    fun y(price: Float): Float {
        val span = (mappedHigh - mappedLow).coerceAtLeast(0.0001f)
        return height - ((mapPrice(price) - mappedLow) / span).coerceIn(-0.4f, 1.4f) * height
    }

    fun priceAtY(y: Float): Float {
        val ratio = ((height - y) / height).coerceIn(0f, 1f)
        val mapped = mappedLow + (mappedHigh - mappedLow) * ratio
        return if (logScale) exp(mapped).toFloat() else mapped
    }

    fun nearestAnchor(offset: Offset): AnchorPoint {
        val index = nearestCandleIndex(offset)
        val candle = candles[index]
        val highY = y(candle.high)
        val lowY = y(candle.low)
        return if (abs(offset.y - highY) <= abs(offset.y - lowY)) {
            AnchorPoint(candle.timeMillis, candle.high)
        } else {
            AnchorPoint(candle.timeMillis, candle.low)
        }
    }

    fun nearestCandleIndex(offset: Offset): Int {
        return localIndexForOffset(offset.x)
            .roundToInt()
            .coerceIn(0, candles.lastIndex)
    }

    fun nearestTooltipCandleIndex(offset: Offset): Int? {
        if (candles.isEmpty()) return null
        val rawIndex = localIndexForOffset(offset.x)
        val nearest = rawIndex.roundToInt()
        if (nearest !in candles.indices) return null
        val nearestX = x(nearest)
        val slotWidth = if (candles.size > 1) {
            abs(x(1) - x(0)).coerceAtLeast(1f)
        } else {
            width
        }
        val hitSlop = min(28f, max(12f, slotWidth * 0.38f))
        return nearest.takeIf { abs(offset.x - nearestX) <= hitSlop }
    }

    private fun localIndexForOffset(xPosition: Float): Float {
        return if (rangeStartPercent != null && rangeEndPercent != null && fullCandleCount > 1) {
            val fullLastIndex = (fullCandleCount - 1).toFloat()
            val startIndex = fullLastIndex * rangeStartPercent / 100f
            val endIndex = fullLastIndex * rangeEndPercent / 100f
            val span = (endIndex - startIndex).coerceAtLeast(0.0001f)
            startIndex + (xPosition / width) * span - firstVisibleIndex
        } else {
            (xPosition / width).coerceIn(0f, 1f) * (candles.size - 1)
        }
    }

    fun nearestLine(offset: Offset, lines: List<UserLine>, excludeId: String? = null): UserLine? {
        val candidates = lines.filterNot { it.id == excludeId }
        return candidates.minByOrNull { line ->
            pointToSegmentDistance(
                offset,
                Offset(xForTime(line.startTimeMillis), y(line.startPrice)),
                Offset(xForTime(line.endTimeMillis), y(line.endPrice)),
            )
        }?.takeIf { line ->
            pointToSegmentDistance(
                offset,
                Offset(xForTime(line.startTimeMillis), y(line.startPrice)),
                Offset(xForTime(line.endTimeMillis), y(line.endPrice)),
            ) < 64f
        }
    }

    fun nearestLineTooltip(offset: Offset, lines: List<UserLine>, patterns: List<PatternCandidate>): LineTooltip? {
        val candidates = buildList {
            lines.forEach { line ->
                val start = Offset(xForTime(line.startTimeMillis), y(line.startPrice))
                val end = Offset(xForTime(line.endTimeMillis), y(line.endPrice))
                add(PriceLineCandidate("선", start, end, line.color))

                val savedTime = line.forecastTimeMillis
                if (savedTime != null) {
                    val anchor = line.futureAnchor()
                    val anchorPoint = Offset(xForTime(anchor.first), y(anchor.second))
                    val forecastEndX = forecastDrawEndX(line, savedTime)
                    val forecastEndY = yForVisualLineAtX(line, forecastEndX)
                    if (forecastEndY != null && forecastEndY.isFinite()) {
                        add(PriceLineCandidate("예측", anchorPoint, Offset(forecastEndX, forecastEndY), 0xFFB4F8C8))
                    }
                }
            }
            patterns.forEach { pattern ->
                val label = patternShortLabel(pattern)
                val color = pattern.color
                val convergence = Offset(xForTime(pattern.convergence.timeMillis), y(pattern.convergence.price))
                add(
                    PriceLineCandidate(
                        "$label 상단",
                        Offset(xForTime(pattern.upperA.timeMillis), y(pattern.upperA.price)),
                        convergence,
                        color,
                    )
                )
                add(
                    PriceLineCandidate(
                        "$label 하단",
                        Offset(xForTime(pattern.lowerA.timeMillis), y(pattern.lowerA.price)),
                        convergence,
                        color,
                    )
                )
            }
        }
        val nearest = candidates.mapNotNull { candidate ->
            val projected = projectPointOnSegment(offset, candidate.start, candidate.end)
            val distance = hypot(offset.x - projected.x, offset.y - projected.y)
            if (distance <= 64f) candidate to projected else null
        }.minByOrNull { (_, projected) ->
            hypot(offset.x - projected.x, offset.y - projected.y)
        } ?: return null

        val candidate = nearest.first
        val point = nearest.second
        val price = priceForScreenY(point.y)
        if (!price.isFinite() || price <= 0f) return null
        return LineTooltip(
            label = candidate.label,
            timeMillis = timeForXPosition(point.x),
            price = price,
            point = point,
            color = candidate.color,
        )
    }

    fun futureTime(widthMultiplier: Float): Long = timeForXPosition(width * widthMultiplier)

    fun timeAtX(xPosition: Float): Long = timeForXPosition(xPosition)

    fun forecastAt(line: UserLine, offset: Offset): LineForecast? {
        val lineEndX = xForTime(max(line.startTimeMillis, line.endTimeMillis))
        val queryX = max(offset.x, lineEndX)
        val queryTime = timeForXPosition(queryX)
        val queryPrice = priceOnVisualLineAtX(line, queryX) ?: return null
        if (!queryPrice.isFinite() || queryPrice <= 0f) return null
        return LineForecast(line, queryTime, queryPrice)
    }

    fun savedForecast(line: UserLine): LineForecast? {
        val savedTime = line.forecastTimeMillis ?: return null
        val lineEndX = xForTime(max(line.startTimeMillis, line.endTimeMillis))
        val savedX = max(xForTime(savedTime), lineEndX)
        val queryTime = timeForXPosition(savedX)
        val queryPrice = priceOnVisualLineAtX(line, savedX) ?: line.forecastPrice ?: return null
        if (!queryPrice.isFinite() || queryPrice <= 0f) return null
        return LineForecast(line, queryTime, queryPrice)
    }

    fun forecastDrawEndX(line: UserLine, forecastTimeMillis: Long?): Float {
        val anchor = line.futureAnchor()
        val anchorX = xForTime(anchor.first)
        val forecastX = forecastTimeMillis?.let { xForTime(it) }
        return max(
            max(width * 1.35f, anchorX + width * 0.35f),
            (forecastX ?: Float.NEGATIVE_INFINITY) + 12f,
        )
    }

    fun yForVisualLineAtX(line: UserLine, xPosition: Float): Float? {
        val start = Offset(xForTime(line.startTimeMillis), y(line.startPrice))
        val end = Offset(xForTime(line.endTimeMillis), y(line.endPrice))
        val dx = end.x - start.x
        if (abs(dx) < 0.0001f) return null
        return start.y + (end.y - start.y) * ((xPosition - start.x) / dx)
    }

    fun priceOnVisualLineAtX(line: UserLine, xPosition: Float): Float? {
        val yPosition = yForVisualLineAtX(line, xPosition) ?: return null
        return priceForScreenY(yPosition)
    }

    fun priceOnLine(line: UserLine, timeMillis: Long): Float? {
        val x1 = line.startTimeMillis.toDouble()
        val x2 = line.endTimeMillis.toDouble()
        if (x1 == x2) return null
        val slope = (line.endPrice - line.startPrice) / (x2 - x1)
        return (line.startPrice + slope * (timeMillis - x1)).toFloat()
    }

    private fun timeForXPosition(xPosition: Float): Long {
        if (allCandles.size <= 1) return allCandles.firstOrNull()?.timeMillis ?: 0L
        val globalIndex = if (rangeStartPercent != null && rangeEndPercent != null && fullCandleCount > 1) {
            val fullLastIndex = (fullCandleCount - 1).toFloat()
            val startIndex = fullLastIndex * rangeStartPercent / 100f
            val endIndex = fullLastIndex * rangeEndPercent / 100f
            val span = (endIndex - startIndex).coerceAtLeast(0.0001f)
            startIndex + (xPosition / width) * span
        } else {
            firstVisibleIndex + xPosition / width * (candles.size - 1)
        }
        val firstTime = allCandles.first().timeMillis
        val lastTime = allCandles.last().timeMillis
        return when {
            globalIndex <= 0f -> (firstTime + leadingTimeStep() * globalIndex).toLong()
            globalIndex >= allCandles.lastIndex -> (lastTime + trailingTimeStep() * (globalIndex - allCandles.lastIndex)).toLong()
            else -> {
                val left = globalIndex.toInt().coerceIn(0, allCandles.lastIndex)
                val right = (left + 1).coerceAtMost(allCandles.lastIndex)
                val ratio = (globalIndex - left).coerceIn(0f, 1f)
                (allCandles[left].timeMillis + (allCandles[right].timeMillis - allCandles[left].timeMillis) * ratio).toLong()
            }
        }
    }

    private fun leadingTimeStep(): Double {
        if (allCandles.size <= 1) return 86_400_000.0
        return (allCandles[1].timeMillis - allCandles[0].timeMillis).toDouble().coerceAtLeast(1.0)
    }

    private fun trailingTimeStep(): Double {
        if (allCandles.size <= 1) return 86_400_000.0
        return (allCandles.last().timeMillis - allCandles[allCandles.lastIndex - 1].timeMillis).toDouble().coerceAtLeast(1.0)
    }

    private fun mapPrice(price: Float): Float {
        val safePrice = price.coerceAtLeast(0.0001f)
        return if (logScale) ln(safePrice) else safePrice
    }

    private fun priceForScreenY(yPosition: Float): Float {
        val ratio = (height - yPosition) / height
        val mapped = mappedLow + (mappedHigh - mappedLow) * ratio
        return if (logScale) exp(mapped).toFloat() else mapped
    }
}

private fun UserLine.futureAnchor(): Pair<Long, Float> {
    return if (startTimeMillis >= endTimeMillis) {
        startTimeMillis to startPrice
    } else {
        endTimeMillis to endPrice
    }
}

private fun pointToSegmentDistance(point: Offset, start: Offset, end: Offset): Float {
    val projection = projectPointOnSegment(point, start, end)
    return hypot(point.x - projection.x, point.y - projection.y)
}

private fun projectPointOnSegment(point: Offset, start: Offset, end: Offset): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return start
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return Offset(start.x + t * dx, start.y + t * dy)
}

private fun intersectFutureLines(first: UserLine, second: UserLine): LineProjection? {
    val origin = min(
        min(first.startTimeMillis, first.endTimeMillis),
        min(second.startTimeMillis, second.endTimeMillis),
    )
    val dayMs = 86_400_000.0
    val x1 = (first.startTimeMillis - origin) / dayMs
    val y1 = first.startPrice.toDouble()
    val x2 = (first.endTimeMillis - origin) / dayMs
    val y2 = first.endPrice.toDouble()
    val x3 = (second.startTimeMillis - origin) / dayMs
    val y3 = second.startPrice.toDouble()
    val x4 = (second.endTimeMillis - origin) / dayMs
    val y4 = second.endPrice.toDouble()
    val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (abs(denominator) < 0.000001) return null
    val px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator
    val py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator
    val firstFutureStart = (max(first.startTimeMillis, first.endTimeMillis) - origin) / dayMs
    val secondFutureStart = (max(second.startTimeMillis, second.endTimeMillis) - origin) / dayMs
    if (px <= min(firstFutureStart, secondFutureStart)) return null
    if (!px.isFinite() || !py.isFinite() || py <= 0.0) return null
    val projectedTime = origin + (px * dayMs).toLong()
    return LineProjection(first, second, projectedTime, py.toFloat())
}

private fun detectConvergencePatterns(
    visible: List<Candle>,
    mapper: ChartMapper,
    box: BoxSelection,
    timeframe: String,
    backgroundColor: Long,
): List<PatternCandidate> {
    val rect = box.rect
    val selected = visible.mapIndexedNotNull { index, candle ->
        if (mapper.x(index) in rect.left..rect.right) candle else null
    }
    if (selected.size < 8) return emptyList()
    val order = when (timeframe) {
        "W" -> 2
        "M" -> 1
        else -> 3
    }
    if (selected.size < order * 4) return emptyList()

    val highs = mutableListOf<Int>()
    val lows = mutableListOf<Int>()

    for (index in order until selected.size - order) {
        val candle = selected[index]
        val left = index - order until index
        val right = index + 1..index + order
        if (left.all { candle.high > selected[it].high } && right.all { candle.high > selected[it].high }) {
            highs += index
        }
        if (left.all { candle.low < selected[it].low } && right.all { candle.low < selected[it].low }) {
            lows += index
        }
    }
    if (highs.size < 2 || lows.size < 2) return emptyList()

    val candidates = mutableListOf<PatternCandidate>()
    val seen = mutableSetOf<String>()
    val patternColors = contrastLineColors(backgroundColor)
    val firstTime = selected.first().timeMillis
    val dayMs = 86_400_000.0
    val xDays = selected.map { (it.timeMillis - firstTime) / dayMs }

    for (hiCursor in highs.lastIndex downTo max(1, highs.size - 5)) {
        val h1 = highs[hiCursor - 1]
        val h2 = highs[hiCursor]
        if (h1 >= h2) continue

        for (loCursor in lows.lastIndex downTo max(1, lows.size - 5)) {
            val l1 = lows[loCursor - 1]
            val l2 = lows[loCursor]
            if (l1 >= l2) continue

            val key = "$h1-$h2-$l1-$l2"
            if (!seen.add(key)) continue

            val tStart = min(h1, l1)
            val tEnd = max(h2, l2)
            if (tEnd - tStart < order * 2) continue

            val x1h = xDays[h1]
            val x2h = xDays[h2]
            val x1l = xDays[l1]
            val x2l = xDays[l2]
            if (x1h == x2h || x1l == x2l) continue

            val hs = ((selected[h2].high - selected[h1].high) / (x2h - x1h)).toFloat()
            val hi = (selected[h1].high - hs * x1h).toFloat()
            val ls = ((selected[l2].low - selected[l1].low) / (x2l - x1l)).toFloat()
            val li = (selected[l1].low - ls * x1l).toFloat()

            val mid = (min(x1h, x1l) + max(x2h, x2l)) / 2.0
            if (hs * mid + hi <= ls * mid + li) continue

            val denominator = hs - ls
            if (abs(denominator) < 0.0001f) continue

            val convX = (li - hi) / denominator
            val tEndX = max(x2h, x2l)
            val spanX = max(tEndX - min(x1h, x1l), 1.0)
            if (convX <= tEndX) continue
            if (convX - tEndX > spanX * 5.0) continue

            val price = hs * convX + hi
            val curPrice = (hs * tEndX + hi + ls * tEndX + li) / 2f
            if (!price.isFinite() || price <= 0f || price > curPrice * 3f) continue

            val label = patternLabel(hs, ls)
            val color = patternColors[candidates.size % patternColors.size]
            val lineStartX = xDays[tStart]
            candidates += PatternCandidate(
                label = label,
                color = color,
                upperA = PatternPoint(timeForXDays(firstTime, lineStartX), (hs * lineStartX + hi).toFloat()),
                upperB = PatternPoint(selected[h2].timeMillis, selected[h2].high),
                lowerA = PatternPoint(timeForXDays(firstTime, lineStartX), (ls * lineStartX + li).toFloat()),
                lowerB = PatternPoint(selected[l2].timeMillis, selected[l2].low),
                convergence = PatternPoint(timeForXDays(firstTime, convX.toDouble()), price),
            )
            if (candidates.size >= 4) return candidates
        }
    }
    return candidates
}

private fun slope(x1: Int, y1: Float, x2: Int, y2: Float): Float = (y2 - y1) / (x2 - x1).toFloat()

private fun intercept(x: Int, y: Float, slope: Float): Float = y - slope * x

private fun patternLabel(highSlope: Float, lowSlope: Float): String {
    val tolerance = max(abs(highSlope), abs(lowSlope)) * 0.2f
    return when {
        highSlope < -tolerance && lowSlope > tolerance -> "대칭 삼각형"
        highSlope > tolerance && lowSlope > tolerance -> "상승 쐐기형"
        highSlope < -tolerance && lowSlope < -tolerance -> "하락 쐐기형"
        abs(highSlope) <= tolerance && lowSlope > tolerance -> "상승 삼각형"
        highSlope < -tolerance && abs(lowSlope) <= tolerance -> "하락 삼각형"
        else -> "수렴 채널"
    }
}

private fun timeForXDays(firstTimeMillis: Long, xDays: Double): Long =
    (firstTimeMillis + xDays * 86_400_000.0).toLong()

private fun visibleCandles(candles: List<Candle>, start: Float, end: Float): List<Candle> {
    if (candles.isEmpty()) return emptyList()
    val from = ((candles.lastIndex) * start / 100f).roundToInt().coerceIn(0, candles.lastIndex)
    val to = ((candles.lastIndex) * end / 100f).roundToInt().coerceIn(from, candles.lastIndex)
    return candles.subList(from, to + 1)
}

private fun visibleFirstIndex(candles: List<Candle>, start: Float): Int {
    if (candles.isEmpty()) return 0
    return ((candles.lastIndex) * start / 100f).roundToInt().coerceIn(0, candles.lastIndex)
}

private fun onPickRecentRange(
    candles: List<Candle>,
    days: Int,
    onPick: (Float, Float) -> Unit,
) {
    if (candles.isEmpty()) return
    if (candles.size == 1) {
        onPick(0f, 100f)
        return
    }
    val cutoff = candles.last().timeMillis - days * 86_400_000L
    val index = candles.indexOfFirst { it.timeMillis >= cutoff }.let { if (it < 0) 0 else it }
    val start = (index / candles.lastIndex.toFloat() * 100f).roundHalf().coerceIn(0f, 99f)
    onPick(start, 100f)
}

private fun overviewCandles(candles: List<Candle>, maxPoints: Int): List<Candle> {
    if (candles.size <= maxPoints) return candles
    val step = candles.size / maxPoints.toFloat()
    return List(maxPoints) { index ->
        candles[(index * step).roundToInt().coerceIn(0, candles.lastIndex)]
    }
}

private fun Float.roundHalf(): Float = (this * 2f).roundToInt() / 2f

private data class ThemeOption(
    val id: String,
    val label: String,
    val background: Long,
    val grid: Long,
    val text: Long,
    val candle: Long,
)

private val THEMES = listOf(
    ThemeOption("midnight", "미드나잇", 0xFF171B2B, 0xFF35405E, 0xFFE8ECFF, 0xFF2E8BFF),
    ThemeOption("ice", "아이스", 0xFFF5F8FC, 0xFFD4DCE8, 0xFF243044, 0xFF2563EB),
    ThemeOption("paper", "페이퍼", 0xFFFAF8F1, 0xFFE1D8C7, 0xFF2F2A22, 0xFF374151),
    ThemeOption("navy", "네이비", 0xFF111D2E, 0xFF2D4362, 0xFFDDEBFF, 0xFF38BDF8),
    ThemeOption("forest", "포레스트", 0xFF203326, 0xFF4E6B57, 0xFFEAF7E8, 0xFF35D06F),
    ThemeOption("wine", "와인", 0xFF2D1822, 0xFF684056, 0xFFFFEAF0, 0xFFFB7185),
    ThemeOption("amber", "앰버", 0xFF302516, 0xFF725633, 0xFFFFF3C7, 0xFFF5A524),
    ThemeOption("violet", "바이올렛", 0xFF211A35, 0xFF55427E, 0xFFF0E8FF, 0xFFA78BFA),
    ThemeOption("slate", "슬레이트", 0xFF273241, 0xFF59677A, 0xFFF1F5F9, 0xFF93C5FD),
    ThemeOption("mint", "민트", 0xFF17302E, 0xFF3E716B, 0xFFE3FFF9, 0xFF2DD4BF),
    ThemeOption("roseLight", "로즈라이트", 0xFFFFF7F8, 0xFFF0CFD8, 0xFF3B1F2A, 0xFFE11D48),
    ThemeOption("terminal", "터미널", 0xFF0E1713, 0xFF2F5646, 0xFFD0FBE1, 0xFF4ADE80),
)

private val CANDLE_SWATCHES = listOf(
    0xFF1E88FF, 0xFF2563EB, 0xFF38BDF8, 0xFF06B6D4,
    0xFF2DD4BF, 0xFF22C55E, 0xFF84CC16, 0xFFF59E0B,
    0xFFFFB000, 0xFFEF6C35, 0xFFEF4444, 0xFFE11D48,
    0xFFFB7185, 0xFFA78BFA, 0xFF8B5CF6, 0xFF94A3B8,
)
private val BACKGROUND_SWATCHES = listOf(
    0xFF0E1713, 0xFF111D2E, 0xFF171B2B, 0xFF171724,
    0xFF273241, 0xFF2D1822, 0xFF302516, 0xFF211A35,
    0xFF203326, 0xFF17302E, 0xFFF5F8FC, 0xFFFAF8F1,
    0xFFFFF7F8, 0xFFEEF1F5, 0xFFE9EEF5, 0xFF101827,
)
private val GRID_SWATCHES = listOf(
    0xFF2F5646, 0xFF2D4362, 0xFF35405E, 0xFF303244,
    0xFF59677A, 0xFF684056, 0xFF725633, 0xFF55427E,
    0xFF4E6B57, 0xFF3E716B, 0xFFD4DCE8, 0xFFE1D8C7,
    0xFFF0CFD8, 0xFFB8C0CC, 0xFFC9D4E2, 0xFF26364F,
)
private val TEXT_SWATCHES = listOf(
    0xFFE8ECFF, 0xFFDDEBFF, 0xFFF0E8FF, 0xFFEAF7E8,
    0xFFE3FFF9, 0xFFFFF3C7, 0xFFFFEAF0, 0xFFD0FBE1,
    0xFF243044, 0xFF2F2A22, 0xFF3B1F2A, 0xFF111827,
    0xFF1F2937, 0xFF374151, 0xFF0F172A, 0xFFFFFFFF,
)
private val PATTERN_COLORS = listOf(0xFFF9E2AFL, 0xFFF38BA8L, 0xFFCBA6F7L, 0xFF89B4FAL)
private const val FUTURE_RANGE_START_LIMIT = 300f
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val TOOLTIP_DATE_FORMAT = SimpleDateFormat("yyyy.MM.dd", Locale.US)
private val AXIS_DATE_FORMAT = SimpleDateFormat("yy-MM-dd", Locale.US)

private fun argbColor(value: Long): Color = Color(value.toInt())

private fun contrastLineColors(backgroundColor: Long): List<Long> {
    return if (relativeLuminance(backgroundColor) > 0.46f) {
        listOf(
            0xFF0F4FE4, // deep blue
            0xFFB91C1C, // red
            0xFF047857, // emerald
            0xFF7C3AED, // violet
            0xFFB45309, // amber brown
            0xFF0F766E, // teal
        )
    } else {
        listOf(
            0xFFF9E2AF, // warm ivory
            0xFF89B4FA, // sky blue
            0xFFA6E3A1, // mint green
            0xFFF38BA8, // rose
            0xFFCBA6F7, // lavender
            0xFF94E2D5, // aqua
        )
    }
}

private fun contrastProjectionColor(backgroundColor: Long): Long {
    return if (relativeLuminance(backgroundColor) > 0.46f) {
        0xFF7C2D12 // dark burnt orange, visible on bright chart backgrounds
    } else {
        0xFFFDE68A // warm gold, visible on dark chart backgrounds
    }
}

private fun readableAccentColor(accentColor: Long, backgroundColor: Long): Long {
    val bg = relativeLuminance(backgroundColor)
    val accent = relativeLuminance(accentColor)
    val contrast = (maxOf(bg, accent) + 0.05f) / (minOf(bg, accent) + 0.05f)
    if (contrast >= 3.2f) return accentColor
    val target = if (bg > 0.5f) 0xFF111827 else 0xFFFFFFFF
    return blendArgb(accentColor, target, 0.55f)
}

private fun blendArgb(from: Long, to: Long, amount: Float): Long {
    fun channel(color: Long, shift: Int): Int = ((color shr shift) and 0xFF).toInt()
    fun mix(a: Int, b: Int): Int = (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
    val a = mix(channel(from, 24), channel(to, 24))
    val r = mix(channel(from, 16), channel(to, 16))
    val g = mix(channel(from, 8), channel(to, 8))
    val b = mix(channel(from, 0), channel(to, 0))
    return ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
}

private fun relativeLuminance(color: Long): Float {
    fun channel(shift: Int): Float {
        val raw = ((color shr shift) and 0xFF).toFloat() / 255f
        return if (raw <= 0.03928f) raw / 12.92f else ((raw + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}

private fun OrientationMode.label(): String = when (this) {
    OrientationMode.System -> "시스템 설정"
    OrientationMode.Portrait -> "세로 고정"
    OrientationMode.Landscape -> "가로 고정"
}
