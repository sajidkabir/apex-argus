package com.apexpredator.argus.nostr

// The public Argus sightings channel (v2.5). Convention, documented in
// the README: plain kind-1 text notes, tagged with the shared hashtag
// #argus-sighting, plus machine-readable aircraft and location tags so
// the feed renders structured rows. No image hosting is built: a photo
// rides along only as a link the poster supplies, and Argus never
// uploads anything. Posts are public to every relay reader, and the
// app says so on the screen before anyone posts.
const val SIGHTING_HASHTAG = "argus-sighting"
const val SIGHTING_KIND = 1

data class PublicSighting(
    val id: String,
    val pubkeyHex: String,
    val authorName: String,
    val aircraft: String,
    val location: String,
    val note: String,
    val photoUrl: String?,
    val createdAt: Long
)

// Returns an error message, or null when the form is publishable.
// A photo link is optional, but when given it must be a plain http(s)
// link the poster already hosts somewhere public.
fun validateSightingInput(
    aircraft: String,
    location: String,
    photoUrl: String
): String? {
    if (aircraft.isBlank()) return "Enter a callsign or aircraft first."
    if (location.isBlank()) return "Enter an airport or location first."
    val photo = photoUrl.trim()
    if (photo.isNotEmpty() &&
        !photo.startsWith("http://") && !photo.startsWith("https://")
    ) {
        return "A photo link must start with http:// or https://."
    }
    return null
}

fun buildSightingTags(aircraft: String, location: String): List<List<String>> =
    listOf(
        listOf("t", SIGHTING_HASHTAG),
        listOf("aircraft", aircraft.trim()),
        listOf("location", location.trim())
    )

// Self-contained plain text: other Nostr clients that do not know the
// Argus tags still read a sensible note. The first line is the
// headline, the optional note follows, and a photo link, when given,
// is the last line on its own.
fun buildSightingContent(
    aircraft: String,
    location: String,
    note: String,
    photoUrl: String
): String {
    val sb = StringBuilder()
    sb.append("Spotted ${aircraft.trim()} at ${location.trim()}.")
    val cleanNote = note.trim()
    if (cleanNote.isNotEmpty()) {
        sb.append("\n\n")
        sb.append(cleanNote)
    }
    val photo = photoUrl.trim()
    if (photo.isNotEmpty()) {
        sb.append("\n\n")
        sb.append(photo)
    }
    return sb.toString()
}

private fun tagValue(event: NostrEvent, key: String): String? =
    event.tags.firstOrNull { it.size >= 2 && it[0] == key }?.get(1)

// Parses a channel event back into a row. Tag values win; the content
// headline is the fallback for posts made before tags, or by other
// clients that only carry the hashtag.
fun parseSighting(event: NostrEvent, authorName: String): PublicSighting? {
    if (event.kind != SIGHTING_KIND) return null
    if (event.tags.none { it.size >= 2 && it[0] == "t" && it[1] == SIGHTING_HASHTAG }) {
        return null
    }
    val lines = event.content.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val photoUrl = lines.lastOrNull()
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val aircraft = tagValue(event, "aircraft")
        ?: lines.firstOrNull()
            ?.removePrefix("Spotted ")
            ?.substringBefore(" at ")
            ?.trim()
            ?.ifEmpty { null }
        ?: return null
    val location = tagValue(event, "location")
        ?: lines.firstOrNull()
            ?.substringAfter(" at ", "")
            ?.removeSuffix(".")
            ?.trim()
            ?.ifEmpty { null }
        ?: return null
    val noteLines = lines.filterIndexed { index, line ->
        index != 0 && line != photoUrl
    }
    return PublicSighting(
        id = event.id,
        pubkeyHex = event.pubkey,
        authorName = authorName,
        aircraft = aircraft,
        location = location,
        note = noteLines.joinToString("\n"),
        photoUrl = photoUrl,
        createdAt = event.createdAt
    )
}
