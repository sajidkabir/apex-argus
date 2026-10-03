package com.apexpredator.argus.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.HistoryEntry
import com.apexpredator.argus.data.HistoryStore
import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.data.WeatherRepository
import com.apexpredator.argus.decoder.MetarDecoder
import com.apexpredator.argus.decoder.TafDecoder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository()
    private val historyStore = HistoryStore(application)

    var icaoInput by mutableStateOf("")
    var liveKind by mutableStateOf(ReportKind.METAR)
    var pastedInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var result by mutableStateOf<DecodedResult?>(null)

    val historyEntries: StateFlow<List<HistoryEntry>> =
        historyStore.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

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
                errorMessage = e.message ?: "Something went wrong. Try again."
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
