package com.apexpredator.argus.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.AircraftInfo
import com.apexpredator.argus.data.DiaryFlight
import com.apexpredator.argus.data.DiaryStore
import com.apexpredator.argus.data.EnrichRepository
import com.apexpredator.argus.data.validateDiaryInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// View model for the flight diary (v2.6). Entries persist locally
// through DiaryStore; live matching itself is a pure function over
// the radar feed, applied in the screen, so this model only owns
// storage, form errors and on-demand enrichment (type and
// registration for a matched aircraft).
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DiaryStore(application)
    private val enrich = EnrichRepository()

    val entries: StateFlow<List<DiaryFlight>> =
        store.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    var formError by mutableStateOf<String?>(null)
        private set

    // Enrichment for matched aircraft, keyed by lowercase ICAO24.
    var enrichByHex by mutableStateOf<Map<String, AircraftInfo?>>(emptyMap())
        private set
    private val enrichLoading = mutableSetOf<String>()

    fun addFlight(airline: String, flightNumber: String, date: String, route: String?) {
        val error = validateDiaryInput(airline, flightNumber, date)
        if (error != null) {
            formError = error
            return
        }
        formError = null
        val flight = DiaryFlight(
            id = UUID.randomUUID().toString(),
            airline = airline.trim(),
            flightNumber = flightNumber.trim().uppercase(),
            date = date.trim(),
            route = route?.trim()?.ifEmpty { null },
        )
        viewModelScope.launch { store.add(flight) }
    }

    fun deleteFlight(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    fun clearFormError() {
        formError = null
    }

    fun loadEnrich(icao24: String) {
        val hex = icao24.trim().lowercase()
        if (hex in enrichByHex || hex in enrichLoading) return
        enrichLoading.add(hex)
        viewModelScope.launch {
            try {
                val info = enrich.aircraftInfo(icao24)
                enrichByHex = enrichByHex + (hex to info)
            } catch (e: Exception) {
                enrichByHex = enrichByHex + (hex to null)
            } finally {
                enrichLoading.remove(hex)
            }
        }
    }
}
