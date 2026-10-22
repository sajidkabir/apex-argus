package com.apexpredator.argus.data

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

// Live precipitation radar tiles from RainViewer, a free public
// weather-radar mosaic (the same layer paid flight trackers charge
// for). The API hands out a tile path per radar frame; the newest past
// frame is close enough to live for a flight map.
class RainViewerRepository {

    // Returns the tile path of the newest radar frame, or null.
    suspend fun latestRadarPath(): String? {
        return try {
            val root = HttpJson.getJson(API_URL)
            val past = root.optJSONObject("radar")?.optJSONArray("past")
            if (past == null || past.length() == 0) return null
            past.getJSONObject(past.length() - 1).optStringOrNull("path")
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val API_URL = "https://api.rainviewer.com/public/weather-maps.json"
        const val HOST = "https://tilecache.rainviewer.com"
    }
}

// Builds an osmdroid tile source for one RainViewer frame. Option "2"
// is the universal blue precipitation palette, "1_1" is color with
// smoothing, matching what the layer looks like in the paid apps.
fun rainViewerTileSource(path: String): XYTileSource {
    return object : XYTileSource(
        "RainViewer",
        1,
        18,
        256,
        ".png",
        arrayOf(RainViewerRepository.HOST),
    ) {
        override fun getTileURLString(pTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pTileIndex)
            val x = MapTileIndex.getX(pTileIndex)
            val y = MapTileIndex.getY(pTileIndex)
            return "${RainViewerRepository.HOST}$path/256/$zoom/$x/$y/2/1_1.png"
        }
    }
}
