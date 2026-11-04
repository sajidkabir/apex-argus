package com.apexpredator.argus.data

import kotlin.math.abs

// Weather-change detection for watched airports (v2.6). Compares the
// latest METAR against the previous one for the same station and
// reports only material changes, in plain English, for a notification:
// a wind shift, a visibility drop below a threshold, newly reported
// cumulonimbus or thunderstorm, or a SPECI special observation.
// Pure Kotlin, no Android imports, unit-testable on the JVM. The
// parser is deliberately small: it reads the tokens a METAR actually
// carries, and anything it cannot parse is treated as unknown rather
// than guessed, so a weird report yields no change instead of a
// wrong one.

data class MetarSnapshot(
    val station: String,
    val raw: String,
    val isSpeci: Boolean,
    val windDirDeg: Int?,
    val windSpeedKt: Int?,
    val gustKt: Int?,
    val visibilityM: Double?,
    val hasCb: Boolean,
    val hasTs: Boolean,
)

// Parses just the fields the change detector needs out of a raw METAR.
fun parseMetarSnapshot(station: String, raw: String): MetarSnapshot {
    val tokens = raw.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    var dir: Int? = null
    var speedKt: Int? = null
    var gustKt: Int? = null
    var visibilityM: Double? = null
    var hasCb = false
    var hasTs = false
    var isSpeci = false
    for ((index, token) in tokens.withIndex()) {
        when {
            token == "SPECI" -> isSpeci = true
            token == "CAVOK" -> visibilityM = 10_000.0
            token.matches(Regex("^(VRB|\\d{3})(\\d{2,3})(G(\\d{2,3}))?(KT|MPS|KMH)$")) -> {
                val m = Regex("^(VRB|\\d{3})(\\d{2,3})(?:G(\\d{2,3}))?(KT|MPS|KMH)$")
                    .find(token)!!
                dir = if (m.groupValues[1] == "VRB") null else m.groupValues[1].toIntOrNull()
                val value = m.groupValues[2].toDoubleOrNull() ?: 0.0
                val unit = m.groupValues[4]
                speedKt = when (unit) {
                    "MPS" -> (value * 1.94384).toInt()
                    "KMH" -> (value / 1.852).toInt()
                    else -> value.toInt()
                }
                val gustValue = m.groupValues[3].toDoubleOrNull()
                gustKt = if (gustValue == null) {
                    null
                } else {
                    when (unit) {
                        "MPS" -> (gustValue * 1.94384).toInt()
                        "KMH" -> (gustValue / 1.852).toInt()
                        else -> gustValue.toInt()
                    }
                }
            }
            token.matches(Regex("^\\d{4}$")) && visibilityM == null -> {
                // A bare 4-digit group after the wind is visibility in
                // meters (9999 means 10 km or more). It must not be the
                // observation time, which is a 7-character DDHHMMZ token.
                visibilityM = if (token == "9999") 10_000.0 else token.toDouble()
            }
            token.matches(Regex("^(M|P)?\\d+(\\.\\d+)?SM$")) -> {
                val num = token.removeSuffix("SM").removePrefix("M").removePrefix("P")
                val miles = num.toDoubleOrNull()
                if (miles != null) visibilityM = miles * 1609.344
            }
            // Two-token statute-mile visibility: "1 1/2SM".
            token.matches(Regex("^\\d+/\\d+SM$")) && index > 0 -> {
                val whole = tokens[index - 1].toDoubleOrNull()
                val parts = token.removeSuffix("SM").split("/")
                val frac = if (parts.size == 2) {
                    val n = parts[0].toDoubleOrNull()
                    val d = parts[1].toDoubleOrNull()
                    if (n != null && d != null && d != 0.0) n / d else null
                } else {
                    null
                }
                if (whole != null && frac != null) visibilityM = (whole + frac) * 1609.344
            }
            token.endsWith("CB") -> hasCb = true
            token.contains("TS") -> hasTs = true
        }
    }
    return MetarSnapshot(
        station = station,
        raw = raw,
        isSpeci = isSpeci,
        windDirDeg = dir,
        windSpeedKt = speedKt,
        gustKt = gustKt,
        visibilityM = visibilityM,
        hasCb = hasCb,
        hasTs = hasTs,
    )
}

// Circular wind-direction difference in degrees, 0 to 180.
fun windShiftDeg(fromDeg: Int, toDeg: Int): Int {
    val diff = abs(fromDeg - toDeg) % 360
    return if (diff > 180) 360 - diff else diff
}

// Material changes between two reports for the same station, in
// plain English, in a stable order: SPECI, wind, visibility, CB, TS.
// Returns an empty list when nothing material changed (or when the
// previous report is missing, since there is nothing to compare).
fun compareMetarChanges(old: MetarSnapshot?, new: MetarSnapshot): List<String> {
    if (old == null) return emptyList()
    val out = ArrayList<String>()
    if (new.isSpeci && !old.isSpeci) {
        out.add("New SPECI special observation")
    }
    val oldDir = old.windDirDeg
    val newDir = new.windDirDeg
    if (oldDir != null && newDir != null && windShiftDeg(oldDir, newDir) >= WIND_SHIFT_DEG) {
        out.add("Wind shifted from ${oldDir} degrees to ${newDir} degrees")
    }
    val oldSpeed = old.windSpeedKt
    val newSpeed = new.windSpeedKt
    if (oldSpeed != null && newSpeed != null &&
        abs(newSpeed - oldSpeed) >= WIND_SPEED_CHANGE_KT
    ) {
        out.add("Wind changed from $oldSpeed kt to $newSpeed kt")
    }
    if (old.gustKt == null && new.gustKt != null) {
        out.add("Gusts now ${new.gustKt} kt")
    } else if (old.gustKt != null && new.gustKt != null &&
        abs(new.gustKt - old.gustKt) >= WIND_SPEED_CHANGE_KT
    ) {
        out.add("Gusts changed from ${old.gustKt} kt to ${new.gustKt} kt")
    }
    val oldVis = old.visibilityM
    val newVis = new.visibilityM
    if (oldVis != null && newVis != null && newVis < oldVis) {
        val crossed = VISIBILITY_THRESHOLDS_M.filter { oldVis >= it && newVis < it }
        if (crossed.isNotEmpty()) {
            val top = crossed.max()
            out.add(
                "Visibility dropped to ${newVis.toInt()} m, below ${top.toInt()} m"
            )
        }
    }
    if (!old.hasCb && new.hasCb) {
        out.add("Cumulonimbus (CB) now reported")
    }
    if (!old.hasTs && new.hasTs) {
        out.add("Thunderstorm (TS) now reported")
    }
    return out
}

const val WIND_SHIFT_DEG = 30
const val WIND_SPEED_CHANGE_KT = 10
val VISIBILITY_THRESHOLDS_M = listOf(10_000.0, 5_000.0, 3_000.0, 1_500.0, 1_000.0, 550.0, 200.0)
