# Apex Argus

**The open-source flight intelligence app for Android.** Live aircraft, drones,
satellites and weather on one fused map, plus a full METAR/TAF weather decoder.
No account, no ads, no tracking, no API keys. Built by an aeronautical
engineer, free forever.

## 📲 Download and install (30 seconds, phone only)

**[⬇️ Download Apex Argus APK](https://github.com/sajidkabir/apex-argus/releases/latest/download/apex-argus.apk)**

Then:

1. Tap the download above. Your browser downloads `apex-argus.apk`.
2. Open the downloaded file (from the notification or your Downloads folder).
3. If Android asks, allow **Install unknown apps** for your browser, just once.
   This is normal for any app installed outside the Play Store.
4. Tap **Install**, then **Open**. Done.

**Auto-updates are built in.** The app checks for a new version every time it
opens and offers the update in one tap. You never need to re-download the APK
manually.

Prefer the release page? Every version is on the
[Releases page](https://github.com/sajidkabir/apex-argus/releases), and the
same app is in closed testing on the Play Store (production listing in
progress).

## What it does

**Six tabs: Radar · Spotter · Sky · Airports · Tools · Chat**

**Spotter (the balcony feature)**

- Point your phone camera at an aircraft in the sky and Argus names it:
  GPS position plus compass and tilt sensors matched against live
  traffic, with the flight's photo, airline and route one tap away
- Near Me: everything within 80 km of you, nearest first, with plain
  verdicts like "On approach to VGHS" or "Departed VGHS, climbing"

**Sky**

- Satellites overhead right now, and every worthwhile pass over your
  location in the next 24 hours: rise time, peak elevation, direction
  and duration, predicted on the phone itself

**Airports**

- Search 9,900 airports by name, city, IATA or ICAO (works offline)
- Watched airports, nearest airports, a movements board (arrivals and
  departures from live position trends), runways, weather and a
  public-webcams shortcut for every field

**Live cams (the godseyeview)**

- A worldwide camera map that is Argus's own: 22 verified free public
  cameras across every continent, no account, no subscription. Tap a
  pin, watch it live in the app
- The same camera pins live on the main radar map as a layer, so one
  map carries flights, satellites and cameras
- A full free directory (SkylineWebcams, 2,500+ cameras) and the
  airport spotting list

**Argus Chat**

- Decentralized chat over the Nostr protocol: no server, no account,
  free forever. Your identity is a secp256k1 keypair generated and kept
  in the app's private storage, with a chosen username and a shareable
  Argus ID (npub)
- Find people by username or exact Argus ID, then talk in NIP-04
  encrypted direct messages. Conversations live on your phone only

**Fused live radar (the Palantir-style picture)**

- Live aircraft worldwide from the OpenSky Network, refreshed on your chosen
  cadence, with heading-rotated markers and callsign labels
- **Drone / UAV layer**: aircraft broadcasting the ADS-B drone category are
  drawn as orange diamonds, with a one-tap "Drones only" filter and a live
  UAV count
- **Satellite layer**: space stations, bright satellites, weather and GPS
  satellites propagated on your phone from CelesTrak orbital elements
  (SGP4), moving in real time, no network needed after the daily download
- **Weather radar layer**: live precipitation overlay from RainViewer, the
  same kind of layer paid trackers charge for
- **Emergency watch**: any aircraft squawking 7500, 7600 or 7700 raises a
  red banner the moment it appears
- **Playback**: the radar records traffic frames while you use it; scrub
  back through them like the playback mode in paid apps
- **Measure tool**: tap two points, get distance and bearing

**Search everything from one box**

- Airports by **name, city, IATA or ICAO** in plain English: try "Dhaka",
  "Shahjalal", "DAC" or "VGHS". About 9,900 airports are bundled in the app,
  so search works offline
- Live flights by callsign or hex code, satellites by name or NORAD ID

**Tap anything for the full picture**

- Aircraft: photo, type, registration, owner, plus the flight's **airline
  and route with origin and destination airports drawn on the map**. Tap
  through to either airport (the entity-linking trick intelligence
  platforms are built on)
- Airports: runways, elevation, latest METAR and TAF, live traffic within
  100 km, and one tap to decode the weather in full
- Satellites: altitude, velocity, position, computed on-device
- **Watchlist**: star aircraft and airports; watched aircraft get a gold
  ring on the map

**Aviation weather decoder (the original Argus)**

- Full METAR and TAF decoding into structured data and plain English,
  fetched live from aviationweather.gov for any station in the world
- Flight category (VFR, MVFR, IFR, LIFR), history, and a paste-your-own
  report mode

## Privacy

Argus has no account system, no analytics and no ads. It only talks to
public data services (OpenSky Network, aviationweather.gov, CelesTrak,
RainViewer, adsbdb, Planespotters, OpenStreetMap) to fetch the data you
are looking at. The watchlist and settings live on your phone only.

## Data sources and credit

Flight positions: [OpenSky Network](https://opensky-network.org) ·
Weather: [aviationweather.gov](https://aviationweather.gov) ·
Satellites: [CelesTrak](https://celestrak.org) ·
Precipitation: [RainViewer](https://www.rainviewer.com) ·
Aircraft and route data: [adsbdb](https://www.adsbdb.com) ·
Photos: [Planespotters](https://www.planespotters.net) (© their photographers) ·
Airports: [OurAirports](https://ourairports.com) (public domain) ·
Maps: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors

## For developers

Native Kotlin + Jetpack Compose. The Android project is in `android/`:

```text
git clone https://github.com/sajidkabir/apex-argus.git
```

Open `android/` in Android Studio and run the `github` flavor for the
public build, or the `play` flavor for the Play Store build (identical
except the Play build links to the store for updates, per Play policy).
Unit tests: `gradle :app:testGithubDebugUnitTest`. Releases are built
and signed by GitHub Actions from version tags.

## License

MIT. See [LICENSE](LICENSE).
