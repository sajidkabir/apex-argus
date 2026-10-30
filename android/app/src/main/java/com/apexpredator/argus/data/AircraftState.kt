package com.apexpredator.argus.data

import org.json.JSONObject

// One live aircraft position from the OpenSky Network API
// (https://opensky-network.org/api/states/all). Field order follows the
// documented 17-element state vector; every nullable field is handled,
// and vectors without a position are dropped.

data class AircraftState(
    val icao24: String,
    val callsign: String?,
    val originCountry: String,
    val longitude: Double,
    val latitude: Double,
    val baroAltitudeM: Double?,
    val onGround: Boolean,
    val velocityMs: Double?,
    val trueTrackDeg: Double?,
    val verticalRateMs: Double?,
    val squawk: String?,
    // ADS-B emitter category, only present in extended state vectors:
    // 8 rotorcraft, 14 UAV/drone, 6 heavy, 2 light, 9 glider, and so on.
    val category: Int? = null,
    // True when this position came from the user's own ADS-B receiver
    // (v2.4 local feed) rather than OpenSky; the map, sheets and
    // Near Me rows mark such traffic distinctly.
    val isLocal: Boolean = false,
    // When this position was observed, epoch milliseconds. OpenSky
    // state vectors carry time_position (index 3, seconds); dump1090
    // carries seen_pos (seconds ago). Null means unknown, and feed
    // fusion treats an unknown timestamp as a tie, won by local.
    val lastSeenMs: Long? = null,
) {
    val altitudeFt: Double? get() = baroAltitudeM?.times(M_TO_FT)
    val speedKt: Double? get() = velocityMs?.times(MS_TO_KT)
    val verticalRateFpm: Double? get() = verticalRateMs?.times(MS_TO_FPM)

    val isUav: Boolean get() = category == CAT_UAV
    val isRotorcraft: Boolean get() = category == CAT_ROTORCRAFT
    val isEmergency: Boolean get() = squawk in EMERGENCY_SQUAWKS

    companion object {
        const val M_TO_FT = 3.28084
        const val MS_TO_KT = 1.94384
        const val MS_TO_FPM = 196.85
        const val CAT_UAV = 14
        const val CAT_ROTORCRAFT = 8
        val EMERGENCY_SQUAWKS = setOf("7500", "7600", "7700")
    }
}

// Pure function over the raw JSON body so it can be unit-tested on the JVM
// without Android dependencies.
fun parseAircraftStates(body: String): List<AircraftState> {
    val root = JSONObject(body)
    val arr = root.optJSONArray("states") ?: return emptyList()
    val out = ArrayList<AircraftState>(arr.length())
    for (i in 0 until arr.length()) {
        val s = arr.optJSONArray(i) ?: continue
        // Guard against short or position-less vectors before touching indices.
        if (s.length() <= IDX_LAT || s.isNull(IDX_LON) || s.isNull(IDX_LAT)) continue
        out.add(
            AircraftState(
                icao24 = s.optStringSafe(IDX_ICAO24),
                callsign = s.optStringSafe(IDX_CALLSIGN).trim().ifEmpty { null },
                originCountry = s.optStringSafe(IDX_COUNTRY).ifEmpty { "Unknown" },
                longitude = s.getDouble(IDX_LON),
                latitude = s.getDouble(IDX_LAT),
                baroAltitudeM = s.optDoubleOrNull(IDX_BARO_ALT),
                onGround = s.optBoolean(IDX_ON_GROUND, false),
                velocityMs = s.optDoubleOrNull(IDX_VELOCITY),
                trueTrackDeg = s.optDoubleOrNull(IDX_TRACK),
                verticalRateMs = s.optDoubleOrNull(IDX_VERT_RATE),
                squawk = s.optStringOrNull(IDX_SQUAWK),
                category = s.optIntOrNull(IDX_CATEGORY),
                lastSeenMs = s.optLongOrNull(IDX_TIME_POSITION)?.times(1000),
                // Slot 18 is Argus's own extension, written by
                // PlaybackStore so a recorded frame keeps the local
                // mark; ordinary OpenSky vectors have no slot 18.
                isLocal = if (s.length() > IDX_LOCAL) s.optBoolean(IDX_LOCAL, false) else false,
            )
        )
    }
    return out
}

private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? =
    if (index >= length() || isNull(index)) null else getDouble(index)

private fun org.json.JSONArray.optIntOrNull(index: Int): Int? =
    if (index >= length() || isNull(index)) null else getInt(index)

private fun org.json.JSONArray.optLongOrNull(index: Int): Long? {
    if (index >= length() || isNull(index)) return null
    val value = get(index)
    return if (value is Number) value.toLong() else null
}

private fun org.json.JSONArray.optStringOrNull(index: Int): String? =
    if (index >= length() || isNull(index)) null else getString(index)

private fun org.json.JSONArray.optStringSafe(index: Int): String =
    optStringOrNull(index) ?: ""

private const val IDX_ICAO24 = 0
private const val IDX_TIME_POSITION = 3
private const val IDX_CALLSIGN = 1
private const val IDX_COUNTRY = 2
private const val IDX_LON = 5
private const val IDX_LAT = 6
private const val IDX_BARO_ALT = 7
private const val IDX_ON_GROUND = 8
private const val IDX_VELOCITY = 9
private const val IDX_TRACK = 10
private const val IDX_VERT_RATE = 11
private const val IDX_SQUAWK = 14
private const val IDX_CATEGORY = 17
private const val IDX_LOCAL = 18
