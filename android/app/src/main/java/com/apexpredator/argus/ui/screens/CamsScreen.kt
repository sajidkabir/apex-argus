package com.apexpredator.argus.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apexpredator.argus.data.CamKind
import com.apexpredator.argus.data.EARTHCAM_DIRECTORY_URL
import com.apexpredator.argus.data.PUBLIC_CAMS
import com.apexpredator.argus.data.SKYLINE_DIRECTORY_URL
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// The Cams tab, rebuilt so it cannot hit a paywall: the map is
// Argus's own osmdroid map with markers for verified free public
// cameras. Zoom anywhere, tap a marker, and its live feed plays in
// the app. No Windy, no subscription, no account. The Directory mode
// is SkylineWebcams' full free directory (2,500+ cameras); the
// Aviation mode is the airport spotting list.
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CamsScreen(
    onBack: () -> Unit,
    initialCenter: Pair<Double, Double>? = null,
    initialCamIndex: Int = -1,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0 map, 1 directory, 2 aviation
    // A camera handed in from the radar map (or an airport page) opens
    // straight into its player.
    val initialCam = PUBLIC_CAMS.getOrNull(initialCamIndex)
    var playerTitle by remember { mutableStateOf(initialCam?.name) }
    var playerUrl by remember { mutableStateOf(initialCam?.url) }
    val playing = playerUrl != null

    BackHandler(enabled = playing) {
        playerTitle = null
        playerUrl = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playerTitle ?: "Live cams") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (playing) {
                                playerTitle = null
                                playerUrl = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
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
            if (playing) {
                CamPlayer(playerUrl!!)
                return@Column
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) { Text("Map") }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) { Text("Directory") }
                SegmentedButton(
                    selected = mode == 2,
                    onClick = { mode = 2 },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) { Text("Aviation") }
            }

            when (mode) {
                0 -> {
                    Text(
                        "Free public cameras on Argus's own map. Zoom, " +
                            "tap a camera, watch it live. No account, no subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    CamMap(initialCenter) { cam ->
                        playerTitle = cam.name
                        playerUrl = cam.url
                    }
                }
                1 -> {
                    Text(
                        "SkylineWebcams' full free directory: 2,500+ live " +
                            "cameras worldwide, no account needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    CamPlayer(SKYLINE_DIRECTORY_URL)
                    Row(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                playerTitle = "EarthCam"
                                playerUrl = EARTHCAM_DIRECTORY_URL
                            }
                        ) { Text("Open EarthCam instead") }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            PUBLIC_CAMS.filter { it.kind == CamKind.AVIATION }
                        ) { cam ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerTitle = cam.name
                                        playerUrl = cam.url
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        cam.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        cam.place,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CamMap(
    initialCenter: Pair<Double, Double>?,
    onSelect: (com.apexpredator.argus.data.PublicCam) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 2.0
                maxZoomLevel = 18.0
                if (initialCenter != null) {
                    controller.setZoom(10.0)
                    controller.setCenter(
                        GeoPoint(initialCenter.first, initialCenter.second)
                    )
                } else {
                    controller.setZoom(2.5)
                    controller.setCenter(GeoPoint(20.0, 10.0))
                }
                for (cam in PUBLIC_CAMS) {
                    val marker = Marker(this).apply {
                        position = GeoPoint(cam.lat, cam.lon)
                        title = cam.name
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onSelect(cam)
                            true
                        }
                    }
                    marker.icon = android.graphics.drawable.BitmapDrawable(
                        context.resources,
                        com.apexpredator.argus.ui.cameraMarkerBitmap()
                    )
                    overlays.add(marker)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    )
}

@Composable
private fun CamPlayer(url: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        modifier = Modifier.fillMaxSize()
    )
}

