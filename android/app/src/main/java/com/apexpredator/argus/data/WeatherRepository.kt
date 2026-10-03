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
}
