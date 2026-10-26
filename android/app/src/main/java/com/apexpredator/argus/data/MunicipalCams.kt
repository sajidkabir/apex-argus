package com.apexpredator.argus.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONObject

// The godseyeview camera mesh: live municipal CCTV networks, the
// same model as Bilawal Sidhu's God's Eye View (bilawalsidhu/
// gods-eye-view, DATA_SOURCES.md). Instead of a handful of tourist
// webcams, the map carries whole city networks fetched live from
// the cities themselves: catalog JSON with coordinates, plus a
// snapshot image and/or an HLS video stream per camera. No keys,
// no accounts, no subscriptions. Networks whose terms block this
// use are left out on purpose: NYC DOT (needs a signed data-sharing
// agreement) and FL511 (personal use only). TfL needs an app key,
// so London waits for a keyless route.
data class MunicipalCam(
    val id: String,
    val name: String,
    val city: String,
    val network: String,
    val lat: Double,
    val lon: Double,
    val imageUrl: String?,
    val videoUrl: String?,
)

sealed interface MeshStatus {
    data object Idle : MeshStatus
    data object Loading : MeshStatus
    data class Ready(
        val total: Int,
        val perNetwork: Map<String, Int>,
    ) : MeshStatus

    data class Partial(
        val total: Int,
        val perNetwork: Map<String, Int>,
        val failed: List<String>,
    ) : MeshStatus

    data class Failed(val message: String) : MeshStatus
}

