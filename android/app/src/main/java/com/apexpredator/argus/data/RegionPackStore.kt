package com.apexpredator.argus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// On-disk storage for one offline region pack (see RegionPack.kt):
// filesDir/region_pack/ holds manifest.json, tle.txt and
// weather.json. Everything is plain files in the app's private
// storage, so deleting the pack is deleting the directory, and the
// size shown in Tools is the real sum of those files.
class RegionPackStore(private val context: Context) {

    private fun packDir(): File = File(context.filesDir, "region_pack")

    private fun tleCacheDir(): File = File(context.filesDir, "tle")

    // The satellite TLE cache the satellite layer already keeps
    // (filesDir/tle/*.tle, refreshed from CelesTrak): the pack copies
    // it, so the satellite layer keeps moving offline from the same
    // elements it already uses online.
    fun tleCacheText(): String {
        val dir = tleCacheDir()
        if (!dir.exists()) return ""
        return dir.listFiles()
            ?.filter { it.name.endsWith(".tle") }
            ?.sortedBy { it.name }
            ?.joinToString("\n") { it.readText() }
            ?.trim()
            ?: ""
    }

    suspend fun savePack(
        airports: List<String>,
        tleText: String,
        weather: List<PackWeatherEntry>,
    ): RegionPackSummary = withContext(Dispatchers.IO) {
        val dir = packDir().apply { mkdirs() }
        val tleFile = File(dir, "tle.txt").apply { writeText(tleText) }
        val weatherJson = JSONArray()
        for (entry in weather) {
            weatherJson.put(
                JSONObject().apply {
                    put("icao", entry.icao)
                    put("kind", entry.kind)
                    put("raw", entry.raw)
                    put("fetchedMs", entry.fetchedMs)
                }
            )
        }
        val weatherFile = File(dir, "weather.json").apply {
            writeText(weatherJson.toString())
        }
        val summary = RegionPackSummary(
            airports = airports,
            createdMs = System.currentTimeMillis(),
            tleBytes = tleFile.length(),
            weatherEntries = weather.size,
            weatherBytes = weatherFile.length(),
        )
        File(dir, "manifest.json").writeText(
            JSONObject().apply {
                put("createdMs", summary.createdMs)
                put("airports", JSONArray(airports))
                put("tleBytes", summary.tleBytes)
                put("weatherEntries", summary.weatherEntries)
                put("weatherBytes", summary.weatherBytes)
            }.toString()
        )
        summary
    }

    suspend fun loadSummary(): RegionPackSummary? = withContext(Dispatchers.IO) {
        val dir = packDir()
        val manifest = File(dir, "manifest.json")
        if (!manifest.exists()) return@withContext null
        try {
            val json = JSONObject(manifest.readText())
            val airports = ArrayList<String>()
            val arr = json.optJSONArray("airports")
            if (arr != null) {
                for (i in 0 until arr.length()) airports.add(arr.getString(i))
            }
            RegionPackSummary(
                airports = airports,
                createdMs = json.optLong("createdMs", 0L),
                tleBytes = json.optLong("tleBytes", 0L),
                weatherEntries = json.optInt("weatherEntries", 0),
                weatherBytes = json.optLong("weatherBytes", 0L),
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readWeather(icao: String, kind: ReportKind): String? =
        withContext(Dispatchers.IO) {
            val file = File(packDir(), "weather.json")
            if (!file.exists()) return@withContext null
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("icao") == icao &&
                        obj.optString("kind") == kind.name
                    ) {
                        return@withContext obj.optString("raw").ifEmpty { null }
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deletePack(): Boolean = withContext(Dispatchers.IO) {
        val dir = packDir()
        if (!dir.exists()) return@withContext false
        dir.deleteRecursively()
        true
    }
}
