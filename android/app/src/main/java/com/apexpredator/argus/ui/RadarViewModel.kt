package com.apexpredator.argus.ui

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.data.OpenSkyRepository
import com.apexpredator.argus.data.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

data class MapCenter(val lat: Double, val lon: Double, val zoom: Double)

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val opensky = OpenSkyRepository()
    private val weather = WeatherRepository()

    var icaoInput by mutableStateOf("")
    var center by mutableStateOf(MapCenter(23.8103, 90.4125, 9.0))
        private set
    // One-shot map move requests (e.g. after an ICAO lookup). The screen
    // consumes them and animates the map, then clears via consumeCenterRequest.
    var centerRequest by mutableStateOf<MapCenter?>(null)
        private set
    var aircraft by mutableStateOf<List<AircraftState>>(emptyList())
        private set
    var selected by mutableStateOf<AircraftState?>(null)
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var lastUpdated by mutableStateOf<String?>(null)
        private set

    // Latest viewport bounding box, kept so auto-refresh re-queries the
    // same area the user is looking at.
    private var lastBbox: BoundingBox? = null
    private var fetchJob: Job? = null
    private var debounceJob: Job? = null

    fun consumeCenterRequest() {
        centerRequest = null
    }

    // Called when the user pans or zooms. Refresh is debounced so a long
    // drag fires one query, keeping the anonymous OpenSky credit budget low.
    fun onViewportChanged(bbox: BoundingBox, centerLat: Double, centerLon: Double) {
        lastBbox = bbox
        center = MapCenter(centerLat, centerLon, center.zoom)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(VIEWPORT_DEBOUNCE_MS)
            refresh(bbox)
        }
    }

    fun onZoomChanged(zoom: Double) {
        center = center.copy(zoom = zoom)
    }

    fun refreshNow() {
        val bbox = lastBbox ?: defaultBbox(center) ?: return
        refresh(bbox)
    }

    fun centerOnIcao() {
        val icao = icaoInput.trim().uppercase()
        if (!icao.matches(Regex("^[A-Z0-9]{4}$"))) {
            errorMessage = "Enter a 4-letter ICAO code, e.g. KJFK"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val (lat, lon) = weather.fetchStationCoords(icao)
                val target = MapCenter(lat, lon, 10.0)
                center = target
                centerRequest = target
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not find that station."
            } finally {
                isLoading = false
            }
        }
    }

    private fun refresh(bbox: BoundingBox) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                aircraft = opensky.fetchStates(bbox)
                // Keep the selection if that aircraft is still visible.
                selected?.let { sel ->
                    selected = aircraft.firstOrNull { it.icao24 == sel.icao24 }
                }
                lastUpdated = "Updated " +
                    DateFormat.getTimeFormat(getApplication()).format(Date())
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not load flight data."
            } finally {
                isLoading = false
            }
        }
    }

    private fun defaultBbox(c: MapCenter): BoundingBox? {
        // Rough degrees-per-zoom estimate around the center; the repository
        // clamps this to the 5x5 degree credit budget anyway.
        val half = 2.5
        return BoundingBox(c.lat - half, c.lon - half, c.lat + half, c.lon + half)
    }

    companion object {
        private const val VIEWPORT_DEBOUNCE_MS = 1_500L
    }
}
