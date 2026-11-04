package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Persists the flight diary locally, capped at 100 entries, sorted by
// date (soonest first). No account, no cloud. Modelled on LogbookStore,
// with the encoding in FlightDiary.kt so it stays unit-testable.

private val Context.diaryDataStore by preferencesDataStore(name = "flight_diary")

class DiaryStore(private val context: Context) {

    private val key = stringPreferencesKey("diary_txt")

    val entries: Flow<List<DiaryFlight>> =
        context.diaryDataStore.data.map { prefs ->
            val text = prefs[key] ?: ""
            text.lineSequence()
                .mapNotNull { decodeDiaryFlight(it) }
                .sortedWith(compareBy({ it.date }, { it.displayName }))
                .toList()
        }

    suspend fun add(flight: DiaryFlight) {
        val current = entries.first()
        val updated = (listOf(flight) + current)
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.displayName }))
            .take(MAX_ENTRIES)
        context.diaryDataStore.edit { prefs ->
            prefs[key] = updated.joinToString("\n") { encodeDiaryFlight(it) }
        }
    }

    suspend fun delete(id: String) {
        val updated = entries.first().filter { it.id != id }
        context.diaryDataStore.edit { prefs ->
            prefs[key] = updated.joinToString("\n") { encodeDiaryFlight(it) }
        }
    }

    suspend fun clear() {
        context.diaryDataStore.edit { it.remove(key) }
    }

    companion object {
        const val MAX_ENTRIES = 100
    }
}
