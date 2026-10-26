package com.apexpredator.argus.ui

import android.graphics.Canvas
import android.graphics.Point
import android.view.MotionEvent
import com.apexpredator.argus.data.MunicipalCam
import com.apexpredator.argus.data.PUBLIC_CAMS
import com.apexpredator.argus.data.PublicCam
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

// The godseyeview layer: free public cameras drawn on the same map
// as flights and satellites. Two kinds of pin: blue for the bundled
// tourist webcams, orange for the live municipal CCTV mesh. The mesh
// is dense (hundreds of pins per city), so below zoom 10 it clusters
// into count badges per screen cell; tapping a badge zooms into it,
// tapping a pin hands the camera to the player.
class CameraOverlay(
    private val visible: () -> Boolean,
    private val municipalCams: () -> List<MunicipalCam> = { emptyList() },
    private val onCamTap: (PublicCam) -> Unit,
    private val onMunicipalTap: (MunicipalCam) -> Unit = {},
) : Overlay() {

    private val pin = cameraMarkerBitmap()
    private val meshPin = municipalMarkerBitmap()
    private val halfW = pin.width / 2f
    private val halfH = pin.height.toFloat()

    private val touristPos = HashMap<PublicCam, Point>()
    private val meshPos = HashMap<MunicipalCam, Point>()
    private val clusterPos = HashMap<GeoPoint, Point>()
    private val clusterMembers = HashMap<GeoPoint, List<MunicipalCam>>()
    private val clusterBadges = HashMap<Int, android.graphics.Bitmap>()
    private val tmp = Point()

    // Below this zoom the mesh clusters; at and above it every pin
    // draws on its own.
    private val clusterBelowZoom = 10.0
    private val cellPx = 72

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible()) return
        val projection = mapView.projection
        val bounds = mapView.boundingBox
        touristPos.clear()
        meshPos.clear()
        clusterPos.clear()
        clusterMembers.clear()
        for (cam in PUBLIC_CAMS) {
            if (cam.lat < bounds.latSouth || cam.lat > bounds.latNorth ||
                cam.lon < bounds.lonWest || cam.lon > bounds.lonEast
            ) {
                continue
            }
            projection.toPixels(GeoPoint(cam.lat, cam.lon), tmp)
            val pos = Point(tmp.x, tmp.y)
            touristPos[cam] = pos
            canvas.drawBitmap(pin, pos.x - halfW, pos.y - halfH, null)
        }
        val mesh = municipalCams().filter {
            it.lat >= bounds.latSouth && it.lat <= bounds.latNorth &&
                it.lon >= bounds.lonWest && it.lon <= bounds.lonEast
        }
        if (mapView.zoomLevelDouble >= clusterBelowZoom) {
            for (cam in mesh) {
                projection.toPixels(GeoPoint(cam.lat, cam.lon), tmp)
                val pos = Point(tmp.x, tmp.y)
                meshPos[cam] = pos
                canvas.drawBitmap(meshPin, pos.x - halfW, pos.y - halfH, null)
            }
        } else {
            val cells = LinkedHashMap<Pair<Int, Int>, MutableList<MunicipalCam>>()
            val cellCenter = HashMap<Pair<Int, Int>, Point>()
            for (cam in mesh) {
                projection.toPixels(GeoPoint(cam.lat, cam.lon), tmp)
                val key = tmp.x / cellPx to tmp.y / cellPx
                cells.getOrPut(key) { ArrayList() }.add(cam)
                cellCenter[key] = Point(tmp.x, tmp.y)
            }
            for ((key, members) in cells) {
                val center = cellCenter.getValue(key)
                if (members.size == 1) {
                    meshPos[members[0]] = center
                    canvas.drawBitmap(
                        meshPin, center.x - halfW, center.y - halfH, null
                    )
                } else {
                    val badge = clusterBadges.getOrPut(members.size) {
                        clusterMarkerBitmap(members.size)
                    }
                    val geo = GeoPoint(
                        members.sumOf { it.lat } / members.size,
                        members.sumOf { it.lon } / members.size
                    )
                    clusterPos[geo] = center
                    clusterMembers[geo] = members
                    canvas.drawBitmap(
                        badge, center.x - halfW, center.y - halfH, null
                    )
                }
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!visible()) return false
        var bestDist = Float.MAX_VALUE
        var bestTourist: PublicCam? = null
        var bestMesh: MunicipalCam? = null
        var bestCluster: GeoPoint? = null
        fun distTo(pos: Point): Float = hypot(
            (e.x - pos.x).toDouble(), (e.y - pos.y).toDouble()
        ).toFloat()
        for ((cam, pos) in touristPos) {
            val d = distTo(pos)
            if (d < bestDist) {
                bestDist = d
                bestTourist = cam
            }
        }
        for ((cam, pos) in meshPos) {
            val d = distTo(pos)
            if (d < bestDist) {
                bestDist = d
                bestMesh = cam
                bestTourist = null
            }
        }
        for ((geo, pos) in clusterPos) {
            val d = distTo(pos)
            if (d < bestDist) {
                bestDist = d
                bestCluster = geo
                bestMesh = null
                bestTourist = null
            }
        }
        // The nearest target wins; a cluster tap zooms into it.
        if (bestCluster != null && bestDist <= 72f) {
            mapView.controller.setZoom(mapView.zoomLevelDouble + 2.0)
            mapView.controller.setCenter(bestCluster)
            return true
        }
        if (bestMesh != null && bestDist <= 64f) {
            onMunicipalTap(bestMesh)
            return true
        }
        if (bestTourist != null && bestDist <= 64f) {
            onCamTap(bestTourist)
            return true
        }
        return false
    }
}
