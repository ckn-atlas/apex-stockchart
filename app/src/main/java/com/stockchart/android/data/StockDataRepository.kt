package com.stockchart.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class StockDataRepository {
    suspend fun searchTickers(query: String): List<TickerSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = URL("https://query2.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=8&newsCount=0&listsCount=0&enableFuzzyQuery=true")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0 StockChartAndroid/0.1")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) return@withContext emptyList()
            parseYahooTickerSuggestions(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchCandles(ticker: String, timeframe: String): List<Candle> = withContext(Dispatchers.IO) {
        val interval = when (timeframe) {
            "W" -> "1wk"
            "M" -> "1mo"
            else -> "1d"
        }
        val range = when (timeframe) {
            "M" -> "20y"
            else -> "10y"
        }
        val symbol = URLEncoder.encode(ticker.uppercase(), "UTF-8")
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=$range&interval=$interval&includePrePost=false")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "Mozilla/5.0 StockChartAndroid/0.1")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                error("Data request failed: HTTP $code $body")
            }
            parseYahooCandles(body)
        } finally {
            connection.disconnect()
        }
    }
}

private val US_EXCHANGES = setOf("NMS", "NYQ", "ASE", "NGM", "NCM", "PCX", "BTS")

private fun parseYahooTickerSuggestions(raw: String): List<TickerSuggestion> {
    val root = JSONObject(raw)
    val quotes = root.optJSONArray("quotes") ?: return emptyList()
    val seen = mutableSetOf<String>()
    return buildList {
        for (index in 0 until quotes.length()) {
            val item = quotes.optJSONObject(index) ?: continue
            val symbol = item.optString("symbol").uppercase()
            val quoteType = item.optString("quoteType")
            val exchange = item.optString("exchange")
            if (symbol.isBlank() || !seen.add(symbol)) continue
            if (symbol.endsWith("=X")) continue
            if (quoteType !in setOf("EQUITY", "ETF")) continue
            if (exchange.isNotBlank() && exchange !in US_EXCHANGES) continue
            val name = item.optString("shortname")
                .ifBlank { item.optString("longname") }
                .ifBlank { symbol }
            add(TickerSuggestion(symbol = symbol, name = name, exchange = exchange))
        }
    }
}

private fun parseYahooCandles(raw: String): List<Candle> {
    val root = JSONObject(raw)
    val chart = root.getJSONObject("chart")
    val error = chart.opt("error")
    if (error != null && error.toString() != "null") {
        error("Yahoo chart error: $error")
    }
    val result = chart.getJSONArray("result").getJSONObject(0)
    val timestamps = result.getJSONArray("timestamp")
    val quote = result
        .getJSONObject("indicators")
        .getJSONArray("quote")
        .getJSONObject(0)
    val opens = quote.getJSONArray("open")
    val highs = quote.getJSONArray("high")
    val lows = quote.getJSONArray("low")
    val closes = quote.getJSONArray("close")

    return buildList {
        for (index in 0 until timestamps.length()) {
            if (opens.isNull(index) || highs.isNull(index) || lows.isNull(index) || closes.isNull(index)) {
                continue
            }
            val open = opens.getDouble(index).toFloat()
            val high = highs.getDouble(index).toFloat()
            val low = lows.getDouble(index).toFloat()
            val close = closes.getDouble(index).toFloat()
            add(
                Candle(
                    timeMillis = timestamps.getLong(index) * 1000L,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                )
            )
        }
    }.sortedBy { it.timeMillis }
}
