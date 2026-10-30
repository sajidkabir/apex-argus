package com.apexpredator.argus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

// Fetches one dump1090 / readsb aircraft.json document from the
// user's own receiver. Short timeouts on purpose: a LAN receiver
// answers in milliseconds, and a dead address must fail fast with a
// plain message instead of hanging the radar refresh. The endpoint
// is fetched only when the user enabled the feed, and the address
// never leaves the phone. Throws nothing: failures come back as
// LocalFeedResult.Error so callers can keep OpenSky traffic on
// screen and show the feed problem separately.
sealed interface LocalFeedResult {
    data class Ok(val aircraft: List<AircraftState>, val fetchedMs: Long) : LocalFeedResult
    data class Error(val message: String) : LocalFeedResult
}

class LocalFeedRepository {

    suspend fun fetch(urlInput: String): LocalFeedResult =
        withContext(Dispatchers.IO) {
            val urlText = normalizeLocalFeedUrl(urlInput)
            if (!isValidLocalFeedUrl(urlText)) {
                return@withContext LocalFeedResult.Error(
                    "That feed address does not look like an http(s) URL. " +
                        "Example: http://192.168.1.10:8080/data/aircraft.json"
                )
            }
            val nowMs = System.currentTimeMillis()
            val connection = try {
                URL(urlText).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return@withContext LocalFeedResult.Error(
                    "Could not open that feed address. Check it and try again."
                )
            }
            connection.apply {
                connectTimeout = FEED_TIMEOUT_MS
                readTimeout = FEED_TIMEOUT_MS
                setRequestProperty("User-Agent", HttpJson.USER_AGENT)
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext LocalFeedResult.Error(
                        "Feed returned HTTP ${connection.responseCode}. " +
                            "Check the receiver is serving aircraft.json at that address."
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    LocalFeedResult.Ok(parseDump1090(body, nowMs), nowMs)
                } catch (e: Exception) {
                    LocalFeedResult.Error(
                        "Feed answered, but its JSON did not look like " +
                            "dump1090 aircraft.json. Check the endpoint path."
                    )
                }
            } catch (e: SocketTimeoutException) {
                LocalFeedResult.Error(
                    "Feed timed out. Check the receiver is running and " +
                        "you are on the same Wi-Fi network."
                )
            } catch (e: ConnectException) {
                LocalFeedResult.Error(
                    "Feed unreachable. Check the receiver is running and " +
                        "you are on the same Wi-Fi network."
                )
            } catch (e: UnknownHostException) {
                LocalFeedResult.Error(
                    "Feed host not found. Check the address and that " +
                        "you are on the same network as the receiver."
                )
            } catch (e: IOException) {
                LocalFeedResult.Error(
                    "Could not reach the feed: ${e.message ?: "network error"}. " +
                        "Check the receiver and your network."
                )
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val FEED_TIMEOUT_MS = 5_000
    }
}
