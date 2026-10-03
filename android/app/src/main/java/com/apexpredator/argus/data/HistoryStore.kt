package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// Persists the last 10 decoded reports locally. No account, no cloud.

private val Context.historyDataStore by preferencesDataStore(name = "history")

data class HistoryEntry(
    val raw: String,
    val kind: ReportKind,
    val station: String,
    val timestampMillis: Long
)

class HistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("history_json")
    private val maxEntries = 10

    val entries: Flow<List<HistoryEntry>> =
        context.historyDataStore.data.map { prefs ->
            val json = prefs[key] ?: "[]"
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    HistoryEntry(
                        raw = o.getString("raw"),
                        kind = ReportKind.valueOf(o.getString("kind")),
                        station = o.optString("station", ""),
                        timestampMillis = o.getLong("ts")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun add(raw: String, kind: ReportKind, station: String) {
        val prefs = context.historyDataStore.data.first()
        val existing = try {
            val arr = JSONArray(prefs[key] ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
        val updated = JSONArray()
        val entry = JSONObject()
            .put("raw", raw)
            .put("kind", kind.name)
            .put("station", station)
            .put("ts", System.currentTimeMillis())
        updated.put(entry)
        // Drop exact duplicates of the raw text, keep newest first, cap at 10.
        existing
            .filter { it.optString("raw") != raw }
            .take(maxEntries - 1)
            .forEach { updated.put(it) }
        context.historyDataStore.edit { it[key] = updated.toString() }
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(key) }
    }
}
