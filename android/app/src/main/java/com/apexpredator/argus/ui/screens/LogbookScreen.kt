package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexpredator.argus.data.Sighting
import com.apexpredator.argus.data.logbookStats
import com.apexpredator.argus.ui.LogbookViewModel
import com.apexpredator.argus.ui.components.EmptyState
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The sighting logbook: every aircraft identified from the radar,
// Spotter or an airport sheet, stored on this phone only, with
// counts and a delete/clear option. Opened from the Tools screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(vm: LogbookViewModel, onBack: () -> Unit) {
    val entries by vm.entries.collectAsState()
    val stats = logbookStats(entries)
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sighting logbook") },
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
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${stats.total} sightings · " +
                                "${stats.uniqueAircraft} aircraft · " +
                                "${stats.uniqueAirlines} airlines",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Stored on this phone only. Identifying an " +
                                "aircraft from Radar, Spotter or an airport " +
                                "sheet adds it here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApexTextSecondary
                        )
                        if (entries.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onBack) { Text("Back to Tools") }
                                OutlinedButton(onClick = { confirmClear = true }) {
                                    Text("Clear all")
                                }
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No sightings yet",
                        hint = "Tap an aircraft on the radar or identify " +
                            "one with Spotter, and it will appear here."
                    )
                }
            }
            items(entries) { entry ->
                SightingRow(entry, onDelete = { vm.delete(entry) })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the whole logbook?") },
            text = { Text("This deletes every sighting on this phone. It cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAll()
                        confirmClear = false
                    }
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep them") }
            }
        )
    }
}

@Composable
private fun SightingRow(entry: Sighting, onDelete: () -> Unit) {
    val timeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    timeFmt.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            val detail = listOfNotNull(
                entry.aircraftType,
                entry.airline,
                entry.airportContext?.let { "near $it" },
                "via ${entry.source}",
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            Text(
                entry.icao24.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
