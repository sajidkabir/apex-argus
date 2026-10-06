package com.apexpredator.argus.radar

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.BoundingBox
import com.apexpredator.argus.data.OpenSkyRepository
import com.apexpredator.argus.data.parseAircraftStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AircraftStateTest {

    private val sampleBody = """
        {
          "time": 1790000000,
          "states": [
            ["a1b2c3", "BAW123  ", "United Kingdom", 1790000000, 1790000001,
             10.5, 20.5, 10000.0, false, 250.0, 90.0, 2.5, null, 10200.0, "1234", false, 0],
            ["d4e5f6", null, "Bangladesh", null, 1790000001,
             null, null, null, true, null, null, null, null, null, null, false, 0],
            ["789abc", "", "United States", 1790000000, 1790000001,
             -73.5, 40.5, 3500.0, false, 120.0, 270.0, -3.0, null, 3600.0, null, false, 0]
          ]
        }
    """.trimIndent()

    @Test
    fun parsesValidStateVectors() {
        val states = parseAircraftStates(sampleBody)
        // The vector without a position is dropped.
        assertEquals(2, states.size)

        val first = states[0]
        assertEquals("a1b2c3", first.icao24)
        assertEquals("BAW123", first.callsign)
        assertEquals("United Kingdom", first.originCountry)
        assertEquals(10.5, first.longitude, 1e-9)
        assertEquals(20.5, first.latitude, 1e-9)
        assertEquals("1234", first.squawk)
    }

    @Test
    fun convertsUnitsCorrectly() {
        val first = parseAircraftStates(sampleBody)[0]
        assertTrue(abs(first.altitudeFt!! - 10000.0 * AircraftState.M_TO_FT) < 0.01)
        assertTrue(abs(first.speedKt!! - 250.0 * AircraftState.MS_TO_KT) < 0.01)
        assertTrue(abs(first.verticalRateFpm!! - 2.5 * AircraftState.MS_TO_FPM) < 0.01)
    }

    @Test
    fun handlesNullsAndBlankCallsign() {
        val states = parseAircraftStates(sampleBody)
        val third = states[1]
        assertNull(third.callsign)
        assertNull(third.squawk)
        assertTrue(abs(third.verticalRateFpm!! + 3.0 * AircraftState.MS_TO_FPM) < 0.01)
    }

    @Test
    fun emptyStatesArrayGivesEmptyList() {
        assertTrue(parseAircraftStates("""{"time":1,"states":[]}""").isEmpty())
        assertTrue(parseAircraftStates("""{"time":1}""").isEmpty())
    }

    @Test
    fun clampKeepsSmallBoxesUntouched() {
        val repo = OpenSkyRepository()
        val bbox = BoundingBox(23.0, 90.0, 24.0, 91.0)
        assertEquals(bbox, repo.clampToBudget(bbox))
    }

    @Test
    fun clampShrinksLargeBoxesToFiveDegrees() {
        val repo = OpenSkyRepository()
        val clamped = repo.clampToBudget(BoundingBox(10.0, 80.0, 40.0, 110.0))
        assertTrue(abs((clamped.lamax - clamped.lamin) - 5.0) < 1e-9)
        assertTrue(abs((clamped.lomax - clamped.lomin) - 5.0) < 1e-9)
        // Still centered on the requested area.
        assertTrue(abs((clamped.lamin + clamped.lamax) / 2.0 - 25.0) < 1e-9)
        assertTrue(abs((clamped.lomin + clamped.lomax) / 2.0 - 95.0) < 1e-9)
    }
}
