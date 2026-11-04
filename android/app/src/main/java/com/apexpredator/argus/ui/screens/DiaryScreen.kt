package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.DiaryFlight
import com.apexpredator.argus.data.deriveDiaryStatus
import com.apexpredator.argus.data.diaryStatusLabel
import com.apexpredator.argus.data.diaryStatusNote
import com.apexpredator.argus.data.findDiaryMatch
import com.apexpredator.argus.ui.DiaryViewModel
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.components.EmptyState
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// The passenger flight diary (v2.6): flights the user plans to take
// or has taken, matched against live traffic by callsign. Opened
// from the Tools screen. The status line is honest about what free
// data can prove: a flight that does not appear is "Not tracked
// live", never silently called landed, because free coverage misses
// flights and absence proves nothing.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    vm: DiaryViewModel,
    radarVm: RadarViewModel,
    onBack: () -> Unit,
    onShowOnRadar: (AircraftState) -> Unit,
) {
    val entries by vm.entries.collectAsState()
    val liveAircraft = radarVm.liveAircraft
    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var airline by remember { mutableStateOf("") }
    var flightNumber by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flight diary") },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Add a flight",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Live tracking uses the same free OpenSky " +
                                "feed as the radar, matched on callsign. " +
                                "Not every flight appears: coverage gaps, " +
                                "callsign differences and ground time all " +
                                "hide a flight, so a flight not found is " +
                                "not proof it landed or was cancelled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApexTextSecondary
                        )
                        OutlinedTextField(
                            value = airline,
                            onValueChange = { airline = it },
                            label = { Text("Airline (code like BG, or name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = flightNumber,
                            onValueChange = { flightNumber = it.uppercase() },
                            label = { Text("Flight number (101, or BG101)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            placeholder = { Text("2026-11-06") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = route,
                            onValueChange = { route = it },
                            label = { Text("Route (optional, e.g. DAC to CGP)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        vm.formError?.let { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = {
                                vm.addFlight(airline, flightNumber, date, route)
                                if (vm.formError == null) {
                                    airline = ""
                                    flightNumber = ""
                                    date = ""
                                    route = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add flight") }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to Tools")
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No flights in your diary yet",
                        hint = "Add a flight above. When it is airborne " +
                            "and in free coverage, its live position " +
                            "appears here and on the radar."
                    )
                }
            }
            items(entries) { flight ->
                DiaryRow(
                    flight = flight,
                    live = findDiaryMatch(liveAircraft, flight),
                    todayIso = todayIso,
                    enrichText = vm.enrichByHex[findDiaryMatch(liveAircraft, flight)
                        ?.icao24?.lowercase()]?.let { info ->
                        listOfNotNull(info?.type, info?.registration)
                            .joinToString(" · ").ifEmpty { null }
                    },
                    onLoadEnrich = { hex -> vm.loadEnrich(hex) },
                    onShowOnRadar = onShowOnRadar,
                    onDelete = { vm.deleteFlight(flight.id) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DiaryRow(
    flight: DiaryFlight,
    live: AircraftState?,
    todayIso: String,
    enrichText: String?,
    onLoadEnrich: (String) -> Unit,
    onShowOnRadar: (AircraftState) -> Unit,
    onDelete: () -> Unit,
) {
    val status = deriveDiaryStatus(flight, live, todayIso)
    if (live != null) {
        LaunchedEffect(live.icao24) { onLoadEnrich(live.icao24) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                flight.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                flight.date + (flight.route?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            Text(
                diaryStatusLabel(status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                diaryStatusNote(status),
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            if (live != null) {
                val alt = live.altitudeFt?.roundToInt()?.let { "$it ft" } ?: "altitude unknown"
                val speed = live.speedKt?.roundToInt()?.let { "$it kt" } ?: "speed unknown"
                Text(
                    "Live: ${live.callsign ?: live.icao24.uppercase()}, " +
                        "${live.latitude}, ${live.longitude}, $alt, $speed",
                    style = MaterialTheme.typography.bodySmall
                )
                enrichText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onShowOnRadar(live) }) {
                        Text("Show on radar")
                    }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}
