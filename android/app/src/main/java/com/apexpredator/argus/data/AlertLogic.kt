package com.apexpredator.argus.data

// Pure decision rules for the v2.3 balcony alerts. No Android imports,
// so every rule here is unit-testable on the JVM. The background worker
// (AlertWorker) only fetches data and posts notifications for what
// these functions flag.

// A satellite pass reduced to what an alert needs. The worker maps
// SatPass objects into this shape so this file never touches the
// SGP4/predict4java types.
data class PassWindow(
    val name: String,
    val startMs: Long,
    val peakMs: Long,
    val maxElevationDeg: Double,
)

// A large airport reduced to what an alert needs, same reason.
data class AirportRef(
    val ident: String,
    val lat: Double,
    val lon: Double,
    val isLarge: Boolean,
)

object AlertLogic {

    // A heavy aircraft on final approach: ADS-B category 6 (heavy,
    // over 300,000 lb), within 60 km of a large airport, heading at
    // the airport and descending below 12,000 ft. Mirrors the
    // "On approach" verdict in SkyMath but restricted to heavies,
    // the ones worth stepping onto a balcony for.
    fun isHeavyOnFinal(
        ac: AircraftState,
        airportLat: Double,
        airportLon: Double,
        distanceToAirportKm: Double,
    ): Boolean {
        if (ac.category != CAT_HEAVY) return false
        if (ac.onGround) return false
        if (distanceToAirportKm > FINAL_RADIUS_KM) return false
        val altFt = ac.altitudeFt ?: return false
        if (altFt > FINAL_MAX_ALT_FT) return false
        val track = ac.trueTrackDeg ?: return false
        val bearing = GeoMath.bearingDeg(
            ac.latitude, ac.longitude, airportLat, airportLon
        )
        if (SkyMath.headingDiffDeg(track, bearing) > FINAL_HEADING_DEG) return false
        val vr = ac.verticalRateMs ?: 0.0
        if (vr >= -DESCENT_MS) return false
        return true
    }

    // Emergency squawks within range of the user, nearest first.
    fun emergenciesWithin(
        states: List<AircraftState>,
        userLat: Double,
        userLon: Double,
        radiusKm: Double = EMERGENCY_RADIUS_KM,
    ): List<Pair<AircraftState, Double>> =
        states.filter { it.isEmergency }
            .map { it to GeoMath.haversineKm(userLat, userLon, it.latitude, it.longitude) }
            .filter { it.second <= radiusKm }
            .sortedBy { it.second }

    // The first pass starting within the alert window (about 10
    // minutes), if any. Passes are already sorted by start time.
    fun passStartingWithin(
        passes: List<PassWindow>,
        nowMs: Long,
        windowMs: Long = PASS_WINDOW_MS,
    ): PassWindow? =
        passes.firstOrNull { it.startMs in nowMs..(nowMs + windowMs) }

    // The best pass in the next 24 hours: the highest peak elevation
    // among passes that have not started yet. Used by the morning
    // briefing card as well as the alert worker.
    fun bestUpcomingPass(
        passes: List<PassWindow>,
        nowMs: Long,
    ): PassWindow? =
        passes.filter { it.startMs >= nowMs }
            .maxByOrNull { it.maxElevationDeg }

    // Nearest large airport within range, for the heavy-final rule.
    fun nearestLargeAirport(
        airports: List<AirportRef>,
        userLat: Double,
        userLon: Double,
        maxKm: Double = FINAL_RADIUS_KM,
    ): Pair<AirportRef, Double>? =
        airports.filter { it.isLarge }
            .map { it to GeoMath.haversineKm(userLat, userLon, it.lat, it.lon) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }

    const val CAT_HEAVY = 6
    const val FINAL_RADIUS_KM = 60.0
    const val FINAL_MAX_ALT_FT = 12_000.0
    const val FINAL_HEADING_DEG = 30.0
    const val DESCENT_MS = 1.0
    const val EMERGENCY_RADIUS_KM = 80.0
    const val PASS_WINDOW_MS = 10L * 60_000
}
