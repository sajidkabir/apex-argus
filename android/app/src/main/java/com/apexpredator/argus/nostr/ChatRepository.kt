package com.apexpredator.argus.nostr

import android.content.Context
import com.apexpredator.argus.nostr.crypto.NostrKeys
import com.apexpredator.argus.nostr.crypto.Secp256k1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class Identity(
    val secretKeyHex: String,
    val pubkeyHex: String,
    val npub: String,
    val username: String
)

data class FoundProfile(
    val pubkeyHex: String,
    val npub: String,
    val name: String,
    val about: String
)

data class ChatMessage(val dir: String, val text: String, val ts: Long)

data class Conversation(
    val peerHex: String,
    val peerNpub: String,
    val peerName: String?,
    val messages: List<ChatMessage>,
    val lastTs: Long
)

// Owns the Argus Chat identity, the Nostr relay pool, people search, and
// encrypted direct messages. The secret key lives only in app-private
// storage (argus_nostr_identity.json); decrypted messages are cached in
// app-private storage (argus_chat_store.json) so the UI renders instantly
// and the network only fills in what is new.
class ChatRepository(private val appContext: Context) {

    private data class PeerStore(var name: String?, val messages: MutableList<ChatMessage>)

    private val identityFile: File get() = File(appContext.filesDir, "argus_nostr_identity.json")
    private val storeFile: File get() = File(appContext.filesDir, "argus_chat_store.json")

    private val clientLock = Any()
    @Volatile private var client: NostrClient? = null
    @Volatile private var profilePublishedFor: String? = null

    private val storeLock = Any()
    private var store: MutableMap<String, PeerStore>

    init {
        // Synchronous disk load at startup so the chat UI can render
        // instantly; network refresh happens afterwards.
        store = loadStore()
    }

    // Identity

