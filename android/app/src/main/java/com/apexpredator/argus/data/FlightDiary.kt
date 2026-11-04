package com.apexpredator.argus.data

// The passenger flight diary (v2.6, the traveller release). A diary
// entry is a flight the user plans to take or has taken: airline plus
// flight number, a date, and an optional route note. Live tracking
// reuses the free OpenSky feed the radar already uses, matched on
// callsign, and the status is only ever what that free data can
// prove. That honesty matters: a flight that does not appear in live
// traffic is NOT_TRACKED, never silently relabelled as landed, because
// free coverage misses flights and absence proves nothing about a
// landing or a cancellation. Pure Kotlin, no Android imports, so
// matching and status derivation are unit-testable on the JVM.

enum class DiaryStatus {
    SCHEDULED,
    AIRBORNE,
    ON_GROUND,
    NOT_TRACKED,
}

data class DiaryFlight(
    val id: String,
    val airline: String,
    val flightNumber: String,
    // ISO date, YYYY-MM-DD, in the user's local calendar.
    val date: String,
    val route: String?,
) {
    // Display name: "BG 101", or just the full callsign when the
    // user typed it that way (for example "BG101" as the number).
    val displayName: String get() {
        val num = flightNumber.trim().uppercase()
        val air = airline.trim().uppercase()
        return if (air.isNotEmpty() && num.startsWith(air)) num else "$air $num".trim()
    }

    // The callsign to look for in live traffic.
    val callsign: String get() = buildCallsign(airline, flightNumber)
}

// Builds the callsign from what the user typed. If the flight number
// already carries letters (BG101), it is the callsign. If the airline
// field is a 2 or 3 letter code (BG) and the number is digits only,
// they join (BG + 101). An airline name (Biman) is not a code, so it
// is not forced into one: the number alone is matched by suffix.
fun buildCallsign(airline: String, flightNumber: String): String {
    val num = flightNumber.trim().uppercase().replace(" ", "")
    if (num.isEmpty()) return ""
    if (num.any { it.isLetter() }) return num
    val code = airline.trim().uppercase().replace(" ", "")
    return if (code.length in 2..3 && code.all { it.isLetter() }) code + num else num
}

// True when a live callsign is this diary flight. Exact match wins.
// A digits-only diary callsign also matches by suffix (an aircraft
// broadcasting ...101 for flight 101), because the user gave no
// airline code to check the prefix against. A coded callsign never
// matches on suffix alone: BG101 and EK101 are different flights.
fun matchesDiaryFlight(liveCallsign: String?, flight: DiaryFlight): Boolean {
    val live = liveCallsign?.trim()?.uppercase() ?: return false
    if (live.isEmpty()) return false
    val wanted = flight.callsign
    if (wanted.isEmpty()) return false
    if (live == wanted) return true
    if (wanted.all { it.isDigit() }) return live.endsWith(wanted)
    return false
}

// Finds the live aircraft for a diary flight, if any, in a traffic list.
fun findDiaryMatch(
    states: List<AircraftState>,
    flight: DiaryFlight,
): AircraftState? =
    states.firstOrNull { matchesDiaryFlight(it.callsign, flight) }

// Status from what free data can prove. todayIso is YYYY-MM-DD.
fun deriveDiaryStatus(
    flight: DiaryFlight,
    live: AircraftState?,
    todayIso: String,
): DiaryStatus {
    if (live != null) {
        return if (live.onGround) DiaryStatus.ON_GROUND else DiaryStatus.AIRBORNE
    }
    return if (flight.date > todayIso) DiaryStatus.SCHEDULED else DiaryStatus.NOT_TRACKED
}

fun diaryStatusLabel(status: DiaryStatus): String = when (status) {
    DiaryStatus.SCHEDULED -> "Scheduled"
    DiaryStatus.AIRBORNE -> "Airborne"
    DiaryStatus.ON_GROUND -> "On ground"
    DiaryStatus.NOT_TRACKED -> "Not tracked live"
}

fun diaryStatusNote(status: DiaryStatus): String = when (status) {
    DiaryStatus.SCHEDULED -> "Flight date is in the future. It will appear here once airborne and in free coverage."
    DiaryStatus.AIRBORNE -> "Live in free traffic right now, at the position shown."
    DiaryStatus.ON_GROUND -> "Last live sighting had this aircraft on the ground. That can mean landed, or not yet departed: Argus does not guess which."
    DiaryStatus.NOT_TRACKED -> "Not found in live traffic. Free coverage misses some flights, and a past flight may already have landed. Absence here proves neither a landing nor a cancellation."
}

// Input check for the add-flight form. Returns an error string, or
// null when the entry is usable.
fun validateDiaryInput(airline: String, flightNumber: String, date: String): String? {
    if (airline.isBlank()) return "Enter the airline (a code like BG, or the name)."
    if (flightNumber.isBlank()) return "Enter the flight number (for example 101, or BG101)."
    if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
        return "Enter the date as YYYY-MM-DD, for example 2026-11-06."
    }
    val month = date.substring(5, 7).toIntOrNull() ?: return "That date looks wrong."
    val day = date.substring(8, 10).toIntOrNull() ?: return "That date looks wrong."
    if (month !in 1..12 || day !in 1..31) return "That date looks wrong."
    return null
}

// Text encoding for one diary entry per line, fields separated by the
// unit separator (U+001F), the same convention as the logbook. Backslash,
// the separator and newlines are escaped, so route notes round-trip.
fun encodeDiaryFlight(f: DiaryFlight): String {
    fun esc(v: String?): String {
        if (v == null) return ""
        return v.replace("\\", "\\\\")
            .replace("\u001F", "\\u")
            .replace("\n", "\\n")
    }
    return listOf(
        esc(f.id), esc(f.airline), esc(f.flightNumber), esc(f.date), esc(f.route),
    ).joinToString("\u001F")
}

fun decodeDiaryFlight(line: String): DiaryFlight? {
    if (line.isBlank()) return null
    val fields = splitDiaryEscaped(line)
    if (fields.size != 5) return null
    if (fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) return null
    if (!fields[3].matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return null
    return DiaryFlight(
        id = fields[0],
        airline = fields[1],
        flightNumber = fields[2],
        date = fields[3],
        route = fields[4].ifEmpty { null },
    )
}

private fun splitDiaryEscaped(line: String): List<String> {
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
