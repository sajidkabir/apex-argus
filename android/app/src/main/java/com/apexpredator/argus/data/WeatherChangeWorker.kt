package com.apexpredator.argus.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apexpredator.argus.MainActivity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Background weather-change alerts (v2.6), checked every 15 minutes
// (the WorkManager periodic minimum) alongside the balcony alerts:
// for each watched airport, fetch the latest METAR, compare it with
// the previous one, and notify on a material change (wind shift,
// visibility drop below a threshold, new CB or TS, SPECI) with the
// plain-English change in the notification. Best-effort and silent
// on failure, like AlertWorker: a failed run never crashes the app,
// the next run tries again. With no watched airports, or with the
// toggle off, or with notifications denied, the worker does nothing.
class WeatherChangeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            if (!hasNotificationPermission()) return Result.success()
            val prefs = PrefsStore(applicationContext)
            if (!prefs.weatherAlert.first()) return Result.success()
            val watched = prefs.watchedAirports.first()
            if (watched.isEmpty()) return Result.success()
            val weather = WeatherRepository()
            val store = WeatherWatchStore(applicationContext)
            for (icao in watched.sorted()) {
                try {
                    val raw = weather.fetchRaw(icao, ReportKind.METAR)
                    val previousRaw = store.lastReport(icao)
                    val previous = previousRaw?.let { parseMetarSnapshot(icao, it) }
                    val current = parseMetarSnapshot(icao, raw)
                    val changes = compareMetarChanges(previous, current)
                    if (changes.isNotEmpty()) {
                        val signature = "$icao:${raw.hashCode()}:${changes.joinToString("|")}"
                        if (store.lastSignature(icao) != signature) {
                            postNotification(
                                title = "Weather change at $icao",
                                text = changes.joinToString("; "),
                            )
                            store.setLastSignature(icao, signature)
                        }
                    }
                    store.setLastReport(icao, raw)
                } catch (e: Exception) {
                    // One airport's failed fetch must not block the rest.
                }
            }
        } catch (e: Exception) {
            // Best-effort by design; the next run retries.
        }
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(title: String, text: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                AlertWorker.CHANNEL_ID, "Balcony alerts", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Satellite passes, heavy finals, emergency squawks and watched-airport weather changes."
            }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(applicationContext, AlertWorker.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), notification)
    }

    companion object {
        const val UNIQUE_NAME = "argus_weather_changes"
    }
}

// Schedules (or keeps) the periodic weather-change check. Called on
// app start and whenever the user turns the toggle on, so the check
// survives app restarts and device reboots through WorkManager itself.
object WeatherChangeScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherChangeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherChangeWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
