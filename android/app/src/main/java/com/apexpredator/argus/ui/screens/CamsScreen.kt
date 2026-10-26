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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.apexpredator.argus.data.CamKind
import com.apexpredator.argus.data.EARTHCAM_DIRECTORY_URL
import com.apexpredator.argus.data.MeshStatus
import com.apexpredator.argus.data.MunicipalCam
import com.apexpredator.argus.data.MunicipalCamMesh
import com.apexpredator.argus.data.PUBLIC_CAMS
import com.apexpredator.argus.data.SKYLINE_DIRECTORY_URL
import com.apexpredator.argus.ui.CameraOverlay
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

// The Cams tab: Argus's own camera map, now with the godseyeview
// mesh. Blue pins are the bundled tourist webcams; orange pins are
// live municipal CCTV (Delaware DOT, Caltrans District 4), fetched
// from the cities themselves with no key, account or subscription.
// Zoomed out the mesh clusters into count badges; zoom in (or tap
// a badge) and it splits into real cameras. Tap a camera for its
// live snapshot or video stream. No Windy anywhere in this path.
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

    // The live mesh, shared with the radar camera layer.
    val meshCams by MunicipalCamMesh.cams.collectAsState()
    val meshStatus by MunicipalCamMesh.status.collectAsState()
    val radarMeshCam by MunicipalCamMesh.selected.collectAsState()
    var meshCam by remember { mutableStateOf<MunicipalCam?>(null) }
    LaunchedEffect(Unit) { MunicipalCamMesh.refresh() }
    LaunchedEffect(radarMeshCam) {
        if (radarMeshCam != null) meshCam = radarMeshCam
    }

    var jumpTarget by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    var jumpNonce by remember { mutableStateOf(0) }

    BackHandler(enabled = playing || meshCam != null) {
        if (meshCam != null) {
            meshCam = null
            MunicipalCamMesh.selected.value = null
        } else {
            playerTitle = null
            playerUrl = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meshCam?.name ?: playerTitle ?: "Live cams") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (meshCam != null) {
                                meshCam = null
                                MunicipalCamMesh.selected.value = null
                            } else if (playing) {
                                playerTitle = null
                                playerUrl = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            if (playing || meshCam != null) Icons.Filled.Close
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
                        meshStatusLine(meshStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 4.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                jumpTarget = Triple(39.0, -75.5, 8.0)
                                jumpNonce++
                            },
                            label = { Text("Delaware") }
                        )
                        AssistChip(
                            onClick = {
                                jumpTarget = Triple(37.7, -122.3, 9.0)
                                jumpNonce++
                            },
                            label = { Text("Bay Area") }
                        )
                        AssistChip(
                            onClick = {
                                jumpTarget = Triple(51.05, -114.07, 10.0)
                                jumpNonce++
                            },
                            label = { Text("Calgary") }
                        )
                        AssistChip(
                            onClick = {
                                jumpTarget = Triple(20.0, 10.0, 2.5)
                                jumpNonce++
                            },
                            label = { Text("World") }
                        )
                        OutlinedButton(
                            onClick = { MunicipalCamMesh.refresh() }
                        ) { Text("Refresh mesh") }
                    }
                    CamMap(
                        initialCenter = initialCenter,
                        jumpTarget = jumpTarget,
                        jumpNonce = jumpNonce,
                        meshCams = meshCams,
                        onTouristSelect = { cam ->
                            playerTitle = cam.name
                            playerUrl = cam.url
                        },
                        onMeshSelect = { meshCam = it }
                    )
                    Text(
                        "Feeds: Delaware DOT, Caltrans District 4, " +
                            "City of Calgary (Open Government Licence). " +
                            "Public traffic cameras, shown with credit " +
                            "to their operators. Argus records nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
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

        if (meshCam != null) {
            MeshCamSheet(
                cam = meshCam!!,
                onDismiss = {
                    meshCam = null
                    MunicipalCamMesh.selected.value = null
                }
            )
        }
    }
}

private fun meshStatusLine(status: MeshStatus): String = when (status) {
    MeshStatus.Idle -> "Loading the live camera mesh…"
    MeshStatus.Loading -> "Loading the live camera mesh…"
    is MeshStatus.Ready -> {
        val per = status.perNetwork.entries.joinToString(", ") {
            "${it.key} ${it.value}"
        }
        "Mesh live: ${status.total} cameras ($per). Orange pins are " +
            "city traffic cameras, blue pins are tourist webcams. " +
            "Tap any pin, or a count badge to zoom in."
    }
    is MeshStatus.Partial -> {
        val per = status.perNetwork.entries.joinToString(", ") {
            "${it.key} ${it.value}"
        }
        "Mesh partial: ${status.total} cameras ($per). " +
            "These networks did not answer: ${status.failed.joinToString(", ")}. " +
            "Tap Refresh mesh to retry."
    }
    is MeshStatus.Failed -> "Mesh failed: ${status.message}"
}

@Composable
private fun CamMap(
    initialCenter: Pair<Double, Double>?,
    jumpTarget: Triple<Double, Double, Double>?,
    jumpNonce: Int,
    meshCams: List<MunicipalCam>,
    onTouristSelect: (com.apexpredator.argus.data.PublicCam) -> Unit,
    onMeshSelect: (MunicipalCam) -> Unit,
) {
    val context = LocalContext.current
    var appliedNonce by remember { mutableStateOf(-1) }
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
                overlays.add(
                    CameraOverlay(
                        visible = { true },
                        // Read the flow directly, not the composition-time
                        // list: the overlay is built once in the factory,
                        // and a captured list would freeze at empty.
                        municipalCams = { MunicipalCamMesh.cams.value },
                        onCamTap = onTouristSelect,
                        onMunicipalTap = onMeshSelect
                    )
                )
            }
        },
        update = { mapView ->
            if (jumpNonce != appliedNonce) {
                appliedNonce = jumpNonce
                jumpTarget?.let {
                    mapView.controller.setZoom(it.third)
                    mapView.controller.setCenter(GeoPoint(it.first, it.second))
                }
            }
            // The overlay reads the mesh list on every draw, so an
            // invalidate is all a fresh fetch needs to appear.
            mapView.invalidate()
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    )
}

// A live municipal camera: HLS video when the network publishes a
// stream, otherwise its snapshot image, refreshed on demand and
// every 60 seconds while open. One stream at a time, which keeps
// Caltrans' fair-use rule (no bulk streaming) intact.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeshCamSheet(cam: MunicipalCam, onDismiss: () -> Unit) {
    var refreshNonce by remember(cam) { mutableStateOf(0) }
    if (cam.imageUrl != null && cam.videoUrl == null) {
        LaunchedEffect(cam) {
            while (true) {
                delay(60_000)
                refreshNonce++
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(cam.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${cam.network} · ${cam.city}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            when {
                cam.videoUrl != null -> HlsPlayer(cam.videoUrl)
                cam.imageUrl != null -> {
                    AsyncImage(
                        model = "${cam.imageUrl}?t=$refreshNonce",
                        contentDescription = cam.name,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { refreshNonce++ }
                    ) { Text("Refresh snapshot") }
                }
                else -> Text(
                    "This camera publishes no live feed right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            Text(
                "Live feed by ${cam.network}. Argus records nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
        }
    }
}

@Composable
private fun HlsPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxWidth()
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
