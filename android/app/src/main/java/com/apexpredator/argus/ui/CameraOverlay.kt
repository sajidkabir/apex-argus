package com.apexpredator.argus.ui

import android.graphics.Canvas
import android.graphics.Point
import android.view.MotionEvent
import com.apexpredator.argus.data.PUBLIC_CAMS
import com.apexpredator.argus.data.PublicCam
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

// The godseyeview layer for the radar: free public cameras drawn on
// the same map as flights and satellites. Tapping a pin hands the
// camera to the Cams player.
class CameraOverlay(
    private val visible: () -> Boolean,
    private val onCamTap: (PublicCam) -> Unit,
) : Overlay() {

    private val pin = cameraMarkerBitmap()
    private val halfW = pin.width / 2f
    private val halfH = pin.height.toFloat()
    private val screenPos = HashMap<PublicCam, Point>()
    private val tmp = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible()) return
        val projection = mapView.projection
        val bounds = mapView.boundingBox
        screenPos.clear()
        for (cam in PUBLIC_CAMS) {
            if (cam.lat < bounds.latSouth || cam.lat > bounds.latNorth ||
                cam.lon < bounds.lonWest || cam.lon > bounds.lonEast
            ) {
                continue
            }
            projection.toPixels(GeoPoint(cam.lat, cam.lon), tmp)
            val pos = Point(tmp.x, tmp.y)
            screenPos[cam] = pos
            canvas.drawBitmap(pin, pos.x - halfW, pos.y - halfH, null)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!visible()) return false
        var best: PublicCam? = null
        var bestDist = Float.MAX_VALUE
        for ((cam, pos) in screenPos) {
            val d = hypot(
                (e.x - pos.x).toDouble(),
                (e.y - pos.y).toDouble()
            ).toFloat()
            if (d < bestDist) {
                bestDist = d
                best = cam
            }
        }
        if (best != null && bestDist <= 64f) {
            onCamTap(best)
            return true
        }
        return false
    }
}
