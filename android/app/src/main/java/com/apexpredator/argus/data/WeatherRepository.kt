package com.apexpredator.argus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Fetches live METAR and TAF reports from the US National Weather Service
// aviationweather.gov API. No API key required. Throws IOException with a
// user-friendly message on any failure.

enum class ReportKind { METAR, TAF }

class WeatherRepository {

    suspend fun fetchRaw(icao: String, kind: ReportKind): String = withContext(Dispatchers.IO) {
        val clean = icao.trim().uppercase()
        require(clean.matches(Regex("^[A-Z0-9]{4}$"))) {
            "Enter a 4-letter ICAO code, e.g. KJFK"
        }
        val path = if (kind == ReportKind.METAR) "metar" else "taf"
        val field = if (kind == ReportKind.METAR) "rawOb" else "rawTAF"
        val url = URL(
            "https://aviationweather.gov/api/data/$path" +
                "?ids=${URLEncoder.encode(clean, "UTF-8")}&format=json"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "ApexArgus/1.0")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Weather service returned HTTP $code. Try again later.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() == 0) {
                throw IOException("No ${kind.name} report found for $clean. Check the ICAO code.")
            }
            val raw = arr.getJSONObject(0).optString(field, "").trim()
            if (raw.isEmpty()) {
                throw IOException("No ${kind.name} report found for $clean. Check the ICAO code.")
            }
            raw
        } catch (e: IOException) {
            val msg = e.message ?: ""
            if (msg.contains("Unable to resolve host") || msg.contains("No address associated")) {
                throw IOException("No network connection. Check your internet and try again.")
            }
            throw e
        } finally {
            connection.disconnect()
        }
    }

    // Resolves an ICAO code to its station coordinates via the same
    // aviationweather.gov API, so the radar map can center on any airport.
    // Returns Pair(latitude, longitude). Throws IOException on any failure.
    suspend fun fetchStationCoords(icao: String): Pair<Double, Double> =
        withContext(Dispatchers.IO) {
            val clean = icao.trim().uppercase()
            require(clean.matches(Regex("^[A-Z0-9]{4}$"))) {
                "Enter a 4-letter ICAO code, e.g. KJFK"
            }
            val url = URL(
                "https://aviationweather.gov/api/data/stationinfo" +
                    "?ids=${URLEncoder.encode(clean, "UTF-8")}&format=json"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "ApexArgus/1.1")
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Station lookup returned HTTP $code. Try again later.")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() == 0) {
                    throw IOException("No station found for $clean. Check the ICAO code.")
                }
                val station = arr.getJSONObject(0)
                val lat = station.optDouble("lat", Double.NaN)
                val lon = station.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) {
                    throw IOException("No coordinates published for $clean.")
                }
                Pair(lat, lon)
            } catch (e: IOException) {
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host") ||
                    msg.contains("No address associated")
                ) {
                    throw IOException("No network connection. Check your internet and try again.")
                }
                throw e
            } finally {
                connection.disconnect()
            }
        }
}
