package com.apexpredator.argus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// One recorded radar frame for the playback feature: every successful
// live fetch is snapshotted to disk, so the user can scrub back through
// the last hour of traffic like the playback mode in paid trackers.
data class Snapshot(val epochMs: Long, val aircraft: List<AircraftState>)

class PlaybackStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "playback").apply { mkdirs() }

    suspend fun record(states: List<AircraftState>) {
        if (states.isEmpty()) return
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val root = JSONObject()
            root.put("time", now)
            val arr = JSONArray()
            for (ac in states) arr.put(ac.toStateVector())
            root.put("states", arr)
            File(dir, "snapshot-$now.json").writeText(root.toString())
            prune()
        }
    }

    suspend fun loadAll(): List<Snapshot> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { f -> f.name.startsWith("snapshot-") }
            ?.sortedBy { it.name }
            ?: return@withContext emptyList()
        val out = ArrayList<Snapshot>()
        for (f in files) {
            try {
                val root = JSONObject(f.readText())
                val body = JSONObject()
                    .put("time", root.optLong("time"))
                    .put("states", root.optJSONArray("states") ?: JSONArray())
                    .toString()
                out.add(Snapshot(root.optLong("time"), parseAircraftStates(body)))
            } catch (e: Exception) {
                // Corrupt snapshot: skip it, keep the rest.
            }
        }
        out.sortedBy { it.epochMs }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.startsWith("snapshot-") }
            ?.sortedBy { it.name }
            ?: return
        if (files.size > MAX_SNAPSHOTS) {
            files.take(files.size - MAX_SNAPSHOTS).forEach { it.delete() }
        }
    }

    companion object {
        private const val MAX_SNAPSHOTS = 60
    }
}

// Serializes back into the OpenSky 18-slot state-vector layout, plus
// Argus's own slot 18 for the local-feed mark, so the existing parser
// reads snapshots and a recorded frame keeps its LOCAL marking.
private fun AircraftState.toStateVector(): JSONArray {
    val arr = JSONArray()
    val slots = arrayOfNulls<Any>(19)
    slots[0] = icao24
    slots[1] = callsign
    slots[2] = originCountry
    slots[3] = lastSeenMs?.div(1000)
    slots[5] = longitude
    slots[6] = latitude
    slots[7] = baroAltitudeM
    slots[8] = onGround
    slots[9] = velocityMs
    slots[10] = trueTrackDeg
    slots[11] = verticalRateMs
    slots[14] = squawk
    slots[17] = category
    slots[18] = isLocal
    for (slot in slots) arr.put(slot ?: JSONObject.NULL)
    return arr
}
