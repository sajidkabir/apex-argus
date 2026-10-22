package com.apexpredator.argus.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexpredator.argus.data.SatPass
import com.apexpredator.argus.data.SkyMath
import com.apexpredator.argus.ui.OverheadSatellite
import com.apexpredator.argus.ui.SkyViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// The Sky tab: satellites above the user right now, and every
// worthwhile pass over their location in the next 24 hours, the
// "step onto the balcony at 19:42" list.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyScreen(vm: SkyViewModel) {
    LaunchedEffect(Unit) { vm.start() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sky: satellites and passes") },
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
                Text(
                    if (vm.usingDefaultLocation) {
                        "Showing the sky over Dhaka (location is off). " +
                            "Enable location for your exact sky."
                    } else {
                        "Your sky, from your GPS position."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (vm.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            item {
                Text(
                    "Overhead now (${vm.overhead.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!vm.isLoading && vm.overhead.isEmpty()) {
                item {
                    Text(
                        "No tracked satellites within 2,000 km right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                }
            }
            items(vm.overhead.take(15)) { entry ->
                OverheadRow(entry)
            }
            item {
                Text(
                    "Passes over you, next 24 hours (${vm.passes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    "Stations and bright satellites peaking at least 10° up. " +
                        "Times are local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            if (!vm.isLoading && vm.passes.isEmpty()) {
                item {
                    Text(
                        "No high passes in the next 24 hours. Check again tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                }
            }
            items(vm.passes) { pass ->
                PassRow(pass)
            }
        }
    }
}

@Composable
private fun OverheadRow(entry: OverheadSatellite) {
    val sat = entry.position
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column {
                Text(sat.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "NORAD ${sat.noradId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    "${sat.altitudeKm.roundToInt()} km up",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${entry.distanceKm.roundToInt()} km away",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
        }
    }
}

@Composable
private fun PassRow(pass: SatPass) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(pass.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "rises ${timeFmt.format(Date(pass.startMs))}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Peaks ${pass.maxElevationDeg.roundToInt()}° at " +
                    "${timeFmt.format(Date(pass.peakMs))} · " +
                    "rises ${SkyMath.compassLabel(pass.riseAzimuthDeg)}, " +
                    "sets ${SkyMath.compassLabel(pass.setAzimuthDeg)} · " +
                    "${pass.durationMin.roundToInt()} min pass",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
        }
    }
}
