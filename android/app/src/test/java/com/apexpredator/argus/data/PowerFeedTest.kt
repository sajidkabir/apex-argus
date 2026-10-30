package com.apexpredator.argus.v2

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.formatBytes
import com.apexpredator.argus.data.isPackStale
import com.apexpredator.argus.data.isValidLocalFeedUrl
import com.apexpredator.argus.data.mergeAircraft
import com.apexpredator.argus.data.normalizeLocalFeedUrl
import com.apexpredator.argus.data.packAgeText
import com.apexpredator.argus.data.parseDump1090
import com.apexpredator.argus.data.parsePackAirports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PowerFeedTest {

    private val nowMs = 1_700_000_000_000L

    private val sampleFeed = """
        {
          "now": 1700000000.0,
          "aircraft": [
            {
              "hex": "abc123", "flight": "BAW123  ",
              "lat": 23.9, "lon": 90.4, "alt_baro": 35000,
              "gs": 450.0, "track": 90.0, "squawk": "1200",
              "seen_pos": 2.0, "seen": 2.0
            },
            {
              "hex": "def456", "flight": "GND1",
              "lat": 23.8, "lon": 90.4, "alt_baro": "ground",
              "seen_pos": 1.0
            },
            { "hex": "nopos1", "flight": "NOPOS", "alt_baro": 10000 },
            { "flight": "NOHEX", "lat": 23.8, "lon": 90.4 }
          ]
        }
    """.trimIndent()

    @Test
    fun dump1090ParsesPositionsAndConvertsUnits() {
        val parsed = parseDump1090(sampleFeed, nowMs)
        assertEquals(2, parsed.size)
        val air = parsed[0]
        assertEquals("abc123", air.icao24)
        assertEquals("BAW123", air.callsign)
        assertTrue(air.isLocal)
        assertEquals("1200", air.squawk)
        assertEquals(nowMs - 2000, air.lastSeenMs)
        assertTrue(abs((air.baroAltitudeM ?: 0.0) - 35000 * 0.3048) < 0.01)
        assertTrue(abs((air.velocityMs ?: 0.0) - 450.0 / 1.94384) < 0.01)
        assertEquals(90.0, air.trueTrackDeg)
    }

    @Test
    fun dump1090GroundFlagAndDroppedEntries() {
        val parsed = parseDump1090(sampleFeed, nowMs)
        val ground = parsed[1]
        assertTrue(ground.onGround)
        assertNull(ground.baroAltitudeM)
        assertEquals(nowMs - 1000, ground.lastSeenMs)
    }

    @Test
    fun mergePrefersFresherLocalAndKeepsFresherOpensky() {
        val openskyOld = state("abc123", lastSeenMs = 1000L, isLocal = false)
        val localNew = state("abc123", lastSeenMs = 2000L, isLocal = true)
        val mergedNew = mergeAircraft(listOf(openskyOld), listOf(localNew))
        assertEquals(1, mergedNew.size)
        assertTrue(mergedNew[0].isLocal)

        val openskyNew = state("abc123", lastSeenMs = 3000L, isLocal = false)
        val mergedOld = mergeAircraft(listOf(openskyNew), listOf(localNew))
        assertEquals(1, mergedOld.size)
        assertFalse(mergedOld[0].isLocal)
    }

    @Test
    fun mergeTreatsMissingTimestampAsTieWonByLocal() {
        val opensky = state("ABC123", lastSeenMs = null, isLocal = false)
        val local = state("abc123", lastSeenMs = null, isLocal = true)
        val merged = mergeAircraft(listOf(opensky), listOf(local))
        assertEquals(1, merged.size)
        assertTrue(merged[0].isLocal)
    }

    @Test
    fun mergeAppendsLocalOnlyAircraft() {
        val opensky = state("aaa111", lastSeenMs = 1000L, isLocal = false)
        val local = state("bbb222", lastSeenMs = 1000L, isLocal = true)
        val merged = mergeAircraft(listOf(opensky), listOf(local))
        assertEquals(2, merged.size)
        assertTrue(merged[1].isLocal)
    }

    @Test
    fun feedUrlValidationAndNormalization() {
        assertTrue(isValidLocalFeedUrl("http://192.168.1.10:8080/data/aircraft.json"))
        assertTrue(isValidLocalFeedUrl("https://example.com/data/aircraft.json"))
        assertTrue(isValidLocalFeedUrl(normalizeLocalFeedUrl("192.168.1.10:8080/data/aircraft.json")))
        assertEquals(
            "http://192.168.1.10:8080/data/aircraft.json",
            normalizeLocalFeedUrl("192.168.1.10:8080/data/aircraft.json")
        )
        assertFalse(isValidLocalFeedUrl(""))
        assertFalse(isValidLocalFeedUrl("ftp://192.168.1.10/data/aircraft.json"))
        assertFalse(isValidLocalFeedUrl("http://192.168.1.10:8080/data/aircraft.json with space"))
    }

    @Test
    fun regionPackHelpers() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2.4 MB", formatBytes(2_500_000))
        assertEquals(
            listOf("VGHS", "KJFK"),
            parsePackAirports("vghs, KJFK, BAD, vghs, XX")
        )
        assertTrue(isPackStale(nowMs - 8 * 24 * 60 * 60 * 1000, nowMs))
        assertFalse(isPackStale(nowMs - 24 * 60 * 60 * 1000, nowMs))
        assertEquals("today", packAgeText(nowMs, nowMs))
        assertEquals("yesterday", packAgeText(nowMs - 24 * 60 * 60 * 1000, nowMs))
        assertEquals("5 days ago", packAgeText(nowMs - 5 * 24 * 60 * 60 * 1000, nowMs))
    }

    private fun state(
        icao24: String,
        lastSeenMs: Long?,
        isLocal: Boolean,
    ) = AircraftState(
        icao24 = icao24,
        callsign = "TEST",
        originCountry = "Test",
        longitude = 90.4,
        latitude = 23.9,
        baroAltitudeM = 10000.0,
        onGround = false,
        velocityMs = 200.0,
        trueTrackDeg = 90.0,
        verticalRateMs = 0.0,
        squawk = "1200",
        category = null,
        isLocal = isLocal,
        lastSeenMs = lastSeenMs,
    )
}
