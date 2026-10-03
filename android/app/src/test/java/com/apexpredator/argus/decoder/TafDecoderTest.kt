package com.apexpredator.argus.decoder

import org.junit.Assert.*
import org.junit.Test

class TafDecoderTest {

    private val sample = "TAF KJFK 021120Z 0212/0318 27010KT P6SM BKN050 " +
        "TEMPO 0214/0218 3SM -RA BR BKN030 " +
        "FM030000 30015G25KT 5SM -SHRA OVC040 " +
        "PROB30 0302/0306 2SM TSRA BKN025CB"

    @Test
    fun `taf header parsed`() {
        val t = TafDecoder.decode(sample)
        assertEquals("KJFK", t.station)
        assertEquals("day 02 at 11:20 UTC", t.issueTimeUtc)
        assertEquals("day 02 12:00 UTC to day 03 18:00 UTC", t.validDescription)
        assertFalse(t.cancelled)
    }

    @Test
    fun `taf base conditions parsed`() {
        val t = TafDecoder.decode(sample)
        assertEquals(270, t.base.wind!!.directionDeg)
        assertEquals(10, t.base.wind!!.speed)
        assertEquals(6.0, t.base.visibility!!.statuteMiles!!, 0.01)
        assertEquals("BKN", t.base.sky[0].coverage)
        assertEquals(5000, t.base.sky[0].heightFeet)
    }

    @Test
    fun `tempo group parsed`() {
        val t = TafDecoder.decode(sample)
        val tempo = t.changes.first { it.kind == "TEMPO" }
        assertEquals("day 02 14:00 UTC to day 02 18:00 UTC", tempo.timeDescription)
        assertEquals(3.0, tempo.conditions.visibility!!.statuteMiles!!, 0.01)
        assertEquals("light rain", tempo.conditions.weather[0].description)
        assertEquals("mist", tempo.conditions.weather[1].description)
        assertEquals(3000, tempo.conditions.sky[0].heightFeet)
    }

    @Test
    fun `fm group parsed with gust`() {
        val t = TafDecoder.decode(sample)
        val fm = t.changes.first { it.kind == "FM" }
        assertEquals("from day 03 00:00 UTC", fm.timeDescription)
        assertEquals(300, fm.conditions.wind!!.directionDeg)
        assertEquals(25, fm.conditions.wind!!.gust)
        assertEquals("light rain showers", fm.conditions.weather[0].description)
    }

    @Test
    fun `prob group carries probability`() {
        val t = TafDecoder.decode(sample)
        val prob = t.changes.first { it.kind == "PROB" }
        assertEquals(30, prob.probability)
        assertEquals("thunderstorm with rain", prob.conditions.weather[0].description)
        assertEquals("CB", prob.conditions.sky[0].cloudType)
    }

    @Test
    fun `becmg group parsed`() {
        val t = TafDecoder.decode("TAF EGLL 021120Z 0212/0318 27010KT 9999 SCT040 BECMG 0216/0218 4000 BR BKN015")
        val bec = t.changes.first { it.kind == "BECMG" }
        assertEquals("day 02 16:00 UTC to day 02 18:00 UTC", bec.timeDescription)
        assertEquals(4000.0, bec.conditions.visibility!!.meters!!, 0.01)
        assertEquals("mist", bec.conditions.weather[0].description)
    }

    @Test
    fun `cancelled taf flagged`() {
        val t = TafDecoder.decode("TAF KJFK 021120Z 0212/0318 CNL")
        assertTrue(t.cancelled)
        val lines = describeTaf(t)
        assertTrue(lines.any { it.contains("cancelled") })
    }

    @Test
    fun `amended taf flagged`() {
        val t = TafDecoder.decode("TAF AMD KJFK 021120Z 0212/0318 27010KT 9999 SCT040")
        assertTrue(t.amended)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty taf throws`() {
        TafDecoder.decode("  ")
    }

    @Test
    fun `taf without prefix still parses`() {
        val t = TafDecoder.decode("KJFK 021120Z 0212/0318 27010KT 9999 SCT040")
        assertEquals("KJFK", t.station)
        assertEquals(270, t.base.wind!!.directionDeg)
    }

    @Test
    fun `taf plain english change titles`() {
        val t = TafDecoder.decode(sample)
        val lines = describeTaf(t)
        assertTrue(lines.any { it.startsWith("Temporarily (day 02 14:00 UTC to day 02 18:00 UTC)") })
        assertTrue(lines.any { it.startsWith("From day 03 00:00 UTC") })
        assertTrue(lines.any { it.startsWith("30% probability") })
    }

    @Test
    fun `taf flight category from base conditions`() {
        val t = TafDecoder.decode("TAF KJFK 021120Z 0212/0318 27010KT 10SM FEW040")
        assertEquals(FlightCategory.VFR, tafFlightCategory(t))
        val t2 = TafDecoder.decode("TAF KJFK 021120Z 0212/0318 27010KT 2SM BR OVC008")
        assertEquals(FlightCategory.IFR, tafFlightCategory(t2))
    }
}
