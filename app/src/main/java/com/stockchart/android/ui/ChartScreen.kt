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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.roundToInt

private enum class ChartMode {
    None,
    BoxSelect,
    DrawLine,
    ConnectLines,
    DeleteLine,
    ForecastLine,
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

private data class LineForecast(
    val line: UserLine,
    val queryTimeMillis: Long,
    val queryPrice: Float,
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
    var forecastLine by remember { mutableStateOf<UserLine?>(null) }
    var lineForecast by remember { mutableStateOf<LineForecast?>(null) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var undoStack by remember { mutableStateOf<List<List<UserLine>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<UserLine>>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var freeMoveZoom by remember { mutableStateOf(false) }
    var guideStepIndex by remember { mutableStateOf(0) }
    var rangeStart by remember(settings.rangeStartPercent) { mutableStateOf(settings.rangeStartPercent) }
    var rangeEnd by remember(settings.rangeEndPercent) { mutableStateOf(settings.rangeEndPercent) }
    val visibleLines = lines.filter { it.ticker == settings.ticker && it.timeframe == settings.timeframe }
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
        chartMode = if (chartMode == next) ChartMode.None else next
        pendingAnchor = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        transientMessage = null
    }

    fun clearAnalysisState() {
        pendingAnchor = null
        activeBox = null
        committedBox = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        projections = emptyList()
        patterns = emptyList()
        activePatternIndex = null
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

    LaunchedEffect(isGuideMode, guideStepIndex) {
        if (!isGuideMode) return@LaunchedEffect
        pendingAnchor = null
        firstConnectLine = null
        forecastLine = null
        lineForecast = null
        transientMessage = null
        chartMode = when (guideStepIndex) {
            2 -> ChartMode.BoxSelect
            3 -> ChartMode.DrawLine
            else -> ChartMode.None
        }
        freeMoveZoom = guideStepIndex == 4
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = argbColor(settings.backgroundColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            TopToolbar(
                ticker = settings.ticker,
                timeframe = settings.timeframe,
                mode = chartMode,
                canConnectLines = visibleLines.size >= 2,
                onTickerChanged = onSaveTicker,
                onTickerSearchTextChanged = onTickerSearchTextChanged,
                tickerSuggestions = tickerSuggestions,
                onPickTimeframe = {
                    freeMoveZoom = false
                    onSaveTimeframe(it)
                },
                onPickMode = ::selectMode,
                onClearLines = {
                    pendingAnchor = null
                    clearAnalysisState()
                    undoStack = undoStack + listOf(lines)
                    redoStack = emptyList()
                    onClearLines()
                },
                onOpenSettings = { showSettings = true },
            )

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                StockChartCanvas(
                    candles = candles,
                    settings = settings,
                    lines = visibleLines,
                    projections = projections,
                    patterns = patterns,
                    activePatternIndex = activePatternIndex,
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
                            selectMode(ChartMode.None)
                            freeMoveZoom = true
                        }
                    },
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
                if (patterns.isNotEmpty()) {
                    PatternButtons(
                        patterns = patterns,
                        activeIndex = activePatternIndex,
                        onPick = { activePatternIndex = it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 44.dp),
                    )
                }
                when {
                    isLoading && candles.isEmpty() -> StatusOverlay("${settings.ticker} 데이터 로딩 중...")
                    dataError != null && candles.isEmpty() -> StatusOverlay(dataError)
                }
                transientMessage?.let {
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

            Spacer(Modifier.height(8.dp))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
            )
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
    mode: ChartMode,
    canConnectLines: Boolean,
    onTickerChanged: (String) -> Unit,
    onTickerSearchTextChanged: (String) -> Unit,
    tickerSuggestions: List<TickerSuggestion>,
    onPickTimeframe: (String) -> Unit,
    onPickMode: (ChartMode) -> Unit,
    onClearLines: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tickerField by remember(ticker) {
        mutableStateOf(TextFieldValue(ticker, selection = TextRange(ticker.length)))
    }

    fun commitTicker() {
        val next = tickerField.text.trim().uppercase()
        if (next.isNotBlank() && next != ticker) {
            tickerField = TextFieldValue(next, selection = TextRange(next.length))
            onTickerChanged(next)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = tickerField,
                onValueChange = {
                    tickerField = it.copy(text = it.text.uppercase())
                    onTickerSearchTextChanged(it.text)
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFFE5E7FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                label = { Text("티커") },
                trailingIcon = {
                    Text(
                        text = "검색",
                        color = Color(0xFF89B4FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .pointerInput(tickerField.text) { detectTapGestures { commitTicker() } }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitTicker() }),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            tickerField = tickerField.copy(
                                selection = TextRange(0, tickerField.text.length),
                            )
                        }
                    }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Enter) {
                            commitTicker()
                            true
                        } else {
                            false
                        }
                    },
            )
            TimeframeButton("일봉", timeframe == "D") { onPickTimeframe("D") }
            TimeframeButton("주봉", timeframe == "W") { onPickTimeframe("W") }
            TimeframeButton("월봉", timeframe == "M") { onPickTimeframe("M") }
            TimeframeButton("⚙", false, onOpenSettings)
        }
        if (tickerSuggestions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tickerSuggestions.take(5).forEach { suggestion ->
                    TickerSuggestionButton(
                        suggestion = suggestion,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolbarButton("분석영역", mode == ChartMode.BoxSelect, { onPickMode(ChartMode.BoxSelect) }, Modifier.weight(1f))
            ToolbarButton("선그리기", mode == ChartMode.DrawLine, { onPickMode(ChartMode.DrawLine) }, Modifier.weight(1f))
            ToolbarButton("선잇기", mode == ChartMode.ConnectLines, { if (canConnectLines) onPickMode(ChartMode.ConnectLines) }, Modifier.weight(1f))
            ToolbarButton("선삭제", mode == ChartMode.DeleteLine, { onPickMode(ChartMode.DeleteLine) }, Modifier.weight(1f))
            ToolbarButton("초기화", false, onClearLines, Modifier.weight(1f))
            ToolbarButton("선가격예측", mode == ChartMode.ForecastLine, { onPickMode(ChartMode.ForecastLine) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimeframeButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (active) Color(0xFF3D4866) else Color(0xFF252538), RoundedCornerShape(6.dp))
            .border(1.dp, if (active) Color(0xFF89B4FA) else Color(0xFF45475A), RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = Color(0xFFE5E7FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TickerSuggestionButton(suggestion: TickerSuggestion, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEE252538), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF45475A), RoundedCornerShape(6.dp))
            .pointerInput(suggestion.symbol) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = suggestion.label,
            color = Color(0xFFE5E7FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF3D4866) else Color(0xFF252538),
            contentColor = Color(0xFFE5E7FF),
        ),
        modifier = modifier.height(38.dp),
    ) {
        Text(text = text, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MiniChartButton(text: String, enabled: Boolean, onClick: () -> Unit) {
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
        modifier = Modifier.height(30.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.height(26.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
        ToolbarButton("전체", activeIndex == null, { onPick(null) })
        patterns.forEachIndexed { index, pattern ->
            ToolbarButton(
                text = "${index + 1}: ${pattern.label} $${"%.0f".format(pattern.convergence.price)}",
                active = activeIndex == index,
                onClick = { onPick(index) },
            )
        }
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
    onAnchorPicked: (AnchorPoint) -> Unit,
    onLinePicked: (UserLine) -> Unit,
    onForecastLinePicked: (UserLine) -> Unit,
    onForecastChanged: (LineForecast?) -> Unit,
    onDeleteLinePicked: (UserLine) -> Unit,
    onBoxChanged: (BoxSelection?) -> Unit,
    onBoxSelectionStarted: () -> Unit,
    onBoxCommitted: (BoxSelection) -> Unit,
    onPatternsDetected: (List<PatternCandidate>) -> Unit,
    onPanRange: (Float, Float) -> Unit,
    onPanFinished: (Float, Float) -> Unit,
    onToggleLogScale: () -> Unit,
    onToggleFreeMoveZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isPanning by remember { mutableStateOf(false) }
    var liveRangeStart by remember { mutableStateOf(rangeStart) }
    var liveRangeEnd by remember { mutableStateOf(rangeEnd) }
    var freeYLow by remember { mutableStateOf<Float?>(null) }
    var freeYHigh by remember { mutableStateOf<Float?>(null) }

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
                                nextStart = nextStart.coerceIn(0f, 100f)
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
                .pointerInput(mode, freeMoveZoom, visible, lines, canvasSize, settings.logScale, forecastLine) {
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
                            candles.size,
                        )
                        when (mode) {
                            ChartMode.DrawLine -> onAnchorPicked(mapper.nearestAnchor(tap))
                            ChartMode.ConnectLines -> mapper.nearestLine(tap, lines)?.let(onLinePicked)
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
                            else -> Unit
                        }
                    }
                }
                .pointerInput(mode, freeMoveZoom, canvasSize) {
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
                                val fallbackLow = visible.minOfOrNull { it.low } ?: 0f
                                val fallbackHigh = visible.maxOfOrNull { it.high } ?: 1f
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
                                    .coerceIn(0f, 100f)
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
                                panLiveStart = (panStartRange + delta).coerceIn(0f, 100f)
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
                                    if (visible.isNotEmpty() && canvasSize.width > 0) {
                                        val mapper = ChartMapper(
                                            visible,
                                            canvasSize.width.toFloat(),
                                            canvasSize.height.toFloat(),
                                            settings.logScale,
                                            rangeStartPercent = liveRangeStart,
                                            rangeEndPercent = liveRangeEnd,
                                            firstVisibleIndex = visibleFirstIndex,
                                            fullCandleCount = candles.size,
                                        )
                                        onPatternsDetected(detectConvergencePatterns(visible, mapper, box, settings.timeframe))
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
                candles.size,
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
            visible.forEachIndexed { index, candle ->
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
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(x - candleWidth / 2f, min(openY, closeY)),
                    size = Size(candleWidth, max(2f, abs(closeY - openY))),
                )
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

            forecastLine?.let { line ->
                drawLineForecast(line, lineForecast, mapper, settings)
            }

            pendingAnchor?.let {
                drawCircle(
                    color = Color(0xFFA6E3A1),
                    radius = settings.pointSize,
                    center = Offset(mapper.xForTime(it.timeMillis), mapper.y(it.price)),
                    style = Stroke(width = 2f),
                )
            }

            committedBox?.let { drawBoxSelection(it, Color(0x6689B4FA), Color(0x2289B4FA)) }
            activeBox?.let { drawBoxSelection(it, Color(0xFFE5C890), Color(0x22E5C890)) }

            val paint = Paint().apply {
                color = settings.textColor.toInt()
                textSize = settings.fontSize * 2.2f
                isAntiAlias = true
            }
            val last = visible.last()
            drawContext.canvas.nativeCanvas.drawText("$${"%.1f".format(last.close)}", 12f, 30f, paint)
        }

        Button(
            onClick = onToggleLogScale,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (settings.logScale) Color(0xFF3D4866) else Color(0xAA252538),
                contentColor = Color(0xFFE5E7FF),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .height(32.dp),
        ) {
            Text("로그스케일", fontSize = 11.sp, maxLines = 1)
        }
        Button(
            onClick = onToggleFreeMoveZoom,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (freeMoveZoom) Color(0xFF3D4866) else Color(0xAA252538),
                contentColor = Color(0xFFE5E7FF),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 46.dp, end = 8.dp)
                .height(32.dp),
        ) {
            Text("자유이동확대", fontSize = 11.sp, maxLines = 1)
        }
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
    val paint = Paint().apply {
        color = settings.textColor.toInt()
        textSize = settings.fontSize * 1.55f
        isAntiAlias = true
    }
    repeat(5) { index ->
        val y = size.height * index / 4f
        val price = mapper.priceAtY(y)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            "$${"%.0f".format(price)}",
            size.width - 8f,
            (y + 18f).coerceIn(18f, size.height - 8f),
            paint,
        )
    }
    repeat(4) { step ->
        val index = ((visible.lastIndex) * step / 3f).roundToInt().coerceIn(0, visible.lastIndex)
        val x = mapper.x(index)
        paint.textAlign = Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(
            AXIS_DATE_FORMAT.format(Date(visible[index].timeMillis)),
            x.coerceIn(42f, size.width - 42f),
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
    val color = argbColor(0xFFF9E2AFL)
    val star = Offset(mapper.xForTime(projection.timeMillis), mapper.y(projection.price))
    listOf(projection.first, projection.second).forEach { line ->
        val start = Offset(mapper.xForTime(line.endTimeMillis), mapper.y(line.endPrice))
        drawLine(color, start, star, strokeWidth = settings.lineWidth, cap = StrokeCap.Round)
    }
    drawStar(star, settings.starSize, color)
    val paint = Paint().apply {
        this.color = android.graphics.Color.argb(240, 249, 226, 175)
        textSize = 24f
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
    val start = Offset(mapper.xForTime(line.endTimeMillis), mapper.y(line.endPrice))
    val endTime = mapper.futureTime(1.35f)
    val endPrice = mapper.priceOnLine(line, endTime) ?: return
    val end = Offset(mapper.xForTime(endTime), mapper.y(endPrice))
    drawLine(color, start, end, strokeWidth = settings.lineWidth, cap = StrokeCap.Round)

    forecast?.let {
        val point = Offset(mapper.xForTime(it.queryTimeMillis), mapper.y(it.queryPrice))
        drawCircle(Color.White, radius = settings.pointSize / 2f, center = point)
        drawLine(Color(0x88B4F8C8), Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = 1f)
        val paint = Paint().apply {
            this.color = settings.textColor.toInt()
            textSize = settings.fontSize * 2.0f
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
            point.y + settings.fontSize * 2.0f + 2f,
            paint,
        )
    }
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
            textSize = settings.fontSize * 2.0f
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
            conv.y + settings.fontSize * 2.0f + 2f,
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
                                liveStart = pointerPercent.coerceIn(0f, liveEnd - 1f)
                                onChange(liveStart, liveEnd)
                            }
                            DragMode.End -> {
                                liveEnd = pointerPercent.coerceIn(liveStart + 1f, 100f)
                                onChange(liveStart, liveEnd)
                            }
                            DragMode.Window -> {
                                val span = endAtDrag - startAtDrag
                                liveStart = (startAtDrag + deltaPercent).coerceIn(0f, 100f - span)
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
            drawRect(
                color = Color(0x442D3554),
                topLeft = Offset(startX, chartTop),
                size = Size((endX - startX).coerceAtLeast(0f), chartHeight),
            )
        }

        val trackY = height * 0.82f
        drawLine(Color(0xFF3A3A4C), Offset(leftPad, trackY), Offset(width - rightPad, trackY), 5f)
        drawLine(Color(0xFF9B5DE5), Offset(startX, trackY), Offset(endX, trackY), 5f, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 5f, center = Offset(startX, trackY))
        drawCircle(Color.White, radius = 5f, center = Offset(endX, trackY))
        drawCircle(Color(0xFF9B5DE5), radius = 3.5f, center = Offset(startX, trackY))
        drawCircle(Color(0xFF9B5DE5), radius = 3.5f, center = Offset(endX, trackY))

        val labelPaint = Paint().apply {
            color = android.graphics.Color.rgb(31, 31, 42)
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelTop = height - 34f
        val labelSize = Size(58f, 30f)
        drawRoundRect(Color.White, Offset(0f, labelTop), labelSize, CornerRadius(5f, 5f))
        drawRoundRect(Color.White, Offset(width - labelSize.width, labelTop), labelSize, CornerRadius(5f, 5f))
        drawContext.canvas.nativeCanvas.drawText(displayStart.prettyPercent(), labelSize.width / 2f, labelTop + 22f, labelPaint)
        drawContext.canvas.nativeCanvas.drawText(displayEnd.prettyPercent(), width - labelSize.width / 2f, labelTop + 22f, labelPaint)
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
                }
                ColorRow("캔들", CANDLE_SWATCHES, settings.candleColor, onSaveCandleColor)
                ColorRow("배경", BACKGROUND_SWATCHES, settings.backgroundColor, onSaveBackgroundColor)
                ColorRow("축/격자", GRID_SWATCHES, settings.gridColor, onSaveGridColor)
                ColorRow("글자", TEXT_SWATCHES, settings.textColor, onSaveTextColor)

                Text("폰트", fontWeight = FontWeight.Bold)
                ChoiceRows(FONT_OPTIONS, columns = 2) { font ->
                    SmallChoiceButton(font, settings.fontFamily == font) { onSaveFontFamily(font) }
                }

                Text("화면 방향", fontWeight = FontWeight.Bold)
                OrientationMode.entries.forEach { mode ->
                    OutlinedButton(
                        onClick = { onPickOrientation(mode) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (mode == settings.orientationMode) "${mode.label()} 선택됨" else mode.label())
                    }
                }
                SettingSlider("글자크기", settings.fontSize, 9f..22f, onSaveFontSize)
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
        Text(text, fontSize = 11.sp, maxLines = 1)
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
    private val fullCandleCount: Int = candles.size,
) {
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
        val startPercent = rangeStartPercent
        val endPercent = rangeEndPercent
        if (startPercent != null && endPercent != null && fullCandleCount > 1) {
            val fullLastIndex = (fullCandleCount - 1).toFloat()
            val startIndex = fullLastIndex * startPercent / 100f
            val endIndex = fullLastIndex * endPercent / 100f
            val span = (endIndex - startIndex).coerceAtLeast(0.0001f)
            val globalIndex = firstVisibleIndex + index
            return (globalIndex - startIndex) / span * width
        }
        if (candles.size <= 1) return width / 2f
        return index / (candles.size - 1).toFloat() * width
    }

    fun xForTime(timeMillis: Long): Float {
        if (candles.size <= 1) return width / 2f

        val exactIndex = candles.indexOfFirst { it.timeMillis == timeMillis }
        if (exactIndex >= 0) return xForLocalIndex(exactIndex.toFloat())

        val firstTime = candles.first().timeMillis
        val lastTime = candles.last().timeMillis
        val averageStep = ((lastTime - firstTime).toDouble() / (candles.size - 1)).coerceAtLeast(1.0)
        val index = when {
            timeMillis < firstTime -> (timeMillis - firstTime) / averageStep
            timeMillis > lastTime -> candles.lastIndex + (timeMillis - lastTime) / averageStep
            else -> {
                val right = candles.indexOfFirst { it.timeMillis > timeMillis }
                val left = (right - 1).coerceAtLeast(0)
                if (right <= 0) {
                    0.0
                } else {
                    val leftTime = candles[left].timeMillis
                    val rightTime = candles[right].timeMillis
                    left + (timeMillis - leftTime).toDouble() / (rightTime - leftTime).toDouble().coerceAtLeast(1.0)
                }
            }
        }
        return xForLocalIndex(index.toFloat())
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
        val index = if (rangeStartPercent != null && rangeEndPercent != null && fullCandleCount > 1) {
            val fullLastIndex = (fullCandleCount - 1).toFloat()
            val startIndex = fullLastIndex * rangeStartPercent / 100f
            val endIndex = fullLastIndex * rangeEndPercent / 100f
            val span = (endIndex - startIndex).coerceAtLeast(0.0001f)
            (startIndex + (offset.x / width) * span - firstVisibleIndex)
                .roundToInt()
                .coerceIn(0, candles.lastIndex)
        } else {
            ((offset.x / width).coerceIn(0f, 1f) * (candles.size - 1)).roundToInt()
        }
        val candle = candles[index]
        val highY = y(candle.high)
        val lowY = y(candle.low)
        return if (abs(offset.y - highY) <= abs(offset.y - lowY)) {
            AnchorPoint(candle.timeMillis, candle.high)
        } else {
            AnchorPoint(candle.timeMillis, candle.low)
        }
    }

    fun nearestLine(offset: Offset, lines: List<UserLine>): UserLine? {
        return lines.minByOrNull { line ->
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

    fun futureTime(widthMultiplier: Float): Long = timeForXPosition(width * widthMultiplier)

    fun forecastAt(line: UserLine, offset: Offset): LineForecast? {
        val lineEndX = xForTime(max(line.startTimeMillis, line.endTimeMillis))
        val queryX = max(offset.x, lineEndX)
        val queryTime = timeForXPosition(queryX)
        val queryPrice = priceOnLine(line, queryTime) ?: return null
        if (!queryPrice.isFinite() || queryPrice <= 0f) return null
        return LineForecast(line, queryTime, queryPrice)
    }

    fun priceOnLine(line: UserLine, timeMillis: Long): Float? {
        val x1 = line.startTimeMillis.toDouble()
        val x2 = line.endTimeMillis.toDouble()
        if (x1 == x2) return null
        val slope = (line.endPrice - line.startPrice) / (x2 - x1)
        return (line.startPrice + slope * (timeMillis - x1)).toFloat()
    }

    private fun timeForXPosition(xPosition: Float): Long {
        if (candles.size <= 1) return candles.firstOrNull()?.timeMillis ?: 0L
        val index = xPosition / width * (candles.size - 1)
        val firstTime = candles.first().timeMillis
        val lastTime = candles.last().timeMillis
        val averageStep = ((lastTime - firstTime).toDouble() / (candles.size - 1)).coerceAtLeast(1.0)
        return when {
            index <= 0f -> (firstTime + averageStep * index).toLong()
            index >= candles.lastIndex -> (lastTime + averageStep * (index - candles.lastIndex)).toLong()
            else -> {
                val left = index.toInt().coerceIn(0, candles.lastIndex)
                val right = (left + 1).coerceAtMost(candles.lastIndex)
                val ratio = (index - left).coerceIn(0f, 1f)
                (candles[left].timeMillis + (candles[right].timeMillis - candles[left].timeMillis) * ratio).toLong()
            }
        }
    }

    private fun mapPrice(price: Float): Float {
        val safePrice = price.coerceAtLeast(0.0001f)
        return if (logScale) ln(safePrice) else safePrice
    }
}

private fun pointToSegmentDistance(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return hypot(point.x - start.x, point.y - start.y)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return hypot(point.x - projection.x, point.y - projection.y)
}

private fun intersectFutureLines(first: UserLine, second: UserLine): LineProjection? {
    val x1 = first.startTimeMillis.toDouble()
    val y1 = first.startPrice.toDouble()
    val x2 = first.endTimeMillis.toDouble()
    val y2 = first.endPrice.toDouble()
    val x3 = second.startTimeMillis.toDouble()
    val y3 = second.startPrice.toDouble()
    val x4 = second.endTimeMillis.toDouble()
    val y4 = second.endPrice.toDouble()
    val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (abs(denominator) < 0.000001) return null
    val px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator
    val py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator
    val firstFutureStart = max(first.startTimeMillis, first.endTimeMillis).toDouble()
    val secondFutureStart = max(second.startTimeMillis, second.endTimeMillis).toDouble()
    if (px <= max(firstFutureStart, secondFutureStart)) return null
    if (!px.isFinite() || !py.isFinite() || py <= 0.0) return null
    return LineProjection(first, second, px.toLong(), py.toFloat())
}

private fun detectConvergencePatterns(
    visible: List<Candle>,
    mapper: ChartMapper,
    box: BoxSelection,
    timeframe: String,
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
            val color = PATTERN_COLORS[candidates.size % PATTERN_COLORS.size]
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

private fun Float.prettyPercent(): String {
    val rounded = roundHalf()
    return if (rounded % 1f == 0f) rounded.roundToInt().toString() else rounded.toString()
}

private data class ThemeOption(
    val id: String,
    val label: String,
    val background: Long,
    val grid: Long,
    val text: Long,
    val candle: Long,
)

private val THEMES = listOf(
    ThemeOption("dark", "어둡게", 0xFF1E1E2E, 0xFF313244, 0xFFCDD6F4, 0xFF1E88FF),
    ThemeOption("light", "밝게", 0xFFF7F7F7, 0xFFD9DDE7, 0xFF1F2937, 0xFF2563EB),
    ThemeOption("mid", "중간", 0xFFEEF1F5, 0xFFB8C0CC, 0xFF111827, 0xFF3B82F6),
    ThemeOption("blue", "블루", 0xFF101827, 0xFF26364F, 0xFFDBEAFE, 0xFF38BDF8),
    ThemeOption("green", "그린", 0xFF152018, 0xFF2F4938, 0xFFDCFCE7, 0xFF22C55E),
)

private val CANDLE_SWATCHES = listOf(0xFF1E88FF, 0xFF2563EB, 0xFF3B82F6, 0xFF38BDF8, 0xFF22C55E, 0xFFFFB000, 0xFFEF6C35)
private val BACKGROUND_SWATCHES = listOf(0xFF1E1E2E, 0xFF171724, 0xFFF7F7F7, 0xFFEEF1F5, 0xFF101827, 0xFF152018)
private val GRID_SWATCHES = listOf(0xFF313244, 0xFF303244, 0xFFD9DDE7, 0xFFB8C0CC, 0xFF26364F, 0xFF2F4938)
private val TEXT_SWATCHES = listOf(0xFFCDD6F4, 0xFFE5E7FF, 0xFF1F2937, 0xFF111827, 0xFFDBEAFE, 0xFFDCFCE7)
private val FONT_OPTIONS = listOf("Arial", "Segoe UI", "Noto Sans KR", "Consolas")
private val PATTERN_COLORS = listOf(0xFFF9E2AFL, 0xFFF38BA8L, 0xFFCBA6F7L, 0xFF89B4FAL)
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val AXIS_DATE_FORMAT = SimpleDateFormat("yy-MM-dd", Locale.US)

private fun argbColor(value: Long): Color = Color(value.toInt())

private fun OrientationMode.label(): String = when (this) {
    OrientationMode.System -> "시스템 설정"
    OrientationMode.Portrait -> "세로 고정"
    OrientationMode.Landscape -> "가로 고정"
}
