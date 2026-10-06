package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.ui.AircraftOverlay
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

private const val AUTO_REFRESH_MS = 180_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(vm: RadarViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current

    val overlay = remember { AircraftOverlay { vm.selected = it } }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
            controller.setZoom(vm.center.zoom)
            controller.setCenter(GeoPoint(vm.center.lat, vm.center.lon))
            overlays.add(overlay)
        }
    }

    fun currentBbox(): BoundingBox {
        val bb = mapView.boundingBox
        return BoundingBox(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    DisposableEffect(mapView) {
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val c = mapView.mapCenter
                vm.onViewportChanged(currentBbox(), c.latitude, c.longitude)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                vm.onZoomChanged(mapView.zoomLevelDouble)
                val c = mapView.mapCenter
                vm.onViewportChanged(currentBbox(), c.latitude, c.longitude)
                return false
            }
        })
        mapView.onResume()
        onDispose { mapView.onDetach() }
    }

    // Initial load.
    LaunchedEffect(Unit) { vm.refreshNow() }

    // Auto-refresh while the screen is visible. The anonymous OpenSky
    // quota (400 credits/day, 1 credit per query here) comfortably covers
    // a 3-minute cadence for normal sessions.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_MS)
            vm.refreshNow()
        }
    }

    // One-shot camera moves, e.g. after an ICAO lookup.
    val centerRequest = vm.centerRequest
    LaunchedEffect(centerRequest) {
        centerRequest?.let { target ->
            mapView.controller.animateTo(GeoPoint(target.lat, target.lon))
            mapView.controller.setZoom(target.zoom)
            vm.onZoomChanged(target.zoom)
            vm.consumeCenterRequest()
            vm.onViewportChanged(currentBbox(), target.lat, target.lon)
        }
    }

    LaunchedEffect(vm.aircraft) {
        overlay.setAircraft(vm.aircraft)
        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live radar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (vm.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { vm.refreshNow() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.icaoInput,
                    onValueChange = { vm.icaoInput = it.uppercase().take(4) },
                    label = { Text("Center on ICAO") },
                    placeholder = { Text("VGHS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { vm.centerOnIcao() },
                    enabled = !vm.isLoading
                ) { Text("Go") }
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    "Flight data: OpenSky Network · Map: © OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            val status = buildString {
                append("${vm.aircraft.size} aircraft")
                vm.lastUpdated?.let { append(" · $it") }
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            vm.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }

    val selected = vm.selected
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.selected = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            AircraftDetails(selected)
        }
    }
}

@Composable
private fun AircraftDetails(ac: com.apexpredator.argus.data.AircraftState) {
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
        Text(
            "${ac.originCountry} · ${ac.icao24.uppercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = ApexTextSecondary
        )
        DetailRow("Altitude", ac.altitudeFt?.let { "${it.roundToInt()} ft" })
        DetailRow("Speed", ac.speedKt?.let { "${it.roundToInt()} kt" })
        DetailRow("Heading", ac.trueTrackDeg?.let { "${it.roundToInt()}°" })
        DetailRow(
            "Vertical rate",
            ac.verticalRateFpm?.let {
                val dir = if (it > 50) "▲ " else if (it < -50) "▼ " else ""
                "$dir${it.roundToInt()} fpm"
            }
        )
        DetailRow("Squawk", ac.squawk)
        DetailRow("On ground", if (ac.onGround) "Yes" else "No")
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ApexTextSecondary)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
