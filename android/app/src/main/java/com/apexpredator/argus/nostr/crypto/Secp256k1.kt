package com.apexpredator.argus.nostr.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure JVM implementation of the secp256k1 operations needed by Nostr chat
 * (NIP-01 events, NIP-04 encryption, NIP-17 gift wrap).
 *
 * Implements BIP-340 x-only Schnorr signing and verification exactly per the
 * spec, plus raw ECDH on x coordinates. Uses only java.math.BigInteger,
 * java.security.SecureRandom and java.security.MessageDigest, so it compiles
 * on plain JVM and on Android.
 *
 * This implementation is not constant time. Do not reuse it for Bitcoin
 * wallets; for Nostr chat keys this matches the threat model (keys live in
 * app-private storage anyway).
 */
object Secp256k1 {

    private val P: BigInteger =
        BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val N: BigInteger =
        BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val GX: BigInteger =
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val GY: BigInteger =
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)

    private data class Point(val x: BigInteger, val y: BigInteger)

    private val G = Point(GX, GY)
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger("2")
    private val SEVEN = BigInteger("7")
    private val SQRT_EXP = P.add(ONE).divide(BigInteger("4"))

    // Infinity is represented as null.

    private fun bytesToInt(b: ByteArray): BigInteger = BigInteger(1, b)

    private fun intToBytes(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
            raw.size < 32 -> ByteArray(32 - raw.size) + raw
            else -> throw IllegalArgumentException("value does not fit in 32 bytes")
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun taggedHash(tag: String, vararg parts: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
        val total = tagHash.size * 2 + parts.sumOf { it.size }
        val buf = ByteArray(total)
        var off = 0
        tagHash.copyInto(buf, off); off += tagHash.size
        tagHash.copyInto(buf, off); off += tagHash.size
        for (part in parts) {
            part.copyInto(buf, off); off += part.size
        }
        return sha256(buf)
    }

    private fun hasEvenY(p: Point): Boolean = !p.y.testBit(0)

    private fun pointAdd(p1: Point?, p2: Point?): Point? {
        if (p1 == null) return p2
        if (p2 == null) return p1
        if (p1.x == p2.x) {
            if (p1.y != p2.y) return null
            return pointDouble(p1)
        }
        val s = p2.y.subtract(p1.y).mod(P)
            .multiply(p2.x.subtract(p1.x).mod(P).modInverse(P)).mod(P)
        val x3 = s.multiply(s).subtract(p1.x).subtract(p2.x).mod(P)
        val y3 = s.multiply(p1.x.subtract(x3)).subtract(p1.y).mod(P)
        return Point(x3, y3)
    }

    private fun pointDouble(p: Point): Point? {
        if (p.y == BigInteger.ZERO) return null
        val s = p.x.multiply(p.x).multiply(BigInteger("3"))
            .multiply(TWO.multiply(p.y).modInverse(P)).mod(P)
        val x3 = s.multiply(s).subtract(p.x.multiply(TWO)).mod(P)
        val y3 = s.multiply(p.x.subtract(x3)).subtract(p.y).mod(P)
        return Point(x3, y3)
    }

    private fun pointNegate(p: Point): Point = Point(p.x, P.subtract(p.y))

    private fun pointMul(point: Point, scalar: BigInteger): Point? {
        var k = scalar.mod(N)
        var result: Point? = null
        var addend: Point? = point
        while (k > BigInteger.ZERO) {
            if (k.testBit(0)) result = pointAdd(result, addend)
            addend = pointDouble(addend!!)
            k = k.shiftRight(1)
        }
        return result
    }

    /** Lift an x-only public key to the full point with even y, or null if invalid. */
    private fun liftX(x: BigInteger): Point? {
        if (x >= P) return null
        val c = x.modPow(BigInteger("3"), P).add(SEVEN).mod(P)
        val y = c.modPow(SQRT_EXP, P)
        if (!y.modPow(TWO, P).equals(c)) return null
        return Point(x, if (hasEvenY(Point(x, y))) y else P.subtract(y))
    }

    /**
     * 32 random bytes forming a valid secret in [1, n-1].
     */
    fun generatePrivateKey(): ByteArray {
        val rng = SecureRandom()
        while (true) {
            val candidate = ByteArray(32)
            rng.nextBytes(candidate)
            val d = bytesToInt(candidate)
            if (d >= ONE && d < N) return candidate
        }
    }

    /**
     * BIP-340 x-only public key: 32-byte big-endian x coordinate of the
     * even-y point. Throws IllegalArgumentException for an invalid secret.
     */
    fun xOnlyPublicKey(secretKey: ByteArray): ByteArray {
        require(secretKey.size == 32) { "secret key must be 32 bytes" }
        val d0 = bytesToInt(secretKey)
        require(d0 >= ONE && d0 < N) { "secret key out of range" }
        val point = pointMul(G, d0)!!
        val d = if (hasEvenY(point)) d0 else N.subtract(d0)
        return intToBytes(pointMul(G, d)!!.x)
    }

    /**
     * BIP-340 signing. Returns the 64-byte signature R.x || s.
     * auxRand should be 32 fresh random bytes per signature.
     */
    fun schnorrSign(secretKey: ByteArray, message: ByteArray, auxRand: ByteArray): ByteArray {
        require(secretKey.size == 32) { "secret key must be 32 bytes" }
        require(auxRand.size == 32) { "aux_rand must be 32 bytes" }
        val d0 = bytesToInt(secretKey)
        require(d0 >= ONE && d0 < N) { "secret key out of range" }

        val pubPoint = pointMul(G, d0)!!
        // BIP-340 uses the even-y normalized secret d (not the raw d0) in the
        // nonce derivation: t = d xor int(tagged_hash("BIP0340/aux", aux_rand)).
        val d = if (hasEvenY(pubPoint)) d0 else N.subtract(d0)
        val px = intToBytes(pubPoint.x)

        val t = d.xor(bytesToInt(taggedHash("BIP0340/aux", auxRand)))
        val rand = taggedHash("BIP0340/nonce", intToBytes(t), px, message)
        var k0 = bytesToInt(rand).mod(N)
        require(k0 != BigInteger.ZERO) { "k0 is zero (won't happen in practice)" }

        val rPoint = pointMul(G, k0)!!
        val k = if (hasEvenY(rPoint)) k0 else N.subtract(k0)

        val e = bytesToInt(taggedHash("BIP0340/challenge", intToBytes(rPoint.x), px, message)).mod(N)
        val sig = ByteArray(64)
        intToBytes(rPoint.x).copyInto(sig, 0)
        intToBytes(k.add(e.multiply(d)).mod(N)).copyInto(sig, 32)
        return sig
    }

    /**
     * BIP-340 verification. Returns true for a valid signature, false for any
     * malformed or invalid input (never throws on attacker input).
     */
    fun schnorrVerify(pubkeyX: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (pubkeyX.size != 32 || signature.size != 64) return false
        val pubPoint = liftX(bytesToInt(pubkeyX)) ?: return false
        val r = bytesToInt(signature.copyOfRange(0, 32))
        if (r >= P) return false
        val s = bytesToInt(signature.copyOfRange(32, 64))
        if (s >= N) return false
        val e = bytesToInt(taggedHash("BIP0340/challenge", intToBytes(r), pubkeyX, message)).mod(N)
        val rPoint = pointAdd(pointMul(G, s), pointMul(pubPoint, e)?.let { pointNegate(it) })
            ?: return false
        if (!hasEvenY(rPoint)) return false
        return rPoint.x == r
    }

    /**
     * Raw ECDH on x coordinates for NIP-04: returns the 32-byte big-endian x
     * of the shared point. Both sides normalize to even-y keys first, so
     * ecdhX(a, pubB) == ecdhX(b, pubA).
     */
    fun ecdhX(secretKey: ByteArray, peerPubkeyX: ByteArray): ByteArray {
        require(secretKey.size == 32) { "secret key must be 32 bytes" }
        require(peerPubkeyX.size == 32) { "peer public key must be 32 bytes" }
        val d0 = bytesToInt(secretKey)
        require(d0 >= ONE && d0 < N) { "secret key out of range" }
        val peerPoint = liftX(bytesToInt(peerPubkeyX))
            ?: throw IllegalArgumentException("peer public key is not a valid x coordinate")
        val ownPoint = pointMul(G, d0)!!
        val d = if (hasEvenY(ownPoint)) d0 else N.subtract(d0)
        val shared = pointMul(peerPoint, d)
            ?: throw IllegalStateException("ECDH shared point is at infinity")
        return intToBytes(shared.x)
    }
}
