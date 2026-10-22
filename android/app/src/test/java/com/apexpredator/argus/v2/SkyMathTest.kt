package com.apexpredator.argus.v2

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.Airport
import com.apexpredator.argus.data.SkyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SkyMathTest {

    @Test
    fun elevationOfCruisingAircraftNearby() {
        // 11 km up, 20 km away on the ground: about 28 degrees up.
        val el = SkyMath.elevationDeg(20.0, 11_000.0, 20.0)
        assertTrue("expected ~28.8, got $el", abs(el - 28.76) < 0.5)
    }

    @Test
    fun elevationDirectlyOverhead() {
        assertEquals(90.0, SkyMath.elevationDeg(0.0, 10_000.0, 0.0), 0.01)
    }

    @Test
    fun headingDiffWraps() {
        assertEquals(20.0, SkyMath.headingDiffDeg(350.0, 10.0), 0.01)
        assertEquals(20.0, SkyMath.headingDiffDeg(10.0, 350.0), 0.01)
        assertEquals(180.0, SkyMath.headingDiffDeg(0.0, 180.0), 0.01)
    }

    @Test
    fun viewOffsetsCentreAndQuadrants() {
        val (r0, u0) = SkyMath.viewOffsets(90.0, 20.0, 90.0, 20.0)
        assertEquals(0.0, r0, 0.01)
        assertEquals(0.0, u0, 0.01)
        val (rEast, _) = SkyMath.viewOffsets(0.0, 0.0, 30.0, 0.0)
        assertTrue(rEast > 0)
        val (_, uUp) = SkyMath.viewOffsets(0.0, 0.0, 0.0, 40.0)
        assertTrue(uUp > 0)
    }

    @Test
    fun projectionCentreAndOutOfFrame() {
        val c = SkyMath.projectToScreen(0.0, 0.0, 46.0, 62.0)
        assertNotNull(c)
        assertEquals(0.5f, c!!.first, 0.001f)
        assertEquals(0.5f, c.second, 0.001f)
        assertNull(SkyMath.projectToScreen(120.0, 0.0, 46.0, 62.0))
    }

    @Test
    fun compassLabels() {
        assertEquals("N", SkyMath.compassLabel(0.0))
        assertEquals("E", SkyMath.compassLabel(90.0))
        assertEquals("SW", SkyMath.compassLabel(225.0))
        assertEquals("N", SkyMath.compassLabel(359.0))
    }

    private val dhaka = Airport(
        ident = "VGHS", iata = "DAC", name = "Hazrat Shahjalal Intl",
        city = "Dhaka", country = "BD", lat = 23.8433, lon = 90.3978,
        elevationFt = 30, type = "large_airport"
    )

    private fun aircraft(
        lat: Double, lon: Double, altM: Double, track: Double, vr: Double,
    ) = AircraftState(
        icao24 = "abc123", callsign = "TEST1", originCountry = "Bangladesh",
        longitude = lon, latitude = lat, baroAltitudeM = altM,
        onGround = false, velocityMs = 120.0, trueTrackDeg = track,
        verticalRateMs = vr, squawk = null,
    )

    @Test
    fun verdictDetectsApproach() {
        // 25 km north of VGHS, flying south toward it, descending.
        val ac = aircraft(24.07, 90.3978, 2_500.0, 180.0, -4.0)
        val verdict = SkyMath.movementVerdict(ac, dhaka, 25.0)
        assertNotNull(verdict)
        assertTrue(verdict!!.contains("approach"))
    }

    @Test
    fun verdictDetectsDeparture() {
        // 10 km south of VGHS, flying away south, climbing.
        val ac = aircraft(23.75, 90.3978, 1_500.0, 180.0, 6.0)
        val verdict = SkyMath.movementVerdict(ac, dhaka, 10.0)
        assertNotNull(verdict)
        assertTrue(verdict!!.contains("Departed"))
    }

    @Test
    fun verdictSilentForUnrelatedCruise() {
        // 50 km east, flying north at cruise: no verdict.
        val ac = aircraft(23.84, 90.90, 11_000.0, 0.0, 0.0)
        assertNull(SkyMath.movementVerdict(ac, dhaka, 50.0))
    }
}
