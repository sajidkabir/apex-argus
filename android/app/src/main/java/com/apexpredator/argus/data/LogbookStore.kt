package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Persists the sighting logbook locally, newest first, capped at 200
// entries. No account, no cloud. Modelled on HistoryStore, but the
// encoding lives in Logbook.kt so it stays unit-testable.

private val Context.logbookDataStore by preferencesDataStore(name = "logbook")

class LogbookStore(private val context: Context) {

    private val key = stringPreferencesKey("logbook_txt")

    val entries: Flow<List<Sighting>> =
        context.logbookDataStore.data.map { prefs ->
            val text = prefs[key] ?: ""
            text.lineSequence()
                .mapNotNull { decodeSighting(it) }
                .sortedByDescending { it.timestampMs }
                .toList()
        }

    // Logs one identification. A re-tap of the same aircraft within
    // 30 minutes is not a new sighting, it is the same sheet reopened,
    // so it is skipped instead of doubling the count.
    suspend fun add(sighting: Sighting) {
        val current = entries.first()
        val recent = current.firstOrNull {
            it.icao24.equals(sighting.icao24, ignoreCase = true) &&
                sighting.timestampMs - it.timestampMs < DEDUPE_MS
        }
        if (recent != null) return
        val updated = (listOf(sighting) + current)
            .sortedByDescending { it.timestampMs }
            .take(MAX_ENTRIES)
        context.logbookDataStore.edit { prefs ->
            prefs[key] = updated.joinToString("\n") { encodeSighting(it) }
        }
    }

    suspend fun delete(id: String) {
        val updated = entries.first().filter { it.id != id }
        context.logbookDataStore.edit { prefs ->
            prefs[key] = updated.joinToString("\n") { encodeSighting(it) }
        }
    }

    suspend fun clear() {
        context.logbookDataStore.edit { it.remove(key) }
    }

    companion object {
        const val MAX_ENTRIES = 200
        const val DEDUPE_MS = 30L * 60_000
    }
}
