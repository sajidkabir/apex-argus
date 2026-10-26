package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apexpredator.argus.data.Airport
import com.apexpredator.argus.data.AirportDb
import com.apexpredator.argus.data.LocationRepository
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.components.EmptyState
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlin.math.roundToInt

// The Airports tab: a hub, not just a search box. Plain-English search
// over the bundled 9,882 airports, the watched list, the fields
// nearest to the user, and a public-webcams shortcut per airport.
// Tapping an airport opens it on the radar with its full board
// (runways, weather, traffic, arrivals and departures).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportsScreen(
    radarVm: RadarViewModel,
    onOpenRadar: () -> Unit,
    onOpenCams: (Airport) -> Unit = {},
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var dbReady by remember { mutableStateOf(AirportDb.isLoaded) }
    var nearest by remember { mutableStateOf<List<Pair<Airport, Double>>>(emptyList()) }

    LaunchedEffect(Unit) {
        AirportDb.ensureLoaded(context)
        dbReady = true
        val fix = LocationRepository(context).lastKnown()
        if (fix != null) {
            nearest = AirportDb.nearest(fix.lat, fix.lon, 6)
        }
    }

    fun open(ap: Airport) {
        radarVm.centerOn(ap.lat, ap.lon, 10.0)
        radarVm.selectAirport(ap)
        onOpenRadar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Airports") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search by name, city, IATA or ICAO") },
                    placeholder = { Text("Dhaka, Shahjalal, DAC, VGHS...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            if (query.trim().length >= 2) {
                val results = if (dbReady) AirportDb.search(query, 15) else emptyList()
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No matches",
                            hint = "Try a different name, city, IATA or ICAO code."
                        )
                    }
                }
                items(results) { ap ->
                    AirportRow(ap, null, { open(ap) }, onOpenCams)
                }
            } else {
                val watched = radarVm.watchedAirports
                    .mapNotNull { AirportDb.byIdent(it) }
                if (watched.isNotEmpty()) {
                    item {
                        Text(
                            "Watched airports",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(watched) { ap ->
                        AirportRow(ap, null, { open(ap) }, onOpenCams)
                    }
                }
                if (nearest.isNotEmpty()) {
                    item {
                        Text(
                            "Nearest to you",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(nearest) { (ap, distKm) ->
                        AirportRow(ap, distKm, { open(ap) }, onOpenCams)
                    }
                }
                if (watched.isEmpty() && nearest.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Nothing to show yet",
                            hint = "Search above, or star airports from the radar " +
                                "to build your watched list."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportRow(
    ap: Airport,
    distanceKm: Double?,
    onOpen: () -> Unit,
    onOpenCams: (Airport) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${ap.ident} · ${ap.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "${ap.city}, ${ap.country}" +
                        (ap.iata?.let { " · IATA $it" } ?: "") +
                        (distanceKm?.let { " · ${it.roundToInt()} km away" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            TextButton(onClick = { onOpenCams(ap) }) { Text("Cams") }
        }
    }
}
