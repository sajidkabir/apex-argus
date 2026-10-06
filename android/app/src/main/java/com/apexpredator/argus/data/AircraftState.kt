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
) {
    val altitudeFt: Double? get() = baroAltitudeM?.times(M_TO_FT)
    val speedKt: Double? get() = velocityMs?.times(MS_TO_KT)
    val verticalRateFpm: Double? get() = verticalRateMs?.times(MS_TO_FPM)

    companion object {
        const val M_TO_FT = 3.28084
        const val MS_TO_KT = 1.94384
        const val MS_TO_FPM = 196.85
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
            )
        )
    }
    return out
}

private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? =
    if (index >= length() || isNull(index)) null else getDouble(index)

private fun org.json.JSONArray.optStringOrNull(index: Int): String? =
    if (index >= length() || isNull(index)) null else getString(index)

private fun org.json.JSONArray.optStringSafe(index: Int): String =
    optStringOrNull(index) ?: ""

private const val IDX_ICAO24 = 0
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
