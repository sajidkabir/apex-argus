# Apex Argus (Android)

Native Android app (Kotlin, Jetpack Compose, Material 3) that decodes aviation
METAR and TAF reports into plain English. First app published under the
"Apex Predator" Google Play developer account.

Package: `com.apexpredator.argus`
Min SDK 26, target/compile SDK 35.

## What it does

- Home screen: enter a 4-letter ICAO code and fetch the live METAR or TAF from
  https://aviationweather.gov/api/data (no API key needed), or paste any raw
  METAR/TAF string and decode it offline.
- Results screen: raw report at the top, a VFR / MVFR / IFR / LIFR flight
  category badge computed from ceiling and visibility, and decoded cards with
  plain-English explanations (wind, visibility, weather, sky layers,
  temperature/dewpoint spread, altimeter/QNH, TAF change groups).
- History: last 10 decoded reports stored locally with DataStore, tap to re-view.
- Share: sends the plain-English decoded text through the Android share sheet.
- Live radar (v1.1.0): OpenStreetMap view with live aircraft positions from the
  OpenSky Network (anonymous access, no API key). Pan/zoom to explore, tap a
  plane for callsign, altitude, speed, heading, vertical rate and squawk, or
  jump the camera to any airport by ICAO code. Queries are clamped to a
  5x5-degree window so each refresh costs 1 anonymous API credit; the map
  auto-refreshes every 3 minutes.

## Data sources

- METAR/TAF and station coordinates: aviationweather.gov (no key needed).
- Live aircraft positions: OpenSky Network `api/states/all` (anonymous,
  400 credits/day; this app spends 1 credit per query).
- Map tiles: OpenStreetMap. Flight data is community ADS-B coverage, so
  oceanic and remote areas may show few or no aircraft.

## Privacy

- No ads, no analytics, no accounts, no location.
- The only permission is INTERNET (for live fetches).
- Pasting a report works fully offline.
- Collects zero user data: the Play Data Safety form is simply
  "no data collected".

## Project layout

The Android project lives in `android/` (the repo root also holds the
original Python metar-decoder project, untouched).

- `android/app/src/main/java/com/apexpredator/argus/decoder/`:
  pure-Kotlin METAR/TAF decoder engine, no Android dependencies.
  `MetarDecoder`, `TafDecoder`, `FlightCategory`, `PlainEnglish`,
  shared `ConditionParser`.
- `android/app/src/main/java/com/apexpredator/argus/data/`:
  `WeatherRepository` (aviationweather.gov fetch),
  `HistoryStore` (DataStore, last 10 entries).
- `android/app/src/main/java/com/apexpredator/argus/ui/`:
  Compose screens (home, result, history), dark Material 3 theme,
  navigation, `MainViewModel`.
- `android/app/src/test/`: unit tests for the decoder engine
  (20+ cases: normal/gusty/variable/calm wind, fractional visibility,
  CAVOK, TAF TEMPO/BECMG/FM/PROB, flight categories, invalid input).

## How to build

Prerequisites: JDK 17, Android SDK with platform 35 and build-tools.

```bash
cd android
./gradlew test                 # run decoder unit tests
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew bundleRelease        # unsigned release App Bundle -> app/build/outputs/bundle/release/
```

The Gradle wrapper downloads Gradle 8.9 automatically on first run.

## Distribution

Two flavors, same app, different update channels:

- **play** (`com.apexpredator.argus`): ships to Google Play. Checks for
  updates against the GitHub releases and links to the Play Store listing,
  because Play policy forbids a Play app from updating itself any other way.
- **github** (`com.apexpredator.argus`): published as a signed APK on
  [GitHub Releases](https://github.com/sajidkabir/apex-argus/releases),
  downloadable by anyone with no login. The app checks for updates itself,
  downloads the new APK, and hands it to the system installer (Android
  always shows its own install confirmation; first install needs the
  "install unknown apps" grant for Apex Argus).

Pushing a version tag (`v1.1.0`) runs the release workflow: it builds the
signed github APK and attaches it plus a `version.json` descriptor to the
GitHub release. The APK is signed with a dedicated GitHub-release key, not
the Play upload key, so switching between the GitHub APK and the Play
build requires a reinstall (different signatures).
No keystore or signing config is included on purpose: signing happens
at release time with the Play App Signing upload key.

Note: the sandbox this was built in blocks the Gradle daemon's
loopback socket, so the first verified build here used a manual
pipeline (kotlinc + aapt2 + d8 + bundletool, no Gradle). The Gradle
files are the canonical build definition and work on any normal
machine. The manual pipeline script is kept at
`~/manualbuild/build_apk.sh` for reference.

## Play release checklist status

Done in v1:
- applicationId `com.apexpredator.argus`, versionCode 1, versionName 1.0.0
- INTERNET permission only; no background work, no extra permissions
- No data collection (Data Safety: nothing to declare)
- Adaptive launcher icon (vector, no binary assets)
- Offline decode path (works in airplane mode)

Still to do outside this repo:
- Generate the upload keystore and register it with Play App Signing
- Store listing: short/long description, screenshots, feature graphic,
  privacy policy URL (can state "no data collected")
- Content rating questionnaire, app category, contact email
- Data Safety form: declare no data collected
- Closed testing: 12+ testers for 14 continuous days (new personal
  account requirement), then apply for production access
- Android developer verification registration for the package name
