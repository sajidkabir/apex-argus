package com.apexpredator.argus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Geographic bounding box in degrees for an OpenSky states query.
data class BoundingBox(
    val lamin: Double,
    val lomin: Double,
    val lamax: Double,
    val lomax: Double,
)

// Fetches live aircraft state vectors from the OpenSky Network, a
// community ADS-B receiver network. Anonymous access, no API key needed.
// Throws IOException with a user-friendly message on any failure.
class OpenSkyRepository {

    // Anonymous quota is 400 credits/day and a bounding box of at most
    // 25 square degrees costs exactly 1 credit per call, so every query
    // is clamped to a 5x5 degree window around the requested center.
    // At the default 3-minute refresh cadence a full day of continuous
    // use costs ~480 credits; the app also refreshes on demand only when
    // the map moves or the user taps refresh.
    suspend fun fetchStates(bbox: BoundingBox): List<AircraftState> =
        withContext(Dispatchers.IO) {
            val b = clampToBudget(bbox)
            val query = String.format(
                Locale.US,
                "lamin=%.4f&lomin=%.4f&lamax=%.4f&lomax=%.4f",
                b.lamin, b.lomin, b.lamax, b.lomax
            )
            val connection =
                (URL("$BASE_URL?$query").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit
                    429 -> throw IOException(
                        "Flight-data quota reached for now. " +
                            "Wait a few minutes, then tap refresh."
                    )
                    else -> throw IOException(
                        "Flight-data service returned HTTP ${connection.responseCode}. " +
                            "Try again later."
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseAircraftStates(body)
            } catch (e: IOException) {
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host") ||
                    msg.contains("No address associated")
                ) {
                    throw IOException("No network connection. Check your internet and try again.")
                }
                throw e
            } finally {
                connection.disconnect()
            }
        }

    // Shrinks any bounding box larger than 25 square degrees to a 5x5 degree
    // window around its center, so every query costs exactly 1 anonymous
    // credit (OpenSky bills /states/all by bbox area). Smaller boxes pass
    // through untouched. Pure function, unit-tested.
    fun clampToBudget(bbox: BoundingBox): BoundingBox {
        val area = (bbox.lamax - bbox.lamin) * (bbox.lomax - bbox.lomin)
        if (area <= MAX_AREA_SQ_DEG) return bbox
        val latCenter = (bbox.lamin + bbox.lamax) / 2.0
        val lonCenter = (bbox.lomin + bbox.lomax) / 2.0
        val half = MAX_WINDOW_DEG / 2.0
        return BoundingBox(
            lamin = (latCenter - half).coerceIn(-90.0, 90.0),
            lomin = (lonCenter - half).coerceIn(-180.0, 180.0),
            lamax = (latCenter + half).coerceIn(-90.0, 90.0),
            lomax = (lonCenter + half).coerceIn(-180.0, 180.0),
        )
    }

    companion object {
        private const val BASE_URL = "https://opensky-network.org/api/states/all"
        private const val USER_AGENT = "ApexArgus/1.1.0"
        const val MAX_WINDOW_DEG = 5.0
        const val MAX_AREA_SQ_DEG = 25.0
    }
}
