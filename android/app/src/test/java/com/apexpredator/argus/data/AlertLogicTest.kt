package com.apexpredator.argus.v2

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.AirportRef
import com.apexpredator.argus.data.AlertLogic
import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.PassWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertLogicTest {

    private val airportLat = 23.8103
    private val airportLon = 90.4125

    private fun heavy(
        lat: Double = 23.91,
        track: Double = 180.0,
        vr: Double = -3.0,
        altM: Double = 3000.0,
        category: Int = 6,
        squawk: String? = null,
    ) = AircraftState(
        icao24 = "abc123",
        callsign = "BAW123",
        originCountry = "United Kingdom",
        longitude = airportLon,
        latitude = lat,
        baroAltitudeM = altM,
        onGround = false,
        velocityMs = 120.0,
        trueTrackDeg = track,
        verticalRateMs = vr,
        squawk = squawk,
        category = category,
    )

    @Test
    fun heavyOnFinalIsFlagged() {
        val ac = heavy()
        val dist = GeoMath.haversineKm(airportLat, airportLon, ac.latitude, ac.longitude)
        assertTrue(AlertLogic.isHeavyOnFinal(ac, airportLat, airportLon, dist))
    }

    @Test
    fun nonHeavyIsNotFlagged() {
        val ac = heavy(category = 2)
        val dist = GeoMath.haversineKm(airportLat, airportLon, ac.latitude, ac.longitude)
        assertFalse(AlertLogic.isHeavyOnFinal(ac, airportLat, airportLon, dist))
    }

    @Test
    fun climbingHeavyIsNotFlagged() {
        val ac = heavy(vr = 2.0)
        val dist = GeoMath.haversineKm(airportLat, airportLon, ac.latitude, ac.longitude)
        assertFalse(AlertLogic.isHeavyOnFinal(ac, airportLat, airportLon, dist))
    }

    @Test
    fun heavyHeadingAwayIsNotFlagged() {
        val ac = heavy(track = 0.0)
        val dist = GeoMath.haversineKm(airportLat, airportLon, ac.latitude, ac.longitude)
        assertFalse(AlertLogic.isHeavyOnFinal(ac, airportLat, airportLon, dist))
    }

    @Test
    fun heavyTooFarIsNotFlagged() {
        val ac = heavy()
        assertFalse(AlertLogic.isHeavyOnFinal(ac, airportLat, airportLon, 120.0))
    }

    @Test
    fun emergenciesWithinRadiusOnly() {
        val near = heavy(squawk = "7700")
        val far = heavy(lat = 25.5, squawk = "7600")
        val normal = heavy(squawk = "1234")
        val found = AlertLogic.emergenciesWithin(
            listOf(near, far, normal), airportLat, airportLon
        )
        assertEquals(1, found.size)
        assertEquals("7700", found[0].first.squawk)
    }

    @Test
    fun passStartingWithinTenMinutes() {
        val now = 1_790_000_000_000L
        val soon = PassWindow("ISS", now + 5 * 60_000, now + 10 * 60_000, 54.0)
        val later = PassWindow("CSS", now + 30 * 60_000, now + 35 * 60_000, 40.0)
        assertNotNull(AlertLogic.passStartingWithin(listOf(later, soon), now))
        assertNull(AlertLogic.passStartingWithin(listOf(later), now))
    }

    @Test
    fun bestUpcomingPassIsHighest() {
        val now = 1_790_000_000_000L
        val low = PassWindow("A", now + 60_000, now + 120_000, 20.0)
        val high = PassWindow("B", now + 120_000, now + 180_000, 70.0)
        val past = PassWindow("C", now - 60_000, now, 89.0)
        assertEquals("B", AlertLogic.bestUpcomingPass(listOf(low, high, past), now)?.name)
    }

    @Test
    fun nearestLargeAirportIgnoresSmallerFields() {
        val airports = listOf(
            AirportRef("VGHS", airportLat, airportLon, true),
            AirportRef("NEAR", airportLat + 0.01, airportLon, false),
        )
        val found = AlertLogic.nearestLargeAirport(airports, airportLat, airportLon)
        assertEquals("VGHS", found?.first?.ident)
    }
}
