package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.Airport
import com.apexpredator.argus.data.AirportDb
import com.apexpredator.argus.data.SatellitePosition
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.util.Locale
import kotlin.math.roundToInt

// Detail sheets for the three entity types on the fused map. This is
// the Palantir pattern in miniature: every entity links to its related
// entities (aircraft to its route airports, airport to its traffic),
// so the user pivots through the picture instead of dead-ending.

@Composable
fun AircraftSheet(vm: RadarViewModel, ac: AircraftState) {
    val info = vm.enrichInfo
    val route = vm.enrichRoute
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            ac.callsign ?: ac.icao24.uppercase(),
            style = MaterialTheme.typography.headlineSmall
        )
        val subtitle = buildString {
            route?.airlineName?.let { append(it).append(" · ") }
            append(ac.originCountry)
            append(" · ")
            append(ac.icao24.uppercase())
            if (ac.isUav) append(" · UAV / drone")
            if (ac.isRotorcraft) append(" · Rotorcraft")
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ApexTextSecondary)

        if (vm.enrichLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                Text(
                    "  Looking up aircraft and route...",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
        }

        info?.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Aircraft photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            info.photographer?.let { p ->
                Text(
                    "Photo © $p via Planespotters",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextSecondary
                )
            }
        }

        if (route?.origin != null && route.destination != null) {
            val o = route.origin
            val d = route.destination
            Text(
                "${o?.iata ?: o?.icao ?: "?"} → ${d?.iata ?: d?.icao ?: "?"}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${o?.name ?: ""} (${o?.city ?: ""}) to ${d?.name ?: ""} (${d?.city ?: ""})",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                o?.icao?.let { icao ->
                    OutlinedButton(onClick = { vm.openAirportByIdent(icao) }) {
                        Text("Origin airport")
                    }
                }
                d?.icao?.let { icao ->
                    OutlinedButton(onClick = { vm.openAirportByIdent(icao) }) {
                        Text("Destination")
                    }
                }
            }
        }

        info?.let {
            DetailRow(
                "Aircraft",
                listOfNotNull(it.manufacturer, it.type).joinToString(" ").ifEmpty { null }
            )
            DetailRow("Registration", it.registration)
            DetailRow("Owner", it.owner)
        }
        DetailRow("Altitude", formatAltitude(ac, vm.metricUnits))
        DetailRow("Speed", formatSpeed(ac, vm.metricUnits))
        DetailRow("Heading", ac.trueTrackDeg?.let { "${it.roundToInt()}°" })
        DetailRow(
            "Vertical rate",
            ac.verticalRateFpm?.let {
                val dir = if (it > 50) "▲ " else if (it < -50) "▼ " else ""
                "$dir${it.roundToInt()} fpm"
            }
        )
        DetailRow(
            "Squawk",
            ac.squawk?.let { if (ac.isEmergency) "$it (EMERGENCY)" else it }
        )
        DetailRow("Position", "%.4f, %.4f".format(Locale.US, ac.latitude, ac.longitude))
        DetailRow(
            "Source",
            if (ac.isLocal) "Local receiver (your ADS-B feed)" else "OpenSky"
        )
        DetailRow("On ground", if (ac.onGround) "Yes" else "No")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.toggleWatchAircraft(ac) }) {
                Text(if (vm.isWatched(ac)) "★ Watched" else "☆ Watch aircraft")
            }
            OutlinedButton(
                onClick = { vm.centerOn(ac.latitude, ac.longitude, 9.0) }
            ) { Text("Center") }
        }
    }
}

