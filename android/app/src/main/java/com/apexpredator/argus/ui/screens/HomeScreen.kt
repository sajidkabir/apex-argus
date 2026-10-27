package com.apexpredator.argus.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexpredator.argus.data.AlertLogic
import com.apexpredator.argus.data.AlertScheduler
import com.apexpredator.argus.data.PassWindow
import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.data.bestPassText
import com.apexpredator.argus.data.buildBriefingLines
import com.apexpredator.argus.data.trafficSummaryText
import com.apexpredator.argus.ui.MainViewModel
import com.apexpredator.argus.ui.RadarViewModel
import com.apexpredator.argus.ui.SkyViewModel
import com.apexpredator.argus.ui.UpdateUiState
import com.apexpredator.argus.ui.theme.ApexTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenSpotter: () -> Unit = {},
    onOpenNearMe: () -> Unit = {},
    onOpenSky: () -> Unit = {},
    onOpenAirports: () -> Unit = {},
    onOpenCams: () -> Unit = {},
    onOpenLogbook: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        vm.checkForUpdates()
        vm.refreshBriefing()
    }
    // Activity-scoped view models shared with the other tabs, so the
    // briefing card reads the same radar feed and satellite passes.
    val activity = LocalContext.current as ComponentActivity
    val radarVm: RadarViewModel = viewModel(viewModelStoreOwner = activity)
    val skyVm: SkyViewModel = viewModel(viewModelStoreOwner = activity)
    LaunchedEffect(Unit) { skyVm.start() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apex Argus") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpdateBanner(vm)

            MorningBriefingCard(vm = vm, skyVm = skyVm, radarVm = radarVm)

            AlertSettingsCard(vm = vm)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Live report",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fetch the latest report from aviationweather.gov",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.icaoInput,
                        onValueChange = { vm.icaoInput = it.uppercase().take(4) },
                        label = { Text("ICAO code") },
                        placeholder = { Text("KJFK") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ReportKind.values().forEach { kind ->
                            SegmentedButton(
                                selected = vm.liveKind == kind,
                                onClick = { vm.liveKind = kind },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = kind.ordinal,
                                    count = 2
                                ),
                                label = { Text(kind.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.fetchLive() },
                        enabled = !vm.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (vm.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Fetch live")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Decode a pasted report",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Works offline. Paste any raw METAR or TAF string.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.pastedInput,
                        onValueChange = { vm.pastedInput = it },
                        label = { Text("Raw METAR or TAF") },
                        placeholder = { Text("KJFK 021251Z 27012G22KT 10SM FEW030 18/16 A2992") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.decodePasted() },
                        enabled = !vm.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Decode")
                    }
                }
            }

            vm.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
                        "Live tools",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenRadar) { Text("Radar") }
                        OutlinedButton(onClick = onOpenSpotter) { Text("Spotter") }
                        OutlinedButton(onClick = onOpenNearMe) { Text("Near me") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenSky) { Text("Sky") }
                        OutlinedButton(onClick = onOpenAirports) { Text("Airports") }
                        OutlinedButton(onClick = onOpenCams) { Text("Live cams") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenHistory) { Text("Decode history") }
                        OutlinedButton(onClick = onOpenLogbook) { Text("Logbook") }
                    }
                }
            }
        }
    }
}

// v2.3 morning briefing: watched-airport weather in plain English,
// tonight's best satellite pass, and a one-line traffic summary, all
// from data the app already fetches. Nothing here starts a new feed
// except the satellite passes the Sky tab computes anyway.
@Composable
private fun MorningBriefingCard(
    vm: MainViewModel,
    skyVm: SkyViewModel,
    radarVm: RadarViewModel,
) {
    val best = AlertLogic.bestUpcomingPass(
        skyVm.passes.map {
            PassWindow(it.name, it.startMs, it.peakMs, it.maxElevationDeg)
        },
        System.currentTimeMillis(),
    )
    val lines = buildBriefingLines(
        metarSummaries = vm.briefingMetars,
        bestPassText = bestPassText(best),
        trafficText = trafficSummaryText(
            radarVm.liveAircraft.size,
            radarVm.emergencies.size,
        ),
    )
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
                "Morning briefing",
                style = MaterialTheme.typography.titleMedium
            )
            if (vm.briefingLoading && lines.isEmpty()) {
                Text(
                    "Loading watched airport weather...",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            } else if (lines.isEmpty()) {
                Text(
                    "No briefing yet. Watch an airport, then tap refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            } else {
                for (line in lines) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            OutlinedButton(onClick = { vm.refreshBriefing() }) {
                Text("Refresh briefing")
            }
        }
    }
}

// v2.3 balcony alerts: one toggle per alert type, plus the
// notification permission flow. Denial is graceful: the toggles stay
// usable, the worker simply posts nothing, and the settings button
// takes the user to the system page where they can re-enable it.
@Composable
private fun AlertSettingsCard(vm: MainViewModel) {
    val context = LocalContext.current
    fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(notificationsGranted()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        if (result) AlertScheduler.schedule(context)
    }
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
                "Balcony alerts",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Checked every 15 minutes in the background. " +
                    "Turn a type off and it never wakes you.",
                style = MaterialTheme.typography.bodySmall,
                color = ApexTextSecondary
            )
            AlertToggleRow(
                label = "Satellite pass starting soon",
                checked = vm.alertSatPass,
                onChange = { vm.updateAlertSatPass(it) },
            )
            AlertToggleRow(
                label = "Heavy aircraft on final approach",
                checked = vm.alertHeavy,
                onChange = { vm.updateAlertHeavy(it) },
            )
            AlertToggleRow(
                label = "Emergency squawk nearby",
                checked = vm.alertEmergency,
                onChange = { vm.updateAlertEmergency(it) },
            )
            if (!granted) {
                Text(
                    "Notifications are off for Apex Argus, so no " +
                        "alert can reach you until you allow them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    ) { Text("Allow notifications") }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) { Text("Open app settings") }
                }
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun UpdateBanner(vm: MainViewModel) {
    when (val s = vm.updateState) {
        is UpdateUiState.Available -> {
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
                        "Apex Argus ${s.info.versionName} is available",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "You are on ${currentVersionName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                    Button(
                        onClick = {
                            if (vm.isPlayFlavor) vm.openPlayListing()
                            else vm.downloadAndInstallUpdate()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (vm.isPlayFlavor) "Update on Google Play" else "Download and install")
                    }
                }
            }
        }
        is UpdateUiState.Downloading -> {
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
                        "Downloading update… ${s.percent}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        is UpdateUiState.Downloaded -> {
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
                        "Update downloaded",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Allow \"install unknown apps\" for Apex Argus when asked, then confirm the install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary
                    )
                    Button(
                        onClick = { vm.installDownloadedUpdate() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install now")
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun currentVersionName(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
