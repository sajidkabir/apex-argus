package com.apexpredator.argus.decoder

import java.util.Locale

// Plain-English rendering of decoded reports, used by the results
// screen and the share sheet. No em dashes: plain hyphens only.

private fun Int.formatFeet(): String = "%,d".format(Locale.US, this)

private fun Double.formatMiles(): String {
    val rounded = (this * 10).toInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        "%d".format(Locale.US, rounded.toInt())
    } else {
        "%.1f".format(Locale.US, rounded)
    }
}

fun describeWind(wind: Wind?): String? {
    if (wind == null) return null
    val unitWord = when (wind.unit) {
        SpeedUnit.KT -> "knots"
        SpeedUnit.MPS -> "meters per second"
        SpeedUnit.KMH -> "km/h"
    }
    val gustPart = if (wind.gust != null) ", gusting ${wind.gust} $unitWord" else ""
    return when {
        wind.speed == 0 -> "Wind calm"
        wind.variable -> "Wind variable at ${wind.speed} $unitWord$gustPart"
        wind.directionDeg == null -> "Wind direction unknown, ${wind.speed} $unitWord$gustPart"
        else -> "Wind ${"%03d".format(Locale.US, wind.directionDeg)} degrees at ${wind.speed} $unitWord$gustPart"
    }
}

fun describeVisibility(vis: Visibility?): String? {
    if (vis == null) return null
    if (vis.cavok) return "Ceiling and visibility OK (CAVOK)"
    val sm = vis.statuteMiles ?: return "Visibility ${vis.raw}"
    return if (vis.meters != null && vis.raw.matches(Regex("^\\d{4}$"))) {
        "Visibility ${vis.meters.toInt().formatFeet()} meters (${sm.formatMiles()} statute miles)"
    } else {
        "Visibility ${sm.formatMiles()} statute miles"
    }
}

fun describeSkyLayer(layer: SkyLayer): String {
    val typeSuffix = when (layer.cloudType) {
        "CB" -> ", cumulonimbus"
        "TCU" -> ", towering cumulus"
        else -> ""
    }
    return when (layer.coverage) {
        "FEW" -> "Few clouds at ${layer.heightFeet?.formatFeet()} ft$typeSuffix"
        "SCT" -> "Scattered clouds at ${layer.heightFeet?.formatFeet()} ft$typeSuffix"
        "BKN" -> "Broken clouds at ${layer.heightFeet?.formatFeet()} ft$typeSuffix"
        "OVC" -> "Overcast at ${layer.heightFeet?.formatFeet()} ft$typeSuffix"
        "VV" -> if (layer.heightFeet != null) {
            "Vertical visibility ${layer.heightFeet.formatFeet()} ft (sky obscured)"
        } else {
            "Sky obscured, vertical visibility unknown"
        }
        "CLR", "SKC" -> "Sky clear"
        "NSC" -> "No significant cloud"
        "NCD" -> "No cloud detected"
        else -> "${layer.coverage} at ${layer.heightFeet?.formatFeet()} ft$typeSuffix"
    }
}

fun describeConditions(c: Conditions): List<String> {
    val lines = mutableListOf<String>()
    describeWind(c.wind)?.let { lines.add(it) }
    describeVisibility(c.visibility)?.let { lines.add(it) }
    if (c.weather.isNotEmpty()) {
        lines.add("Weather: " + c.weather.joinToString(", ") { it.description })
    }
    c.sky.forEach { lines.add(describeSkyLayer(it)) }
    if (c.temperatureC != null && c.dewpointC != null) {
        val spread = c.temperatureC - c.dewpointC
        lines.add("Temperature ${c.temperatureC} C, dewpoint ${c.dewpointC} C (spread $spread C)")
    } else if (c.temperatureC != null) {
        lines.add("Temperature ${c.temperatureC} C")
    }
    c.altimeterInHg?.let {
        lines.add("Altimeter ${"%.2f".format(Locale.US, it)} inches of mercury")
    }
    c.qnhHpa?.let { lines.add("QNH $it hPa") }
    return lines
}

fun describeMetar(m: DecodedMetar): List<String> {
    val lines = mutableListOf<String>()
    val header = buildString {
        append("Station ${m.station}")
        m.observationTimeUtc?.let { append(", observed $it") }
        if (m.automatic) append(" (automatic station)")
        if (m.corrected) append(" (corrected report)")
    }
    lines.add(header)
    lines.addAll(describeConditions(m.conditions))
    m.remarks?.let { lines.add("Remarks: $it") }
    return lines
}

fun describeTafChange(g: TafChangeGroup): List<String> {
    val lines = mutableListOf<String>()
    val title = when (g.kind) {
        "FM" -> "From ${g.timeDescription.removePrefix("from ")}"
        "TEMPO" -> "Temporarily (${g.timeDescription})"
        "BECMG" -> "Becoming (${g.timeDescription})"
        "PROB" -> "${g.probability}% probability (${g.timeDescription})"
        else -> "${g.kind} (${g.timeDescription})"
    }
    lines.add(title)
    lines.addAll(describeConditions(g.conditions).map { "  $it" })
    return lines
}

fun describeTaf(t: DecodedTaf): List<String> {
    val lines = mutableListOf<String>()
    val header = buildString {
        append("TAF for station ${t.station}")
        t.issueTimeUtc?.let { append(", issued $it") }
        t.validDescription?.let { append(", valid $it") }
        if (t.amended) append(" (amended)")
        if (t.corrected) append(" (corrected)")
    }
    lines.add(header)
    if (t.cancelled) {
        lines.add("This TAF is cancelled.")
        return lines
    }
    if (t.base != Conditions(null, null, emptyList(), emptyList(), null, null, null, null)) {
        lines.add("Prevailing conditions:")
        lines.addAll(describeConditions(t.base).map { "  $it" })
    }
    t.changes.forEach { g ->
        lines.addAll(describeTafChange(g))
    }
    return lines
}

// Flight category for a TAF is computed from its prevailing (base) conditions.
fun tafFlightCategory(t: DecodedTaf): FlightCategory = flightCategory(t.base)

fun shareText(title: String, lines: List<String>): String {
    return buildString {
        appendLine(title)
        appendLine()
        lines.forEach { appendLine(it) }
    }.trimEnd()
}
