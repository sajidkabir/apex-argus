package com.apexpredator.argus.data

import android.content.Context
import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.PassPredictor
import com.github.amsacode.predict4java.Satellite
import com.github.amsacode.predict4java.SatelliteFactory
import com.github.amsacode.predict4java.TLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import kotlin.math.sqrt

// A tracked satellite with its live propagated position. Positions are
// computed on-device from CelesTrak two-line elements with the SGP4
// propagator in predict4java, so after the one TLE download the layer
// keeps moving with no network at all.
data class SatellitePosition(
    val name: String,
    val noradId: Int,
    val group: String,
    val lat: Double,
    val lon: Double,
    val altitudeKm: Double,
    val velocityKmS: Double?,
) {
    val altitudeFt: Double get() = altitudeKm * 3280.84
}

// One predicted satellite pass over the observer.
data class SatPass(
    val name: String,
    val noradId: Int,
    val group: String,
    val startMs: Long,
    val peakMs: Long,
    val endMs: Long,
    val maxElevationDeg: Double,
    val riseAzimuthDeg: Double,
    val setAzimuthDeg: Double,
) {
    val durationMin: Double get() = (endMs - startMs) / 60_000.0
}

// CelesTrak groups fetched for the satellite layer. Starlink is left
// out on purpose: 8,000+ objects would bury the map and the phone.
private val TLE_GROUPS = listOf("stations", "visual", "weather", "gps-ops", "science")

class SatelliteRepository(private val context: Context) {

    private var satellites: List<Satellite> = emptyList()
    private var groupByNorad: Map<Int, String> = emptyMap()

    val isLoaded: Boolean get() = satellites.isNotEmpty()
    val count: Int get() = satellites.size

    // Downloads fresh TLEs when the cache is older than a day, otherwise
    // reuses the cache. Falls back to any cache on network failure.
    suspend fun load(forceRefresh: Boolean = false) {
        if (satellites.isNotEmpty() && !forceRefresh) return
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "tle").apply { mkdirs() }
            val parsed = ArrayList<Satellite>()
            val groups = HashMap<Int, String>()
            for (group in TLE_GROUPS) {
                val file = File(dir, "$group.tle")
                val fresh = file.exists() &&
                    System.currentTimeMillis() - file.lastModified() < CACHE_MS
                if (!fresh) {
                    try {
                        val body = HttpJson.getText(
                            "https://celestrak.org/NORAD/elements/gp.php" +
                                "?GROUP=$group&FORMAT=tle"
                        )
                        file.writeText(body)
                    } catch (e: Exception) {
                        // Keep any stale cache rather than losing the layer.
                        if (!file.exists()) continue
                    }
                }
                if (!file.exists()) continue
                for (tle in parseTleText(file.readText())) {
                    try {
                        val sat = SatelliteFactory.createSatellite(tle)
                        val id = tle.catnum
                        if (groups.put(id, group) == null) parsed.add(sat)
                    } catch (e: Exception) {
                        // Skip malformed elements; one bad TLE must not
                        // take down the whole layer.
                    }
                }
            }
            satellites = parsed
            groupByNorad = groups
        }
    }

    // Propagates every tracked satellite to the given instant.
    fun positionsAt(date: Date = Date()): List<SatellitePosition> {
        if (satellites.isEmpty()) return emptyList()
        val origin = GroundStationPosition(0.0, 0.0, 0.0)
        val later = Date(date.time + VELOCITY_STEP_MS)
        val out = ArrayList<SatellitePosition>(satellites.size)
        for (sat in satellites) {
            try {
                val p1 = sat.getPosition(origin, date)
                val p2 = sat.getPosition(origin, later)
                val lat1 = Math.toDegrees(p1.latitude)
                val lon1 = Math.toDegrees(p1.longitude)
                val lat2 = Math.toDegrees(p2.latitude)
                val lon2 = Math.toDegrees(p2.longitude)
                val surfaceKm = GeoMath.haversineKm(lat1, lon1, lat2, lon2)
                val dAlt = p2.altitude - p1.altitude
                val velocity = sqrt(surfaceKm * surfaceKm + dAlt * dAlt) /
                    (VELOCITY_STEP_MS / 1000.0)
                val tle = sat.tle
                out.add(
                    SatellitePosition(
                        name = tle.name.trim(),
                        noradId = tle.catnum,
                        group = groupByNorad[tle.catnum] ?: "",
                        lat = lat1,
                        lon = lon1,
                        altitudeKm = p1.altitude,
                        velocityKmS = velocity,
                    )
                )
            } catch (e: Exception) {
                // A single bad propagation must not empty the layer.
            }
        }
        return out
    }

    fun search(query: String, limit: Int = 12): List<SatellitePosition> {
        val q = query.trim().uppercase()
        if (q.length < 2) return emptyList()
        return positionsAt()
            .filter {
                it.name.uppercase().contains(q) || it.noradId.toString() == q
            }
            .take(limit)
    }

    // Upcoming passes over an observer: when each satellite rises,
    // peaks and sets, computed with predict4java's pass predictor.
    // Limited to stations and bright satellites, the ones actually
    // worth stepping onto a balcony for, and to passes peaking at
    // least 10 degrees up.
    suspend fun passesOver(
        lat: Double,
        lon: Double,
        hoursAhead: Int = 24,
    ): List<SatPass> = withContext(Dispatchers.Default) {
        if (satellites.isEmpty()) return@withContext emptyList()
        val station = GroundStationPosition(lat, lon, 0.0)
        val now = Date()
        val out = ArrayList<SatPass>()
        for (sat in satellites) {
            val group = groupByNorad[sat.tle.catnum] ?: continue
            if (group != "stations" && group != "visual") continue
            try {
                val predictor = PassPredictor(sat.tle, station)
                for (pass in predictor.getPasses(now, hoursAhead, true)) {
                    if (pass.maxEl < 10.0) continue
                    out.add(
                        SatPass(
                            name = sat.tle.name.trim(),
                            noradId = sat.tle.catnum,
                            group = group,
                            startMs = pass.startTime.time,
                            peakMs = pass.tca.time,
                            endMs = pass.endTime.time,
                            maxElevationDeg = pass.maxEl,
                            riseAzimuthDeg = pass.aosAzimuth.toDouble(),
                            setAzimuthDeg = pass.losAzimuth.toDouble(),
                        )
                    )
                }
            } catch (e: Exception) {
                // One satellite's bad elements must not sink the list.
            }
        }
        out.sortedBy { it.startMs }
    }

    companion object {
        private const val CACHE_MS = 24L * 60 * 60 * 1000
        private const val VELOCITY_STEP_MS = 30_000L
    }
}

// Splits CelesTrak 3-line TLE text into TLE objects. Pure function so
// it is unit-testable.
fun parseTleText(text: String): List<TLE> {
    val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    val out = ArrayList<TLE>()
    var i = 0
    while (i + 2 <= lines.lastIndex) {
        val name = lines[i]
        val l1 = lines[i + 1]
        val l2 = lines[i + 2]
        if (l1.startsWith("1 ") && l2.startsWith("2 ")) {
            try {
                out.add(TLE(arrayOf(name, l1, l2)))
            } catch (e: Exception) {
                // Skip malformed triplet.
            }
            i += 3
        } else {
            i += 1
        }
    }
    return out
}
