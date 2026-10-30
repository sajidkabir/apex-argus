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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexpredator.argus.data.SkyMath
import com.apexpredator.argus.ui.NearbyAircraft
import com.apexpredator.argus.ui.SpotterViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlin.math.roundToInt

// Everything airborne near the user, nearest first, each with the
// balcony answers: how far, which direction, how high, doing what.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearMeScreen(
    vm: SpotterViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.start() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Near me") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val fix = vm.fix
            Text(
                when {
                    !vm.hasLocationPermission ->
                        "Location permission is off. Enable it to see traffic around you."
                    fix == null -> "Finding your position..."
                    else -> "Within 80 km of you · ${vm.nearby.size} aircraft · " +
                        "updated from live traffic"
                },
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary,
                modifier = Modifier.padding(16.dp)
            )
            vm.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            vm.localFeedError?.let {
                Text(
                    "Local feed: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.nearby) { near ->
                    NearbyRow(near) { vm.select(near.aircraft) }
                }
            }
        }
    }

    val selected = vm.selected
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.selected = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            // Reuse the spotter detail sheet via the radar sheet's
            // shared rows is overkill here; the row tap opens the same
            // enrichment sheet used by the spotter.
            SpotterDetailSheet(vm, selected)
        }
    }
}

@Composable
private fun NearbyRow(near: NearbyAircraft, onClick: () -> Unit) {
    val ac = near.aircraft
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    (ac.callsign ?: ac.icao24.uppercase()) +
                        when {
                            ac.isUav -> " · UAV"
                            ac.isRotorcraft -> " · Helicopter"
                            ac.isEmergency -> " · EMERGENCY"
                            else -> ""
                        } + if (ac.isLocal) " · LOCAL" else "",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "%.1f km".format(near.distanceKm),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                "${SkyMath.compassLabel(near.bearingDeg)} · " +
                    "${near.bearingDeg.roundToInt()}° · " +
                    (if (ac.onGround) {
                        "on the ground"
                    } else {
                        "${ac.altitudeFt?.roundToInt() ?: 0} ft · " +
                            "${near.elevationDeg.roundToInt()}° above your horizon"
                    }),
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            near.verdict?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