@Composable
fun AirportSheet(
    vm: RadarViewModel,
    ap: Airport,
    onDecodeMetar: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(ap.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${ap.codeLabel} · ${ap.city}, ${ap.country}",
            style = MaterialTheme.typography.bodyMedium,
            color = ApexTextSecondary
        )
        DetailRow("Elevation", ap.elevationFt?.let { "$it ft" })
        DetailRow(
            "Type",
            ap.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
        )
        DetailRow(
            "Coordinates",
            "%.4f, %.4f".format(Locale.US, ap.lat, ap.lon)
        )

        val runways = AirportDb.runwaysFor(ap.ident)
        if (runways.isNotEmpty()) {
            Text("Runways", style = MaterialTheme.typography.titleMedium)
            for (rw in runways.take(6)) {
                DetailRow(
                    rw.label,
                    listOfNotNull(
                        rw.lengthFt?.let { "$it ft" },
                        rw.surface
                    ).joinToString(" · ").ifEmpty { null }
                )
            }
        }

        vm.airportMetar?.let { metar ->
            Text("Latest METAR", style = MaterialTheme.typography.titleMedium)
            Text(metar, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onDecodeMetar(ap.ident) }) {
                Text("Decode in Argus")
            }
        }
        vm.airportTaf?.let { taf ->
            Text("Latest TAF", style = MaterialTheme.typography.titleMedium)
            Text(taf, style = MaterialTheme.typography.bodySmall)
        }

        if (vm.airportArrivals.isNotEmpty() || vm.airportDepartures.isNotEmpty()) {
            Text("Movements board", style = MaterialTheme.typography.titleMedium)
            Text(
                "From live position trends; fills in as the radar refreshes.",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            for ((traffic, distKm) in vm.airportArrivals) {
                DetailRow(
                    "▼ ARR ${traffic.callsign ?: traffic.icao24.uppercase()}",
                    "${distKm.roundToInt()} km · " +
                        (traffic.altitudeFt?.let { "${it.roundToInt()} ft" } ?: "")
                )
            }
            for ((traffic, distKm) in vm.airportDepartures) {
                DetailRow(
                    "▲ DEP ${traffic.callsign ?: traffic.icao24.uppercase()}",
                    "${distKm.roundToInt()} km · " +
                        (traffic.altitudeFt?.let { "${it.roundToInt()} ft" } ?: "")
                )
            }
        }

        if (vm.airportTraffic.isNotEmpty()) {
            Text(
                "Traffic within 100 km",
                style = MaterialTheme.typography.titleMedium
            )
            for ((traffic, distKm) in vm.airportTraffic) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectAircraft(traffic) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        traffic.callsign ?: traffic.icao24.uppercase(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${distKm.roundToInt()} km · " +
                            (traffic.altitudeFt?.let { "${it.roundToInt()} ft" } ?: "ground"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexTextSecondary
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.toggleWatchAirport(ap) }) {
                Text(if (vm.isWatched(ap)) "★ Watched" else "☆ Watch airport")
            }
            OutlinedButton(onClick = { vm.centerOn(ap.lat, ap.lon, 11.0) }) {
                Text("Center")
            }
        }
    }
}

@Composable
fun SatelliteSheet(sat: SatellitePosition) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(sat.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "NORAD ${sat.noradId} · ${groupLabel(sat.group)}",
            style = MaterialTheme.typography.bodyMedium,
            color = ApexTextSecondary
        )
        DetailRow("Altitude", "%.0f km".format(Locale.US, sat.altitudeKm))
        DetailRow(
            "Velocity",
            sat.velocityKmS?.let { "%.2f km/s".format(Locale.US, it) }
        )
        DetailRow(
            "Position",
            "%.3f, %.3f".format(Locale.US, sat.lat, sat.lon)
        )
        Text(
            "Position computed on this device from CelesTrak orbital " +
                "elements (SGP4), refreshed daily.",
            style = MaterialTheme.typography.bodySmall,
            color = ApexTextSecondary
        )
    }
}

private fun groupLabel(group: String): String = when (group) {
    "stations" -> "Space station"
    "visual" -> "Bright satellite"
    "weather" -> "Weather satellite"
    "gps-ops" -> "Navigation (GPS)"
    "science" -> "Science satellite"
    else -> "Satellite"
}

@Composable
fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ApexTextSecondary)
        Text(
            value ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatAltitude(ac: AircraftState, metric: Boolean): String? {
    if (ac.onGround) return "On ground"
    return if (metric) {
        ac.baroAltitudeM?.let { "%,d m".format(Locale.US, it.roundToInt()) }
    } else {
        ac.altitudeFt?.let { "%,d ft".format(Locale.US, it.roundToInt()) }
    }
}

private fun formatSpeed(ac: AircraftState, metric: Boolean): String? {
    val v = ac.velocityMs ?: return null
    return if (metric) {
        "${(v * 3.6).roundToInt()} km/h"
    } else {
        "${ac.speedKt?.roundToInt()} kt"
    }
}
