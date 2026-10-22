package com.apexpredator.argus.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import com.apexpredator.argus.data.Airport
import com.apexpredator.argus.data.SatellitePosition
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

// Draws propagated satellites as circled dots with name labels. Kept
// visually distinct from aircraft (white-blue, round) so the fused
// picture stays readable.
class SatelliteOverlay(
    private val onSelect: (SatellitePosition) -> Unit,
) : Overlay() {

    private var satellites: List<SatellitePosition> = emptyList()
    private val screenPos = HashMap<Int, Point>()
    private val geoPoint = GeoPoint(0.0, 0.0)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#7FD4FF")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#E8EAF0")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B9E6FF")
        textSize = 26f
    }

    fun setSatellites(list: List<SatellitePosition>) {
        satellites = list
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val density = mapView.context.resources.displayMetrics.density
        val r = 5f * density
        val showLabels = mapView.zoomLevelDouble >= 4.0
        val tmp = Point()
        screenPos.clear()
        for (sat in satellites) {
            geoPoint.latitude = sat.lat
            geoPoint.longitude = sat.lon
            projection.toPixels(geoPoint, tmp)
            val x = tmp.x.toFloat()
            val y = tmp.y.toFloat()
            screenPos[sat.noradId] = Point(tmp.x, tmp.y)
            // Four-point star: satellites read as their own class of
            // object, never confused with aircraft markers.
            val outer = r * 1.9f
            val inner = r * 0.6f
            val star = android.graphics.Path()
            star.moveTo(x, y - outer)
            star.lineTo(x + inner, y - inner)
            star.lineTo(x + outer, y)
            star.lineTo(x + inner, y + inner)
            star.lineTo(x, y + outer)
            star.lineTo(x - inner, y + inner)
            star.lineTo(x - outer, y)
            star.lineTo(x - inner, y - inner)
            star.close()
            canvas.drawPath(star, dotPaint)
            canvas.drawPath(star, ringPaint)
            if (showLabels) {
                canvas.drawText(sat.name, x + r + 4f * density, y + 4f * density, labelPaint)
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val density = mapView.context.resources.displayMetrics.density
        val hitRadius = 28f * density
        var best: SatellitePosition? = null
        var bestDist = Float.MAX_VALUE
        for (sat in satellites) {
            val p = screenPos[sat.noradId] ?: continue
            val d = hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
            if (d < hitRadius && d < bestDist) {
                bestDist = d
                best = sat
            }
        }
        return if (best != null) {
            onSelect(best)
            true
        } else {
            super.onSingleTapConfirmed(e, mapView)
        }
    }
}

// Draws airports in view as small squares (large airports) or dots,
// with ident labels when zoomed in. The screen feeds it only the
// airports near the current viewport, so it never holds the full 10k.
class AirportOverlay(
    private val onSelect: (Airport) -> Unit,
) : Overlay() {

    private var airports: List<Airport> = emptyList()
    private val screenPos = HashMap<String, Point>()
    private val geoPoint = GeoPoint(0.0, 0.0)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#9AA3B2")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EAF0")
        textSize = 26f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA0D1117")
    }

    fun setAirports(list: List<Airport>) {
        airports = list
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val density = mapView.context.resources.displayMetrics.density
        val half = 5f * density
        val showLabels = mapView.zoomLevelDouble >= 8.0
        val tmp = Point()
        screenPos.clear()
        for (ap in airports) {
            geoPoint.latitude = ap.lat
            geoPoint.longitude = ap.lon
            projection.toPixels(geoPoint, tmp)
            val x = tmp.x.toFloat()
            val y = tmp.y.toFloat()
            screenPos[ap.ident] = Point(tmp.x, tmp.y)
            canvas.drawRect(x - half, y - half, x + half, y + half, fillPaint)
            if (showLabels) {
                val textWidth = labelPaint.measureText(ap.ident)
                val pad = 4f * density
                canvas.drawRect(
                    x + half, y - half - pad,
                    x + half + textWidth + pad * 2, y + pad,
                    labelBgPaint
                )
                canvas.drawText(ap.ident, x + half + pad, y - 2f * density, labelPaint)
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val density = mapView.context.resources.displayMetrics.density
        val hitRadius = 26f * density
        var best: Airport? = null
        var bestDist = Float.MAX_VALUE
        for (ap in airports) {
            val p = screenPos[ap.ident] ?: continue
            val d = hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
            if (d < hitRadius && d < bestDist) {
                bestDist = d
                best = ap
            }
        }
        return if (best != null) {
            onSelect(best)
            true
        } else {
            super.onSingleTapConfirmed(e, mapView)
        }
    }
}
