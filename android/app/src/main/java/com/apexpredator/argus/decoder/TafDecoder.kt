package com.apexpredator.argus.decoder

// TAF decoder. Parses the header plus change groups
// (FM, TEMPO, BECMG, PROB). Unknown tokens are skipped.

object TafDecoder {

    private val STATION_RE = Regex("^[A-Z0-9]{4}$")
    private val FM_RE = Regex("^FM(\\d{6})$")
    private val PROB_RE = Regex("^PROB(\\d{2})$")
    private val PERIOD_RE = Regex("^\\d{4}/\\d{4}$")

    fun decode(raw: String): DecodedTaf {
        val tokens = normalizeReport(raw)
        var i = 0

        if (i < tokens.size && tokens[i] == "TAF") i++

        var amended = false
        var corrected = false
        if (i < tokens.size && tokens[i] == "AMD") { amended = true; i++ }
        if (i < tokens.size && tokens[i] == "COR") { corrected = true; i++ }

        var station = "????"
        if (i < tokens.size && STATION_RE.matches(tokens[i])) {
            station = tokens[i]
            i++
        }

        var issueTime: String? = null
        if (i < tokens.size) {
            issueTime = describeZuluTime(tokens[i])
            if (issueTime != null) i++
        }

        var valid: String? = null
        var cancelled = false
        if (i < tokens.size && tokens[i] == "CNL") {
            cancelled = true
            i++
        }
        if (i < tokens.size && PERIOD_RE.matches(tokens[i])) {
            valid = describeValidPeriod(tokens[i])
            i++
        }
        // Some feeds place CNL after the valid period; accept that order too.
        if (!cancelled && i < tokens.size && tokens[i] == "CNL") {
            cancelled = true
            i++
        }

        val baseBuilder = ConditionsBuilder()
        val changes = mutableListOf<TafChangeGroup>()

        var currentKind: String? = null
        var currentProb: Int? = null
        var currentTime: String? = null
        var currentBuilder: ConditionsBuilder? = null

        fun flushGroup() {
            val b = currentBuilder ?: return
            changes.add(
                TafChangeGroup(
                    kind = currentKind ?: "TEMPO",
                    probability = currentProb,
                    timeDescription = currentTime ?: "unspecified period",
                    conditions = b.build()
                )
            )
            currentBuilder = null
            currentKind = null
            currentProb = null
            currentTime = null
        }

        while (i < tokens.size) {
            val t = tokens[i]
            when {
                FM_RE.matches(t) -> {
                    flushGroup()
                    val m = FM_RE.matchEntire(t)!!
                    val d = m.groupValues[1]
                    currentKind = "FM"
                    currentTime = "from day ${d.substring(0, 2)} ${d.substring(2, 4)}:${d.substring(4, 6)} UTC"
                    currentBuilder = ConditionsBuilder()
                }
                t == "TEMPO" || t == "BECMG" -> {
                    flushGroup()
                    currentKind = t
                    currentBuilder = ConditionsBuilder()
                    val next = tokens.getOrNull(i + 1)
                    if (next != null && PERIOD_RE.matches(next)) {
                        currentTime = describeValidPeriod(next)
                        i++
                    }
                }
                PROB_RE.matches(t) -> {
                    flushGroup()
                    val m = PROB_RE.matchEntire(t)!!
                    currentKind = "PROB"
                    currentProb = m.groupValues[1].toInt()
                    currentBuilder = ConditionsBuilder()
                    // PROB is usually followed by TEMPO and an optional period.
                    val next = tokens.getOrNull(i + 1)
                    if (next == "TEMPO") {
                        i++
                        val after = tokens.getOrNull(i + 1)
                        if (after != null && PERIOD_RE.matches(after)) {
                            currentTime = describeValidPeriod(after)
                            i++
                        }
                    }
                }
                t == "RMK" || t == "REMARKS" -> break
                else -> {
                    val target = currentBuilder ?: baseBuilder
                    target.tryParseGroup(t)
                }
            }
            i++
        }
        flushGroup()

        return DecodedTaf(
            raw = raw.trim(),
            station = station,
            issueTimeUtc = issueTime,
            validDescription = valid,
            cancelled = cancelled,
            amended = amended,
            corrected = corrected,
            base = baseBuilder.build(),
            changes = changes
        )
    }
}
