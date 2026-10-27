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

// Background balcony alerts, checked every 15 minutes (the minimum
// WorkManager periodic interval) while the app is installed:
// a satellite pass starting within 10 minutes, a heavy aircraft on
// final approach to the nearest large airport, and any emergency
// squawk within 80 km. Every check is best-effort and silent on
// failure: a failed run must never crash the app or spam retries,
// the next periodic run tries again. If notifications are denied,
// the worker simply does nothing until the user re-enables them.
class AlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            if (!hasNotificationPermission()) return Result.success()
            val prefs = PrefsStore(applicationContext)
            val satOn = prefs.alertSatPass.first()
            val heavyOn = prefs.alertHeavy.first()
            val emergencyOn = prefs.alertEmergency.first()
            if (!satOn && !heavyOn && !emergencyOn) return Result.success()

            val fix = LocationRepository(applicationContext).lastKnown()
            // Dhaka: the user's home sky when no fix is available,
            // the same fallback the Sky tab uses.
            val lat = fix?.lat ?: DEFAULT_LAT
            val lon = fix?.lon ?: DEFAULT_LON

            if (satOn) checkSatPass(prefs, lat, lon)
            if (heavyOn || emergencyOn) checkTraffic(prefs, lat, lon, heavyOn, emergencyOn)
        } catch (e: Exception) {
            // Best-effort by design; the next run retries.
        }
        return Result.success()
    }

    private suspend fun checkSatPass(prefs: PrefsStore, lat: Double, lon: Double) {
        val repo = SatelliteRepository(applicationContext)
        repo.load()
        val now = System.currentTimeMillis()
        val windows = repo.passesOver(lat, lon, hoursAhead = 24).map {
            PassWindow(it.name, it.startMs, it.peakMs, it.maxElevationDeg)
        }
        val pass = AlertLogic.passStartingWithin(windows, now) ?: return
        val key = "${pass.name}:${pass.startMs}"
        if (prefs.lastSatAlertKey.first() == key) return
        postNotification(
            title = "${pass.name} passing overhead soon",
            text = "Rises in under 10 minutes, peaks " +
                "${pass.maxElevationDeg.toInt()} degrees up. Step out and look up.",
        )
        prefs.setLastSatAlertKey(key)
    }

    private suspend fun checkTraffic(
        prefs: PrefsStore,
        lat: Double,
        lon: Double,
        heavyOn: Boolean,
        emergencyOn: Boolean,
    ) {
        val half = 1.0
        val states = OpenSkyRepository().fetchStates(
            BoundingBox(lat - half, lon - half, lat + half, lon + half)
        )
        if (emergencyOn) {
            val near = AlertLogic.emergenciesWithin(states, lat, lon)
            val first = near.firstOrNull()
            if (first != null) {
                val (ac, distKm) = first
                val key = "${ac.icao24}:${ac.squawk}"
                if (prefs.lastEmergencyAlertKey.first() != key) {
                    postNotification(
                        title = "Emergency squawk ${ac.squawk} nearby",
                        text = "${ac.callsign ?: ac.icao24.uppercase()} is " +
                            "${distKm.toInt()} km away, squawking ${ac.squawk}.",
                    )
                    prefs.setLastEmergencyAlertKey(key)
                }
            }
        }
        if (heavyOn) {
            AirportDb.ensureLoaded(applicationContext)
            val airports = AirportDb.all().map {
                AirportRef(it.ident, it.lat, it.lon, it.isLarge)
            }
            val nearest = AlertLogic.nearestLargeAirport(airports, lat, lon) ?: return
            val (airport, _) = nearest
            for (ac in states) {
                val dist = GeoMath.haversineKm(
                    airport.lat, airport.lon, ac.latitude, ac.longitude
                )
                if (!AlertLogic.isHeavyOnFinal(ac, airport.lat, airport.lon, dist)) continue
                val key = "${ac.callsign ?: ac.icao24}:${airport.ident}"
                if (prefs.lastHeavyAlertKey.first() == key) return
                postNotification(
                    title = "Heavy on final to ${airport.ident}",
                    text = "${ac.callsign ?: ac.icao24.uppercase()} is on " +
                        "approach to ${airport.ident}. Look up.",
                )
                prefs.setLastHeavyAlertKey(key)
                return
            }
        }
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
                CHANNEL_ID, "Balcony alerts", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Satellite passes, heavy finals and emergency squawks nearby."
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
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
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
        const val CHANNEL_ID = "argus_alerts"
        const val UNIQUE_NAME = "argus_balcony_alerts"
        private const val DEFAULT_LAT = 23.8103
        private const val DEFAULT_LON = 90.4125
    }
}

// Schedules (or keeps) the periodic alert check. Called on app start
// and whenever the user toggles an alert on, so the check survives
// app restarts and device reboots through WorkManager itself.
object AlertScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlertWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
