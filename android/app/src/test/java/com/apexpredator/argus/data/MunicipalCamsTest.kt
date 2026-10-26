package com.apexpredator.argus.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Parser tests for the municipal camera mesh. Samples mirror the
// real feed shapes: DelDOT's videoCameras array, Caltrans D4's
// data[].cctv objects, and Calgary's Socrata rows, as fetched
// 2026-10-26.
class MunicipalCamsTest {

    @Test
    fun delDotKeepsOnlyActiveAndCleansTitles() {
        val root = JSONObject(
            """
            {
              "cameraCount": 3,
              "videoCameras": [
                {
                  "id": "KCAM001", "title": "DE\n 1 @ MILFORD NECK ROAD",
                  "county": "Kent", "lat": 38.99, "lon": -75.44,
                  "status": "Active",
                  "urls": {"m3u8s": "https://video.deldot.gov/live/x/playlist.m3u8"}
                },
                {
                  "id": "KCAM002", "title": "DOWN CAM",
                  "county": "Kent", "lat": 39.15, "lon": -75.54,
                  "status": "Unavailable",
                  "urls": {"m3u8s": "https://video.deldot.gov/live/y/playlist.m3u8"}
                },
                {
                  "id": "KCAM003", "title": "NO COORDS",
                  "county": "Kent", "status": "Active",
                  "urls": {"m3u8s": "https://video.deldot.gov/live/z/playlist.m3u8"}
                }
              ]
            }
            """.trimIndent()
        )
        val cams = parseDelDot(root)
        assertEquals(1, cams.size)
        assertEquals("KCAM001", cams[0].id)
        assertEquals("DE 1 @ MILFORD NECK ROAD", cams[0].name)
        assertEquals("DelDOT", cams[0].network)
        assertEquals(
            "https://video.deldot.gov/live/x/playlist.m3u8",
            cams[0].videoUrl
        )
        assertNull(cams[0].imageUrl)
    }

    @Test
    fun caltransKeepsOnlyInServiceAndNullsMissingStreams() {
        val root = JSONObject(
            """
            {
              "data": [
                {"cctv": {
                  "index": "1",
                  "location": {
                    "locationName": "TV102 -- I-580 : West of SR-24",
                    "nearbyPlace": "Oakland",
                    "longitude": "-122.27291", "latitude": "37.82539"
                  },
                  "inService": "true",
                  "imageData": {
                    "streamingVideoURL": "https://wzmedia.dot.ca.gov/D4/x.stream/playlist.m3u8",
                    "static": {"currentImageURL": "https://cwwp2.dot.ca.gov/data/d4/cctv/image/a/a.jpg"}
                  }
                }},
                {"cctv": {
                  "index": "2",
                  "location": {
                    "locationName": "TV709 -- SR-238",
                    "nearbyPlace": "San Lorenzo",
                    "longitude": "-122.12463", "latitude": "37.69022"
                  },
                  "inService": "true",
                  "imageData": {
                    "streamingVideoURL": "Not Reported",
                    "static": {"currentImageURL": "https://cwwp2.dot.ca.gov/data/d4/cctv/image/b/b.jpg"}
                  }
                }},
                {"cctv": {
                  "index": "3",
                  "location": {
                    "locationName": "DEAD CAM",
                    "nearbyPlace": "Fremont",
                    "longitude": "-122.0", "latitude": "37.5"
                  },
                  "inService": "false",
                  "imageData": {
                    "streamingVideoURL": "",
                    "static": {"currentImageURL": "https://example.com/c.jpg"}
                  }
                }}
              ]
            }
            """.trimIndent()
        )
        val cams = parseCaltransD4(root)
        assertEquals(2, cams.size)
        assertEquals("TV102 -- I-580 : West of SR-24", cams[0].name)
        assertEquals("Oakland", cams[0].city)
        assertEquals(
            "https://wzmedia.dot.ca.gov/D4/x.stream/playlist.m3u8",
            cams[0].videoUrl
        )
        assertNull(cams[1].videoUrl)
        assertEquals(
            "https://cwwp2.dot.ca.gov/data/d4/cctv/image/b/b.jpg",
            cams[1].imageUrl
        )
    }

    @Test
    fun calgaryReadsGeoJsonOrderAndSnapshotUrl() {
        val arr = org.json.JSONArray(
            """
            [
              {
                "camera_url": {
                  "url": "https://trafficcam.calgary.ca/loc29.jpg",
                  "description": "Camera 30"
                },
                "quadrant": "SE",
                "camera_location": "114 Avenue / 52 Street SE",
                "point": {
                  "type": "Point",
                  "coordinates": [-113.958331, 50.9504395]
                }
              },
              {
                "camera_url": {"url": "", "description": "Camera X"},
                "camera_location": "Nowhere",
                "point": {"type": "Point", "coordinates": [0.0, 0.0]}
              }
            ]
            """.trimIndent()
        )
        val cams = parseCalgary(arr)
        assertEquals(1, cams.size)
        assertEquals("114 Avenue / 52 Street SE", cams[0].name)
        assertEquals("Calgary", cams[0].city)
        assertEquals("Calgary", cams[0].network)
        // GeoJSON order is longitude first: lat comes second.
        assertEquals(50.9504395, cams[0].lat, 0.000001)
        assertEquals(-113.958331, cams[0].lon, 0.000001)
        assertEquals(
            "https://trafficcam.calgary.ca/loc29.jpg",
            cams[0].imageUrl
        )
        assertNull(cams[0].videoUrl)
    }
}
