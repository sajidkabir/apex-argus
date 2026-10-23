package com.apexpredator.argus.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private data class Subscription(
    val id: String,
    val filters: List<Map<String, Any>>,
    val onEvent: (NostrEvent) -> Unit,
    val onEose: (() -> Unit)?,
    val seenIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
)

// A small Nostr relay pool over OkHttp WebSockets (NIP-01). Keeps one
// socket per relay, resends live subscriptions on reconnect, queues
// publishes made while offline, and reconnects with backoff. All network
// work stays on OkHttp threads and a Dispatchers.IO scope, never the UI
// thread.
class NostrClient(
    relays: List<String> = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.primal.net"
    )
) {
    private val relayUrls = relays.distinct()
    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val pendingPublishes = ConcurrentHashMap.newKeySet<NostrEvent>()
    private val backoffLevel = ConcurrentHashMap<String, Int>()
    private val readyCallbacks = mutableListOf<() -> Unit>()
    @Volatile private var closed = false
    @Volatile private var readyFired = false

    fun connectedRelayCount(): Int = sockets.size

    // Opens a socket per relay. onReady fires once, when the first relay
    // connects (or immediately if one already has).
    fun connect(onReady: () -> Unit = {}) {
        synchronized(readyCallbacks) {
            if (readyFired) {
                try { onReady() } catch (_: Exception) {}
            } else {
                readyCallbacks.add(onReady)
            }
        }
        if (closed) return
        for (url in relayUrls) {
            if (!sockets.containsKey(url)) openSocket(url)
        }
    }

    fun publish(event: NostrEvent) {
        if (closed) return
        val open = sockets.values.toList()
        if (open.isEmpty()) {
            pendingPublishes.add(event)
            scope.launch { connect() }
        } else {
            for (ws in open) sendEvent(ws, event)
        }
    }

    // Subscribes on every connected relay. Returns a handle that sends
    // CLOSE and drops the subscription.
    fun subscribe(
        filters: List<Map<String, Any>>,
        onEvent: (NostrEvent) -> Unit,
        onEose: (() -> Unit)? = null
    ): AutoCloseable {
        val id = "argus" + UUID.randomUUID().toString().replace("-", "").take(16)
        val sub = Subscription(id, filters, onEvent, onEose)
        subscriptions[id] = sub
        for (ws in sockets.values) sendReq(ws, sub)
        if (sockets.isEmpty()) scope.launch { connect() }
        return AutoCloseable {
            subscriptions.remove(id)
            val closeMsg = JSONArray().put("CLOSE").put(id).toString()
            for (ws in sockets.values) {
                try { ws.send(closeMsg) } catch (_: Exception) {}
            }
        }
    }

    fun close() {
        closed = true
        for (ws in sockets.values) {
            try { ws.close(1000, "client closing") } catch (_: Exception) {}
        }
        sockets.clear()
        subscriptions.clear()
        pendingPublishes.clear()
        try { http.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    private fun openSocket(url: String) {
        if (closed) return
        val request = Request.Builder().url(url).build()
        http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sockets[url] = webSocket
                backoffLevel.remove(url)
                fireReady()
                for (sub in subscriptions.values) sendReq(webSocket, sub)
                val queued = pendingPublishes.toList()
                pendingPublishes.removeAll(queued.toSet())
                for (event in queued) sendEvent(webSocket, event)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                sockets.remove(url, webSocket)
                scheduleReconnect(url)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                sockets.remove(url, webSocket)
                try { webSocket.cancel() } catch (_: Exception) {}
                scheduleReconnect(url)
            }
        })
    }

    private fun fireReady() {
        val callbacks: List<() -> Unit>
        synchronized(readyCallbacks) {
            if (readyFired) return
            readyFired = true
            callbacks = readyCallbacks.toList()
            readyCallbacks.clear()
        }
        for (cb in callbacks) {
            try { cb() } catch (_: Exception) {}
        }
    }

    private fun scheduleReconnect(url: String) {
        if (closed) return
        val level = minOf((backoffLevel[url] ?: 0) + 1, 6)
        backoffLevel[url] = level
        scope.launch {
            delay(minOf(1L shl level, 60L) * 1000)
            if (!closed && !sockets.containsKey(url)) openSocket(url)
        }
    }

    private fun sendEvent(ws: WebSocket, event: NostrEvent) {
        try {
            val arr = JSONArray()
            arr.put("EVENT")
            arr.put(JSONObject(event.toJsonString()))
            ws.send(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun sendReq(ws: WebSocket, sub: Subscription) {
        try {
            val arr = JSONArray()
            arr.put("REQ")
            arr.put(sub.id)
            for (filter in sub.filters) arr.put(JSONObject(filter))
            ws.send(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun handleMessage(text: String) {
        val arr = try { JSONArray(text) } catch (_: Exception) { return }
        if (arr.length() == 0) return
        when (arr.optString(0)) {
            "EVENT" -> {
                val sub = subscriptions[arr.optString(1, "")] ?: return
                val obj = arr.optJSONObject(2) ?: return
                val event = try {
                    NostrEvent.parse(obj.toString())
                } catch (_: Exception) { return }
                if (!sub.seenIds.add(event.id)) return
                try { sub.onEvent(event) } catch (_: Exception) {}
            }
            "EOSE" -> {
                val sub = subscriptions[arr.optString(1, "")] ?: return
                try { sub.onEose?.invoke() } catch (_: Exception) {}
            }
            "CLOSED" -> {
                subscriptions.remove(arr.optString(1, ""))
            }
            // NOTICE, OK, AUTH: informational; this client does not
            // authenticate with relays, so AUTH challenges are ignored.
            else -> {}
        }
    }
}
