# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Live flight radar screen: OpenStreetMap (osmdroid, no API key) with live
  aircraft positions from the OpenSky Network. Aircraft render as
  heading-rotated markers with callsign labels; tapping one opens a detail
  sheet (callsign, altitude, speed, heading, vertical rate, squawk,
  on-ground state). Camera can jump to any airport by ICAO code via the
  aviationweather.gov station lookup. Bbox queries are clamped to 5x5 degrees
  (1 anonymous API credit each) and auto-refresh every 3 minutes.

## [1.0.0] - 2026-10-01

First stable release.

### Added

- Token-based METAR parser (`parse_metar`) producing a structured
  `MetarReport`: station, observation time, wind (with gusts, variable
  direction, and direction variation range), visibility in meters or
  statute miles, runway visual range, weather phenomena, cloud layers,
  temperature and dewpoint (including negative values), and altimeter
  setting in hPa or inHg.
- CAVOK handling: visibility of 10 km or more with no significant cloud
  or weather.
- Weather code tables and a group splitter covering intensity prefixes,
  descriptors (thunderstorm, shower, freezing, and more), and the standard
  precipitation and obscuration phenomena.
- Plain-English renderer (`human_readable`) summarizing wind, visibility,
  conditions, clouds, temperature, and pressure in readable sentences.
- Command-line interface with `decode` (plain English) and `parse` (JSON)
  subcommands.
- Test suite of 26 tests covering the fields of eight real-world style
  reports, weather code decoding, unit conversions, and the plain-English
  renderer and CLI output.
- Example script decoding five reports from Dhaka, New York, Hong Kong,
  London, and Dubai.
