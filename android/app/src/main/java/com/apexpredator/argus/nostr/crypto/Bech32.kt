package com.apexpredator.argus.nostr.crypto

/**
 * Bech32 (BIP-173) encoder/decoder used for Nostr npub/nsec key formats.
 * Pure Kotlin, no dependencies.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val CHAR_TO_VALUE: IntArray = IntArray(128) { -1 }.also {
        for (i in CHARSET.indices) it[CHARSET[i].code] = i
    }
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun hrpExpand(hrp: String): IntArray {
        val out = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) out[i] = hrp[i].code shr 5
        out[hrp.length] = 0
        for (i in hrp.indices) out[hrp.length + 1 + i] = hrp[i].code and 31
        return out
    }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val b = chk shr 25
            chk = (chk and 0x1ffffff shl 5) xor v
            for (i in 0 until 5) {
                if ((b shr i) and 1 == 1) chk = chk xor GENERATOR[i]
            }
        }
        return chk
    }

    private fun createChecksum(hrp: String, data: IntArray): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6)
        val pm = polymod(values) xor 1
        return IntArray(6) { i -> (pm shr (5 * (5 - i))) and 31 }
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>(data.size * 2)
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            acc = (acc shl fromBits) or (b.toInt() and 0xFF)
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.add((acc shr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (toBits - bits)) and maxv)
        } else {
            if (bits >= fromBits) throw IllegalArgumentException("invalid padding")
            if (((acc shl (toBits - bits)) and maxv) != 0) throw IllegalArgumentException("invalid padding")
        }
        return out.map { it.toByte() }.toByteArray()
    }

    /**
     * Encode 8-bit bytes as bech32 with the given human-readable part.
     * Data is converted 8 -> 5 bits with padding, per BIP-173.
     */
    fun encode(hrp: String, data: ByteArray): String {
        require(hrp.isNotEmpty() && hrp.all { it.code in 33..126 }) {
            "invalid human-readable part"
        }
        val data5 = convertBits(data, 8, 5, true).map { it.toInt() and 0xFF }.toIntArray()
        val combined = data5 + createChecksum(hrp.lowercase(), data5)
        val sb = StringBuilder(hrp.lowercase()).append('1')
        for (v in combined) sb.append(CHARSET[v])
        return sb.toString()
    }

    /**
     * Decode a bech32 string, verifying the hrp and checksum, and return the
     * original 8-bit bytes (5 -> 8 bit conversion without padding).
     */
    fun decode(hrp: String, bech: String): ByteArray {
        require(bech.length >= 8) { "bech32 string too short" }
        require(bech.length <= 90) { "bech32 string too long" }
        val lower = bech.lowercase()
        require(bech == bech.lowercase() || bech == bech.uppercase()) {
            "mixed case bech32 string"
        }
        val sep = lower.lastIndexOf('1')
        require(sep >= 1) { "missing separator" }
        val foundHrp = lower.substring(0, sep)
        require(foundHrp == hrp.lowercase()) { "wrong human-readable part" }
        val dataPart = lower.substring(sep + 1)
        require(dataPart.length >= 6) { "data part too short" }
        val values = IntArray(dataPart.length) { i ->
            val c = dataPart[i]
            val v = if (c.code < 128) CHAR_TO_VALUE[c.code] else -1
            require(v >= 0) { "invalid bech32 character" }
            v
        }
        require(polymod(hrpExpand(foundHrp) + values) == 1) { "checksum failure" }
        val payload = values.copyOfRange(0, values.size - 6)
        return convertBits(payload.map { it.toByte() }.toByteArray(), 5, 8, false)
    }
}
