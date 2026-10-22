package com.apexpredator.argus.v2

import com.apexpredator.argus.data.GeoMath
import com.apexpredator.argus.data.parseAirports
import com.apexpredator.argus.data.parseAircraftStates
import com.apexpredator.argus.data.parseRunways
import com.apexpredator.argus.data.parseTleText
import com.apexpredator.argus.data.searchAirports
import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.SatelliteFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class V2LogicTest {

    private val tsv = listOf(
        "VGHS\tDAC\tHazrat Shahjalal International Airport\tDhaka\tBD\t23.84335\t90.39778\t30\tlarge_airport",
        "VGEG\tCGP\tShah Amanat International Airport\tChattogram\tBD\t22.24960\t91.81330\t14\tmedium_airport",
        "KJFK\tJFK\tJohn F Kennedy International Airport\tNew York\tUS\t40.64130\t-73.77810\t13\tlarge_airport",
        "EGLL\tLHR\tLondon Heathrow Airport\tLondon\tGB\t51.47000\t-0.45430\t83\tlarge_airport",
    ).joinToString("\n")

    @Test
    fun searchByCityFindsDhakaAirportFirst() {
        val results = searchAirports(parseAirports(tsv), "dhaka")
        assertTrue(results.isNotEmpty())
        assertEquals("VGHS", results.first().ident)
    }

    @Test
    fun searchByNameFindsAirport() {
        val results = searchAirports(parseAirports(tsv), "shahjalal")
        assertEquals("VGHS", results.first().ident)
    }

    @Test
    fun searchByIataAndIcao() {
        val airports = parseAirports(tsv)
        assertEquals("VGHS", searchAirports(airports, "DAC").first().ident)
        assertEquals("KJFK", searchAirports(airports, "jfk").first().ident)
        assertEquals("EGLL", searchAirports(airports, "EGLL").first().ident)
        assertEquals("KJFK", searchAirports(airports, "new york").first().ident)
    }

    @Test
    fun runwayParsing() {
        val text = "VGHS\t10499\tPEM\t14\t32\u001F8000\tASP\t05\t23"
        val map = parseRunways(text)
        assertEquals(2, map["VGHS"]?.size)
        assertEquals(10499, map["VGHS"]?.first()?.lengthFt)
        assertEquals("14/32", map["VGHS"]?.first()?.label)
    }

    @Test
    fun extendedVectorParsesCategoryAndEmergency() {
        // 18-slot extended vector: category 14 (UAV) at index 17.
        val body = """
            {"time": 1700000000, "states": [
              ["abc123", "DRONE1  ", "Germany", 0, 0, 8.5, 50.0, 300.0, false, 20.0, 90.0, 0.0, null, 300.0, "7700", false, 0, 14]
            ]}
        """.trimIndent()
        val parsed = parseAircraftStates(body)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].isUav)
        assertTrue(parsed[0].isEmergency)
        assertEquals(14, parsed[0].category)
    }

    @Test
    fun tleParsingAndPropagation() {
        val text = """
            ISS (ZARYA)
            1 25544U 98067A   26294.04791053  .00009575  00000+0  18188-3 0  9996
            2 25544  51.6327  37.2623 0006759 289.5321  70.4938 15.48967427591263
        """.trimIndent()
        val tles = parseTleText(text)
        assertEquals(1, tles.size)
        assertEquals(25544, tles[0].catnum)
        val sat = SatelliteFactory.createSatellite(tles[0])
        val pos = sat.getPosition(
            GroundStationPosition(0.0, 0.0, 0.0),
            Date(1792560000000L)
        )
        val latDeg = Math.toDegrees(pos.latitude)
        val altKm = pos.altitude
        assertTrue("lat in ISS band: $latDeg", latDeg in -52.0..52.0)
        assertTrue("alt in LEO band: $altKm", altKm in 300.0..600.0)
        assertNotNull(pos)
    }

    @Test
    fun haversineDhakaToChattogram() {
        val km = GeoMath.haversineKm(23.8103, 90.4125, 22.3569, 91.7832)
        assertTrue("expected ~215 km, got $km", km in 200.0..260.0)
        val bearing = GeoMath.bearingDeg(23.8103, 90.4125, 22.3569, 91.7832)
        assertTrue("expected SE bearing, got $bearing", bearing in 120.0..170.0)
    }
}
