package com.apexpredator.argus.nostr

import com.apexpredator.argus.nostr.crypto.Secp256k1
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// NIP-04 encrypted direct messages. The shared key is the ECDH shared
// secret (secp256k1, x-only); the plaintext is encrypted with
// AES-256/CBC/PKCS5Padding and a random 16-byte IV. The payload format is
// the NIP-04 standard: base64(ciphertext)?iv=base64(iv).
object Nip04 {

    private val random = SecureRandom()

    private fun sharedKey(secretKey: ByteArray, peerPubkeyHex: String): SecretKeySpec {
        val peerX = Hex.decode(peerPubkeyHex)
        val shared = Secp256k1.ecdhX(secretKey, peerX)
        return SecretKeySpec(shared, "AES")
    }

    fun encrypt(secretKey: ByteArray, peerPubkeyHex: String, plaintext: String): String {
        val iv = ByteArray(16).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey(secretKey, peerPubkeyHex), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(ciphertext) + "?iv=" + encoder.encodeToString(iv)
    }

    fun decrypt(secretKey: ByteArray, peerPubkeyHex: String, payload: String): String {
        val parts = payload.split("?iv=")
        require(parts.size == 2) { "Malformed NIP-04 payload" }
        val decoder = Base64.getDecoder()
        val ciphertext = decoder.decode(parts[0])
        val iv = decoder.decode(parts[1])
        require(iv.size == 16) { "Bad NIP-04 IV length" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, sharedKey(secretKey, peerPubkeyHex), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}
