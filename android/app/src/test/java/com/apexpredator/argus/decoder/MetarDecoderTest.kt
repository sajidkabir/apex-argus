package com.apexpredator.argus.decoder

import org.junit.Assert.*
import org.junit.Test

class MetarDecoderTest {

    @Test
    fun `normal METAR decodes all groups`() {
        val m = MetarDecoder.decode("KJFK 021251Z 27012G22KT 10SM -RA BKN045 OVC060 18/16 A2992")
        assertEquals("KJFK", m.station)
        assertEquals("day 02 at 12:51 UTC", m.observationTimeUtc)
        assertFalse(m.automatic)

        val w = m.conditions.wind!!
        assertEquals(270, w.directionDeg)
        assertFalse(w.variable)
        assertEquals(12, w.speed)
        assertEquals(22, w.gust)
        assertEquals(SpeedUnit.KT, w.unit)

        assertEquals(10.0, m.conditions.visibility!!.statuteMiles!!, 0.01)
        assertEquals(1, m.conditions.weather.size)
        assertEquals("light rain", m.conditions.weather[0].description)
        assertEquals(2, m.conditions.sky.size)
        assertEquals("BKN", m.conditions.sky[0].coverage)
        assertEquals(4500, m.conditions.sky[0].heightFeet)
        assertEquals(18, m.conditions.temperatureC)
        assertEquals(16, m.conditions.dewpointC)
        assertEquals(29.92, m.conditions.altimeterInHg!!, 0.001)
    }

    @Test
    fun `variable wind`() {
        val m = MetarDecoder.decode("EDDF 021220Z VRB03KT CAVOK 12/08 Q1013")
        val w = m.conditions.wind!!
        assertTrue(w.variable)
        assertNull(w.directionDeg)
        assertEquals(3, w.speed)
        assertTrue(m.conditions.visibility!!.cavok)
        assertEquals(1013, m.conditions.qnhHpa)
    }

    @Test
    fun `calm wind`() {
        val m = MetarDecoder.decode("OMDB 021200Z 00000KT 8000 DU SCT040 41/12 Q1006")
        val w = m.conditions.wind!!
        assertEquals(0, w.speed)
        assertEquals(0, w.directionDeg)
        assertEquals("dust", m.conditions.weather[0].description)
    }

    @Test
    fun `low visibility in meters with mist`() {
        val m = MetarDecoder.decode("VABB 021130Z 27008KT 2000 BR SCT015 29/26 Q1007")
        assertEquals(2000.0, m.conditions.visibility!!.meters!!, 0.01)
        assertEquals(1.24, m.conditions.visibility!!.statuteMiles!!, 0.01)
        assertEquals("mist", m.conditions.weather[0].description)
        assertEquals(1500, m.conditions.sky[0].heightFeet)
    }

    @Test
    fun `fractional statute mile visibility`() {
        val m = MetarDecoder.decode("KORD 021253Z 31008KT 1/2SM FG OVC003 12/11 A2988")
        assertEquals(0.5, m.conditions.visibility!!.statuteMiles!!, 0.001)
        assertEquals("fog", m.conditions.weather[0].description)
    }

    @Test
    fun `less than quarter mile visibility`() {
        val m = MetarDecoder.decode("KORD 021253Z 31008KT M1/4SM FG VV002 12/11 A2988")
        assertEquals(0.25, m.conditions.visibility!!.statuteMiles!!, 0.001)
        assertEquals("VV", m.conditions.sky[0].coverage)
        assertEquals(200, m.conditions.sky[0].heightFeet)
    }

    @Test
    fun `minus temperatures`() {
        val m = MetarDecoder.decode("ESSA 021220Z 35006KT 9999 SCT020 M02/M05 Q1002")
        assertEquals(-2, m.conditions.temperatureC)
        assertEquals(-5, m.conditions.dewpointC)
        assertEquals(1002, m.conditions.qnhHpa)
    }

    @Test
    fun `missing wind yields null wind`() {
        val m = MetarDecoder.decode("KJFK 021251Z 10SM FEW030 18/16 A2992")
        assertNull(m.conditions.wind)
        assertEquals("KJFK", m.station)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty input throws`() {
        MetarDecoder.decode("   ")
    }

    @Test
    fun `garbage input does not crash`() {
        val m = MetarDecoder.decode("HELLO WORLD")
        assertEquals("????", m.station)
        assertNull(m.conditions.wind)
    }

    @Test
    fun `cumulonimbus suffix parsed`() {
        val m = MetarDecoder.decode("VHHH 021200Z 09012KT 9999 FEW030CB 28/24 Q1008 NOSIG")
        assertEquals("CB", m.conditions.sky[0].coverage.let { m.conditions.sky[0].cloudType })
        assertEquals(3000, m.conditions.sky[0].heightFeet)
    }

    @Test
    fun `remarks captured after RMK`() {
        val m = MetarDecoder.decode("KJFK 021251Z 27012KT 10SM FEW030 18/16 A2992 RMK AO2 SLP134")
        assertEquals("AO2 SLP134", m.remarks)
    }

    @Test
    fun `AUTO flag detected`() {
        val m = MetarDecoder.decode("KJFK 021251Z AUTO 27012KT 10SM CLR 18/16 A2992")
        assertTrue(m.automatic)
        assertEquals("CLR", m.conditions.sky[0].coverage)
    }

    @Test
    fun `heavy thunderstorm with rain`() {
        val m = MetarDecoder.decode("KATL 021252Z 18015G28KT 3SM +TSRA BKN030CB 24/22 A2985")
        assertEquals("thunderstorm with heavy rain", m.conditions.weather[0].description)
        assertEquals(28, m.conditions.wind!!.gust)
    }

    @Test
    fun `vfr flight category`() {
        val m = MetarDecoder.decode("KJFK 021251Z 27008KT 10SM FEW030 18/16 A2992")
        assertEquals(FlightCategory.VFR, flightCategory(m.conditions))
    }

    @Test
    fun `ifr flight category from low ceiling`() {
        val m = MetarDecoder.decode("KORD 021253Z 31008KT 2SM BR BKN006 12/11 A2988")
        assertEquals(FlightCategory.IFR, flightCategory(m.conditions))
    }

    @Test
    fun `lifr flight category from very low visibility`() {
        val m = MetarDecoder.decode("KORD 021253Z 31008KT 1/2SM FG OVC003 12/11 A2988")
        assertEquals(FlightCategory.LIFR, flightCategory(m.conditions))
    }

    @Test
    fun `mvfr flight category`() {
        val m = MetarDecoder.decode("KJFK 021251Z 27008KT 4SM BR SCT025 18/16 A2992")
        assertEquals(FlightCategory.MVFR, flightCategory(m.conditions))
    }

    @Test
    fun `plain english wind line`() {
        val m = MetarDecoder.decode("KJFK 021251Z 27012G22KT 10SM FEW030 18/16 A2992")
        val lines = describeMetar(m)
        assertTrue(lines.any { it.contains("Wind 270 degrees at 12 knots, gusting 22 knots") })
        assertTrue(lines.any { it.contains("Temperature 18 C, dewpoint 16 C (spread 2 C)") })
        assertTrue(lines.any { it.contains("Altimeter 29.92 inches of mercury") })
    }
}
