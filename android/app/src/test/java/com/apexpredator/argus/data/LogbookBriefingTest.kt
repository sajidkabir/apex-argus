package com.apexpredator.argus.v2

import com.apexpredator.argus.data.PassWindow
import com.apexpredator.argus.data.Sighting
import com.apexpredator.argus.data.bestPassText
import com.apexpredator.argus.data.buildBriefingLines
import com.apexpredator.argus.data.decodeSighting
import com.apexpredator.argus.data.encodeSighting
import com.apexpredator.argus.data.logbookStats
import com.apexpredator.argus.data.trafficSummaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogbookBriefingTest {

    private fun sighting(
        icao: String = "abc123",
        airline: String? = "British Airways",
    ) = Sighting(
        id = "1-$icao",
        timestampMs = 1_790_000_000_000L,
        callsign = "BAW123",
        icao24 = icao,
        aircraftType = "Boeing 777-300ER",
        airline = airline,
        airportContext = "VGHS",
        source = "radar",
    )

    @Test
    fun logbookRoundTripPreservesFields() {
        val original = sighting()
        val decoded = decodeSighting(encodeSighting(original))
        assertEquals(original, decoded)
    }

    @Test
    fun logbookRoundTripSurvivesEscapes() {
        val tricky = sighting().copy(
            airline = "Air\\Line\nWith\u001FSeparator",
            aircraftType = null,
        )
        assertEquals(tricky, decodeSighting(encodeSighting(tricky)))
    }

    @Test
    fun malformedLogbookLineIsDropped() {
        assertNull(decodeSighting("only-one-field"))
        assertNull(decodeSighting(""))
    }

    @Test
    fun logbookStatsCountUniques() {
        val entries = listOf(
            sighting("abc123"),
            sighting("abc123"),
            sighting("def456", airline = "Emirates"),
            sighting("ghi789", airline = null),
        )
        val stats = logbookStats(entries)
        assertEquals(4, stats.total)
        assertEquals(3, stats.uniqueAircraft)
        assertEquals(2, stats.uniqueAirlines)
    }

    @Test
    fun briefingLinesAssembleInOrder() {
        val lines = buildBriefingLines(
            metarSummaries = listOf("VGHS - VFR: Wind calm"),
            bestPassText = "ISS rises 19:42, peaks 54 deg",
            trafficText = "Radar is tracking 3 aircraft in view.",
        )
        assertEquals("Watched airports:", lines[0])
        assertEquals("VGHS - VFR: Wind calm", lines[1])
        assertTrue(lines[2].contains("ISS"))
        assertTrue(lines[3].contains("3 aircraft"))
    }

    @Test
    fun bestPassTextIsNullWithoutPass() {
        assertNull(bestPassText(null))
        val pass = PassWindow("ISS", 1_790_000_000_000L, 1_790_000_300_000L, 54.4)
        assertTrue(bestPassText(pass)!!.contains("ISS"))
    }

    @Test
    fun trafficSummaryHandlesCounts() {
        assertTrue(trafficSummaryText(0, 0).contains("No live radar feed"))
        assertTrue(trafficSummaryText(1, 0).contains("1 aircraft"))
        assertTrue(trafficSummaryText(3, 1).contains("emergency"))
    }
}
