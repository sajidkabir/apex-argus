package com.apexpredator.argus.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import com.apexpredator.argus.data.AircraftState
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

// Draws live aircraft as heading-rotated triangles on the osmdroid map.
// Tap detection reuses the last drawn screen positions, so it stays in
// sync with the current zoom and pan without extra projection math.
class AircraftOverlay(
    private val onSelect: (AircraftState) -> Unit,
) : Overlay() {

    private var aircraft: List<AircraftState> = emptyList()
    private val screenPos = HashMap<String, Point>()
    private val geoPoint = GeoPoint(0.0, 0.0)

    private val planePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C79A3B")
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#757575")
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#0D1117")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EAF0")
        textSize = 30f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA0D1117")
    }

    fun setAircraft(list: List<AircraftState>) {
        aircraft = list
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val showLabels = mapView.zoomLevelDouble >= 8.0
        val density = mapView.context.resources.displayMetrics.density
        val size = 14f * density
        val tmp = Point()

        screenPos.clear()
        for (ac in aircraft) {
            geoPoint.latitude = ac.latitude
            geoPoint.longitude = ac.longitude
            projection.toPixels(geoPoint, tmp)
            val x = tmp.x.toFloat()
            val y = tmp.y.toFloat()
            screenPos[ac.icao24] = Point(tmp.x, tmp.y)

            val paint = if (ac.onGround) groundPaint else planePaint
            val path = Path().apply {
                moveTo(x, y - size)
                lineTo(x + size * 0.7f, y + size * 0.7f)
                lineTo(x, y + size * 0.3f)
                lineTo(x - size * 0.7f, y + size * 0.7f)
                close()
            }
            canvas.save()
            canvas.rotate((ac.trueTrackDeg ?: 0.0).toFloat(), x, y)
            canvas.drawPath(path, paint)
            canvas.drawPath(path, outlinePaint)
            canvas.restore()

            val label = ac.callsign ?: ac.icao24.uppercase()
            if (showLabels) {
                val textWidth = labelPaint.measureText(label)
                val pad = 6f * density
                canvas.drawRect(
                    x + size, y - size - pad,
                    x + size + textWidth + pad * 2, y + pad,
                    labelBgPaint
                )
                canvas.drawText(label, x + size + pad, y - 4f * density, labelPaint)
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val density = mapView.context.resources.displayMetrics.density
        val hitRadius = 32f * density
        var best: AircraftState? = null
        var bestDist = Float.MAX_VALUE
        for (ac in aircraft) {
            val p = screenPos[ac.icao24] ?: continue
            val d = hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
            if (d < hitRadius && d < bestDist) {
                bestDist = d
                best = ac
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
