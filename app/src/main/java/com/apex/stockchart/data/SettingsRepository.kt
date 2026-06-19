package com.apex.stockchart.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore("stock_chart_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val Orientation = stringPreferencesKey("orientation")
        val Ticker = stringPreferencesKey("ticker")
        val GuideReturnTicker = stringPreferencesKey("guide_return_ticker")
        val GuideSnapshot = stringPreferencesKey("guide_snapshot")
        val Timeframe = stringPreferencesKey("timeframe")
        val LogScale = booleanPreferencesKey("log_scale")
        val Theme = stringPreferencesKey("theme")
        val CandleMode = stringPreferencesKey("candle_mode")
        val CandleColor = longPreferencesKey("candle_color")
        val BackgroundColor = longPreferencesKey("background_color")
        val GridColor = longPreferencesKey("grid_color")
        val TextColor = longPreferencesKey("text_color")
        val FontFamily = stringPreferencesKey("font_family")
        val FontSize = floatPreferencesKey("font_size")
        val LineWidth = floatPreferencesKey("line_width")
        val PointSize = floatPreferencesKey("point_size")
        val StarSize = floatPreferencesKey("star_size")
        val RangeStart = floatPreferencesKey("range_start")
        val RangeEnd = floatPreferencesKey("range_end")
        val LinesJson = stringPreferencesKey("lines_json")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            orientationMode = prefs[Keys.Orientation]?.let { runCatching { OrientationMode.valueOf(it) }.getOrNull() }
                ?: OrientationMode.System,
            ticker = prefs[Keys.Ticker] ?: "TSLA",
            guideReturnTicker = prefs[Keys.GuideReturnTicker],
            timeframe = prefs[Keys.Timeframe] ?: "D",
            logScale = prefs[Keys.LogScale] ?: false,
            theme = prefs[Keys.Theme] ?: "dark",
            candleMode = prefs[Keys.CandleMode] ?: "mono",
            candleColor = prefs[Keys.CandleColor] ?: 0xFF1E88FF,
            backgroundColor = prefs[Keys.BackgroundColor] ?: 0xFF171724,
            gridColor = prefs[Keys.GridColor] ?: 0xFF303244,
            textColor = prefs[Keys.TextColor] ?: 0xFFE5E7FF,
            fontFamily = prefs[Keys.FontFamily] ?: "Arial",
            fontSize = prefs[Keys.FontSize] ?: 13f,
            lineWidth = prefs[Keys.LineWidth] ?: 2.6f,
            pointSize = prefs[Keys.PointSize] ?: 8f,
            starSize = prefs[Keys.StarSize] ?: 10f,
            rangeStartPercent = prefs[Keys.RangeStart] ?: 80f,
            rangeEndPercent = prefs[Keys.RangeEnd] ?: 100f,
            hasSavedRange = prefs[Keys.RangeStart] != null && prefs[Keys.RangeEnd] != null,
        )
    }

    val lines: Flow<List<UserLine>> = context.settingsDataStore.data.map { prefs ->
        decodeLines(prefs[Keys.LinesJson].orEmpty())
    }

    suspend fun saveOrientation(mode: OrientationMode) {
        context.settingsDataStore.edit { it[Keys.Orientation] = mode.name }
    }

    suspend fun saveTicker(ticker: String) {
        context.settingsDataStore.edit { it[Keys.Ticker] = ticker.uppercase() }
    }

    suspend fun saveGuideReturnTicker(ticker: String?) {
        context.settingsDataStore.edit {
            if (ticker.isNullOrBlank()) {
                it.remove(Keys.GuideReturnTicker)
            } else {
                it[Keys.GuideReturnTicker] = ticker.uppercase()
            }
        }
    }

    suspend fun saveGuideSnapshot(settings: AppSettings, lines: List<UserLine>) {
        context.settingsDataStore.edit {
            it[Keys.GuideSnapshot] = encodeGuideSnapshot(settings, lines)
            it[Keys.GuideReturnTicker] = settings.ticker.uppercase()
        }
    }

    suspend fun restoreGuideSnapshot(fallbackTicker: String = "TSLA") {
        context.settingsDataStore.edit { prefs ->
            val snapshot = prefs[Keys.GuideSnapshot]
            if (snapshot.isNullOrBlank()) {
                prefs[Keys.Ticker] = fallbackTicker.uppercase()
                prefs.remove(Keys.GuideReturnTicker)
                return@edit
            }
            val obj = runCatching { JSONObject(snapshot) }.getOrNull()
            if (obj == null) {
                prefs[Keys.Ticker] = fallbackTicker.uppercase()
                prefs.remove(Keys.GuideSnapshot)
                prefs.remove(Keys.GuideReturnTicker)
                return@edit
            }

            prefs[Keys.Orientation] = obj.optString("orientation", OrientationMode.System.name)
            prefs[Keys.Ticker] = obj.optString("ticker", fallbackTicker).uppercase()
            prefs[Keys.Timeframe] = obj.optString("timeframe", "D")
            prefs[Keys.LogScale] = obj.optBoolean("logScale", false)
            prefs[Keys.Theme] = obj.optString("theme", "dark")
            prefs[Keys.CandleMode] = obj.optString("candleMode", "mono")
            prefs[Keys.CandleColor] = obj.optLong("candleColor", 0xFF1E88FF)
            prefs[Keys.BackgroundColor] = obj.optLong("backgroundColor", 0xFF171724)
            prefs[Keys.GridColor] = obj.optLong("gridColor", 0xFF303244)
            prefs[Keys.TextColor] = obj.optLong("textColor", 0xFFE5E7FF)
            prefs[Keys.FontFamily] = obj.optString("fontFamily", "Arial")
            prefs[Keys.FontSize] = obj.optDouble("fontSize", 13.0).toFloat()
            prefs[Keys.LineWidth] = obj.optDouble("lineWidth", 2.6).toFloat()
            prefs[Keys.PointSize] = obj.optDouble("pointSize", 8.0).toFloat()
            prefs[Keys.StarSize] = obj.optDouble("starSize", 10.0).toFloat()
            prefs[Keys.RangeStart] = obj.optDouble("rangeStartPercent", 80.0).toFloat()
            prefs[Keys.RangeEnd] = obj.optDouble("rangeEndPercent", 100.0).toFloat()
            prefs[Keys.LinesJson] = obj.optString("linesJson", "[]")
            prefs.remove(Keys.GuideSnapshot)
            prefs.remove(Keys.GuideReturnTicker)
        }
    }

    suspend fun saveTimeframe(timeframe: String) {
        context.settingsDataStore.edit { it[Keys.Timeframe] = timeframe }
    }

    suspend fun saveLogScale(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LogScale] = enabled }
    }

    suspend fun saveTheme(theme: String, background: Long, grid: Long, text: Long, candle: Long) {
        context.settingsDataStore.edit {
            it[Keys.Theme] = theme
            it[Keys.BackgroundColor] = background
            it[Keys.GridColor] = grid
            it[Keys.TextColor] = text
            it[Keys.CandleColor] = candle
        }
    }

    suspend fun saveCandleMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.CandleMode] = mode }
    }

    suspend fun saveCandleColor(color: Long) {
        context.settingsDataStore.edit { it[Keys.CandleColor] = color }
    }

    suspend fun saveBackgroundColor(color: Long) {
        context.settingsDataStore.edit { it[Keys.BackgroundColor] = color }
    }

    suspend fun saveGridColor(color: Long) {
        context.settingsDataStore.edit { it[Keys.GridColor] = color }
    }

    suspend fun saveTextColor(color: Long) {
        context.settingsDataStore.edit { it[Keys.TextColor] = color }
    }

    suspend fun saveFontFamily(font: String) {
        context.settingsDataStore.edit { it[Keys.FontFamily] = font }
    }

    suspend fun saveFontSize(value: Float) {
        context.settingsDataStore.edit { it[Keys.FontSize] = value.coerceIn(12f, 22f) }
    }

    suspend fun saveLineWidth(value: Float) {
        context.settingsDataStore.edit { it[Keys.LineWidth] = value.coerceIn(1f, 8f) }
    }

    suspend fun savePointSize(value: Float) {
        context.settingsDataStore.edit { it[Keys.PointSize] = value.coerceIn(3f, 18f) }
    }

    suspend fun saveStarSize(value: Float) {
        context.settingsDataStore.edit { it[Keys.StarSize] = value.coerceIn(6f, 24f) }
    }

    suspend fun saveRange(start: Float, end: Float) {
        context.settingsDataStore.edit {
            val safeStart = start.coerceIn(0f, 300f)
            it[Keys.RangeStart] = safeStart
            it[Keys.RangeEnd] = end.coerceIn(safeStart + 1f, 400f)
        }
    }

    suspend fun saveLine(line: UserLine) {
        context.settingsDataStore.edit { prefs ->
            val lines = decodeLines(prefs[Keys.LinesJson].orEmpty())
                .filterNot { it.id == line.id } + line
            prefs[Keys.LinesJson] = encodeLines(lines)
        }
    }

    suspend fun replaceLines(lines: List<UserLine>) {
        context.settingsDataStore.edit { it[Keys.LinesJson] = encodeLines(lines) }
    }

    suspend fun clearLines() {
        context.settingsDataStore.edit { it[Keys.LinesJson] = "[]" }
    }

    suspend fun deleteLatestLine(ticker: String, timeframe: String) {
        context.settingsDataStore.edit { prefs ->
            val lines = decodeLines(prefs[Keys.LinesJson].orEmpty()).toMutableList()
            val targetIndex = lines.indexOfLast { it.ticker.equals(ticker, ignoreCase = true) }
            if (targetIndex >= 0) {
                lines.removeAt(targetIndex)
                prefs[Keys.LinesJson] = encodeLines(lines)
            }
        }
    }
}

