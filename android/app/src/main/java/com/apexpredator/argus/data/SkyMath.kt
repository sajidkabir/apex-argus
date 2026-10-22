package com.apexpredator.argus.data

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tan

// Pure sky-geometry helpers for the Spotter (point-at-the-sky aircraft
// identification) and Near Me features. Everything here works off one
// observer fix and one aircraft state, so it is fully unit-testable.
object SkyMath {

    // Elevation angle in degrees of an aircraft above the observer's
    // horizon: positive means up in the sky.
    fun elevationDeg(
        observerAltM: Double,
        aircraftAltM: Double,
        groundDistanceKm: Double,
    ): Double {
        val groundM = groundDistanceKm * 1000.0
        if (groundM < 1.0) return 90.0
        return Math.toDegrees(atan2(aircraftAltM - observerAltM, groundM))
    }

    // Smallest absolute difference between two headings, 0 to 180.
    fun headingDiffDeg(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return kotlin.math.abs(d)
    }

    // Where an aircraft sits relative to where the phone is pointing:
    // returns (offsetRightDeg, offsetUpDeg). Positive right means the
    // aircraft is clockwise of the aim point; positive up means above.
    fun viewOffsets(
        aimAzimuthDeg: Double,
        aimPitchDeg: Double,
        targetAzimuthDeg: Double,
        targetElevationDeg: Double,
    ): Pair<Double, Double> {
        var dAz = (targetAzimuthDeg - aimAzimuthDeg) % 360.0
        if (dAz > 180.0) dAz -= 360.0
        if (dAz < -180.0) dAz += 360.0
        // Correct the horizontal offset for the aim pitch: near the
        // zenith a degree of azimuth covers less sky.
        val cosPitch = kotlin.math.cos(Math.toRadians(aimPitchDeg))
        val right = dAz * cosPitch
        val up = targetElevationDeg - aimPitchDeg
        return right to up
    }

    // Slant (3D) distance in km between observer and aircraft.
    fun slantKm(groundDistanceKm: Double, altDiffM: Double): Double {
        val altKm = altDiffM / 1000.0
        return hypot(groundDistanceKm, altKm)
    }

    // Field-of-view projection: converts angular offsets (degrees) to
    // fractional screen positions, 0.5 being the centre. Returns null
    // when the target is well outside the frame.
    fun projectToScreen(
        offsetRightDeg: Double,
        offsetUpDeg: Double,
        horizontalFovDeg: Double,
        verticalFovDeg: Double,
    ): Pair<Float, Float>? {
        val x = 0.5f + (tan(Math.toRadians(offsetRightDeg)) /
            tan(Math.toRadians(horizontalFovDeg / 2))).toFloat() * 0.5f
        val y = 0.5f - (tan(Math.toRadians(offsetUpDeg)) /
            tan(Math.toRadians(verticalFovDeg / 2))).toFloat() * 0.5f
        if (x < -0.25f || x > 1.25f || y < -0.25f || y > 1.25f) return null
        return x to y
    }

    fun compassLabel(deg: Double): String {
        val dirs = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val idx = (((deg % 360.0 + 360.0) % 360.0 + 11.25) / 22.5).toInt() % 16
        return dirs[idx]
    }

    // A one-line verdict for a nearby aircraft relative to an airport,
    // the balcony question: is it landing here, leaving, or passing?
    fun movementVerdict(
        ac: AircraftState,
        airport: Airport,
        distanceToAirportKm: Double,
    ): String? {
        if (ac.onGround) return null
        val bearingToAirport = GeoMath.bearingDeg(
            ac.latitude, ac.longitude, airport.lat, airport.lon
        )
        val track = ac.trueTrackDeg ?: return null
        val vr = ac.verticalRateMs ?: 0.0
        val altFt = ac.altitudeFt ?: return null
        val headingToAirport = headingDiffDeg(track, bearingToAirport) < 30.0
        val headingAway = headingDiffDeg(track, bearingToAirport) > 150.0
        return when {
            distanceToAirportKm < 60 && headingToAirport && vr < -1.0 ->
                "On approach to ${airport.ident}"
            distanceToAirportKm < 60 && headingToAirport && altFt < 12_000 ->
                "Inbound to ${airport.ident}"
            distanceToAirportKm < 30 && headingAway && vr > 1.0 ->
                "Departed ${airport.ident}, climbing"
            distanceToAirportKm < 30 && headingAway ->
                "Outbound from ${airport.ident}"
            else -> null
        }
    }
}
