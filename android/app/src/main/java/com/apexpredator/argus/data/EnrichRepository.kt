package com.apexpredator.argus.data

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// What the public aircraft databases know about one airframe, from
// adsbdb.com (type, registration, owner) and Planespotters (photo).
data class AircraftInfo(
    val type: String?,
    val icaoType: String?,
    val manufacturer: String?,
    val registration: String?,
    val owner: String?,
    val ownerCountry: String?,
    val photoUrl: String?,
    val photoPageUrl: String?,
    val photographer: String?,
)

// One scheduled route for a callsign, from adsbdb.com: the flight's
// airline plus origin and destination airports with coordinates, which
// is what lets the radar draw the great-circle route and link the
// aircraft to its two airports (the Palantir-style entity link).
data class FlightRoute(
    val airlineName: String?,
    val airlineIata: String?,
    val callsignIata: String?,
    val origin: RouteAirport?,
    val destination: RouteAirport?,
)

data class RouteAirport(
    val icao: String?,
    val iata: String?,
    val name: String?,
    val city: String?,
    val country: String?,
    val lat: Double?,
    val lon: Double?,
)

// Enrichment lookups are best-effort decoration on top of the live
// position feed: any failure returns null and the detail sheet simply
// shows the live data alone. Results are cached for the session.
class EnrichRepository {

    private val infoCache = ConcurrentHashMap<String, AircraftInfo>()
    private val routeCache = ConcurrentHashMap<String, FlightRoute>()

    suspend fun aircraftInfo(icao24: String): AircraftInfo? {
        val hex = icao24.trim().uppercase()
        infoCache[hex]?.let { return it }
        val info = try {
            val root = HttpJson.getJson("https://api.adsbdb.com/v0/aircraft/$hex")
            val ac = root.optJSONObject("response")?.optJSONObject("aircraft")
            if (ac == null) {
                null
            } else {
                var photoUrl = ac.optStringOrNull("url_photo")
                var photoPage: String? = null
                var photographer: String? = null
                if (photoUrl == null) {
                    val sp = fetchPlanespottersPhoto(hex)
                    photoUrl = sp?.first
                    photoPage = sp?.second
                    photographer = sp?.third
                }
                AircraftInfo(
                    type = ac.optStringOrNull("type"),
                    icaoType = ac.optStringOrNull("icao_type"),
                    manufacturer = ac.optStringOrNull("manufacturer"),
                    registration = ac.optStringOrNull("registration"),
                    owner = ac.optStringOrNull("registered_owner"),
                    ownerCountry = ac.optStringOrNull("registered_owner_country_name"),
                    photoUrl = photoUrl,
                    photoPageUrl = photoPage,
                    photographer = photographer,
                )
            }
        } catch (e: Exception) {
            null
        }
        if (info != null) infoCache[hex] = info
        return info
    }

    // Returns (imageUrl, photoPageUrl, photographer).
    private suspend fun fetchPlanespottersPhoto(hex: String):
        Triple<String, String?, String?>? {
        return try {
            val root = HttpJson.getJson(
                "https://api.planespotters.net/pub/photos/hex/$hex"
            )
            val photos = root.optJSONArray("photos") ?: return null
            if (photos.length() == 0) return null
            val first = photos.getJSONObject(0)
            val thumb = first.optJSONObject("thumbnail_large")
                ?: first.optJSONObject("thumbnail")
            val src = thumb?.optStringOrNull("src") ?: return null
            Triple(src, first.optStringOrNull("link"), first.optStringOrNull("photographer"))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun route(callsign: String): FlightRoute? {
        val cs = callsign.trim().uppercase()
        if (cs.isEmpty()) return null
        routeCache[cs]?.let { return it }
        val route = try {
            val root = HttpJson.getJson("https://api.adsbdb.com/v0/callsign/$cs")
            val fr = root.optJSONObject("response")?.optJSONObject("flightroute")
            if (fr == null) {
                null
            } else {
                val airline = fr.optJSONObject("airline")
                FlightRoute(
                    airlineName = airline?.optStringOrNull("name"),
                    airlineIata = airline?.optStringOrNull("iata"),
                    callsignIata = fr.optStringOrNull("callsign_iata"),
                    origin = fr.optJSONObject("origin")?.toRouteAirport(),
                    destination = fr.optJSONObject("destination")?.toRouteAirport(),
                )
            }
        } catch (e: Exception) {
            null
        }
        if (route != null) routeCache[cs] = route
        return route
    }
}

private fun JSONObject.toRouteAirport(): RouteAirport = RouteAirport(
    icao = optStringOrNull("icao_code"),
    iata = optStringOrNull("iata_code"),
    name = optStringOrNull("name"),
    city = optStringOrNull("municipality"),
    country = optStringOrNull("country_name"),
    lat = if (has("latitude") && !isNull("latitude")) getDouble("latitude") else null,
    lon = if (has("longitude") && !isNull("longitude")) getDouble("longitude") else null,
)

internal fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val v = optString(name, "")
    return v.ifEmpty { null }
}
