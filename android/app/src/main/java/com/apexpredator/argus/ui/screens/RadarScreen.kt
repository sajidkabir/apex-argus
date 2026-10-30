package com.apexpredator.argus.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.rainViewerTileSource
import com.apexpredator.argus.ui.AircraftOverlay
import com.apexpredator.argus.ui.AirportOverlay
import com.apexpredator.argus.ui.AltBand
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.SatelliteOverlay
import com.apexpredator.argus.ui.SearchHit
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    vm: RadarViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    onDecodeMetar: (String) -> Unit = {},
    onOpenCam: (com.apexpredator.argus.data.PublicCam) -> Unit = {},
    onOpenMunicipalCam: (com.apexpredator.argus.data.MunicipalCam) -> Unit = {},
) {
    val context = LocalContext.current
    var panelOpen by remember { mutableStateOf(false) }
    var measureOn by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    val aircraftOverlay = remember {
        AircraftOverlay { vm.selectAircraft(it) }
    }
    val airportOverlay = remember {
        AirportOverlay { vm.selectAirport(it) }
    }
    val satelliteOverlay = remember {
        SatelliteOverlay { vm.selectedSatellite = it }
    }
    val cameraOverlay = remember {
        com.apexpredator.argus.ui.CameraOverlay(
            visible = { vm.showCameras },
            municipalCams = {
                com.apexpredator.argus.data.MunicipalCamMesh.cams.value
            },
            onCamTap = { onOpenCam(it) },
            onMunicipalTap = { onOpenMunicipalCam(it) }
        )
    }
    val routeLine = remember {
        Polyline().apply {
            outlinePaint.color = AndroidColor.parseColor("#7FD4FF")
            outlinePaint.strokeWidth = 4f
        }
    }
    val measureLine = remember {
        Polyline().apply {
            outlinePaint.color = AndroidColor.parseColor("#FFD54F")
            outlinePaint.strokeWidth = 4f
        }
    }
    val measureTapOverlay = remember {
        object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(
                e: android.view.MotionEvent,
                mapView: MapView,
            ): Boolean {
                if (!measureOn) return false
                val gp = mapView.projection.fromPixels(
                    e.x.toInt(), e.y.toInt()
                ) as GeoPoint
                measurePoints = (measurePoints + gp).takeLast(2)
                return true
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
            controller.setZoom(vm.center.zoom)
            controller.setCenter(GeoPoint(vm.center.lat, vm.center.lon))
            overlays.add(airportOverlay)
            overlays.add(routeLine)
            overlays.add(measureLine)
            overlays.add(aircraftOverlay)
            overlays.add(satelliteOverlay)
            overlays.add(cameraOverlay)
            overlays.add(measureTapOverlay)
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

    LaunchedEffect(Unit) { vm.refreshNow() }

    // Auto-refresh while visible, at the user's chosen cadence. The
    // anonymous OpenSky quota (400 credits/day, 1 credit per clamped
    // query) covers even the fastest setting for normal sessions.
    LaunchedEffect(vm.refreshSeconds) {
        while (true) {
            delay(vm.refreshSeconds * 1000L)
            vm.refreshNow()
        }
    }

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

    LaunchedEffect(
        vm.liveAircraft, vm.snapshots, vm.playbackIndex,
        vm.hideGround, vm.uavOnly, vm.altBand, vm.showAircraft,
        vm.watchedAircraft
    ) {
        aircraftOverlay.watchedHexes = vm.watchedAircraft
        aircraftOverlay.setAircraft(
            if (vm.showAircraft) vm.displayAircraft else emptyList()
        )
        mapView.invalidate()
    }

    LaunchedEffect(vm.satellites, vm.showSatellites) {
        satelliteOverlay.setSatellites(
            if (vm.showSatellites) vm.satellites else emptyList()
        )
        mapView.invalidate()
    }

    LaunchedEffect(vm.visibleAirports, vm.showAirports) {
        airportOverlay.setAirports(
            if (vm.showAirports) vm.visibleAirports else emptyList()
        )
        mapView.invalidate()
    }

    // Weather radar tiles (RainViewer), slipped under the markers.
    var weatherOverlay by remember { mutableStateOf<TilesOverlay?>(null) }
    LaunchedEffect(vm.showWeather, vm.weatherPath) {
        weatherOverlay?.let {
            mapView.overlays.remove(it)
            it.onDetach(mapView)
        }
        weatherOverlay = null
        val path = vm.weatherPath
        if (vm.showWeather && path != null) {
            val overlay = TilesOverlay(
                MapTileProviderBasic(context, rainViewerTileSource(path)),
                context
            ).apply {
                loadingBackgroundColor = AndroidColor.TRANSPARENT
                loadingLineColor = AndroidColor.TRANSPARENT
            }
            mapView.overlays.add(0, overlay)
            weatherOverlay = overlay
        }
        mapView.invalidate()
    }

    // Route line for the selected aircraft's flight.
    LaunchedEffect(vm.enrichRoute, vm.selected) {
        val r = vm.enrichRoute
        val ac = vm.selected
        val o = r?.origin
        val d = r?.destination
        if (r != null && ac != null && o?.lat != null && o.lon != null &&
            d?.lat != null && d.lon != null
        ) {
            routeLine.setPoints(
                listOf(
                    GeoPoint(o.lat, o.lon),
                    GeoPoint(ac.latitude, ac.longitude),
                    GeoPoint(d.lat, d.lon)
                )
            )
        } else {
            routeLine.setPoints(emptyList())
        }
        mapView.invalidate()
    }

    LaunchedEffect(measurePoints) {
        measureLine.setPoints(measurePoints)
        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Argus Radar") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
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
                    IconButton(onClick = { panelOpen = !panelOpen }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Layers and filters")
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
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.onSearchQueryChange(it) },
                label = { Text("Search airports, flights, satellites") },
                placeholder = { Text("Dhaka, Shahjalal, DAC, BA117, ISS...") },
                singleLine = true,
                trailingIcon = {
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (vm.searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(vm.searchResults) { hit ->
                            SearchHitRow(hit) { vm.onSearchHitSelected(hit) }
                        }
                    }
                }
            }

            if (panelOpen) {
                ControlPanel(vm)
            }

            val emergencies = vm.emergencies
            if (emergencies.isNotEmpty() && !vm.inPlayback) {
                val first = emergencies.first()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { vm.selectAircraft(first) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        "EMERGENCY: ${first.callsign ?: first.icao24.uppercase()} " +
                            "squawking ${first.squawk}" +
                            if (emergencies.size > 1) " (+${emergencies.size - 1} more)" else "",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            measureOn = !measureOn
                            if (!measureOn) measurePoints = emptyList()
                        }
                    ) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = "Measure distance"
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            if (vm.inPlayback) vm.exitPlayback() else vm.enterPlayback()
                        }
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Playback")
                    }
                }
                if (measureOn) {
                    val label = measureLabel(measurePoints, vm.metricUnits)
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Text(
                    "Data: OpenSky, CelesTrak, RainViewer, OurAirports · Map: © OpenStreetMap",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            if (vm.inPlayback) {
                PlaybackBar(vm)
            }

            val status = buildString {
                if (vm.inPlayback) {
                    append("PLAYBACK · ")
                    append("${vm.displayAircraft.size} aircraft in frame")
                } else {
                    append("${vm.liveAircraft.size} aircraft")
                    if (vm.localFeedCount > 0) {
                        append(" · ${vm.localFeedCount} local")
                    }
                    if (vm.uavCount > 0) append(" · ${vm.uavCount} UAV")
                    append(" · ${vm.satellites.size} satellites")
                    vm.lastUpdated?.let { append(" · $it") }
                }
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
            vm.localFeedError?.let { msg ->
                Text(
                    "Local feed: $msg",
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
            AircraftSheet(vm, selected)
        }
    }
    val selectedAirport = vm.selectedAirport
    if (selectedAirport != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.selectedAirport = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            AirportSheet(vm, selectedAirport, onDecodeMetar)
        }
    }
    val selectedSatellite = vm.selectedSatellite
    if (selectedSatellite != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.selectedSatellite = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            SatelliteSheet(selectedSatellite)
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    val (tag, title, subtitle) = when (hit) {
        is SearchHit.AirportHit -> Triple(
            "AIRPORT",
            "${hit.airport.ident} · ${hit.airport.name}",
            "${hit.airport.city}, ${hit.airport.country}" +
                (hit.airport.iata?.let { " · IATA $it" } ?: "")
        )
        is SearchHit.AircraftHit -> Triple(
            if (hit.aircraft.isUav) "DRONE" else "FLIGHT",
            hit.aircraft.callsign ?: hit.aircraft.icao24.uppercase(),
            "${hit.aircraft.originCountry} · " +
                (hit.aircraft.altitudeFt?.let { "${it.roundToInt()} ft" } ?: "on ground")
        )
        is SearchHit.SatelliteHit -> Triple(
            "SATELLITE",
            hit.satellite.name,
            "NORAD ${hit.satellite.noradId} · " +
                "${hit.satellite.altitudeKm.roundToInt()} km up"
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
        }
    }
}

@Composable
private fun ControlPanel(vm: RadarViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Layers", style = MaterialTheme.typography.titleMedium)
            LayerSwitch("Aircraft", vm.showAircraft) { vm.showAircraft = it }
            LayerSwitch("Satellites", vm.showSatellites) { vm.showSatellites = it }
            LayerSwitch("Airports", vm.showAirports) { vm.showAirports = it }
            LayerSwitch("Weather radar", vm.showWeather) { vm.showWeather = it }
            LayerSwitch("Public cameras", vm.showCameras) { vm.showCameras = it }
            Text("Legend", style = MaterialTheme.typography.titleMedium)
            LegendRow("▲", "Aeroplane (bigger = heavy, smaller = light)")
            LegendRow("◆", "Drone / UAV")
            LegendRow("H", "Helicopter (circled H on the map)")
            LegendRow("△", "Unknown type")
            LegendRow("✦", "Satellite")
            LegendRow("■", "Airport")
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.uavOnly,
                    onClick = { vm.uavOnly = !vm.uavOnly },
                    label = { Text("Drones / UAV only") }
                )
                FilterChip(
                    selected = vm.hideGround,
                    onClick = { vm.hideGround = !vm.hideGround },
                    label = { Text("Hide ground") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (band in AltBand.entries) {
                    FilterChip(
                        selected = vm.altBand == band,
                        onClick = { vm.altBand = band },
                        label = {
                            Text(
                                when (band) {
                                    AltBand.ALL -> "All altitudes"
                                    AltBand.LOW -> "Below 10k ft"
                                    AltBand.MID -> "10k-30k ft"
                                    AltBand.HIGH -> "Above 30k ft"
                                }
                            )
                        }
                    )
                }
            }
            Text("Refresh and units", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (seconds in listOf(60, 180, 300)) {
                    FilterChip(
                        selected = vm.refreshSeconds == seconds,
                        onClick = { vm.updateRefreshSeconds(seconds) },
                        label = { Text("${seconds / 60} min") }
                    )
                }
                FilterChip(
                    selected = vm.metricUnits,
                    onClick = { vm.updateMetricUnits(!vm.metricUnits) },
                    label = { Text(if (vm.metricUnits) "Metric" else "Aviation units") }
                )
            }
        }
    }
}

