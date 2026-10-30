package com.apexpredator.argus.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.AircraftInfo
import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.Airport
import com.apexpredator.argus.data.AirportDb
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.data.EnrichRepository
import com.apexpredator.argus.data.FlightRoute
import com.apexpredator.argus.data.GeoFix
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.LocalFeedRepository
import com.apexpredator.argus.data.LocalFeedResult
import com.apexpredator.argus.data.LocationRepository
import com.apexpredator.argus.data.LogbookStore
import com.apexpredator.argus.data.OpenSkyRepository
import com.apexpredator.argus.data.isValidLocalFeedUrl
import com.apexpredator.argus.data.mergeAircraft
import com.apexpredator.argus.data.normalizeLocalFeedUrl
import com.apexpredator.argus.data.Sighting
import com.apexpredator.argus.data.PrefsStore
import com.apexpredator.argus.data.SkyMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// One nearby aircraft with everything the balcony questions need:
// how far, which way, how high above the horizon, and a plain verdict.
data class NearbyAircraft(
    val aircraft: AircraftState,
    val distanceKm: Double,
    val bearingDeg: Double,
    val elevationDeg: Double,
    val nearestAirport: Airport?,
    val verdict: String?,
)

// Shared brain for the Spotter (camera identification) and Near Me
// screens: where am I, what is around me, and what is it doing.
class SpotterViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepo = LocationRepository(application)
    private val opensky = OpenSkyRepository()
    private val localFeedRepo = LocalFeedRepository()
    private val enrich = EnrichRepository()
    private val prefs = PrefsStore(application)
    private val logbookStore = LogbookStore(application)

    var fix by mutableStateOf<GeoFix?>(null)
        private set
    var hasLocationPermission by mutableStateOf(locationRepo.hasPermission())
        private set
    var aircraft by mutableStateOf<List<AircraftState>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    // v2.4: a dead local feed must not hide working OpenSky traffic
    // here either, so its problem is reported separately.
    var localFeedError by mutableStateOf<String?>(null)
        private set

    var selected by mutableStateOf<AircraftState?>(null)
    var enrichLoading by mutableStateOf(false)
        private set
    var enrichInfo by mutableStateOf<AircraftInfo?>(null)
        private set
    var enrichRoute by mutableStateOf<FlightRoute?>(null)
        private set
    var watchedAircraft by mutableStateOf<Set<String>>(emptySet())
        private set

    private var started = false
    private var fetchJob: Job? = null

    val nearby: List<NearbyAircraft>
        get() {
            val f = fix ?: return emptyList()
            val observerAlt = f.altitudeM ?: 20.0
            return aircraft.map { ac ->
                val distKm = GeoMath.haversineKm(f.lat, f.lon, ac.latitude, ac.longitude)
                val bearing = GeoMath.bearingDeg(f.lat, f.lon, ac.latitude, ac.longitude)
                val elev = SkyMath.elevationDeg(
                    observerAlt, ac.baroAltitudeM ?: 0.0, distKm
                )
                val nearest = if (AirportDb.isLoaded) {
                    AirportDb.nearest(ac.latitude, ac.longitude, 1).firstOrNull()?.first
                } else {
                    null
                }
                val verdict = nearest?.let { ap ->
                    val dAp = GeoMath.haversineKm(
                        ac.latitude, ac.longitude, ap.lat, ap.lon
                    )
                    SkyMath.movementVerdict(ac, ap, dAp)
                }
                NearbyAircraft(ac, distKm, bearing, elev, nearest, verdict)
            }.filter { it.distanceKm <= NEAR_RADIUS_KM }
                .sortedBy { it.distanceKm }
        }

    fun onPermissionResult(granted: Boolean) {
        hasLocationPermission = granted || locationRepo.hasPermission()
        if (hasLocationPermission) start()
    }

    fun start() {
        if (started) return
        if (!hasLocationPermission) return
        started = true
        locationRepo.lastKnown()?.let { fix = it }
        viewModelScope.launch {
            locationRepo.updates().collect { fix = it }
        }
        viewModelScope.launch {
            prefs.watchedAircraft.collect { watchedAircraft = it }
        }
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    fun refresh() {
        val f = fix ?: run {
            locationRepo.lastKnown()?.let { fix = it }
            return
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            isLoading = true
            try {
                val half = 1.3
                val bbox = BoundingBox(f.lat - half, f.lon - half, f.lat + half, f.lon + half)
                var openskyError: String? = null
                val openskyStates = try {
                    opensky.fetchStates(bbox)
                } catch (e: Exception) {
                    openskyError = e.message ?: "Could not load nearby traffic."
                    null
                }
                // v2.4 local feed: same merge as the radar, so Near
                // Me and Spotter see the user's own receiver traffic
                // marked as local, including when OpenSky is down.
                var localStates: List<AircraftState> = emptyList()
                localFeedError = null
                try {
                    val enabled = first(prefs.localFeedEnabled)
                    val url = normalizeLocalFeedUrl(first(prefs.localFeedUrl))
                    if (enabled && isValidLocalFeedUrl(url)) {
                        when (val result = localFeedRepo.fetch(url)) {
                            is LocalFeedResult.Ok -> localStates = result.aircraft
                            is LocalFeedResult.Error -> localFeedError = result.message
                        }
                    }
                } catch (e: Exception) {
                    localFeedError = "Local feed could not be read."
                }
                aircraft = when {
                    openskyStates != null -> mergeAircraft(openskyStates, localStates)
                    localStates.isNotEmpty() -> localStates
                    else -> emptyList()
                }
                errorMessage = if (openskyStates == null && localStates.isEmpty()) {
                    openskyError ?: "Could not load nearby traffic."
                } else {
                    null
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not load nearby traffic."
            } finally {
                isLoading = false
            }
        }
    }

    fun select(ac: AircraftState) {
        selected = ac
        enrichInfo = null
        enrichRoute = null
        enrichLoading = true
        viewModelScope.launch {
            val info = enrich.aircraftInfo(ac.icao24)
            val route = ac.callsign?.let { enrich.route(it) }
            if (selected?.icao24 == ac.icao24) {
                enrichInfo = info
                enrichRoute = route
                enrichLoading = false
                // Sighting logbook (v2.3): a Spotter identification
                // logs locally, with the nearest airport as context.
                val airportContext = route?.origin?.icao
                    ?: route?.origin?.iata
                    ?: if (AirportDb.isLoaded) {
                        AirportDb.nearest(ac.latitude, ac.longitude, 1)
                            .firstOrNull()?.first?.ident
                    } else {
                        null
                    }
                val typeText = listOfNotNull(info?.manufacturer, info?.type)
                    .joinToString(" ").ifEmpty { null }
                logbookStore.add(
                    Sighting(
                        id = "${System.currentTimeMillis()}-${ac.icao24}",
                        timestampMs = System.currentTimeMillis(),
                        callsign = ac.callsign,
                        icao24 = ac.icao24,
                        aircraftType = typeText,
                        airline = route?.airlineName,
                        airportContext = airportContext,
                        source = "spotter",
                    )
                )
            }
        }
    }

    fun toggleWatch(ac: AircraftState) {
        viewModelScope.launch { prefs.toggleWatchedAircraft(ac.icao24) }
    }

    fun isWatched(ac: AircraftState): Boolean =
        ac.icao24.lowercase() in watchedAircraft

    companion object {
        private const val REFRESH_MS = 45_000L
        private const val NEAR_RADIUS_KM = 80.0
    }
}
