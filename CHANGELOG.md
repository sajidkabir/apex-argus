# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and versions follow
[Semantic Versioning](https://semver.org/).

## [2.3.0] - 2026-10-27

The daily-habit release: Argus starts watching the sky for you, and
keeps a record of everything you spot.

### Added

- Balcony alerts: a background check every 15 minutes (WorkManager)
  that notifies you when a satellite pass over your location starts
  within 10 minutes, when a heavy aircraft is on final approach to
  the nearest large airport, or when any aircraft within 80 km
  squawks an emergency code (7500, 7600, 7700). One toggle per alert
  type on the Tools screen, and a correct notification-permission
  flow: if you deny notifications, the toggles stay usable, nothing
  posts, and a button takes you to the system settings page.
- Sighting logbook: every aircraft you identify, from the radar,
  Spotter, or an airport sheet's traffic list, is logged locally
  with timestamp, callsign, type and airline when enrichment knows
  them, and airport context. A Logbook screen from Tools shows the
  list with totals (sightings, unique aircraft, airlines) and a
  per-entry delete plus a clear-all option. Fully local storage,
  nothing leaves the phone.
- Morning briefing card on the Tools screen: plain-English METAR
  summaries for your watched airports (VGHS when none are watched),
  tonight's best satellite pass, and a one-line summary of the
  traffic the radar is already tracking.

### Notes

- Alerts use your last known location, falling back to Dhaka when
  location is off, the same fallback the Sky tab uses. The 15-minute
  interval is the platform minimum for periodic background work.
- A re-tap of the same aircraft within 30 minutes is not logged
  twice; it is the same sheet reopened.

## [2.2.2] - 2026-10-26

The godseyeview mesh: the camera map goes from 22 tourist webcams
to whole city camera networks, live, on the model of Bilawal Sidhu's
God's Eye View (municipal CCTV fetched from the cities themselves).

### Added

- A live municipal camera mesh: Delaware DOT (364 cameras, live HLS
  video), Caltrans District 4, Bay Area (live snapshots, many
  with HLS video), and the City of Calgary's open traffic cameras
  (live snapshots), fetched at runtime with no key, account or
  subscription. Orange pins are city traffic cameras; blue pins stay
  the bundled tourist webcams.
- Clustering: zoomed out, the mesh collapses into count badges per
  area; tap a badge to zoom into real pins. The same mesh pins draw
  on the main radar map, so one map carries flights, satellites,
  tourist webcams and city CCTV.
- A native camera sheet: HLS video in-app where the network
  publishes a stream, otherwise a live snapshot with a refresh
  button and a 60-second auto-refresh while open. One stream at a
  time, which keeps Caltrans' fair-use rule intact.
- City jump chips (Delaware, Bay Area, World) and a mesh status
  line with per-network counts and a manual refresh.

### Notes

- Feeds are public traffic cameras, credited to their operators
  (DelDOT, Caltrans, City of Calgary under its Open Government
  Licence). Argus records nothing.
- Deliberately excluded: NYC DOT (its feed needs a signed
  data-sharing agreement) and FL511 (personal use only). London's
  TfL JamCams need an app key, so London waits for a keyless route.

## [2.2.1] - 2026-10-26

The godseyeview fix: the camera feature no longer depends on any
third party's map or paywall. Windy is gone from the app entirely.

### Added

- A worldwide camera map that is Argus's own: 22 verified free public
  cameras across every continent, from Times Square and the Eiffel
  Tower to Petra, Iceland's glacier lagoon, Ushuaia at the end of the
  world, a Zambian safari, Cape Town and Hawaii, plus airport spotting
  cameras at LAX, Heathrow, St Maarten, Dhaka, Frankfurt and Haneda.
- Cameras on the radar: the same camera pins appear on the main
  fused map as a layer (on by default, switchable in Layers), so one
  map carries flights, satellites and cameras. Tapping a pin on the
  radar opens that camera's live feed.
- The Airports Cams button now opens the in-app camera map centred
  on that airport instead of leaving the app.

### Changed

- The Live Cams tab is rebuilt around a native map with tappable
  pins and an in-app player. The Directory mode is SkylineWebcams'
  full free directory (2,500+ cameras, no account); every bundled
  camera page was verified to play without a subscription.

## [2.2.0] - 2026-10-23

The chat release: Argus grows a sixth tab, and the whole app feels
smoother while data is on its way.

### Added

- Argus Chat: decentralized messaging over the Nostr protocol, free
  forever with no server. The identity is a secp256k1 keypair generated
  and kept in the app's private storage, with a profile (chosen
  username), a shareable Argus ID (npub), people search by username or
  exact ID, NIP-04 encrypted direct messages, and conversations stored
  locally on the device.
