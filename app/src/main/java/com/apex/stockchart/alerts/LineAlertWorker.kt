package com.apex.stockchart.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apex.stockchart.R
import com.apex.stockchart.data.Candle
import com.apex.stockchart.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.sin

private const val CHANNEL_ID = "line_touch_alerts"
private const val WORK_NAME = "line-touch-alert-check"

class LineAlertWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        ensureNotificationChannel(applicationContext)

        val repository = SettingsRepository(applicationContext)
        val settings = repository.settings.first()
        val lines = repository.lines.first()
            .filter { it.alertEnabled && it.ticker.equals(settings.ticker, ignoreCase = true) }

        if (lines.isEmpty()) return Result.success()

        val latestCandle = placeholderLatestCandle(settings.ticker)
        val touched = lines.firstOrNull { it.touches(latestCandle) }
        if (touched != null) {
            notifyLineTouched(applicationContext, settings.ticker, latestCandle.close)
        }
        return Result.success()
    }
}

fun scheduleLineAlerts(context: Context) {
    val request = PeriodicWorkRequestBuilder<LineAlertWorker>(15, TimeUnit.MINUTES)
        .addTag(WORK_NAME)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
    )
}

fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Line touch alerts",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Alerts when price touches a user drawn line."
    }
    manager.createNotificationChannel(channel)
}

private fun notifyLineTouched(context: Context, ticker: String, price: Float) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("$ticker line touched")
        .setContentText("Latest candle touched a saved line near $${"%.2f".format(price)}")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(ticker.hashCode(), notification)
}

private fun placeholderLatestCandle(ticker: String): Candle {
    val now = System.currentTimeMillis()
    val base = if (ticker == "TSLA") 430f else 100f
    val close = base + (sin(now / 86_400_000.0) * 8.0).toFloat()
    return Candle(
        timeMillis = now,
        open = close - 2f,
        high = close + 4f,
        low = close - 4f,
        close = close,
    )
}
