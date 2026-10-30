package com.apexpredator.argus.data

// An offline region pack: the data a user downloads while online so
// the app keeps working with zero connectivity for a chosen set of
// airports. Airports and runways are already bundled globally, so
// the pack adds what is not bundled: cached satellite TLE text and
// the latest METAR/TAF per chosen airport. Map tiles are not in the
// pack: osmdroid caches tiles on the phone as the user pans and
// zooms, so the honest route is to view the region while online
// first (the Tools card says exactly that). Pure helpers,
// unit-tested.
data class PackWeatherEntry(
    val icao: String,
    val kind: String,
    val raw: String,
    val fetchedMs: Long,
)

data class RegionPackSummary(
    val airports: List<String>,
    val createdMs: Long,
    val tleBytes: Long,
    val weatherEntries: Int,
    val weatherBytes: Long,
) {
    val totalBytes: Long get() = tleBytes + weatherBytes
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

// Airport codes typed free-form (comma or space separated): keep
// valid 4-letter codes, uppercase, deduped in order, capped so one
// typo-heavy paste cannot balloon the pack.
fun parsePackAirports(input: String): List<String> {
    val seen = LinkedHashSet<String>()
    for (token in input.split(',', ' ', '\n', '\t')) {
        val clean = token.trim().uppercase()
        if (clean.matches(Regex("^[A-Z0-9]{4}$"))) {
            seen.add(clean)
            if (seen.size >= MAX_PACK_AIRPORTS) break
        }
    }
    return seen.toList()
}

// A pack never stops working, but its weather and TLE go stale: past
// a week old the card says so and suggests a refresh, rather than
// silently serving old data as if it were current.
fun isPackStale(createdMs: Long, nowMs: Long): Boolean =
    nowMs - createdMs > PACK_STALE_MS

fun packAgeText(createdMs: Long, nowMs: Long): String {
    val days = (nowMs - createdMs) / DAY_MS
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

const val MAX_PACK_AIRPORTS = 20
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val PACK_STALE_MS = 7 * DAY_MS