@Composable
private fun LegendRow(symbol: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LayerSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PlaybackBar(vm: RadarViewModel) {
    val snaps = vm.snapshots
    val idx = vm.playbackIndex ?: 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (snaps.isEmpty()) {
                        "No frames recorded yet. Keep the radar open and frames will collect."
                    } else {
                        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                        fmt.format(Date(snaps[idx].epochMs)) +
                            " · frame ${idx + 1} of ${snaps.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            if (snaps.size > 1) {
                Slider(
                    value = idx.toFloat(),
                    onValueChange = { vm.setPlaybackIndex(it.roundToInt()) },
                    valueRange = 0f..(snaps.size - 1).toFloat(),
                    steps = (snaps.size - 2).coerceAtLeast(0)
                )
            }
        }
    }
}

private fun measureLabel(points: List<GeoPoint>, metric: Boolean): String {
    if (points.isEmpty()) return "Tap two points on the map to measure"
    if (points.size == 1) return "First point set. Tap the second point."
    val a = points[0]
    val b = points[1]
    val km = GeoMath.haversineKm(a.latitude, a.longitude, b.latitude, b.longitude)
    val bearing = GeoMath.bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude)
    return if (metric) {
        "%.1f km · bearing %03.0f°".format(Locale.US, km, bearing)
    } else {
        "%.1f nm · bearing %03.0f°".format(Locale.US, GeoMath.kmToNm(km), bearing)
    }
}
