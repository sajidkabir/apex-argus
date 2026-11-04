package com.apexpredator.argus.v2

import com.apexpredator.argus.data.AircraftState
import com.apexpredator.argus.data.DiaryFlight
import com.apexpredator.argus.data.DiaryStatus
import com.apexpredator.argus.data.buildCallsign
import com.apexpredator.argus.data.compareMetarChanges
import com.apexpredator.argus.data.decodeDiaryFlight
import com.apexpredator.argus.data.deriveDiaryStatus
import com.apexpredator.argus.data.encodeDiaryFlight
import com.apexpredator.argus.data.findDiaryMatch
import com.apexpredator.argus.data.matchesDiaryFlight
import com.apexpredator.argus.data.parseMetarSnapshot
import com.apexpredator.argus.data.validateDiaryInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Unit tests for the v2.6 traveller release: flight-diary matching
// and status, weather-change detection, and diary encoding.
class DiaryWeatherTest {

    private fun aircraft(callsign: String?, onGround: Boolean = false) =
        AircraftState(
            icao24 = "abc123",
            callsign = callsign,
            originCountry = "Bangladesh",
            longitude = 90.4,
            latitude = 23.8,
            baroAltitudeM = 10_000.0,
            onGround = onGround,
            velocityMs = 250.0,
            trueTrackDeg = 90.0,
            verticalRateMs = 0.0,
            squawk = null,
        )

    @Test
    fun callsignBuildsFromCodeAndNumber() {
        assertEquals("BG101", buildCallsign("BG", "101"))
        assertEquals("BG101", buildCallsign("bg", "BG101"))
        // An airline name is not a code, so it is not forced into one.
        assertEquals("101", buildCallsign("Biman", "101"))
    }

    @Test
    fun codedCallsignNeverMatchesAnotherAirlineOnSuffix() {
        val flight = DiaryFlight("1", "BG", "101", "2026-11-06", null)
        assertTrue(matchesDiaryFlight("BG101", flight))
        assertFalse(matchesDiaryFlight("EK101", flight))
        assertTrue(matchesDiaryFlight(" BG101 ", flight))
    }

    @Test
    fun digitsOnlyFlightMatchesBySuffix() {
        val flight = DiaryFlight("1", "Biman", "101", "2026-11-06", null)
        assertTrue(matchesDiaryFlight("BG101", flight))
        assertFalse(matchesDiaryFlight("BG102", flight))
    }

    @Test
    fun diaryStatusIsOnlyWhatFreeDataProves() {
        val flight = DiaryFlight("1", "BG", "101", "2026-11-06", null)
        assertEquals(
            DiaryStatus.AIRBORNE,
            deriveDiaryStatus(flight, aircraft("BG101"), "2026-11-06")
        )
        assertEquals(
            DiaryStatus.ON_GROUND,
            deriveDiaryStatus(flight, aircraft("BG101", onGround = true), "2026-11-06")
        )
        assertEquals(
            DiaryStatus.SCHEDULED,
            deriveDiaryStatus(flight, null, "2026-11-05")
        )
        // A past flight with no live match is NOT_TRACKED, never
        // silently relabelled as landed: absence proves nothing.
        assertEquals(
            DiaryStatus.NOT_TRACKED,
            deriveDiaryStatus(flight, null, "2026-11-07")
        )
        assertEquals(
            aircraft("BG101").icao24,
            findDiaryMatch(listOf(aircraft("EK202"), aircraft("BG101")), flight)?.icao24
        )
        assertNull(findDiaryMatch(listOf(aircraft("EK202")), flight))
    }

    @Test
    fun diaryInputValidation() {
        assertNull(validateDiaryInput("BG", "101", "2026-11-06"))
        assertTrue(validateDiaryInput("", "101", "2026-11-06") != null)
        assertTrue(validateDiaryInput("BG", "101", "06-11-2026") != null)
        assertTrue(validateDiaryInput("BG", "101", "2026-13-01") != null)
    }

    @Test
    fun diaryEncodingRoundTrips() {
        val flight = DiaryFlight("id-1", "BG", "101", "2026-11-06", "DAC to CGP")
        assertEquals(flight, decodeDiaryFlight(encodeDiaryFlight(flight)))
        assertNull(decodeDiaryFlight("broken"))
    }

    @Test
    fun weatherChangesFlagWindVisibilityCbTsAndSpeci() {
        val old = parseMetarSnapshot(
            "VGHS",
            "METAR VGHS 041200Z 09008KT 6000 FEW015 28/22 Q1008"
        )
        val shifted = parseMetarSnapshot(
            "VGHS",
            "METAR VGHS 041300Z 14020KT 4000 FEW015CB 28/22 Q1008"
        )
        val changes = compareMetarChanges(old, shifted)
        assertTrue(changes.any { it.contains("Wind shifted") })
        assertTrue(changes.any { it.contains("Wind changed") })
        assertTrue(changes.any { it.contains("Visibility dropped") })
        assertTrue(changes.any { it.contains("Cumulonimbus") })
        val ts = parseMetarSnapshot(
            "VGHS",
            "METAR VGHS 041400Z 09008KT 6000 TSRA FEW015 28/22 Q1008"
        )
        assertTrue(compareMetarChanges(old, ts).any { it.contains("Thunderstorm") })
        val speci = parseMetarSnapshot(
            "VGHS",
            "SPECI VGHS 041500Z 09008KT 6000 FEW015 28/22 Q1008"
        )
        assertTrue(compareMetarChanges(old, speci).any { it.contains("SPECI") })
    }

    @Test
    fun weatherChangesStaySilentWithoutMaterialChange() {
        val old = parseMetarSnapshot(
            "VGHS",
            "METAR VGHS 041200Z 09008KT 6000 FEW015 28/22 Q1008"
        )
        val same = parseMetarSnapshot(
            "VGHS",
            "METAR VGHS 041300Z 10009KT 6000 FEW015 28/22 Q1008"
        )
        assertTrue(compareMetarChanges(old, same).isEmpty())
        // No previous report means nothing to compare, not a change.
        assertTrue(compareMetarChanges(null, same).isEmpty())
    }
}
