package com.apexpredator.argus.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.SkyMath
import com.apexpredator.argus.ui.SpotterViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlin.math.hypot
import kotlin.math.roundToInt

// The balcony feature: point the phone at an aircraft in the sky and
// Argus names it. The camera preview runs under a label layer fed by
// the phone's rotation sensor and live OpenSky positions around the
// user's GPS fix.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotterScreen(
    vm: SpotterViewModel,
    onOpenNearMe: () -> Unit,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var azimuthDeg by remember { mutableStateOf(0f) }
    var pitchDeg by remember { mutableStateOf(0f) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        cameraGranted = results[Manifest.permission.CAMERA] == true
        val loc = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.onPermissionResult(loc)
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted || !vm.hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        } else {
            vm.start()
        }
    }

    // Rotation vector gives the camera's true pointing direction in
    // any pose: the camera looks down the device -Z axis, and the
    // rotation matrix's third column is that axis in world coords
    // (x east, y north, z up).
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    DisposableEffect(sensorManager) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val r = FloatArray(9)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, event.values)
                val east = -r[2]
                val north = -r[5]
                val up = -r[8]
                azimuthDeg = (Math.toDegrees(
                    kotlin.math.atan2(east.toDouble(), north.toDouble())
                ).toFloat() + 360f) % 360f
                pitchDeg = Math.toDegrees(
                    kotlin.math.asin(up.toDouble().coerceIn(-1.0, 1.0))
                ).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spotter: name that aircraft") },
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
            if (!cameraGranted || !vm.hasLocationPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "The spotter needs the camera (to see the sky) and " +
                            "your location (to know which aircraft are above you).",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    ) { Text("Enable camera and location") }
                    OutlinedButton(onClick = onOpenNearMe) {
                        Text("Use the Near Me list instead")
                    }
                }
                return@Column
            }

            Text(
                "Aiming ${azimuthDeg.roundToInt()}° ${SkyMath.compassLabel(azimuthDeg.toDouble())}" +
                    " · up ${pitchDeg.roundToInt()}° · ${vm.nearby.size} aircraft within 80 km",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                CameraPreview()
                SpotterLabels(vm, azimuthDeg, pitchDeg) { vm.select(it) }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            val best = bestMatch(vm, azimuthDeg, pitchDeg)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (best != null) {
                        Text(
                            best.callsign ?: best.icao24.uppercase(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "${best.originCountry} · " +
                                (best.altitudeFt?.let { "${it.roundToInt()} ft" } ?: "ground") +
                                " · in your view",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexTextSecondary
                        )
                        Button(
                            onClick = { vm.select(best) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Full details") }
                    } else {
                        Text(
                            "Point the camera at an aircraft in the sky.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Its name, airline and route will appear here. " +
                                "Positions refresh every 45 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApexTextSecondary
                        )
                        OutlinedButton(
                            onClick = onOpenNearMe,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("See everything near me as a list") }
                    }
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
            SpotterDetailSheet(vm, selected)
        }
    }
}

@Composable
private fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    LaunchedEffect(previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
            )
        }, ContextCompat.getMainExecutor(context))
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun SpotterLabels(
    vm: SpotterViewModel,
    azimuthDeg: Float,
    pitchDeg: Float,
    onSelect: (AircraftState) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val hFov = if (landscape) 62f else 46f
    val vFov = if (landscape) 46f else 62f
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val best = bestMatch(vm, azimuthDeg, pitchDeg)
        for (near in vm.nearby) {
            val ac = near.aircraft
            if (ac.onGround || near.elevationDeg < -2.0) continue
            val (right, up) = SkyMath.viewOffsets(
                azimuthDeg.toDouble(), pitchDeg.toDouble(),
                near.bearingDeg, near.elevationDeg
            )
            val projected = SkyMath.projectToScreen(
                right, up, hFov.toDouble(), vFov.toDouble()
            ) ?: continue
            val isBest = best?.icao24 == ac.icao24
            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (projected.first * widthPx - 60).toInt(),
                            (projected.second * heightPx - 24).toInt()
                        )
                    }
                    .clickable { onSelect(ac) },
                border = if (isBest) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            ) {
                Text(
                    (ac.callsign ?: ac.icao24.uppercase()) +
                        if (ac.isUav) " · UAV" else "",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// The aircraft closest to where the phone is pointing, if it is close
// enough to count as "the one I am looking at".
private fun bestMatch(
    vm: SpotterViewModel,
    azimuthDeg: Float,
    pitchDeg: Float,
): AircraftState? {
    var best: AircraftState? = null
    var bestScore = Double.MAX_VALUE
    for (near in vm.nearby) {
        val ac = near.aircraft
        if (ac.onGround) continue
        val (right, up) = SkyMath.viewOffsets(
            azimuthDeg.toDouble(), pitchDeg.toDouble(),
            near.bearingDeg, near.elevationDeg
        )
        val score = hypot(right, up)
        if (score < bestScore) {
            bestScore = score
            best = ac
        }
    }
    return if (bestScore <= 14.0) best else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotterDetailSheet(vm: SpotterViewModel, ac: AircraftState) {
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
        Text(
            ac.originCountry + " · " + ac.icao24.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            color = ApexTextSecondary
        )
        info?.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Aircraft photo",
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (route?.origin != null && route.destination != null) {
            DetailRow(
                "Route",
                "${route.origin.iata ?: route.origin.icao} → " +
                    "${route.destination.iata ?: route.destination.icao}"
            )
            DetailRow("Airline", route.airlineName)
        }
        info?.let {
            DetailRow(
                "Aircraft",
                listOfNotNull(it.manufacturer, it.type).joinToString(" ").ifEmpty { null }
            )
            DetailRow("Registration", it.registration)
            DetailRow("Owner", it.owner)
        }
        DetailRow(
            "Altitude",
            ac.altitudeFt?.let { "${it.roundToInt()} ft" }
        )
        DetailRow(
            "Speed",
            ac.speedKt?.let { "${it.roundToInt()} kt" }
        )
        Row {
            Button(onClick = { vm.toggleWatch(ac) }) {
                Text(if (vm.isWatched(ac)) "★ Watched" else "☆ Watch aircraft")
            }
        }
    }
}
