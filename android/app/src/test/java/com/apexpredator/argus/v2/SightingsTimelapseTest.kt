package com.apexpredator.argus.v2

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.SatellitePosition
import com.apexpredator.argus.data.Snapshot
import com.apexpredator.argus.data.projectToFrame
import com.apexpredator.argus.data.selectTimelapseFrames
import com.apexpredator.argus.data.timelapseBounds
import com.apexpredator.argus.nostr.NostrEvent
import com.apexpredator.argus.nostr.SIGHTING_HASHTAG
import com.apexpredator.argus.nostr.buildSightingContent
import com.apexpredator.argus.nostr.buildSightingTags
import com.apexpredator.argus.nostr.parseSighting
import com.apexpredator.argus.nostr.validateSightingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SightingsTimelapseTest {

    private fun ac(lat: Double, lon: Double, callsign: String? = null) =
        AircraftState(
            icao24 = "abc123",
            callsign = callsign,
            originCountry = "Bangladesh",
            longitude = lon,
            latitude = lat,
            baroAltitudeM = 10000.0,
            onGround = false,
            velocityMs = 250.0,
            trueTrackDeg = 90.0,
            verticalRateMs = 0.0,
            squawk = null
        )

    private fun sat(name: String, lat: Double, lon: Double) =
        SatellitePosition(
            name = name,
            noradId = 25544,
            group = "stations",
            lat = lat,
            lon = lon,
            altitudeKm = 420.0,
            velocityKmS = 7.66
        )

    @Test
    fun boundsCoverAircraftAndSatellites() {
        val frames = listOf(
            Snapshot(1000L, listOf(ac(23.8, 90.4)), listOf(sat("ISS", 24.5, 91.0))),
            Snapshot(2000L, listOf(ac(23.9, 90.5)), emptyList())
        )
        val bounds = timelapseBounds(frames)
        assertTrue(bounds.minLat <= 23.8)
        assertTrue(bounds.maxLat >= 24.5)
        assertTrue(bounds.minLon <= 90.4)
        assertTrue(bounds.maxLon >= 91.0)
    }

    @Test
    fun projectionPutsNorthUpAndEastRight() {
        val bounds = timelapseBounds(
            listOf(Snapshot(1000L, listOf(ac(20.0, 80.0), ac(30.0, 90.0))))
        )
        val (xSouth, ySouth) = projectToFrame(20.0, 80.0, bounds, 720)
        val (xNorth, yNorth) = projectToFrame(30.0, 90.0, bounds, 720)
        assertTrue(yNorth < ySouth)
        assertTrue(xNorth > xSouth)
    }

    @Test
    fun frameSelectionKeepsFirstAndLast() {
        val frames = (0 until 60).map { Snapshot(it * 1000L, listOf(ac(23.8, 90.4))) }
        val chosen = selectTimelapseFrames(frames, 10)
        assertEquals(10, chosen.size)
        assertEquals(0L, chosen.first().epochMs)
        assertEquals(59000L, chosen.last().epochMs)
    }

    @Test
    fun frameSelectionKeepsAllWhenShort() {
        val frames = (0 until 5).map { Snapshot(it * 1000L, listOf(ac(23.8, 90.4))) }
        assertEquals(5, selectTimelapseFrames(frames, 30).size)
    }

    @Test
    fun sightingValidationRejectsBadPhotoLink() {
        assertNotNull(validateSightingInput("", "VGHS", ""))
        assertNotNull(validateSightingInput("BAW123", "", ""))
        assertNotNull(validateSightingInput("BAW123", "VGHS", "ftp://example.com/x.jpg"))
        assertNull(validateSightingInput("BAW123", "VGHS", "https://example.com/x.jpg"))
        assertNull(validateSightingInput("BAW123", "VGHS", ""))
    }

    @Test
    fun sightingRoundTripThroughTags() {
        val tags = buildSightingTags("BAW123", "VGHS")
        assertTrue(tags.any { it == listOf("t", SIGHTING_HASHTAG) })
        val content = buildSightingContent("BAW123", "VGHS", "Low over the city", "")
        val event = NostrEvent.create("00".repeat(32), 1, tags, content)
        val parsed = parseSighting(event, "Saji")
        assertNotNull(parsed)
        assertEquals("BAW123", parsed!!.aircraft)
        assertEquals("VGHS", parsed.location)
        assertEquals("Low over the city", parsed.note)
        assertNull(parsed.photoUrl)
    }

    @Test
    fun sightingRoundTripKeepsPhotoLink() {
        val tags = buildSightingTags("BAW123", "VGHS")
        val content = buildSightingContent(
            "BAW123", "VGHS", "", "https://example.com/photo.jpg"
        )
        val event = NostrEvent.create("00".repeat(32), 1, tags, content)
        val parsed = parseSighting(event, "Saji")
        assertNotNull(parsed)
        assertEquals("https://example.com/photo.jpg", parsed!!.photoUrl)
        assertEquals("", parsed.note)
    }

    @Test
    fun nonChannelEventIsNotASighting() {
        val event = NostrEvent.create(
            "00".repeat(32), 1, listOf(listOf("t", "other")), "Hello"
        )
        assertNull(parseSighting(event, "Saji"))
    }
}
