package com.apexpredator.argus.data

// The sighting logbook: every aircraft the user identifies, stored
// locally on the phone, nothing leaves the device. Pure data plus
// stats and a small text encoding, so the store (LogbookStore) stays
// thin and everything here is unit-testable without Android.

data class Sighting(
    val id: String,
    val timestampMs: Long,
    val callsign: String?,
    val icao24: String,
    val aircraftType: String?,
    val airline: String?,
    val airportContext: String?,
    val source: String,
) {
    // Display name: callsign when known, otherwise the hex code.
    val displayName: String get() = callsign ?: icao24.uppercase()
}

data class LogbookStats(
    val total: Int,
    val uniqueAircraft: Int,
    val uniqueAirlines: Int,
)

fun logbookStats(entries: List<Sighting>): LogbookStats = LogbookStats(
    total = entries.size,
    uniqueAircraft = entries.map { it.icao24.lowercase() }.toSet().size,
    uniqueAirlines = entries.mapNotNull { it.airline?.takeIf { s -> s.isNotBlank() } }.toSet().size,
)

// Text encoding for one sighting per line, fields separated by the
// unit separator (U+001F, the same separator runways.tsv uses).
// Backslash, the separator and newlines are escaped, so callsigns,
// airline names and airport notes round-trip exactly.
fun encodeSighting(s: Sighting): String {
    fun esc(v: String?): String {
        if (v == null) return ""
        return v.replace("\\", "\\\\")
            .replace("\u001F", "\\u")
            .replace("\n", "\\n")
    }
    return listOf(
        esc(s.id), s.timestampMs.toString(), esc(s.callsign), esc(s.icao24),
        esc(s.aircraftType), esc(s.airline), esc(s.airportContext), esc(s.source),
    ).joinToString("\u001F")
}

fun decodeSighting(line: String): Sighting? {
    if (line.isBlank()) return null
    val fields = splitEscaped(line)
    if (fields.size != 8) return null
    val ts = fields[1].toLongOrNull() ?: return null
    if (fields[3].isBlank()) return null
    return Sighting(
        id = fields[0],
        timestampMs = ts,
        callsign = fields[2].ifEmpty { null },
        icao24 = fields[3],
        aircraftType = fields[4].ifEmpty { null },
        airline = fields[5].ifEmpty { null },
        airportContext = fields[6].ifEmpty { null },
        source = fields[7].ifEmpty { "radar" },
    )
}

private fun splitEscaped(line: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\\' && i + 1 < line.length) {
            when (line[i + 1]) {
                '\\' -> cur.append('\\')
                'u' -> cur.append('\u001F')
                'n' -> cur.append('\n')
                else -> {
                    cur.append('\\')
                    cur.append(line[i + 1])
                }
            }
            i += 2
        } else if (c == '\u001F') {
            out.add(cur.toString())
            cur.clear()
            i += 1
        } else {
            cur.append(c)
            i += 1
        }
    }
    out.add(cur.toString())
    return out
}
