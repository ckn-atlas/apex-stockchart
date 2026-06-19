package com.apex.stockchart.data

import kotlin.math.abs

enum class OrientationMode {
    System,
    Portrait,
    Landscape,
}

data class Candle(
    val timeMillis: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
)

data class TickerSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String,
) {
    val label: String
        get() = if (exchange.isBlank()) {
            "$symbol | $name"
        } else {
            "$symbol | $name [$exchange]"
        }
}

data class UserLine(
    val id: String,
    val ticker: String,
    val timeframe: String,
    val startTimeMillis: Long,
    val startPrice: Float,
    val endTimeMillis: Long,
    val endPrice: Float,
    val color: Long = 0xFFF9E2AFL,
    val alertEnabled: Boolean = true,
    val forecastTimeMillis: Long? = null,
    val forecastPrice: Float? = null,
) {
    fun priceAt(timeMillis: Long): Float {
        if (startTimeMillis == endTimeMillis) return endPrice
        val ratio = (timeMillis - startTimeMillis).toFloat() /
            (endTimeMillis - startTimeMillis).toFloat()
        return startPrice + (endPrice - startPrice) * ratio
    }

    fun touches(candle: Candle, tolerancePercent: Float = 0.001f): Boolean {
        val linePrice = priceAt(candle.timeMillis)
        val tolerance = abs(linePrice) * tolerancePercent
        return candle.low - tolerance <= linePrice && linePrice <= candle.high + tolerance
    }
}

data class AppSettings(
    val orientationMode: OrientationMode = OrientationMode.System,
    val ticker: String = "TSLA",
    val guideReturnTicker: String? = null,
    val timeframe: String = "D",
    val logScale: Boolean = false,
    val theme: String = "dark",
    val candleMode: String = "mono",
    val candleColor: Long = 0xFF1E88FF,
    val backgroundColor: Long = 0xFF171724,
    val gridColor: Long = 0xFF303244,
    val textColor: Long = 0xFFE5E7FF,
    val fontFamily: String = "Arial",
    val fontSize: Float = 13f,
    val lineWidth: Float = 2.6f,
    val pointSize: Float = 8f,
    val starSize: Float = 10f,
    val rangeStartPercent: Float = 80f,
    val rangeEndPercent: Float = 100f,
    val hasSavedRange: Boolean = false,
)