private fun encodeGuideSnapshot(settings: AppSettings, lines: List<UserLine>): String =
    JSONObject()
        .put("orientation", settings.orientationMode.name)
        .put("ticker", settings.ticker)
        .put("timeframe", settings.timeframe)
        .put("logScale", settings.logScale)
        .put("theme", settings.theme)
        .put("candleMode", settings.candleMode)
        .put("candleColor", settings.candleColor)
        .put("backgroundColor", settings.backgroundColor)
        .put("gridColor", settings.gridColor)
        .put("textColor", settings.textColor)
        .put("fontFamily", settings.fontFamily)
        .put("fontSize", settings.fontSize.toDouble())
        .put("lineWidth", settings.lineWidth.toDouble())
        .put("pointSize", settings.pointSize.toDouble())
        .put("starSize", settings.starSize.toDouble())
        .put("rangeStartPercent", settings.rangeStartPercent.toDouble())
        .put("rangeEndPercent", settings.rangeEndPercent.toDouble())
        .put("linesJson", encodeLines(lines))
        .toString()

fun encodeLines(lines: List<UserLine>): String {
    val array = JSONArray()
    lines.forEach { line ->
        array.put(
            JSONObject()
                .put("id", line.id)
                .put("ticker", line.ticker)
                .put("timeframe", line.timeframe)
                .put("startTimeMillis", line.startTimeMillis)
                .put("startPrice", line.startPrice.toDouble())
                .put("endTimeMillis", line.endTimeMillis)
                .put("endPrice", line.endPrice.toDouble())
                .put("color", line.color)
                .put("alertEnabled", line.alertEnabled)
                .put("forecastTimeMillis", line.forecastTimeMillis ?: JSONObject.NULL)
                .put("forecastPrice", line.forecastPrice?.toDouble() ?: JSONObject.NULL)
        )
    }
    return array.toString()
}

fun decodeLines(raw: String): List<UserLine> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    UserLine(
                        id = obj.getString("id"),
                        ticker = obj.optString("ticker", "TSLA"),
                        timeframe = obj.optString("timeframe", "D"),
                        startTimeMillis = obj.getLong("startTimeMillis"),
                        startPrice = obj.getDouble("startPrice").toFloat(),
                        endTimeMillis = obj.getLong("endTimeMillis"),
                        endPrice = obj.getDouble("endPrice").toFloat(),
                        color = obj.optLong("color", 0xFFF9E2AFL),
                        alertEnabled = obj.optBoolean("alertEnabled", true),
                        forecastTimeMillis = if (obj.isNull("forecastTimeMillis")) null else obj.optLong("forecastTimeMillis"),
                        forecastPrice = if (obj.isNull("forecastPrice")) null else obj.optDouble("forecastPrice").toFloat(),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
