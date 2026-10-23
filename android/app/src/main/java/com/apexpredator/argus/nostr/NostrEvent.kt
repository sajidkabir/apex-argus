package com.apexpredator.argus.nostr

import com.apexpredator.argus.nostr.crypto.Secp256k1
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

// Hex helpers shared by the nostr package.
internal object Hex {
    fun encode(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    fun decode(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Odd-length hex string" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

// A Nostr event (NIP-01). The id is the lowercase hex of SHA-256 over the
// UTF-8 bytes of the canonical serialization:
//   [0,"<pubkey>",<createdAt>,<kind>,<tags>,"<content>"]
// with strict minimal escaping, exactly as NIP-01 specifies. The sig is a
// BIP-340 Schnorr signature over the 32 id bytes.
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {
        fun canonicalJson(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String {
            val sb = StringBuilder()
            sb.append("[0,\"")
            sb.append(pubkey)
            sb.append("\",")
            sb.append(createdAt)
            sb.append(',')
            sb.append(kind)
            sb.append(',')
            sb.append('[')
            tags.forEachIndexed { i, tag ->
                if (i > 0) sb.append(',')
                sb.append('[')
                tag.forEachIndexed { j, value ->
                    if (j > 0) sb.append(',')
                    sb.append('"')
                    sb.append(escapeString(value))
                    sb.append('"')
                }
                sb.append(']')
            }
            sb.append("],\"")
            sb.append(escapeString(content))
            sb.append("\"]")
            return sb.toString()
        }

        // Strict minimal JSON string escaping: quote, backslash, and the
        // control characters with short escapes, everything else below
        // 0x20 as \u00XX. Non-ASCII passes through as UTF-8.
        fun escapeString(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c.code < 0x20) {
                        sb.append("\\u00")
                        sb.append(c.code.toString(16).padStart(2, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
            return sb.toString()
        }

        fun create(
            pubkeyHex: String,
            kind: Int,
            tags: List<List<String>>,
            content: String,
            createdAt: Long = System.currentTimeMillis() / 1000
        ): NostrEvent {
            val preimage = canonicalJson(pubkeyHex, createdAt, kind, tags, content)
            val idBytes = MessageDigest.getInstance("SHA-256")
                .digest(preimage.toByteArray(Charsets.UTF_8))
            return NostrEvent(
                id = Hex.encode(idBytes),
                pubkey = pubkeyHex,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = ""
            )
        }

        fun parse(json: String): NostrEvent {
            val o = JSONObject(json)
            val tagsJson = o.getJSONArray("tags")
            val tags = ArrayList<List<String>>(tagsJson.length())
            for (i in 0 until tagsJson.length()) {
                val tag = tagsJson.getJSONArray(i)
                val items = ArrayList<String>(tag.length())
                for (j in 0 until tag.length()) {
                    items.add(tag.getString(j))
                }
                tags.add(items)
            }
            return NostrEvent(
                id = o.getString("id"),
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = tags,
                content = o.getString("content"),
                sig = o.optString("sig", "")
            )
        }
    }

    // Signs the event id with a BIP-340 Schnorr signature. The message
    // signed is the 32 raw id bytes, per NIP-01.
    fun sign(
        secretKey: ByteArray,
        auxRand: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    ): NostrEvent {
        val idBytes = Hex.decode(id)
        val sigBytes = Secp256k1.schnorrSign(secretKey, idBytes, auxRand)
        return copy(sig = Hex.encode(sigBytes))
    }

    fun toJsonString(): String {
        val o = JSONObject()
        o.put("id", id)
        o.put("pubkey", pubkey)
        o.put("created_at", createdAt)
        o.put("kind", kind)
        val tagsJson = JSONArray()
        for (tag in tags) {
            val t = JSONArray()
            for (value in tag) t.put(value)
            tagsJson.put(t)
        }
        o.put("tags", tagsJson)
        o.put("content", content)
        o.put("sig", sig)
        return o.toString()
    }
}
