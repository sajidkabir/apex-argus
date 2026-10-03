package com.apexpredator.argus.decoder

// Structured model for decoded METAR and TAF reports.
// Pure Kotlin: no Android dependencies, fully unit testable.

enum class SpeedUnit { KT, MPS, KMH }

data class Wind(
    val directionDeg: Int?,
    val variable: Boolean,
    val speed: Int,
    val gust: Int?,
    val unit: SpeedUnit
)

data class Visibility(
    // Visibility in meters. Null when the report gives no usable value.
    val meters: Double?,
    val raw: String,
    // True when the report states CAVOK (ceiling and visibility OK).
    val cavok: Boolean = false
) {
    val statuteMiles: Double?
        get() = meters?.div(1609.344)
}

data class SkyLayer(
    // FEW, SCT, BKN, OVC, VV, CLR, SKC, NSC, NCD
    val coverage: String,
    // Cloud base height in feet. Null for CLR/SKC/NSC/NCD.
    val heightFeet: Int?,
    // CB or TCU when present.
    val cloudType: String?
)

data class PresentWeather(
    val raw: String,
    val description: String
)

data class Conditions(
    val wind: Wind?,
    val visibility: Visibility?,
    val weather: List<PresentWeather>,
    val sky: List<SkyLayer>,
    val temperatureC: Int?,
    val dewpointC: Int?,
    val altimeterInHg: Double?,
    val qnhHpa: Int?
)

data class DecodedMetar(
    val raw: String,
    val station: String,
    val observationTimeUtc: String?,
    val automatic: Boolean,
    val corrected: Boolean,
    val conditions: Conditions,
    val remarks: String?
)

data class TafChangeGroup(
    // FM, TEMPO, BECMG, PROB
    val kind: String,
    // Probability percent for PROB groups, else null.
    val probability: Int?,
    val timeDescription: String,
    val conditions: Conditions
)

data class DecodedTaf(
    val raw: String,
    val station: String,
    val issueTimeUtc: String?,
    val validDescription: String?,
    val cancelled: Boolean,
    val amended: Boolean,
    val corrected: Boolean,
    val base: Conditions,
    val changes: List<TafChangeGroup>
)

enum class FlightCategory { VFR, MVFR, IFR, LIFR, UNKNOWN }
