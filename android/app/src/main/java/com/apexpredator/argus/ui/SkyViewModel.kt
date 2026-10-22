package com.apexpredator.argus.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.GeoFix
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.LocationRepository
import com.apexpredator.argus.data.SatPass
import com.apexpredator.argus.data.SatellitePosition
import com.apexpredator.argus.data.SatelliteRepository
import kotlinx.coroutines.launch
import java.util.Date

data class OverheadSatellite(val position: SatellitePosition, val distanceKm: Double)

// Brain of the Sky tab: what is above the user right now, and which
// satellites will pass over in the next 24 hours.
class SkyViewModel(application: Application) : AndroidViewModel(application) {

    private val satelliteRepo = SatelliteRepository(application)
    private val locationRepo = LocationRepository(application)

    var fix by mutableStateOf<GeoFix?>(null)
        private set
    var overhead by mutableStateOf<List<OverheadSatellite>>(emptyList())
        private set
    var passes by mutableStateOf<List<SatPass>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var usingDefaultLocation by mutableStateOf(false)
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        val located = locationRepo.lastKnown()
        if (located != null) {
            fix = located
        } else {
            // Dhaka: the user's home sky when no fix is available.
            fix = GeoFix(23.8103, 90.4125, 20.0, null, System.currentTimeMillis())
            usingDefaultLocation = true
        }
        viewModelScope.launch {
            try {
                satelliteRepo.load()
                refresh()
            } finally {
                isLoading = false
            }
        }
        viewModelScope.launch {
            // Positions advance every 20 seconds; passes are computed
            // once, they barely move within a day.
            while (true) {
                kotlinx.coroutines.delay(20_000)
                refreshOverhead()
            }
        }
    }

    private suspend fun refresh() {
        val f = fix ?: return
        refreshOverhead()
        passes = satelliteRepo.passesOver(f.lat, f.lon, hoursAhead = 24)
    }

    private fun refreshOverhead() {
        val f = fix ?: return
        overhead = satelliteRepo.positionsAt(Date())
            .map {
                it to GeoMath.haversineKm(f.lat, f.lon, it.lat, it.lon)
            }
            .filter { it.second <= OVERHEAD_RADIUS_KM }
            .sortedBy { it.second }
            .map { OverheadSatellite(it.first, it.second) }
    }

    companion object {
        private const val OVERHEAD_RADIUS_KM = 2_000.0
    }
}
