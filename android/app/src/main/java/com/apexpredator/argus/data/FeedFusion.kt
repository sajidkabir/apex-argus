package com.apexpredator.argus.data

// Merges a user's own ADS-B receiver traffic into the OpenSky feed.
// Dedupe key is the ICAO24 hex code, case-insensitive. When both
// feeds report the same aircraft, the local position wins unless the
// OpenSky position is provably fresher (a strictly newer lastSeenMs);
// a missing timestamp on either side counts as a tie, and ties go to
// the local receiver, which sits metres from the antenna instead of
// behind an internet round trip. Local-only aircraft are appended.
// Pure function, unit-tested.
fun mergeAircraft(
    opensky: List<AircraftState>,
    local: List<AircraftState>,
): List<AircraftState> {
    val byHex = LinkedHashMap<String, AircraftState>()
    for (ac in opensky) {
        byHex.putIfAbsent(ac.icao24.lowercase(), ac)
    }
    for (raw in local) {
        val ac = raw.copy(isLocal = true)
        val key = ac.icao24.lowercase()
        val existing = byHex[key]
        if (existing == null) {
            byHex[key] = ac
        } else if (localWins(existing, ac)) {
            byHex[key] = ac
        }
    }
    return byHex.values.toList()
}

private fun localWins(opensky: AircraftState, local: AircraftState): Boolean {
    val openSeen = opensky.lastSeenMs
    val localSeen = local.lastSeenMs
    if (openSeen == null || localSeen == null) return true
    return localSeen >= openSeen
}

fun localAircraftCount(aircraft: List<AircraftState>): Int =
    aircraft.count { it.isLocal }
