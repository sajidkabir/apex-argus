package com.apexpredator.argus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    // Balcony alerts (v2.3): one toggle per alert type, all on by
    // default. The worker also records the last alert it posted per
    // type, so a repeated periodic run does not re-notify the same
    // emergency, heavy or pass.
    val alertSatPass: Flow<Boolean> =
        context.argusDataStore.data.map { it[KEY_ALERT_SAT] ?: true }
    val alertHeavy: Flow<Boolean> =
        context.argusDataStore.data.map { it[KEY_ALERT_HEAVY] ?: true }
    val alertEmergency: Flow<Boolean> =
        context.argusDataStore.data.map { it[KEY_ALERT_EMERGENCY] ?: true }
    val lastSatAlertKey: Flow<String?> =
        context.argusDataStore.data.map { it[KEY_LAST_SAT_ALERT] }
    val lastHeavyAlertKey: Flow<String?> =
        context.argusDataStore.data.map { it[KEY_LAST_HEAVY_ALERT] }
    val lastEmergencyAlertKey: Flow<String?> =
        context.argusDataStore.data.map { it[KEY_LAST_EMERGENCY_ALERT] }

    // Own ADS-B receiver (v2.4): off until the user types an endpoint
    // and enables it. The address stays on the phone and is only ever
    // fetched from the app itself, on the user's own network.
    val localFeedEnabled: Flow<Boolean> =
        context.argusDataStore.data.map { it[KEY_LOCAL_FEED_ENABLED] ?: false }
    val localFeedUrl: Flow<String> =
        context.argusDataStore.data.map { it[KEY_LOCAL_FEED_URL] ?: "" }

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

    suspend fun setAlertSatPass(on: Boolean) {
        context.argusDataStore.edit { it[KEY_ALERT_SAT] = on }
    }

    suspend fun setAlertHeavy(on: Boolean) {
        context.argusDataStore.edit { it[KEY_ALERT_HEAVY] = on }
    }

    suspend fun setAlertEmergency(on: Boolean) {
        context.argusDataStore.edit { it[KEY_ALERT_EMERGENCY] = on }
    }

    suspend fun setLastSatAlertKey(key: String) {
        context.argusDataStore.edit { it[KEY_LAST_SAT_ALERT] = key }
    }

    suspend fun setLastHeavyAlertKey(key: String) {
        context.argusDataStore.edit { it[KEY_LAST_HEAVY_ALERT] = key }
    }

    suspend fun setLastEmergencyAlertKey(key: String) {
        context.argusDataStore.edit { it[KEY_LAST_EMERGENCY_ALERT] = key }
    }

    suspend fun setLocalFeedEnabled(on: Boolean) {
        context.argusDataStore.edit { it[KEY_LOCAL_FEED_ENABLED] = on }
    }

    suspend fun setLocalFeedUrl(url: String) {
        context.argusDataStore.edit { it[KEY_LOCAL_FEED_URL] = url.trim() }
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 180
        private val KEY_WATCHED_AIRCRAFT = stringSetPreferencesKey("watched_aircraft")
        private val KEY_WATCHED_AIRPORTS = stringSetPreferencesKey("watched_airports")
        private val KEY_REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        private val KEY_METRIC = booleanPreferencesKey("metric_units")
        private val KEY_ALERT_SAT = booleanPreferencesKey("alert_sat_pass")
        private val KEY_ALERT_HEAVY = booleanPreferencesKey("alert_heavy")
        private val KEY_ALERT_EMERGENCY = booleanPreferencesKey("alert_emergency")
        private val KEY_LAST_SAT_ALERT = stringPreferencesKey("last_sat_alert")
        private val KEY_LAST_HEAVY_ALERT = stringPreferencesKey("last_heavy_alert")
        private val KEY_LAST_EMERGENCY_ALERT = stringPreferencesKey("last_emergency_alert")
        private val KEY_LOCAL_FEED_ENABLED = booleanPreferencesKey("local_feed_enabled")
        private val KEY_LOCAL_FEED_URL = stringPreferencesKey("local_feed_url")
    }
}