    fun getIdentity(): Identity? {
        val file = identityFile
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            Identity(
                secretKeyHex = o.getString("secretKeyHex"),
                pubkeyHex = o.getString("pubkeyHex"),
                npub = o.getString("npub"),
                username = o.getString("username")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveIdentity(identity: Identity) {
        val o = JSONObject()
        o.put("secretKeyHex", identity.secretKeyHex)
        o.put("pubkeyHex", identity.pubkeyHex)
        o.put("npub", identity.npub)
        o.put("username", identity.username)
        identityFile.writeText(o.toString())
    }

    fun getOrCreateIdentity(username: String): Identity {
        val clean = username.trim().ifEmpty { "Argus user" }
        val existing = getIdentity()
        if (existing != null) {
            if (existing.username == clean) return existing
            val updated = existing.copy(username = clean)
            saveIdentity(updated)
            profilePublishedFor = null
            ensureClient().connect { publishProfile(updated) }
            return updated
        }
        val secret = Secp256k1.generatePrivateKey()
        val pub = Secp256k1.xOnlyPublicKey(secret)
        val identity = Identity(
            secretKeyHex = Hex.encode(secret),
            pubkeyHex = Hex.encode(pub),
            npub = NostrKeys.npub(pub),
            username = clean
        )
        saveIdentity(identity)
        ensureClient().connect { publishProfile(identity) }
        return identity
    }

    fun importIdentity(nsec: String, username: String): Identity {
        val clean = username.trim().ifEmpty { "Argus user" }
        val secret = NostrKeys.secretFromNsec(nsec.trim())
        require(secret.size == 32) { "Invalid nsec key" }
        val pub = Secp256k1.xOnlyPublicKey(secret)
        val identity = Identity(
            secretKeyHex = Hex.encode(secret),
            pubkeyHex = Hex.encode(pub),
            npub = NostrKeys.npub(pub),
            username = clean
        )
        saveIdentity(identity)
        profilePublishedFor = null
        ensureClient().connect { publishProfile(identity) }
        return identity
    }

    fun ensureConnected() {
        val identity = getIdentity() ?: return
        ensureClient().connect { publishProfile(identity) }
    }

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

    private fun publishProfile(identity: Identity) {
        if (profilePublishedFor == identity.pubkeyHex) return
        profilePublishedFor = identity.pubkeyHex
        try {
            val content = JSONObject()
                .put("name", identity.username)
                .put("display_name", identity.username)
                .put("about", "Chat from Apex Argus")
                .toString()
            val event = NostrEvent.create(identity.pubkeyHex, 0, emptyList(), content)
                .sign(Hex.decode(identity.secretKeyHex))
            ensureClient().publish(event)
        } catch (_: Exception) {
        }
    }

    // Profiles and people search

    private fun parseFoundProfile(event: NostrEvent): FoundProfile? {
        if (event.kind != 0) return null
        val o = try { JSONObject(event.content) } catch (_: Exception) { return null }
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

    suspend fun fetchProfile(pubkeyHex: String, timeoutMs: Long = 12_000): FoundProfile? =
        withContext(Dispatchers.IO) {
            val c = ensureClient()
            c.connect()
            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        var sub: AutoCloseable? = null
                        sub = c.subscribe(
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
                                    cont.resume(parseFoundProfile(event))
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

    // NIP-50 username search against a relay that supports it.
    suspend fun searchPeople(query: String): List<FoundProfile> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val searchClient = NostrClient(listOf("wss://relay.nostr.band"))
            try {
                searchClient.connect()
                withTimeout(15_000) {
                    suspendCancellableCoroutine { cont ->
                        val results = mutableListOf<FoundProfile>()
                        val seen = mutableSetOf<String>()
                        var done = false
                        var sub: AutoCloseable? = null
                        fun finish() {
                            if (!done) {
                                done = true
                                sub?.close()
                                if (!cont.isCompleted) cont.resume(results.toList())
                            }
                        }
                        sub = searchClient.subscribe(
                            listOf(
                                mapOf(
                                    "kinds" to listOf(0),
                                    "search" to q,
                                    "limit" to 20
                                )
                            ),
                            onEvent = { event ->
                                synchronized(seen) {
                                    if (seen.add(event.pubkey)) {
                                        parseFoundProfile(event)?.let { results.add(it) }
                                    }
                                }
                            },
                            onEose = { finish() }
                        )
                        cont.invokeOnCancellation { finish() }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                try { searchClient.close() } catch (_: Exception) {}
            }
        }

    suspend fun lookupNpub(npub: String): FoundProfile? =
        withContext(Dispatchers.IO) {
            val pubHex = try {
                Hex.encode(NostrKeys.pubkeyFromNpub(npub.trim()))
            } catch (_: Exception) {
                return@withContext null
            }
            fetchProfile(pubHex)
        }

    // Direct messages (NIP-04)

    suspend fun sendDm(peerHex: String, text: String): Boolean =
        withContext(Dispatchers.IO) {
            val identity = getIdentity() ?: return@withContext false
            val body = text.trim()
            if (body.isEmpty()) return@withContext false
            val cleanPeer = peerHex.lowercase()
            try {
                val secret = Hex.decode(identity.secretKeyHex)
                val payload = Nip04.encrypt(secret, cleanPeer, body)
                val event = NostrEvent.create(
                    pubkeyHex = identity.pubkeyHex,
                    kind = 4,
                    tags = listOf(listOf("p", cleanPeer)),
                    content = payload
                ).sign(secret)
                val c = ensureClient()
                c.connect()
                c.publish(event)
                val msg = ChatMessage("out", body, System.currentTimeMillis() / 1000)
                synchronized(storeLock) {
                    val peer = store.getOrPut(cleanPeer) { PeerStore(null, mutableListOf()) }
                    peer.messages.add(msg)
                    trimPeer(peer)
                    saveStore()
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    // Pulls kind-4 events addressed to us and sent by us, decrypts them,
    // merges with the local store, and returns conversations newest first.
    suspend fun refreshConversations(): List<Conversation> =
        withContext(Dispatchers.IO) {
            val identity = getIdentity() ?: return@withContext cachedConversations()
            val secret = try {
                Hex.decode(identity.secretKeyHex)
            } catch (_: Exception) {
                return@withContext cachedConversations()
            }
            val ownHex = identity.pubkeyHex.lowercase()
            val c = ensureClient()
            c.connect()
            val incoming = fetchEvents(
                c, mapOf("kinds" to listOf(4), "#p" to listOf(ownHex), "limit" to 200)
            )
            val sent = fetchEvents(
                c, mapOf("kinds" to listOf(4), "authors" to listOf(ownHex), "limit" to 200)
            )
            synchronized(storeLock) {
                for (event in (incoming + sent).sortedBy { it.createdAt }) {
                    val peerHex: String
                    val dir: String
                    if (event.pubkey.lowercase() == ownHex) {
                        val p = event.tags.firstOrNull { it.firstOrNull() == "p" }
                            ?.getOrNull(1) ?: continue
                        peerHex = p.lowercase()
                        dir = "out"
                    } else {
                        peerHex = event.pubkey.lowercase()
                        dir = "in"
                    }
                    val text = try {
                        Nip04.decrypt(secret, peerHex, event.content)
                    } catch (_: Exception) {
                        continue
                    }
                    val peer = store.getOrPut(peerHex) { PeerStore(null, mutableListOf()) }
                    val known = peer.messages.any {
                        it.ts == event.createdAt && it.dir == dir && it.text == text
                    }
                    if (!known) {
                        peer.messages.add(ChatMessage(dir, text, event.createdAt))
                    }
                }
                for (peer in store.values) {
                    peer.messages.sortBy { it.ts }
                    trimPeer(peer)
                }
                saveStore()
            }
            // Best-effort display names for peers we have not named yet.
            val unknown = synchronized(storeLock) {
                store.filter { it.value.name.isNullOrEmpty() }.keys.toList()
            }
            for (peerHex in unknown.take(8)) {
                val profile = fetchProfile(peerHex, 6_000)
                if (profile != null && profile.name.isNotEmpty()) {
                    synchronized(storeLock) {
                        store[peerHex]?.name = profile.name
                    }
                    saveStore()
                }
            }
            cachedConversations()
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

    fun cachedConversations(): List<Conversation> {
        synchronized(storeLock) {
            return store.map { (peerHex, peer) ->
                Conversation(
                    peerHex = peerHex,
                    peerNpub = try {
                        NostrKeys.npub(Hex.decode(peerHex))
                    } catch (_: Exception) {
                        ""
                    },
                    peerName = peer.name,
                    messages = peer.messages.toList(),
                    lastTs = peer.messages.maxOfOrNull { it.ts } ?: 0L
                )
            }.sortedByDescending { it.lastTs }
        }
    }

    fun cachePeerName(peerHex: String, name: String) {
        if (name.isEmpty()) return
        synchronized(storeLock) {
            val peer = store.getOrPut(peerHex.lowercase()) { PeerStore(null, mutableListOf()) }
            if (peer.name.isNullOrEmpty()) {
                peer.name = name
                saveStore()
            }
        }
    }

    // Local persistence

    private fun loadStore(): MutableMap<String, PeerStore> {
        val out = mutableMapOf<String, PeerStore>()
        try {
            val file = storeFile
            if (!file.exists()) return out
            val peers = JSONObject(file.readText()).optJSONObject("peers") ?: return out
            val keys = peers.keys()
            while (keys.hasNext()) {
                val hex = keys.next()
                val p = peers.optJSONObject(hex) ?: continue
                val name = p.optString("name", "").ifEmpty { null }
                val messages = mutableListOf<ChatMessage>()
                val arr = p.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    messages.add(
                        ChatMessage(
                            dir = m.optString("dir", "in"),
                            text = m.optString("text", ""),
                            ts = m.optLong("ts", 0)
                        )
                    )
                }
                messages.sortBy { it.ts }
                out[hex] = PeerStore(name, messages.takeLast(MAX_MESSAGES_PER_PEER).toMutableList())
            }
        } catch (_: Exception) {
            // Corrupt store: start fresh rather than crash.
        }
        return out
    }

    private fun saveStore() {
        try {
            val peers = JSONObject()
            for ((hex, peer) in store) {
                val p = JSONObject()
                p.put("name", peer.name ?: "")
                val arr = JSONArray()
                for (m in peer.messages.takeLast(MAX_MESSAGES_PER_PEER)) {
                    arr.put(
                        JSONObject()
                            .put("dir", m.dir)
                            .put("text", m.text)
                            .put("ts", m.ts)
                    )
                }
                p.put("messages", arr)
                peers.put(hex, p)
            }
            storeFile.writeText(JSONObject().put("peers", peers).toString())
        } catch (_: Exception) {
            // Persistence is best-effort; the in-memory store keeps working.
        }
    }

    private fun trimPeer(peer: PeerStore) {
        while (peer.messages.size > MAX_MESSAGES_PER_PEER) {
            peer.messages.removeAt(0)
        }
    }

    companion object {
        private const val MAX_MESSAGES_PER_PEER = 200
    }
}
