package com.apexpredator.argus.data

// Curated public aviation webcams and live spotting streams. These
// are cameras their operators publish for anyone to watch (airport
// spotting channels, city and harbour cams near famous approaches);
// the app opens them in the browser rather than embedding streams it
// has no right to redistribute.
data class LiveCam(
    val name: String,
    val place: String,
    val note: String,
    val url: String,
)

val LIVE_CAMS: List<LiveCam> = listOf(
    LiveCam(
        "Windy Webcams Map",
        "Worldwide",
        "Thousands of public webcams on a map. Pan to any airport or city.",
        "https://www.windy.com/webcams"
    ),
    LiveCam(
        "SkylineWebcams",
        "Worldwide",
        "Live HD public cams: airports, harbours, cities and landmarks.",
        "https://www.skylinewebcams.com"
    ),
    LiveCam(
        "EarthCam",
        "Worldwide",
        "Public webcam network with many airport and city views.",
        "https://www.earthcam.com"
    ),
    LiveCam(
        "Los Angeles (LAX) live spotting",
        "Los Angeles, US",
        "YouTube live plane-spotting streams at LAX, a spotter favourite.",
        "https://www.youtube.com/results?search_query=lax+airport+live+plane+spotting"
    ),
    LiveCam(
        "London Heathrow live spotting",
        "London, UK",
        "YouTube live streams of Heathrow arrivals and departures.",
        "https://www.youtube.com/results?search_query=heathrow+airport+live+plane+spotting"
    ),
    LiveCam(
        "St Maarten (SXM) beach cam",
        "Sint Maarten",
        "The famous Maho Beach approach over the fence at Princess Juliana.",
        "https://www.youtube.com/results?search_query=maho+beach+st+maarten+live+cam"
    ),
    LiveCam(
        "Dhaka (VGHS) plane spotting",
        "Dhaka, Bangladesh",
        "Spotting videos and any live streams at Hazrat Shahjalal International.",
        "https://www.youtube.com/results?search_query=dhaka+airport+plane+spotting+live"
    ),
    LiveCam(
        "Frankfurt (FRA) live spotting",
        "Frankfurt, Germany",
        "YouTube live streams from Frankfurt am Main airport.",
        "https://www.youtube.com/results?search_query=frankfurt+airport+live+plane+spotting"
    ),
    LiveCam(
        "Tokyo Haneda (HND) live cam",
        "Tokyo, Japan",
        "Haneda airport live cameras and spotting streams.",
        "https://www.youtube.com/results?search_query=haneda+airport+live+camera"
    ),
)

// A reasonable public-webcam starting point for any airport: the
// Windy webcams map centred on the field.
fun webcamsNearUrl(airport: Airport): String =
    "https://www.windy.com/webcams?${airport.lat},${airport.lon},10"
