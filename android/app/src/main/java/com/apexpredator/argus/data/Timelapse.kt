package com.apexpredator.argus.data

import kotlin.math.max
import kotlin.math.min

// Pure timelapse helpers (no Android types, so they unit-test on the
// JVM). The exporter draws one canvas frame per recorded snapshot:
// aircraft as heading triangles, satellites as stars, all projected
// into a square frame fitted to the traffic itself, so the video
// always shows the action without needing the live map view.

// Geographic extent of a set of frames, padded so markers near the
// edge stay visible. Falls back to a Dhaka-centred box when a frame
// set is empty.
data class TimelapseBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

fun timelapseBounds(frames: List<Snapshot>): TimelapseBounds {
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    var any = false
    for (frame in frames) {
        for (ac in frame.aircraft) {
            minLat = min(minLat, ac.latitude)
            maxLat = max(maxLat, ac.latitude)
            minLon = min(minLon, ac.longitude)
            maxLon = max(maxLon, ac.longitude)
            any = true
        }
        for (sat in frame.satellites) {
            minLat = min(minLat, sat.lat)
            maxLat = max(maxLat, sat.lat)
            minLon = min(minLon, sat.lon)
            maxLon = max(maxLon, sat.lon)
            any = true
        }
    }
    if (!any) return TimelapseBounds(21.0, 26.0, 88.0, 93.0)
    // Square the box (the video frame is square) and pad by 10%.
    val latSpan = max(maxLat - minLat, 0.05)
    val lonSpan = max(maxLon - minLon, 0.05)
    val span = max(latSpan, lonSpan)
    val midLat = (minLat + maxLat) / 2.0
    val midLon = (minLon + maxLon) / 2.0
    val half = span * 0.55
    return TimelapseBounds(midLat - half, midLat + half, midLon - half, midLon + half)
}

// Projects a geographic point into pixel coordinates of a square
// frame of the given size. North is up, east is right.
fun projectToFrame(
    lat: Double,
    lon: Double,
    bounds: TimelapseBounds,
    size: Int
): Pair<Float, Float> {
    val latSpan = (bounds.maxLat - bounds.minLat).takeIf { it > 0 } ?: 1.0
    val lonSpan = (bounds.maxLon - bounds.minLon).takeIf { it > 0 } ?: 1.0
    val x = ((lon - bounds.minLon) / lonSpan * size).toFloat()
    val y = ((bounds.maxLat - lat) / latSpan * size).toFloat()
    return x to y
}

// Evenly samples a long recording down to at most maxFrames frames,
// always keeping the first and last frame so the video spans the
// whole recorded period.
fun selectTimelapseFrames(
    frames: List<Snapshot>,
    maxFrames: Int
): List<Snapshot> {
    if (frames.isEmpty() || maxFrames <= 0) return emptyList()
    if (frames.size <= maxFrames) return frames
    val step = frames.size.toDouble() / maxFrames
    val out = ArrayList<Snapshot>(maxFrames)
    var i = 0
    while (out.size < maxFrames - 1) {
        out.add(frames[(i * step).toInt().coerceAtMost(frames.lastIndex)])
        i++
    }
    out.add(frames.last())
    return out
}
