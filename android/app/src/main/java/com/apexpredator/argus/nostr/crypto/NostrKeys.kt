package com.apexpredator.argus.nostr.crypto

/**
 * Nostr key formats: npub/nsec bech32 encodings of the 32-byte x-only public
 * key and the 32-byte secret key.
 */
object NostrKeys {

    /** Encode a 32-byte x-only public key as npub. */
    fun npub(pubkeyX: ByteArray): String {
        require(pubkeyX.size == 32) { "public key must be 32 bytes" }
        return Bech32.encode("npub", pubkeyX)
    }

    /** Decode an npub back to the 32-byte x-only public key. */
    fun pubkeyFromNpub(npub: String): ByteArray {
        val raw = Bech32.decode("npub", npub)
        require(raw.size == 32) { "npub payload must be 32 bytes" }
        return raw
    }

    /** Encode a 32-byte secret key as nsec. */
    fun nsec(secretKey: ByteArray): String {
        require(secretKey.size == 32) { "secret key must be 32 bytes" }
        return Bech32.encode("nsec", secretKey)
    }

    /** Decode an nsec back to the 32-byte secret key. */
    fun secretFromNsec(nsec: String): ByteArray {
        val raw = Bech32.decode("nsec", nsec)
        require(raw.size == 32) { "nsec payload must be 32 bytes" }
        return raw
    }
}
