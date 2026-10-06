package com.apexpredator.argus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Describes the newest published release, read from the version.json
// asset attached to the latest GitHub release. No authentication needed;
// the releases/download endpoint is public.
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

// Pure function over the version.json body so it is unit-testable.
fun parseUpdateInfo(body: String): UpdateInfo {
    val o = JSONObject(body)
    return UpdateInfo(
        versionCode = o.getInt("versionCode"),
        versionName = o.getString("versionName"),
        apkUrl = o.getString("apkUrl"),
    )
}

suspend fun fetchUpdateInfo(): UpdateInfo = withContext(Dispatchers.IO) {
    val connection =
        (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ApexArgus/1.1")
        }
    try {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("Update check failed (HTTP $code).")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        parseUpdateInfo(body)
    } catch (e: IOException) {
        val msg = e.message ?: ""
        if (msg.contains("Unable to resolve host") || msg.contains("No address associated")) {
            throw IOException("No network connection.")
        }
        throw e
    } finally {
        connection.disconnect()
    }
}

private const val VERSION_JSON_URL =
    "https://github.com/sajidkabir/apex-argus/releases/latest/download/version.json"