- Smooth tab transitions: destinations slide a quarter width and fade
  when switching tabs.

### Changed

- Loading skeletons and tidy empty states across the data screens,
  replacing the old spinner and loose placeholder text.

## [2.1.0] - 2026-10-22

The super-app release: Argus grows a five-tab menu and a set of
personal, location-aware tools around the radar.

### Added

- Five-tab menu: Radar, Spotter, Sky, Airports, Tools.
- Spotter: point the phone camera at an aircraft in the sky and Argus
  names it, using GPS plus the compass/tilt sensors against live
  traffic; the best match in view opens the full detail sheet.
- Near Me: every aircraft within 80 km of the user, nearest first,
  with distance, bearing, height above the horizon, and movement
  verdicts ("On approach to VGHS", "Departed VGHS, climbing").
- Sky tab: satellites overhead now, plus predicted passes over the
  user's location for the next 24 hours (rise time, peak elevation,
  direction, duration) from the same on-device SGP4 engine.
- Airports tab: plain-English search over the bundled airport set,
  watched airports, nearest airports, a public-webcams shortcut per
  airport, and a movements board (arrivals/departures from live
  position trends) in each airport's sheet.
- Live Cams tab: a world map of public webcams (Windy) to pan and
  zoom globally and watch in place, plus a curated aviation cams list.
- Map identity glyphs: aeroplanes, drones (diamond), helicopters
  (circled H), satellites (star) and unknown traffic (hollow marker)
  each read differently at a glance, with a legend in the panel.

## [2.0.0] - 2026-10-22

The fused-picture release: Apex Argus grows from a weather decoder with
a radar screen into an open-source flight-intelligence app.

### Added

- Unified search across airports, live flights and satellites. Airports
  are searchable by name, city, IATA or ICAO in plain English; about
  9,900 airports with runways are bundled in the app (OurAirports,
  public domain), so airport search works offline.
- Satellite layer: stations, bright, weather, GPS and science satellites
  from CelesTrak, propagated on-device with SGP4 (predict4java) and
  refreshed every 10 seconds with no network after the daily TLE fetch.
- Drone / UAV layer: OpenSky extended state vectors expose the ADS-B
  emitter category, so UAVs render as orange diamonds, rotorcraft in
  teal, and a "Drones / UAV only" filter isolates them.
- Weather radar overlay from RainViewer.
- Aircraft enrichment: photo (Planespotters), type, registration and
  owner (adsbdb), plus the flight's airline and route with origin and
  destination airports drawn on the map and tappable (entity linking).
- Airport detail sheet: runways, elevation, live METAR and TAF, traffic
  within 100 km, and a one-tap jump into the weather decoder.
- Emergency squawk watch: 7500 / 7600 / 7700 raises a banner.
- Watchlist for aircraft and airports (persisted on-device), watched
  aircraft ringed in gold on the map.
- Playback: radar frames are recorded while the app runs and can be
  scrubbed in a playback bar.
- Measure tool: two taps give distance (nm or km) and bearing.
- Filters: hide ground traffic, altitude bands; settings for refresh
  cadence (1, 3 or 5 minutes) and aviation or metric units.

## [1.1.1] - 2026-10-06

### Added

- Live flight radar screen: OpenStreetMap (osmdroid, no API key) with live
  aircraft positions from the OpenSky Network. Aircraft render as
  heading-rotated markers with callsign labels; tapping one opens a detail
  sheet (callsign, altitude, speed, heading, vertical rate, squawk,
  on-ground state). Camera can jump to any airport by ICAO code via the
  aviationweather.gov station lookup. Bbox queries are clamped to 5x5 degrees
  (1 anonymous API credit each) and auto-refresh every 3 minutes.
- In-app updates: the app checks the latest GitHub release on launch. The
  github flavor (public APK) downloads and installs updates itself via the
  system installer; the play flavor links to the Play Store listing, since
  Play policy forbids self-updating a Play-distributed app.
- Public APK distribution: pushing a version tag builds a signed github
  APK and publishes it to GitHub Releases (no login needed to download),
  with a `version.json` descriptor the in-app updater reads.

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
