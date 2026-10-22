package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.argusDataStore by preferencesDataStore(name = "argus_prefs")

// User preferences that persist across launches: the watchlist
// (aircraft hex codes and airport idents the user follows), the radar
// refresh cadence, the units toggle, and which map layers are on.
class PrefsStore(private val context: Context) {

    val watchedAircraft: Flow<Set<String>> =
        context.argusDataStore.data.map { it[KEY_WATCHED_AIRCRAFT] ?: emptySet() }

    val watchedAirports: Flow<Set<String>> =
        context.argusDataStore.data.map { it[KEY_WATCHED_AIRPORTS] ?: emptySet() }

    val refreshSeconds: Flow<Int> =
        context.argusDataStore.data.map { it[KEY_REFRESH_SECONDS] ?: DEFAULT_REFRESH_SECONDS }

    val metricUnits: Flow<Boolean> =
        context.argusDataStore.data.map { it[KEY_METRIC] ?: false }

    suspend fun toggleWatchedAircraft(hex: String) {
        val clean = hex.trim().lowercase()
        context.argusDataStore.edit { prefs ->
            val current = prefs[KEY_WATCHED_AIRCRAFT] ?: emptySet()
            prefs[KEY_WATCHED_AIRCRAFT] =
                if (clean in current) current - clean else current + clean
        }
    }

    suspend fun toggleWatchedAirport(ident: String) {
        val clean = ident.trim().uppercase()
        context.argusDataStore.edit { prefs ->
            val current = prefs[KEY_WATCHED_AIRPORTS] ?: emptySet()
            prefs[KEY_WATCHED_AIRPORTS] =
                if (clean in current) current - clean else current + clean
        }
    }

    suspend fun setRefreshSeconds(seconds: Int) {
        context.argusDataStore.edit { it[KEY_REFRESH_SECONDS] = seconds }
    }

    suspend fun setMetricUnits(metric: Boolean) {
        context.argusDataStore.edit { it[KEY_METRIC] = metric }
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 180
        private val KEY_WATCHED_AIRCRAFT = stringSetPreferencesKey("watched_aircraft")
        private val KEY_WATCHED_AIRPORTS = stringSetPreferencesKey("watched_airports")
        private val KEY_REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        private val KEY_METRIC = booleanPreferencesKey("metric_units")
    }
}
