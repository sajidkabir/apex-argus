package com.apexpredator.argus.ui

import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.decoder.DecodedMetar
import com.apexpredator.argus.decoder.DecodedTaf
import com.apexpredator.argus.decoder.FlightCategory
import com.apexpredator.argus.decoder.describeMetar
import com.apexpredator.argus.decoder.describeTaf
import com.apexpredator.argus.decoder.flightCategory
import com.apexpredator.argus.decoder.shareText
import com.apexpredator.argus.decoder.tafFlightCategory

// UI-friendly wrapper around a decoded report.

sealed interface DecodedResult {
    val raw: String
    val station: String
    val kind: ReportKind
    val category: FlightCategory
    val lines: List<String>
    val shareTitle: String
}

data class MetarResult(val decoded: DecodedMetar) : DecodedResult {
    override val raw: String get() = decoded.raw
    override val station: String get() = decoded.station
    override val kind: ReportKind get() = ReportKind.METAR
    override val category: FlightCategory get() = flightCategory(decoded.conditions)
    override val lines: List<String> get() = describeMetar(decoded)
    override val shareTitle: String get() = "METAR ${decoded.station} (Apex Argus)"
}

data class TafResult(val decoded: DecodedTaf) : DecodedResult {
    override val raw: String get() = decoded.raw
    override val station: String get() = decoded.station
    override val kind: ReportKind get() = ReportKind.TAF
    override val category: FlightCategory get() = tafFlightCategory(decoded)
    override val lines: List<String> get() = describeTaf(decoded)
    override val shareTitle: String get() = "TAF ${decoded.station} (Apex Argus)"
}

fun DecodedResult.shareBody(): String = shareText(shareTitle, lines)
