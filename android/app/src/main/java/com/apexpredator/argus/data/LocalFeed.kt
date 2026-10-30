package com.apexpredator.argus.data

import org.json.JSONObject

// A user's own ADS-B receiver, in dump1090 / readsb aircraft.json
// format: an HTTP JSON endpoint on their own network, for example
// http://192.168.1.10:8080/data/aircraft.json served by a Raspberry
// Pi running dump1090. Pure functions over the raw body so they can
// be unit-tested on the JVM without Android dependencies.
//
// A dump1090 aircraft object carries, among other fields:
// hex (ICAO24), flight (callsign, often padded with spaces), lat,
// lon, alt_baro (feet, or the string "ground"), alt_geom, gs
// (knots), track (degrees), squawk, seen and seen_pos (seconds
// since that value was last seen). Objects without a position are
// dropped, exactly like position-less OpenSky vectors.
fun parseDump1090(body: String, nowMs: Long): List<AircraftState> {
    val root = JSONObject(body)
    val arr = root.optJSONArray("aircraft") ?: return emptyList()
    val out = ArrayList<AircraftState>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val hex = obj.optString("hex", "").trim().lowercase()
        if (hex.isEmpty()) continue
        if (obj.isNull("lat") || obj.isNull("lon")) continue
        val lat = obj.optDouble("lat", Double.NaN)
        val lon = obj.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue
        val onGround = obj.optString("alt_baro", "") == "ground"
        val altFt: Double? = when (val raw = obj.opt("alt_baro")) {
            is Number -> raw.toDouble()
            else -> when (val geom = obj.opt("alt_geom")) {
                is Number -> geom.toDouble()
                else -> null
            }
        }
        val seenPosSec = obj.optDoubleOrNull("seen_pos")
            ?: obj.optDoubleOrNull("seen")
        out.add(
            AircraftState(
                icao24 = hex,
                callsign = obj.optString("flight", "").trim().ifEmpty { null },
                originCountry = "Local receiver",
                longitude = lon,
                latitude = lat,
                baroAltitudeM = altFt?.times(0.3048),
                onGround = onGround,
                velocityMs = obj.optDoubleOrNull("gs")?.div(AircraftState.MS_TO_KT),
                trueTrackDeg = obj.optDoubleOrNull("track"),
                verticalRateMs = obj.optDoubleOrNull("baro_rate")?.times(0.3048 / 60.0),
                squawk = obj.optString("squawk", "").ifEmpty { null },
                category = null,
                isLocal = true,
                lastSeenMs = seenPosSec?.let { nowMs - (it * 1000).toLong() },
            )
        )
    }
    return out
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key, Double.NaN).takeUnless { it.isNaN() }

// A local feed address is only ever fetched when the user enables it
// and types it in, so validation stays permissive about LAN hosts
// (bare hostnames and IPs are normal on a home network) and strict
// about scheme: plain http for a LAN receiver, or https.
fun isValidLocalFeedUrl(url: String): Boolean {
    val clean = url.trim()
    if (clean.isEmpty() || clean.contains(" ")) return false
    val match = FEED_URL_REGEX.matchEntire(clean) ?: return false
    val scheme = match.groupValues[1]
    val host = match.groupValues[2]
    return (scheme == "http" || scheme == "https") && host.isNotEmpty()
}

// Users often type a bare LAN address (192.168.1.10:8080/data/...)
// without a scheme; a receiver endpoint is plain http by design, so
// add it rather than rejecting the entry.
fun normalizeLocalFeedUrl(input: String): String {
    val clean = input.trim()
    if (clean.isEmpty()) return clean
    if (clean.contains("://")) return clean
    return "http://$clean"
}

private val FEED_URL_REGEX =
    Regex("^(https?)://([^/:?#\\s]+)(?::\\d+)?(?:/[^\\s]*)?$")
