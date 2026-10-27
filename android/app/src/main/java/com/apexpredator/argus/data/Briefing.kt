package com.apexpredator.argus.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Pure composition for the morning briefing card on the Tools screen.
// It only arranges strings the app already fetched (watched-airport
// METAR summaries, satellite passes, radar traffic), so the card never
// triggers a fetch of its own beyond what the Tools screen already does.

// One line per watched airport, e.g. "VGHS - MVFR: Wind 090 degrees
// at 8 knots, Visibility 6,000 meters". Built by MainViewModel from a
// decoded METAR; this file only assembles the card.
fun buildBriefingLines(
    metarSummaries: List<String>,
    bestPassText: String?,
    trafficText: String?,
): List<String> {
    val lines = ArrayList<String>()
    if (metarSummaries.isNotEmpty()) {
        lines.add("Watched airports:")
        lines.addAll(metarSummaries)
    }
    bestPassText?.let { lines.add("Tonight's best pass: $it") }
    trafficText?.let { lines.add(it) }
    return lines
}

// "ISS rises 19:42, peaks 54 deg" for the best upcoming pass.
fun bestPassText(pass: PassWindow?): String? {
    if (pass == null) return null
    val fmt = SimpleDateFormat("HH:mm", Locale.US)
    return "${pass.name} rises ${fmt.format(Date(pass.startMs))}, " +
        "peaks ${pass.maxElevationDeg.roundToInt()} deg"
}

// One line for nearby traffic from the radar feed the app already has.
fun trafficSummaryText(
    aircraftInView: Int,
    emergencyCount: Int,
): String {
    val base = if (aircraftInView == 0) {
        "No live radar feed yet - open Radar to load traffic."
    } else if (aircraftInView == 1) {
        "Radar is tracking 1 aircraft in view."
    } else {
        "Radar is tracking $aircraftInView aircraft in view."
    }
    return if (emergencyCount > 0) {
        "$base $emergencyCount squawking an emergency code."
    } else {
        base
    }
}
