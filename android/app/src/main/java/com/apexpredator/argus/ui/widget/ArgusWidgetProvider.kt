package com.apexpredator.argus.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.apexpredator.argus.MainActivity
import com.apexpredator.argus.R
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.LocationRepository
import com.apexpredator.argus.data.OpenSkyRepository
import com.apexpredator.argus.data.PrefsStore
import com.apexpredator.argus.data.ReportKind
import com.apexpredator.argus.data.SatelliteRepository
import com.apexpredator.argus.data.WeatherRepository
import com.apexpredator.argus.decoder.MetarDecoder
import com.apexpredator.argus.decoder.describeConditions
import com.apexpredator.argus.decoder.flightCategory
import com.apexpredator.argus.decoder.flightCategoryLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Home-screen widget (v2.6): one glanceable card with the next
// satellite pass over the user's location, the nearest airborne
// aircraft, and home or watched airport weather. It updates on the
// platform widget cadence (30 minutes, the minimum update period)
// and refreshes its data on each update; a failed fetch leaves a
// plain "open Argus" line instead of stale-looking data. Tapping a
// row opens the matching tab: pass to Sky, aircraft to Radar,
// weather to Tools. Location falls back to Dhaka, the same fallback
// the Sky tab uses, when no fix is available.
class ArgusWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_argus)
            views.setTextViewText(R.id.widget_pass, "Next pass: loading...")
            views.setTextViewText(R.id.widget_aircraft, "Nearest aircraft: loading...")
            views.setTextViewText(R.id.widget_weather, "Airport weather: loading...")
            views.setOnClickPendingIntent(
                R.id.widget_pass_row, tabIntent(context, appWidgetId, "sky")
            )
            views.setOnClickPendingIntent(
                R.id.widget_aircraft_row, tabIntent(context, appWidgetId, "radar")
            )
            views.setOnClickPendingIntent(
                R.id.widget_weather_row, tabIntent(context, appWidgetId, "tools")
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            scope.launch {
                val lines = computeLines(context)
                val updated = RemoteViews(context.packageName, R.layout.widget_argus)
                updated.setTextViewText(R.id.widget_pass, lines.pass)
                updated.setTextViewText(R.id.widget_aircraft, lines.aircraft)
                updated.setTextViewText(R.id.widget_weather, lines.weather)
                updated.setOnClickPendingIntent(
                    R.id.widget_pass_row, tabIntent(context, appWidgetId, "sky")
                )
                updated.setOnClickPendingIntent(
                    R.id.widget_aircraft_row, tabIntent(context, appWidgetId, "radar")
                )
                updated.setOnClickPendingIntent(
                    R.id.widget_weather_row, tabIntent(context, appWidgetId, "tools")
                )
                appWidgetManager.updateAppWidget(appWidgetId, updated)
            }
        }
    }

    private fun tabIntent(context: Context, appWidgetId: Int, tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("argus_tab", tab)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val code = appWidgetId * 10 + tab.hashCode().let { if (it < 0) -it else it } % 1000
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class WidgetLines(
        val pass: String,
        val aircraft: String,
        val weather: String,
    )

    private suspend fun computeLines(context: Context): WidgetLines {
        val fix = LocationRepository(context).lastKnown()
        val lat = fix?.lat ?: DEFAULT_LAT
        val lon = fix?.lon ?: DEFAULT_LON
        val passLine = try {
            val repo = SatelliteRepository(context)
            repo.load()
            val now = System.currentTimeMillis()
            val next = repo.passesOver(lat, lon, hoursAhead = 24)
                .firstOrNull { it.startMs >= now }
            if (next == null) {
                "Next pass: none in the next 24 hours"
            } else {
                val fmt = SimpleDateFormat("HH:mm", Locale.US)
                "Next pass: ${next.name} rises ${fmt.format(Date(next.startMs))}, " +
                    "peaks ${next.maxElevationDeg.roundToInt()} deg"
            }
        } catch (e: Exception) {
            "Next pass: open Argus to refresh"
        }
        val aircraftLine = try {
            val states = OpenSkyRepository().fetchStates(
                BoundingBox(lat - 0.5, lon - 0.5, lat + 0.5, lon + 0.5)
            )
            val nearest = states.filter { !it.onGround }
                .map { it to GeoMath.haversineKm(lat, lon, it.latitude, it.longitude) }
                .minByOrNull { it.second }
            if (nearest == null) {
                "Nearest aircraft: none airborne nearby"
            } else {
                val (ac, dist) = nearest
                val alt = ac.altitudeFt?.roundToInt()?.let { "$it ft" } ?: "altitude unknown"
                "Nearest: ${ac.callsign ?: ac.icao24.uppercase()}, " +
                    "${dist.roundToInt()} km away, $alt"
            }
        } catch (e: Exception) {
            "Nearest aircraft: open Argus to refresh"
        }
        val weatherLine = try {
            val prefs = PrefsStore(context)
            val station = prefs.watchedAirports.first().sorted().firstOrNull() ?: "VGHS"
            val raw = WeatherRepository().fetchRaw(station, ReportKind.METAR)
            val decoded = MetarDecoder.decode(raw)
            val bits = describeConditions(decoded.conditions)
                .filter { it.startsWith("Wind") || it.startsWith("Visibility") }
                .take(2)
                .joinToString("; ")
            val category = flightCategoryLabel(flightCategory(decoded.conditions))
            "$station - $category" + if (bits.isEmpty()) "" else ": $bits"
        } catch (e: Exception) {
            "Airport weather: open Argus to refresh"
        }
        return WidgetLines(passLine, aircraftLine, weatherLine)
    }

    companion object {
        private const val DEFAULT_LAT = 23.8103
        private const val DEFAULT_LON = 90.4125
    }
}
