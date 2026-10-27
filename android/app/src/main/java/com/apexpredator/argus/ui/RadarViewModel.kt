package com.apexpredator.argus.ui

import android.app.Application
import android.text.format.DateFormat
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
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.LogbookStore
import com.apexpredator.argus.data.Sighting
import com.apexpredator.argus.data.OpenSkyRepository
import com.apexpredator.argus.data.PlaybackStore
import com.apexpredator.argus.data.PrefsStore
import com.apexpredator.argus.data.RainViewerRepository
import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.data.SatellitePosition
import com.apexpredator.argus.data.SatelliteRepository
import com.apexpredator.argus.data.Snapshot
import com.apexpredator.argus.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

data class MapCenter(val lat: Double, val lon: Double, val zoom: Double)

// One row of the unified search: the Palantir-style single box that
// finds airports, live aircraft, and satellites alike.
sealed interface SearchHit {
    data class AirportHit(val airport: Airport) : SearchHit
    data class AircraftHit(val aircraft: AircraftState) : SearchHit
    data class SatelliteHit(val satellite: SatellitePosition) : SearchHit
}

enum class AltBand { ALL, LOW, MID, HIGH }

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val opensky = OpenSkyRepository()
    private val weather = WeatherRepository()
    private val enrich = EnrichRepository()
    private val rainViewer = RainViewerRepository()
    private val satelliteRepo = SatelliteRepository(application)
    private val prefs = PrefsStore(application)
    private val playbackStore = PlaybackStore(application)
    private val logbookStore = LogbookStore(application)

    // ---- Map / feed state ----
    var center by mutableStateOf(MapCenter(23.8103, 90.4125, 9.0))
        private set
    var centerRequest by mutableStateOf<MapCenter?>(null)
        private set
    var liveAircraft by mutableStateOf<List<AircraftState>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var lastUpdated by mutableStateOf<String?>(null)
        private set

    // ---- Selection ----
    var selected by mutableStateOf<AircraftState?>(null)
    var selectedAirport by mutableStateOf<Airport?>(null)
    var selectedSatellite by mutableStateOf<SatellitePosition?>(null)

    // Enrichment for the selected aircraft (photo, type, route).
    var enrichLoading by mutableStateOf(false)
        private set
    var enrichInfo by mutableStateOf<AircraftInfo?>(null)
        private set
    var enrichRoute by mutableStateOf<FlightRoute?>(null)
        private set

    // Airport detail extras: live traffic nearby + raw weather reports.
    var airportTraffic by mutableStateOf<List<Pair<AircraftState, Double>>>(emptyList())
        private set
    // Movements board: arrivals are closing on the airport and
    // descending, departures are opening and climbing. Trends need two
    // radar refreshes, so the board fills in as the feed runs.
    var airportArrivals by mutableStateOf<List<Pair<AircraftState, Double>>>(emptyList())
        private set
    var airportDepartures by mutableStateOf<List<Pair<AircraftState, Double>>>(emptyList())
        private set
    private var prevPositions: Map<String, Pair<Double, Double>> = emptyMap()
    var airportMetar by mutableStateOf<String?>(null)
        private set
    var airportTaf by mutableStateOf<String?>(null)
        private set

    // ---- Unified search ----
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    // ---- Layers and filters ----
    var showAircraft by mutableStateOf(true)
    var showSatellites by mutableStateOf(true)
    var showAirports by mutableStateOf(true)
    var showWeather by mutableStateOf(false)
    var showCameras by mutableStateOf(true)
    var weatherPath by mutableStateOf<String?>(null)
        private set
    var hideGround by mutableStateOf(false)
    var uavOnly by mutableStateOf(false)
    var altBand by mutableStateOf(AltBand.ALL)

    // ---- Satellites ----
    var satellites by mutableStateOf<List<SatellitePosition>>(emptyList())
        private set
    var visibleAirports by mutableStateOf<List<Airport>>(emptyList())
        private set

    // ---- Preferences (mirrored from DataStore) ----
    var watchedAircraft by mutableStateOf<Set<String>>(emptySet())
        private set
    var watchedAirports by mutableStateOf<Set<String>>(emptySet())
        private set
    var refreshSeconds by mutableStateOf(PrefsStore.DEFAULT_REFRESH_SECONDS)
        private set
    var metricUnits by mutableStateOf(false)
        private set

    // ---- Playback ----
    var snapshots by mutableStateOf<List<Snapshot>>(emptyList())
        private set
    var playbackIndex by mutableStateOf<Int?>(null)
        private set
    val inPlayback: Boolean get() = playbackIndex != null

    private var lastBbox: BoundingBox? = null
    private var fetchJob: Job? = null
    private var debounceJob: Job? = null
    private var searchJob: Job? = null

    // The aircraft actually drawn: live feed or the playback frame,
    // with the user's filters applied.
    val displayAircraft: List<AircraftState>
        get() {
            val base = playbackIndex?.let { idx ->
                snapshots.getOrNull(idx)?.aircraft
            } ?: liveAircraft
            return base.filter { ac ->
                (!hideGround || !ac.onGround) &&
                    (!uavOnly || ac.isUav) &&
                    altBandMatches(ac)
            }
        }

    val emergencies: List<AircraftState>
        get() = liveAircraft.filter { it.isEmergency }

    val uavCount: Int get() = liveAircraft.count { it.isUav }

    init {
        viewModelScope.launch { AirportDb.ensureLoaded(getApplication()) }
        viewModelScope.launch {
            prefs.watchedAircraft.collect { watchedAircraft = it }
        }
        viewModelScope.launch {
            prefs.watchedAirports.collect { watchedAirports = it }
        }
        viewModelScope.launch {
            prefs.refreshSeconds.collect { refreshSeconds = it }
        }
        viewModelScope.launch {
            prefs.metricUnits.collect { metricUnits = it }
        }
        viewModelScope.launch {
            try {
                satelliteRepo.load()
                while (true) {
                    satellites = withContext(Dispatchers.Default) {
                        satelliteRepo.positionsAt()
                    }
                    delay(SATELLITE_TICK_MS)
                }
            } catch (e: Exception) {
                // Satellite layer simply stays empty if TLEs cannot load.
            }
        }
        viewModelScope.launch {
            weatherPath = rainViewer.latestRadarPath()
        }
    }

    private fun altBandMatches(ac: AircraftState): Boolean {
        val ft = ac.altitudeFt ?: return altBand == AltBand.ALL
        return when (altBand) {
            AltBand.ALL -> true
            AltBand.LOW -> ft < 10_000
            AltBand.MID -> ft in 10_000.0..30_000.0
            AltBand.HIGH -> ft > 30_000
        }
    }

    fun consumeCenterRequest() {
        centerRequest = null
    }

    fun centerOn(lat: Double, lon: Double, zoom: Double) {
        requestCenter(lat, lon, zoom)
    }

    // Entity pivot: from an aircraft's route (or anywhere) straight to
    // an airport's sheet, using the bundled dataset when possible and
    // falling back to the route's own airport record.
    fun openAirportByIdent(icao: String) {
        val fromDb = AirportDb.byIdent(icao)
        if (fromDb != null) {
            selected = null
            requestCenter(fromDb.lat, fromDb.lon, 10.0)
            selectAirport(fromDb)
            return
        }
        val routeAirport = listOfNotNull(enrichRoute?.origin, enrichRoute?.destination)
            .firstOrNull { it.icao?.equals(icao, ignoreCase = true) == true }
        val lat = routeAirport?.lat
        val lon = routeAirport?.lon
        if (routeAirport != null && lat != null && lon != null) {
            selected = null
            requestCenter(lat, lon, 10.0)
            selectAirport(
                Airport(
                    ident = routeAirport.icao ?: icao,
                    iata = routeAirport.iata,
                    name = routeAirport.name ?: (routeAirport.icao ?: icao),
                    city = routeAirport.city ?: "",
                    country = routeAirport.country ?: "",
                    lat = lat,
                    lon = lon,
                    elevationFt = null,
                    type = "airport",
                )
            )
        }
    }

    private fun requestCenter(lat: Double, lon: Double, zoom: Double) {
        val target = MapCenter(lat, lon, zoom)
        center = target
        centerRequest = target
    }

    fun onViewportChanged(bbox: BoundingBox, centerLat: Double, centerLon: Double) {
        lastBbox = bbox
        center = MapCenter(centerLat, centerLon, center.zoom)
        updateVisibleAirports()
        if (inPlayback) return
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(VIEWPORT_DEBOUNCE_MS)
            refresh(bbox)
        }
    }

    fun onZoomChanged(zoom: Double) {
        center = center.copy(zoom = zoom)
        updateVisibleAirports()
    }

    private fun updateVisibleAirports() {
        if (!AirportDb.isLoaded) return
        val bbox = lastBbox ?: return
        visibleAirports = AirportDb.all()
            .filter {
                (it.isLarge || it.isMedium) &&
                    it.lat in bbox.lamin..bbox.lamax &&
                    it.lon in bbox.lomin..bbox.lomax
            }
            .sortedBy { GeoMath.haversineKm(center.lat, center.lon, it.lat, it.lon) }
            .take(MAX_VISIBLE_AIRPORTS)
    }

    fun refreshNow() {
        if (inPlayback) return
        val bbox = lastBbox ?: defaultBbox(center)
        refresh(bbox)
    }

    private fun refresh(bbox: BoundingBox) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val fresh = opensky.fetchStates(bbox)
                prevPositions = liveAircraft.associate {
                    it.icao24 to (it.latitude to it.longitude)
                }
                liveAircraft = fresh
                viewModelScope.launch { playbackStore.record(fresh) }
                selected?.let { sel ->
                    selected = fresh.firstOrNull { it.icao24 == sel.icao24 }
                }
                selectedAirport?.let { computeAirportTraffic(it) }
                lastUpdated = "Updated " +
                    DateFormat.getTimeFormat(getApplication()).format(Date())
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not load flight data."
            } finally {
                isLoading = false
            }
        }
    }

    // ---- Search ----

    fun onSearchQueryChange(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = ArrayList<SearchHit>()
            if (AirportDb.isLoaded) {
                AirportDb.search(q, limit = 6).forEach { hits.add(SearchHit.AirportHit(it)) }
            }
            val qUp = q.trim().uppercase()
            liveAircraft.filter {
                (it.callsign?.uppercase()?.contains(qUp) == true) ||
                    it.icao24.uppercase().startsWith(qUp)
            }.take(5).forEach { hits.add(SearchHit.AircraftHit(it)) }
            withContext(Dispatchers.Default) {
                satelliteRepo.search(q, limit = 5)
            }.forEach { hits.add(SearchHit.SatelliteHit(it)) }
            searchResults = hits
        }
    }

    fun onSearchHitSelected(hit: SearchHit) {
        searchQuery = ""
        searchResults = emptyList()
        when (hit) {
            is SearchHit.AirportHit -> {
                requestCenter(hit.airport.lat, hit.airport.lon, 10.0)
                selectAirport(hit.airport)
            }
            is SearchHit.AircraftHit -> {
                requestCenter(hit.aircraft.latitude, hit.aircraft.longitude, 9.0)
                selectAircraft(hit.aircraft)
            }
            is SearchHit.SatelliteHit -> {
                requestCenter(hit.satellite.lat, hit.satellite.lon, 4.0)
                selectedSatellite = hit.satellite
            }
        }
    }

    // ---- Selection ----

    fun selectAircraft(ac: AircraftState) {
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
                // Sighting logbook (v2.3): identifying an aircraft on
                // the radar, or from an airport sheet's traffic list,
                // logs it locally with what enrichment knows.
                val airportContext = selectedAirport?.ident
                    ?: route?.origin?.icao
                    ?: route?.origin?.iata
                    ?: AirportDb.nearest(ac.latitude, ac.longitude, 1)
                        .firstOrNull()?.first?.ident
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
                        source = "radar",
                    )
                )
            }
        }
    }

    fun selectAirport(ap: Airport) {
        selectedAirport = ap
        airportMetar = null
        airportTaf = null
        computeAirportTraffic(ap)
        viewModelScope.launch {
            // METAR/TAF exist only for 4-letter ICAO-style idents.
            if (ap.ident.length == 4) {
                airportMetar = try {
                    weather.fetchRaw(ap.ident, ReportKind.METAR)
                } catch (e: Exception) {
                    null
                }
                airportTaf = try {
                    weather.fetchRaw(ap.ident, ReportKind.TAF)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun computeAirportTraffic(ap: Airport) {
        airportTraffic = liveAircraft
            .map { it to GeoMath.haversineKm(ap.lat, ap.lon, it.latitude, it.longitude) }
            .filter { it.second <= AIRPORT_TRAFFIC_RADIUS_KM }
            .sortedBy { it.second }
            .take(10)

        val arrivals = ArrayList<Pair<AircraftState, Double>>()
        val departures = ArrayList<Pair<AircraftState, Double>>()
        for ((ac, distNow) in airportTraffic) {
            if (ac.onGround) continue
            val prev = prevPositions[ac.icao24] ?: continue
            val distPrev = GeoMath.haversineKm(ap.lat, ap.lon, prev.first, prev.second)
            val trend = distNow - distPrev
            val vr = ac.verticalRateMs ?: 0.0
            when {
                trend < -0.5 && vr < 0.5 -> arrivals.add(ac to distNow)
                trend > 0.5 && vr > -0.5 -> departures.add(ac to distNow)
            }
        }
        airportArrivals = arrivals.sortedBy { it.second }.take(6)
        airportDepartures = departures.sortedBy { it.second }.take(6)
    }

    // ---- Watchlist and settings ----

    fun toggleWatchAircraft(ac: AircraftState) {
        viewModelScope.launch { prefs.toggleWatchedAircraft(ac.icao24) }
    }

    fun toggleWatchAirport(ap: Airport) {
        viewModelScope.launch { prefs.toggleWatchedAirport(ap.ident) }
    }

    fun isWatched(ac: AircraftState): Boolean =
        ac.icao24.lowercase() in watchedAircraft

    fun isWatched(ap: Airport): Boolean = ap.ident in watchedAirports

    fun updateRefreshSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setRefreshSeconds(seconds) }
    }

    fun updateMetricUnits(metric: Boolean) {
        viewModelScope.launch { prefs.setMetricUnits(metric) }
    }

    // ---- Playback ----

    fun enterPlayback() {
        viewModelScope.launch {
            snapshots = playbackStore.loadAll()
            playbackIndex = if (snapshots.isEmpty()) null else snapshots.lastIndex
        }
    }

    fun exitPlayback() {
        playbackIndex = null
        refreshNow()
    }

    fun setPlaybackIndex(index: Int) {
        playbackIndex = index.coerceIn(0, snapshots.lastIndex.coerceAtLeast(0))
    }

    private fun defaultBbox(c: MapCenter): BoundingBox {
        val half = 2.5
        return BoundingBox(c.lat - half, c.lon - half, c.lat + half, c.lon + half)
    }

    companion object {
        private const val VIEWPORT_DEBOUNCE_MS = 1_500L
        private const val SEARCH_DEBOUNCE_MS = 250L
        private const val SATELLITE_TICK_MS = 10_000L
        private const val MAX_VISIBLE_AIRPORTS = 80
        private const val AIRPORT_TRAFFIC_RADIUS_KM = 100.0
    }
}
