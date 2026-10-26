package com.apexpredator.argus.data

// Free public cameras, verified one by one (Oct 2026): every URL here
// is a camera page that plays without an account or a subscription.
// Windy is deliberately absent: its webcam map started demanding a
// subscription inside the app, and Argus must never gate a feature
// behind someone else's paywall. SkylineWebcams pages are free to
// watch; the aviation entries are public live-spotting searches on
// YouTube, which also need no account.
enum class CamKind { CITY, AVIATION }

data class PublicCam(
    val name: String,
    val place: String,
    val lat: Double,
    val lon: Double,
    val url: String,
    val kind: CamKind,
)

val PUBLIC_CAMS: List<PublicCam> = listOf(
    PublicCam(
        "Times Square", "New York, US", 40.7580, -73.9855,
        "https://www.skylinewebcams.com/en/webcam/united-states/new-york/new-york/times-square.html",
        CamKind.CITY
    ),
    PublicCam(
        "Eiffel Tower", "Paris, France", 48.8584, 2.2945,
        "https://www.skylinewebcams.com/en/webcam/france/ile-de-france/paris/tour-eiffel.html",
        CamKind.CITY
    ),
    PublicCam(
        "Sydney Harbour", "Sydney, Australia", -33.8568, 151.2153,
        "https://www.skylinewebcams.com/en/webcam/australia/new-south-wales/sydney/sydney.html",
        CamKind.CITY
    ),
    PublicCam(
        "Skyline of Shanghai", "Shanghai, China", 31.2397, 121.4998,
        "https://www.skylinewebcams.com/en/webcam/china/shanghai/shanghai/skyline-of-shanghai.html",
        CamKind.CITY
    ),
    PublicCam(
        "Piazza San Marco", "Venice, Italy", 45.4343, 12.3388,
        "https://www.skylinewebcams.com/en/webcam/italia/veneto/venezia/piazza-san-marco.html",
        CamKind.CITY
    ),
    PublicCam(
        "Piazza di Spagna", "Rome, Italy", 41.9058, 12.4827,
        "https://www.skylinewebcams.com/en/webcam/italia/lazio/roma/piazza-di-spagna.html",
        CamKind.CITY
    ),
    PublicCam(
        "Trevi Fountain", "Rome, Italy", 41.9009, 12.4833,
        "https://www.skylinewebcams.com/en/webcam/italia/lazio/roma/fontana-di-trevi.html",
        CamKind.CITY
    ),
    PublicCam(
        "Milan Cathedral", "Milan, Italy", 45.4642, 9.1900,
        "https://www.skylinewebcams.com/en/webcam/italia/lombardia/milano/duomo-milano.html",
        CamKind.CITY
    ),
    PublicCam(
        "Hellenic Parliament", "Athens, Greece", 37.9755, 23.7348,
        "https://www.skylinewebcams.com/en/webcam/ellada/atiki/athina/hellenic-parliament.html",
        CamKind.CITY
    ),
    PublicCam(
        "Meeru Island Beach", "Maldives", 4.4567, 73.7125,
        "https://www.skylinewebcams.com/en/webcam/maldives/north-male-atoll/meerufenfushi/meeru-island-beach.html",
        CamKind.CITY
    ),
    PublicCam(
        "Los Angeles (LAX) live spotting", "Los Angeles, US", 33.9416, -118.4085,
        "https://www.youtube.com/results?search_query=lax+airport+live+plane+spotting",
        CamKind.AVIATION
    ),
    PublicCam(
        "London Heathrow live spotting", "London, UK", 51.4700, -0.4543,
        "https://www.youtube.com/results?search_query=heathrow+airport+live+plane+spotting",
        CamKind.AVIATION
    ),
    PublicCam(
        "St Maarten (SXM) beach cam", "Sint Maarten", 18.0410, -63.1089,
        "https://www.youtube.com/results?search_query=maho+beach+st+maarten+live+cam",
        CamKind.AVIATION
    ),
    PublicCam(
        "Dhaka (VGHS) plane spotting", "Dhaka, Bangladesh", 23.8433, 90.3978,
        "https://www.youtube.com/results?search_query=dhaka+airport+plane+spotting+live",
        CamKind.AVIATION
    ),
    PublicCam(
        "Frankfurt (FRA) live spotting", "Frankfurt, Germany", 50.0333, 8.5622,
        "https://www.youtube.com/results?search_query=frankfurt+airport+live+plane+spotting",
        CamKind.AVIATION
    ),
    PublicCam(
        "Tokyo Haneda (HND) live cam", "Tokyo, Japan", 35.5494, 139.7798,
        "https://www.youtube.com/results?search_query=haneda+airport+live+camera",
        CamKind.AVIATION
    ),
    PublicCam(
        "Petra - The Treasury", "Jordan", 30.3285, 35.4444,
        "https://www.skylinewebcams.com/en/webcam/jordan/maan/amman/petra-the-treasury.html",
        CamKind.CITY
    ),
    PublicCam(
        "Jokulsarlon Glacier Lagoon", "Iceland", 64.0784, -16.2306,
        "https://www.skylinewebcams.com/en/webcam/iceland/austurland/hofn/jokulsarlon.html",
        CamKind.CITY
    ),
    PublicCam(
        "Ushuaia Port", "Argentina", -54.8019, -68.3030,
        "https://www.skylinewebcams.com/en/webcam/argentina/tierra-del-fuego/ushuaia/ushuaia.html",
        CamKind.CITY
    ),
    PublicCam(
        "Lower Zambezi Safari", "Zambia", -15.7500, 29.4000,
        "https://www.skylinewebcams.com/en/webcam/zambia/lusaka-province/mafuta/zambezi-national-park.html",
        CamKind.CITY
    ),
    PublicCam(
        "Cape Town - Zeekoevlei", "South Africa", -34.0667, 18.5167,
        "https://www.skylinewebcams.com/en/webcam/south-africa/western-cape/cape-town/zeekoevlei.html",
        CamKind.CITY
    ),
    PublicCam(
        "Kahului - Haleakala", "Hawaii, US", 20.8895, -156.4729,
        "https://www.skylinewebcams.com/en/webcam/united-states/hawaii/maui/kahului.html",
        CamKind.CITY
    ),
)

// Full free directories, for browsing far beyond the bundled markers.
const val SKYLINE_DIRECTORY_URL = "https://www.skylinewebcams.com/en.html"
const val EARTHCAM_DIRECTORY_URL = "https://www.earthcam.com"
