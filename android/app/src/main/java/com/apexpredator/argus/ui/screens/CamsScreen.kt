package com.apexpredator.argus.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apexpredator.argus.data.LIVE_CAMS
import com.apexpredator.argus.ui.theme.ApexTextSecondary

private const val GLOBAL_CAM_MAP_URL = "https://www.windy.com/webcams"

// The Cams tab: a world map of public cameras. The map mode loads the
// public Windy webcams map, tens of thousands of operator-published
// cameras worldwide; pan and zoom anywhere on Earth, tap a camera and
// watch its live feed in place. The aviation list jumps the same
// viewer to famous spotting cameras and streams.
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CamsScreen(onBack: () -> Unit) {
    var mapMode by remember { mutableStateOf(true) }
    var feedUrl by remember { mutableStateOf(GLOBAL_CAM_MAP_URL) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live cams") },
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
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = mapMode,
                    onClick = {
                        mapMode = true
                        feedUrl = GLOBAL_CAM_MAP_URL
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("World map") }
                SegmentedButton(
                    selected = !mapMode,
                    onClick = { mapMode = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Aviation cams") }
            }
            if (mapMode) {
                Text(
                    "Public cameras worldwide. Zoom anywhere, tap a " +
                        "camera, watch it live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = WebViewClient()
                            webView = this
                            loadUrl(feedUrl)
                        }
                    },
                    update = { view ->
                        if (view.url != feedUrl) view.loadUrl(feedUrl)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LIVE_CAMS) { cam ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    feedUrl = cam.url
                                    mapMode = true
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
                                Text(
                                    cam.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
