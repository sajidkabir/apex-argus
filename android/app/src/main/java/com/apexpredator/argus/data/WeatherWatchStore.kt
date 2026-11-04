package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Stores, per watched airport, the last METAR the weather-change
// worker compared against, and the signature of the last change it
// notified about. The signature stops a repeated periodic run from
// re-notifying the same change; the stored report stops the worker
// from comparing a fresh report against a stale one. Both live on
// the phone only. Lines are "ICAO<unit separator>value" with
// backslash escaping, the same convention as the logbook.

private val Context.weatherWatchDataStore by preferencesDataStore(name = "weather_watch")

class WeatherWatchStore(private val context: Context) {

    private val lastKey = stringPreferencesKey("weather_watch_last")
    private val sigKey = stringPreferencesKey("weather_watch_sig")

    private fun esc(v: String): String =
        v.replace("\\", "\\\\").replace("\u001F", "\\u").replace("\n", "\\n")

    private fun unesc(v: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    '\\' -> out.append('\\')
                    'u' -> out.append('\u001F')
                    'n' -> out.append('\n')
                    else -> {
                        out.append('\\')
                        out.append(v[i + 1])
                    }
                }
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
        return out.toString()
    }

    private fun readMap(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val sep = line.indexOf('\u001F')
            if (sep < 0) continue
            out[line.substring(0, sep)] = unesc(line.substring(sep + 1))
        }
        return out
    }

    private fun writeMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}\u001F${esc(it.value)}" }

    val lastReports: Flow<Map<String, String>> =
        context.weatherWatchDataStore.data.map { readMap(it[lastKey] ?: "") }

    val lastSignatures: Flow<Map<String, String>> =
        context.weatherWatchDataStore.data.map { readMap(it[sigKey] ?: "") }

    suspend fun lastReport(icao: String): String? {
        return lastReports.first()[icao.trim().uppercase()]
    }

    suspend fun lastSignature(icao: String): String? {
        return lastSignatures.first()[icao.trim().uppercase()]
    }

    suspend fun setLastReport(icao: String, raw: String) {
        val clean = icao.trim().uppercase()
        val updated = lastReports.first().toMutableMap()
        updated[clean] = raw
        context.weatherWatchDataStore.edit { it[lastKey] = writeMap(updated) }
    }

    suspend fun setLastSignature(icao: String, signature: String) {
        val clean = icao.trim().uppercase()
        val updated = lastSignatures.first().toMutableMap()
        updated[clean] = signature
        context.weatherWatchDataStore.edit { it[sigKey] = writeMap(updated) }
    }
}
