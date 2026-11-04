package com.apexpredator.argus.nostr

import com.apexpredator.argus.nostr.crypto.NostrKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

// Reads and writes the public Argus sightings channel over the same
// relay pool as Argus Chat, but with its own client so the channel
// subscription never interferes with direct messages. The identity
// (keypair and username) is shared with Chat: one Argus ID, used for
// both private chats and public sightings.
class SightingsRepository {

    private val clientLock = Any()
    @Volatile private var client: NostrClient? = null

    private fun ensureClient(): NostrClient {
        synchronized(clientLock) {
            var c = client
            if (c == null) {
                c = NostrClient()
                client = c
            }
            return c
        }
    }

    suspend fun publishSighting(
        identity: Identity,
        aircraft: String,
        location: String,
        note: String,
        photoUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val secret = Hex.decode(identity.secretKeyHex)
            val event = NostrEvent.create(
                pubkeyHex = identity.pubkeyHex,
                kind = SIGHTING_KIND,
                tags = buildSightingTags(aircraft, location),
                content = buildSightingContent(aircraft, location, note, photoUrl)
            ).sign(secret)
            val c = ensureClient()
            c.connect()
            c.publish(event)
            true
        } catch (_: Exception) {
            false
        }
    }

    // The most recent channel posts, newest first, with poster display
    // names resolved best-effort from kind-0 profiles. Authors with no
    // published profile show a short form of their Argus ID instead.
    suspend fun fetchSightings(): List<PublicSighting> =
        withContext(Dispatchers.IO) {
            val c = ensureClient()
            c.connect()
            val events = fetchEvents(
                c,
                mapOf(
                    "kinds" to listOf(SIGHTING_KIND),
                    "#t" to listOf(SIGHTING_HASHTAG),
                    "limit" to 100
                )
            )
            if (events.isEmpty()) return@withContext emptyList()
            val names = mutableMapOf<String, String>()
            for (event in events) {
                names.putIfAbsent(event.pubkey.lowercase(), "")
            }
            for (hex in names.keys.take(12)) {
                val profile = fetchProfile(c, hex, 6_000)
                if (profile != null && profile.name.isNotEmpty()) {
                    names[hex] = profile.name
                }
            }
            events.sortedByDescending { it.createdAt }.mapNotNull { event ->
                val hex = event.pubkey.lowercase()
                val name = names[hex].orEmpty().ifEmpty {
                    shortNpub(event.pubkey)
                }
                parseSighting(event, name)
            }
        }

    private suspend fun fetchProfile(
        client: NostrClient,
        pubkeyHex: String,
        timeoutMs: Long
    ): FoundProfile? {
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    var sub: AutoCloseable? = null
                    sub = client.subscribe(
                        listOf(
                            mapOf(
                                "kinds" to listOf(0),
                                "authors" to listOf(pubkeyHex.lowercase()),
                                "limit" to 1
                            )
                        ),
                        onEvent = { event ->
                            if (!cont.isCompleted) {
                                sub?.close()
                                cont.resume(parseProfile(event))
                            }
                        }
                    )
                    cont.invokeOnCancellation { sub?.close() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProfile(event: NostrEvent): FoundProfile? {
        if (event.kind != 0) return null
        val o = try {
            org.json.JSONObject(event.content)
        } catch (_: Exception) {
            return null
        }
        val name = o.optString("display_name", "").ifEmpty { o.optString("name", "") }
        val npub = try {
            NostrKeys.npub(Hex.decode(event.pubkey))
        } catch (_: Exception) {
            ""
        }
        return FoundProfile(
            pubkeyHex = event.pubkey,
            npub = npub,
            name = name,
            about = o.optString("about", "")
        )
    }

    private fun shortNpub(pubkeyHex: String): String {
        return try {
            NostrKeys.npub(Hex.decode(pubkeyHex)).take(16) + "..."
        } catch (_: Exception) {
            "Argus user"
        }
    }

    private suspend fun fetchEvents(
        client: NostrClient,
        filter: Map<String, Any>
    ): List<NostrEvent> {
        return try {
            withTimeout(20_000) {
                suspendCancellableCoroutine { cont ->
                    val events = mutableListOf<NostrEvent>()
                    val seen = mutableSetOf<String>()
                    val eoseCount = AtomicInteger(0)
                    val expected = maxOf(client.connectedRelayCount(), 1)
                    var done = false
                    var sub: AutoCloseable? = null
                    fun finish() {
                        if (!done) {
                            done = true
                            sub?.close()
                            if (!cont.isCompleted) cont.resume(events.toList())
                        }
                    }
                    sub = client.subscribe(
                        listOf(filter),
                        onEvent = { event ->
                            synchronized(seen) {
                                if (seen.add(event.id)) events.add(event)
                            }
                        },
                        onEose = {
                            if (eoseCount.incrementAndGet() >= expected) finish()
                        }
                    )
                    cont.invokeOnCancellation { finish() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
