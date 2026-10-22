package com.apexpredator.argus.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class GeoFix(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val accuracyM: Float?,
    val timeMs: Long,
)

// Device location without Google Play services: the platform
// LocationManager is enough for "what is flying over me" accuracy.
class LocationRepository(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun lastKnown(): GeoFix? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.time > (best?.time ?: 0)) best = loc
            } catch (e: SecurityException) {
                // Permission raced away; treat as no fix.
            }
        }
        return best?.toFix()
    }

    fun updates(minTimeMs: Long = 2_000L): Flow<GeoFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            @Deprecated("Deprecated in API 30, still required by the interface")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(provider, minTimeMs, 5f, listener)
                } catch (e: Exception) {
                    // Provider unavailable on this device; the other may work.
                }
            }
        } catch (e: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}

private fun Location.toFix(): GeoFix = GeoFix(
    lat = latitude,
    lon = longitude,
    altitudeM = if (hasAltitude()) altitude else null,
    accuracyM = if (hasAccuracy()) accuracy else null,
    timeMs = time,
)
