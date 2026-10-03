package com.apexpredator.argus.decoder

// METAR decoder. Parses common METAR reports into structured data.
// Unknown tokens are skipped rather than failing the whole report.

object MetarDecoder {

    private val STATION_RE = Regex("^[A-Z0-9]{4}$")

    fun decode(raw: String): DecodedMetar {
        val tokens = normalizeReport(raw)
        var i = 0

        // Optional METAR / SPECI prefix.
        if (tokens[i] == "METAR" || tokens[i] == "SPECI") i++

        var station = "????"
        if (i < tokens.size && STATION_RE.matches(tokens[i])) {
            station = tokens[i]
            i++
        }

        var obsTime: String? = null
        var automatic = false
        var corrected = false

        // Header tokens: time, AUTO, COR.
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t == "AUTO" -> { automatic = true; i++ }
                t == "COR" -> { corrected = true; i++ }
                describeZuluTime(t) != null && obsTime == null -> {
                    obsTime = describeZuluTime(t); i++
                }
                else -> break
            }
        }

        val builder = ConditionsBuilder()
        var remarks: String? = null

        while (i < tokens.size) {
            val t = tokens[i]
            if (t == "RMK" || t == "REMARKS") {
                remarks = tokens.drop(i + 1).joinToString(" ").ifEmpty { null }
                break
            }
            // Best effort: skip tokens that are not condition groups.
            builder.tryParseGroup(t)
            i++
        }

        return DecodedMetar(
            raw = raw.trim(),
            station = station,
            observationTimeUtc = obsTime,
            automatic = automatic,
            corrected = corrected,
            conditions = builder.build(),
            remarks = remarks
        )
    }
}
