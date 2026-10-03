package com.apexpredator.argus.decoder

// US National Weather Service flight categories, computed from
// ceiling (lowest BKN/OVC/VV layer) and visibility.

fun flightCategory(conditions: Conditions): FlightCategory {
    val visSm = conditions.visibility?.statuteMiles
    val ceilingFeet = conditions.sky
        .filter { it.coverage in setOf("BKN", "OVC", "VV") && it.heightFeet != null }
        .minOfOrNull { it.heightFeet!! }

    if (visSm == null && ceilingFeet == null) return FlightCategory.UNKNOWN

    val vis = visSm ?: Double.MAX_VALUE
    val ceiling = ceilingFeet ?: Int.MAX_VALUE

    return when {
        ceiling < 500 || vis < 1.0 -> FlightCategory.LIFR
        ceiling < 1000 || vis < 3.0 -> FlightCategory.IFR
        ceiling <= 3000 || vis <= 5.0 -> FlightCategory.MVFR
        else -> FlightCategory.VFR
    }
}

fun flightCategoryLabel(category: FlightCategory): String = when (category) {
    FlightCategory.VFR -> "VFR"
    FlightCategory.MVFR -> "MVFR"
    FlightCategory.IFR -> "IFR"
    FlightCategory.LIFR -> "LIFR"
    FlightCategory.UNKNOWN -> "Unknown"
}

fun flightCategoryExplanation(category: FlightCategory): String = when (category) {
    FlightCategory.VFR -> "Visual flight rules: ceiling above 3,000 ft and visibility above 5 miles"
    FlightCategory.MVFR -> "Marginal VFR: ceiling 1,000 to 3,000 ft or visibility 3 to 5 miles"
    FlightCategory.IFR -> "Instrument flight rules: ceiling 500 to below 1,000 ft or visibility 1 to below 3 miles"
    FlightCategory.LIFR -> "Low IFR: ceiling below 500 ft or visibility below 1 mile"
    FlightCategory.UNKNOWN -> "Not enough ceiling or visibility data to determine category"
}
