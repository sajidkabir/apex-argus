package com.apexpredator.argus.ui

import android.app.Application
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.BuildConfig
import com.apexpredator.argus.data.AlertScheduler
import com.apexpredator.argus.data.HistoryEntry
import com.apexpredator.argus.data.HistoryStore
import com.apexpredator.argus.data.PackWeatherEntry
import com.apexpredator.argus.data.PrefsStore
import com.apexpredator.argus.data.RegionPackStore
import com.apexpredator.argus.data.RegionPackSummary
import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.data.SatelliteRepository
import com.apexpredator.argus.data.formatBytes
import com.apexpredator.argus.data.isPackStale
import com.apexpredator.argus.data.packAgeText
import com.apexpredator.argus.data.parsePackAirports
import com.apexpredator.argus.data.UpdateInfo
import com.apexpredator.argus.data.WeatherRepository
import com.apexpredator.argus.data.fetchUpdateInfo
import com.apexpredator.argus.decoder.MetarDecoder
import com.apexpredator.argus.decoder.TafDecoder
import com.apexpredator.argus.decoder.describeConditions
import com.apexpredator.argus.decoder.flightCategory
import com.apexpredator.argus.decoder.flightCategoryLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data class Downloaded(val apk: File) : UpdateUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository()
    private val historyStore = HistoryStore(application)
    private val prefs = PrefsStore(application)
    private val packStore = RegionPackStore(application)
    private val satelliteRepo = SatelliteRepository(application)

    var icaoInput by mutableStateOf("")
    var liveKind by mutableStateOf(ReportKind.METAR)
    var pastedInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var result by mutableStateOf<DecodedResult?>(null)

    // In-app updates. The github flavor downloads and installs the APK
    // itself; the play flavor just links to the Play Store listing, since
    // Play policy forbids self-updating a Play-distributed app.
    var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set
    val isPlayFlavor: Boolean = BuildConfig.FLAVOR == "play"

    val historyEntries: StateFlow<List<HistoryEntry>> =
        historyStore.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    // Watched airports, mirrored from DataStore: the briefing card
    // covers these, falling back to VGHS when none are watched.
    var watchedAirports by mutableStateOf<Set<String>>(emptySet())
        private set

    // Balcony alert toggles (v2.3), mirrored from DataStore.
    var alertSatPass by mutableStateOf(true)
        private set
    var alertHeavy by mutableStateOf(true)
        private set
    var alertEmergency by mutableStateOf(true)
        private set

    // Morning briefing: one plain-English line per watched airport.
    var briefingMetars by mutableStateOf<List<String>>(emptyList())
        private set
    var briefingLoading by mutableStateOf(false)
        private set

    // Offline region pack (v2.4): downloaded TLE plus METAR/TAF for
    // chosen airports, stored on the phone. Airports themselves are
    // already bundled, so the pack is what makes a region usable
    // with zero connectivity.
    var packInput by mutableStateOf("")
    var packSummary by mutableStateOf<RegionPackSummary?>(null)
        private set
    var packStatus by mutableStateOf<String?>(null)
        private set
    var packBusy by mutableStateOf(false)
        private set

    fun packSizeText(): String =
        packSummary?.let { formatBytes(it.totalBytes) } ?: "No pack saved"

    fun packAgeLine(): String? =
        packSummary?.let {
            val age = packAgeText(it.createdMs, System.currentTimeMillis())
            val stale = if (isPackStale(it.createdMs, System.currentTimeMillis())) {
                ". Over a week old: refresh it before relying on it offline."
            } else {
                ""
            }
            "Saved $age for ${it.airports.joinToString(", ")}$stale"
        }

    init {
        refreshPackSummary()
        viewModelScope.launch {
            prefs.watchedAirports.collect { watchedAirports = it }
        }
        viewModelScope.launch {
            prefs.alertSatPass.collect { alertSatPass = it }
        }
        viewModelScope.launch {
            prefs.alertHeavy.collect { alertHeavy = it }
        }
        viewModelScope.launch {
            prefs.alertEmergency.collect { alertEmergency = it }
        }
    }

    fun updateAlertSatPass(on: Boolean) {
        viewModelScope.launch {
            prefs.setAlertSatPass(on)
            if (on) AlertScheduler.schedule(getApplication())
        }
    }

    fun updateAlertHeavy(on: Boolean) {
        viewModelScope.launch {
            prefs.setAlertHeavy(on)
            if (on) AlertScheduler.schedule(getApplication())
        }
    }

    fun updateAlertEmergency(on: Boolean) {
        viewModelScope.launch {
            prefs.setAlertEmergency(on)
            if (on) AlertScheduler.schedule(getApplication())
        }
    }

    // ---- Offline region pack (v2.4) ----

    fun refreshPackSummary() {
        viewModelScope.launch {
            packSummary = packStore.loadSummary()
        }
    }

    fun downloadRegionPack() {
        if (packBusy) return
        val airports = parsePackAirports(packInput)
            .ifEmpty { watchedAirports.ifEmpty { setOf("VGHS") }.toList() }
        packBusy = true
        packStatus = "Downloading pack for ${airports.joinToString(", ")}..."
        viewModelScope.launch {
            try {
                // Refresh the TLE cache first so the pack carries
                // current elements, not whatever happens to be cached.
                try {
                    satelliteRepo.load()
                } catch (e: Exception) {
                    // A missing TLE cache still leaves a useful pack:
                    // the weather entries below are the core data.
                }
                val tleText = withContext(Dispatchers.IO) {
                    packStore.tleCacheText()
                }
                val entries = ArrayList<PackWeatherEntry>()
                var failures = 0
                for (icao in airports) {
                    for (kind in ReportKind.values()) {
                        try {
                            val raw = repository.fetchRaw(icao, kind)
                            entries.add(
                                PackWeatherEntry(
                                    icao = icao,
                                    kind = kind.name,
                                    raw = raw,
                                    fetchedMs = System.currentTimeMillis(),
                                )
                            )
                        } catch (e: Exception) {
                            failures++
                        }
                    }
                }
                if (entries.isEmpty()) {
                    packStatus =
                        "Pack download failed: no METAR or TAF could " +
                            "be fetched for those airports. Check your " +
                            "connection and the ICAO codes."
                    return@launch
                }
                val summary = packStore.savePack(airports, tleText, entries)
                packSummary = summary
                packStatus =
                    "Pack saved: ${airports.size} airport(s), " +
                        "${entries.size} weather reports, " +
                        "${formatBytes(summary.totalBytes)}." +
                        if (tleText.isEmpty()) {
                            " No satellite TLE was cached yet: open " +
                                "the Sky tab once while online, then " +
                                "download again for the satellite layer."
                        } else {
                            ""
                        } +
                        if (failures > 0) {
                            " $failures report(s) could not be fetched " +
                                "and are missing from the pack."
                        } else {
                            ""
                        }
            } finally {
                packBusy = false
            }
        }
    }

    fun deleteRegionPack() {
        viewModelScope.launch {
            if (packStore.deletePack()) {
                packSummary = null
                packStatus = "Offline pack deleted."
            } else {
                packStatus = "No offline pack to delete."
            }
        }
    }

    // Fetches a short plain-English METAR line for each watched
    // airport (VGHS when none are watched). Failures are skipped
    // silently: a missing line beats a broken card.
    fun refreshBriefing() {
        if (briefingLoading) return
        briefingLoading = true
        viewModelScope.launch {
            try {
                val stations = watchedAirports.ifEmpty { setOf("VGHS") }.take(3)
                val lines = ArrayList<String>()
                for (station in stations) {
                    try {
                        val raw = repository.fetchRaw(station, ReportKind.METAR)
                        val decoded = MetarDecoder.decode(raw)
                        val category = flightCategoryLabel(
                            flightCategory(decoded.conditions)
                        )
                        val bits = describeConditions(decoded.conditions)
                            .filter {
                                it.startsWith("Wind") || it.startsWith("Visibility")
                            }
                            .take(2)
                            .joinToString("; ")
                        lines.add(
                            if (bits.isEmpty()) "$station - $category"
                            else "$station - $category: $bits"
                        )
                    } catch (e: Exception) {
                        // Skip this station; the card shows the rest.
                    }
                }
                briefingMetars = lines
            } finally {
                briefingLoading = false
            }
        }
    }

    fun fetchLive() {
        val icao = icaoInput.trim().uppercase()
        if (!icao.matches(Regex("^[A-Z0-9]{4}$"))) {
            errorMessage = "Enter a 4-letter ICAO code, e.g. KJFK"
            return
        }
        val kind = liveKind
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val raw = repository.fetchRaw(icao, kind)
                val decoded = decodeRaw(raw, kind)
                result = decoded
                viewModelScope.launch {
                    historyStore.add(raw, kind, decoded.station)
                }
            } catch (e: Exception) {
                // v2.4 offline fallback: with no connection, a saved
                // region pack report still decodes, labelled as such
                // instead of failing outright.
                val packed = try {
                    packStore.readWeather(icao, kind)
                } catch (pe: Exception) {
                    null
                }
                if (packed != null) {
                    try {
                        result = decodeRaw(packed, kind)
                        errorMessage =
                            "Live fetch failed. Showing the saved " +
                                "offline pack report for $icao."
                    } catch (de: Exception) {
                        errorMessage = e.message ?: "Something went wrong. Try again."
                    }
                } else {
                    errorMessage = e.message ?: "Something went wrong. Try again."
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun decodePasted() {
        val raw = pastedInput.trim()
        if (raw.isEmpty()) {
            errorMessage = "Paste a METAR or TAF report first."
            return
        }
        errorMessage = null
        try {
            val kind = detectKind(raw)
            val decoded = decodeRaw(raw, kind)
            result = decoded
            viewModelScope.launch {
                historyStore.add(raw, kind, decoded.station)
            }
        } catch (e: IllegalArgumentException) {
            errorMessage = e.message ?: "Could not decode that report."
        }
    }

    fun openHistory(entry: HistoryEntry) {
        errorMessage = null
        try {
            result = decodeRaw(entry.raw, entry.kind)
        } catch (e: IllegalArgumentException) {
            errorMessage = e.message ?: "Could not decode that report."
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    fun backToHome() {
        result = null
        errorMessage = null
    }

    fun checkForUpdates() {
        if (updateState != UpdateUiState.Idle) return
        viewModelScope.launch {
            updateState = UpdateUiState.Checking
            try {
                val info = fetchUpdateInfo()
                updateState = if (info.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateUiState.Available(info)
                } else {
                    UpdateUiState.Idle
                }
            } catch (e: Exception) {
                // Update checks are best-effort; stay silent on failure.
                updateState = UpdateUiState.Idle
            }
        }
    }

    fun openPlayListing() {
        val app = getApplication<Application>()
        val pkg = app.packageName
        val market = Intent(
            Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            app.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // github flavor only: downloads the release APK, then hands it to the
    // system installer. Android always shows its own confirmation dialog;
    // there is no silent install.
    fun downloadAndInstallUpdate() {
        val info = (updateState as? UpdateUiState.Available)?.info ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                updateState = UpdateUiState.Downloading(0)
                val dir = app.getExternalFilesDir("updates")
                if (dir == null) {
                    updateState = UpdateUiState.Available(info)
                    return@launch
                }
                dir.mkdirs()
                val apk = File(dir, "apex-argus-update.apk")
                if (apk.exists()) apk.delete()
                val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
                    setTitle("Apex Argus ${info.versionName}")
                    setDescription("Downloading update")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationUri(Uri.fromFile(apk))
                }
                val dm = app.getSystemService(DownloadManager::class.java)
                val downloadId = dm.enqueue(request)
                while (true) {
                    delay(500)
                    dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                        if (!c.moveToFirst()) {
                            updateState = UpdateUiState.Available(info)
                            return@launch
                        }
                        val status = c.getInt(
                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                installApk(apk)
                                return@launch
                            }
                            DownloadManager.STATUS_FAILED -> {
                                updateState = UpdateUiState.Available(info)
                                return@launch
                            }
                            else -> {
                                val total = c.getLong(
                                    c.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                    )
                                )
                                val done = c.getLong(
                                    c.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                    )
                                )
                                if (total > 0) {
                                    updateState = UpdateUiState.Downloading(
                                        (done * 100 / total).toInt().coerceIn(0, 100)
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                updateState = UpdateUiState.Idle
            }
        }
    }

    // github flavor only. If the user has not yet allowed this app to
    // install unknown apps, they are sent to Settings first; the update
    // banner then offers the install again.
    fun installDownloadedUpdate() {
        val apk = (updateState as? UpdateUiState.Downloaded)?.apk ?: return
        installApk(apk)
    }

    private fun installApk(apk: File) {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            updateState = UpdateUiState.Downloaded(apk)
            app.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", apk
        )
        app.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        updateState = UpdateUiState.Idle
    }

    private fun decodeRaw(raw: String, kind: ReportKind): DecodedResult {
        return when (kind) {
            ReportKind.METAR -> MetarResult(MetarDecoder.decode(raw))
            ReportKind.TAF -> TafResult(TafDecoder.decode(raw))
        }
    }

    private fun detectKind(raw: String): ReportKind {
        val upper = raw.trim().uppercase()
        return if (upper.startsWith("TAF") || upper.contains(" TEMPO ") ||
            upper.contains(" BECMG ") || upper.contains(" FM")
        ) {
            ReportKind.TAF
        } else {
            ReportKind.METAR
        }
    }
}
