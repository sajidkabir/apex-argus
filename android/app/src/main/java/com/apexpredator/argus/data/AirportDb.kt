package com.apexpredator.argus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// One airport from the bundled OurAirports-derived dataset
// (android/app/src/main/assets/airports.tsv). The dataset ships inside
// the APK, so airport search works fully offline: by ICAO or GPS ident,
// by IATA code, by airport name, or by city in plain English.
data class Airport(
    val ident: String,
    val iata: String?,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?,
    val type: String,
) {
    val isLarge: Boolean get() = type == "large_airport"
    val isMedium: Boolean get() = type == "medium_airport"
    val codeLabel: String get() = if (iata != null) "$ident · $iata" else ident
}

data class Runway(
    val lengthFt: Int?,
    val surface: String?,
    val leIdent: String?,
    val heIdent: String?,
) {
    val label: String
        get() = listOfNotNull(leIdent, heIdent).joinToString("/").ifEmpty { "Runway" }
}

// Loads the bundled airport + runway tables once, then serves lookups
// and ranked search from memory (~10k airports, parse takes well under
// a second on a phone).
object AirportDb {

    @Volatile private var airports: List<Airport> = emptyList()
    @Volatile private var byIdent: Map<String, Airport> = emptyMap()
    @Volatile private var runways: Map<String, List<Runway>> = emptyMap()
    @Volatile private var loaded = false
    private val mutex = Mutex()

    val isLoaded: Boolean get() = loaded
    val size: Int get() = airports.size

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val airportText = context.assets.open("airports.tsv")
                    .bufferedReader().use { it.readText() }
                val runwayText = context.assets.open("runways.tsv")
                    .bufferedReader().use { it.readText() }
                airports = parseAirports(airportText)
                byIdent = airports.associateBy { it.ident }
                runways = parseRunways(runwayText)
                loaded = true
            }
        }
    }

    fun all(): List<Airport> = airports

    fun byIdent(ident: String): Airport? = byIdent[ident.trim().uppercase()]

    fun byIata(iata: String): Airport? {
        val q = iata.trim().uppercase()
        return airports.firstOrNull { it.iata == q }
    }

    fun runwaysFor(ident: String): List<Runway> =
        runways[ident.trim().uppercase()] ?: emptyList()

    fun search(query: String, limit: Int = 12): List<Airport> =
        searchAirports(airports, query, limit)

    // Airports nearest to a point, as (airport, distanceKm) pairs.
    fun nearest(lat: Double, lon: Double, limit: Int = 8): List<Pair<Airport, Double>> =
        airports.asSequence()
            .map { it to GeoMath.haversineKm(lat, lon, it.lat, it.lon) }
            .sortedBy { it.second }
            .take(limit)
            .toList()
}

// Parses the bundled TSV: ident, iata, name, city, country, lat, lon,
// elevationFt, type. Pure function so it is unit-testable.
fun parseAirports(tsv: String): List<Airport> {
    val out = ArrayList<Airport>()
    for (line in tsv.lineSequence()) {
        if (line.isBlank()) continue
        val f = line.split('\t')
        if (f.size < 9) continue
        val lat = f[5].toDoubleOrNull() ?: continue
        val lon = f[6].toDoubleOrNull() ?: continue
        out.add(
            Airport(
                ident = f[0],
                iata = f[1].ifEmpty { null },
                name = f[2],
                city = f[3],
                country = f[4],
                lat = lat,
                lon = lon,
                elevationFt = f[7].toIntOrNull(),
                type = f[8],
            )
        )
    }
    return out
}

// Parses runways.tsv: ident, then runway records separated by \u001F,
// each "lengthFt \t surface \t leIdent \t heIdent". The first tab ends
// the ident; records carry their own tabs, so split in that order.
fun parseRunways(tsv: String): Map<String, List<Runway>> {
    val out = HashMap<String, List<Runway>>()
    for (line in tsv.lineSequence()) {
        if (line.isBlank()) continue
        val tab = line.indexOf('\t')
        if (tab < 1) continue
        val ident = line.substring(0, tab)
        val records = line.substring(tab + 1).split('\u001F')
        val list = ArrayList<Runway>()
        for (rec in records) {
            val f = rec.split('\t')
            if (f.isEmpty()) continue
            list.add(
                Runway(
                    lengthFt = f.getOrNull(0)?.toIntOrNull(),
                    surface = f.getOrNull(1)?.ifEmpty { null },
                    leIdent = f.getOrNull(2)?.ifEmpty { null },
                    heIdent = f.getOrNull(3)?.ifEmpty { null },
                )
            )
        }
        if (list.isNotEmpty()) out[ident] = list
    }
    return out
}

// Ranked plain-English airport search. Codes win first (exact ICAO,
// exact IATA), then code prefixes, then name and city matches with every
// query token present. Larger airports win ties, so "Dhaka" returns
// Hazrat Shahjalal International first, not a grass strip.
fun searchAirports(list: List<Airport>, query: String, limit: Int = 12): List<Airport> {
    val q = query.trim().uppercase()
    if (q.length < 2) return emptyList()
    val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val scored = ArrayList<Pair<Airport, Int>>()
    for (ap in list) {
        val score = airportScore(ap, q, tokens) ?: continue
        scored.add(ap to score)
    }
    return scored
        .sortedWith(
            compareBy<Pair<Airport, Int>> { it.second }
                .thenBy { airportSizeRank(it.first) }
                .thenBy { it.first.name }
        )
        .take(limit)
        .map { it.first }
}

private fun airportSizeRank(ap: Airport): Int = when (ap.type) {
    "large_airport" -> 0
    "medium_airport" -> 1
    else -> 2
}

private fun airportScore(ap: Airport, q: String, tokens: List<String>): Int? {
    val ident = ap.ident.uppercase()
    val iata = ap.iata?.uppercase()
    val name = ap.name.uppercase()
    val city = ap.city.uppercase()
    if (ident == q) return 0
    if (iata != null && iata == q) return 1
    if (ident.startsWith(q)) return 2
    if (iata != null && iata.startsWith(q)) return 3
    if (tokens.all { name.contains(it) }) return if (name.startsWith(tokens.first())) 4 else 6
    if (tokens.all { city.contains(it) }) return if (city.startsWith(tokens.first())) 5 else 7
    if (tokens.all { name.contains(it) || city.contains(it) || ident.contains(it) }) return 8
    return null
}