internal fun cleanTitle(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim()

internal fun parseDelDot(root: JSONObject): List<MunicipalCam> {
    val out = ArrayList<MunicipalCam>()
    val arr = root.optJSONArray("videoCameras") ?: return out
    for (i in 0 until arr.length()) {
        val cam = arr.optJSONObject(i) ?: continue
        if (cam.optString("status") != "Active") continue
        val lat = cam.optDouble("lat", Double.NaN)
        val lon = cam.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue
        val urls = cam.optJSONObject("urls")
        val video = urls?.optString("m3u8s")?.takeIf { it.isNotBlank() }
        out.add(
            MunicipalCam(
                id = cam.optString("id"),
                name = cleanTitle(cam.optString("title")),
                city = cam.optString("county").ifBlank { "Delaware" },
                network = "DelDOT",
                lat = lat,
                lon = lon,
                imageUrl = null,
                videoUrl = video,
            )
        )
    }
    return out
}

internal fun parseCaltransD4(root: JSONObject): List<MunicipalCam> {
    val out = ArrayList<MunicipalCam>()
    val arr = root.optJSONArray("data") ?: return out
    for (i in 0 until arr.length()) {
        val cctv = arr.optJSONObject(i)?.optJSONObject("cctv") ?: continue
        if (cctv.optString("inService") != "true") continue
        val loc = cctv.optJSONObject("location") ?: continue
        val lat = loc.optString("latitude").toDoubleOrNull() ?: continue
        val lon = loc.optString("longitude").toDoubleOrNull() ?: continue
        val imageData = cctv.optJSONObject("imageData")
        val image = imageData?.optJSONObject("static")
            ?.optString("currentImageURL")?.takeIf { it.isNotBlank() }
        val video = imageData?.optString("streamingVideoURL")
            ?.takeIf { it.isNotBlank() && it != "Not Reported" }
        out.add(
            MunicipalCam(
                id = cctv.optString("index"),
                name = loc.optString("locationName"),
                city = loc.optString("nearbyPlace").ifBlank { "Bay Area" },
                network = "Caltrans D4",
                lat = lat,
                lon = lon,
                imageUrl = image,
                videoUrl = video,
            )
        )
    }
    return out
}

// Calgary's open traffic cameras: a Socrata JSON array. Each row
// carries a snapshot image URL, a street location, and a GeoJSON
// point whose coordinates are [longitude, latitude] in that order.
internal fun parseCalgary(root: org.json.JSONArray): List<MunicipalCam> {
    val out = ArrayList<MunicipalCam>()
    for (i in 0 until root.length()) {
        val row = root.optJSONObject(i) ?: continue
        val image = row.optJSONObject("camera_url")
            ?.optString("url")?.takeIf { it.isNotBlank() } ?: continue
        val point = row.optJSONObject("point")
            ?.optJSONArray("coordinates") ?: continue
        if (point.length() < 2) continue
        val lon = point.optDouble(0, Double.NaN)
        val lat = point.optDouble(1, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue
        out.add(
            MunicipalCam(
                id = row.optJSONObject("camera_url")
                    ?.optString("description").orEmpty(),
                name = row.optString("camera_location"),
                city = "Calgary",
                network = "Calgary",
                lat = lat,
                lon = lon,
                imageUrl = image,
                videoUrl = null,
            )
        )
    }
    return out
}

// One mesh entry: a live catalog URL plus its parser. Adding a
// network is one entry here plus its parser above.
private data class MeshSource(
    val network: String,
    val catalogUrl: String,
    val parse: (JSONObject) -> List<MunicipalCam>,
)

private val MESH_SOURCES = listOf(
    MeshSource(
        "DelDOT",
        "https://tmc.deldot.gov/json/videocamera.json",
        ::parseDelDot
    ),
    MeshSource(
        "Caltrans D4",
        "https://cwwp2.dot.ca.gov/data/d4/cctv/cctvStatusD04.json",
        ::parseCaltransD4
    ),
)

// Calgary's catalog is a JSON array, not an object, so it rides
// alongside the object feeds with its own fetch step.
private const val CALGARY_CATALOG_URL =
    "https://data.calgary.ca/resource/k7p9-kppz.json?\$limit=50000"

// The live mesh: fetched once per app session (plus manual
// refresh), held in memory, shared by the Cams map and the radar
// camera layer. A single network failing never takes the mesh down.
object MunicipalCamMesh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cams = MutableStateFlow<List<MunicipalCam>>(emptyList())
    val cams: StateFlow<List<MunicipalCam>> = _cams.asStateFlow()

    private val _status = MutableStateFlow<MeshStatus>(MeshStatus.Idle)
    val status: StateFlow<MeshStatus> = _status.asStateFlow()

    // A mesh camera handed in from the radar map: the Cams screen
    // opens its sheet for it, then clears this.
    val selected = MutableStateFlow<MunicipalCam?>(null)

    fun refresh() {
        if (_status.value == MeshStatus.Loading) return
        _status.value = MeshStatus.Loading
        scope.launch {
            try {
                val results = supervisorScope {
                    MESH_SOURCES.map { source ->
                        async {
                            try {
                                source.network to source.parse(
                                    HttpJson.getJson(source.catalogUrl)
                                )
                            } catch (e: Exception) {
                                source.network to null
                            }
                        }
                    }.map { it.await() }
                }
                val perNetwork = LinkedHashMap<String, Int>()
                val all = ArrayList<MunicipalCam>()
                val failed = ArrayList<String>()
                for ((network, cams) in results) {
                    if (cams == null) {
                        failed.add(network)
                    } else {
                        perNetwork[network] = cams.size
                        all.addAll(cams)
                    }
                }
                try {
                    val calgary = parseCalgary(
                        org.json.JSONArray(HttpJson.getText(CALGARY_CATALOG_URL))
                    )
                    perNetwork["Calgary"] = calgary.size
                    all.addAll(calgary)
                } catch (e: Exception) {
                    failed.add("Calgary")
                }
                _cams.value = all
                _status.value = when {
                    all.isEmpty() -> MeshStatus.Failed(
                        "No camera network answered. Check connection and retry."
                    )
                    failed.isEmpty() -> MeshStatus.Ready(all.size, perNetwork)
                    else -> MeshStatus.Partial(all.size, perNetwork, failed)
                }
            } catch (e: Exception) {
                _status.value = MeshStatus.Failed(
                    e.message ?: "Mesh fetch failed."
                )
            }
        }
    }
}
