package com.apexpredator.argus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Shared tiny HTTP helpers for the JSON APIs the app talks to
// (adsbdb, RainViewer, Planespotters). All calls carry a descriptive
// User-Agent: some providers reject generic library agents.
object HttpJson {

    const val USER_AGENT = "ApexArgus/2.0 (+https://github.com/sajidkabir/apex-argus)"

    suspend fun getText(url: String, userAgent: String = USER_AGENT): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP $code from $url")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
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

    suspend fun getJson(url: String, userAgent: String = USER_AGENT): JSONObject =
        JSONObject(getText(url, userAgent))
}
