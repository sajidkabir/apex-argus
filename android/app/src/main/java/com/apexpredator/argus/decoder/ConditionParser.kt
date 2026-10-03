package com.apexpredator.argus.decoder

// Shared parsing of the condition groups that appear in both METAR and TAF
// bodies: wind, visibility, present weather, sky, temperature/dewpoint,
// altimeter. Pure Kotlin, no Android dependencies.

internal data class ConditionsBuilder(
    var wind: Wind? = null,
    var visibility: Visibility? = null,
    val weather: MutableList<PresentWeather> = mutableListOf(),
    val sky: MutableList<SkyLayer> = mutableListOf(),
    var temperatureC: Int? = null,
    var dewpointC: Int? = null,
    var altimeterInHg: Double? = null,
    var qnhHpa: Int? = null
) {
    fun build() = Conditions(
        wind = wind,
        visibility = visibility,
        weather = weather.toList(),
        sky = sky.toList(),
        temperatureC = temperatureC,
        dewpointC = dewpointC,
        altimeterInHg = altimeterInHg,
        qnhHpa = qnhHpa
    )
}

private val WIND_RE = Regex("^(VRB|\\d{3})(\\d{2,3})(G(\\d{2,3}))?(KT|MPS|KMH)$")
private val VIS_SM_RE = Regex("^(M?)(P?)(\\d+(?:_\\d+/\\d+)?|\\d+/\\d+)SM$")
private val VIS_M_RE = Regex("^(\\d{4})$")
private val TEMP_RE = Regex("^(M?\\d{1,2})/(M?\\d{1,2})$")
private val ALT_A_RE = Regex("^A(\\d{4})$")
private val ALT_Q_RE = Regex("^Q(\\d{4})$")
private val SKY_RE = Regex("^(FEW|SCT|BKN|OVC)(\\d{3})(CB|TCU)?$")
private val VV_RE = Regex("^VV(\\d{3}|///)$")
private val CLEAR_RE = Regex("^(CLR|SKC|NSC|NCD)$")

// Present weather: optional intensity, optional descriptor, phenomenon code.
private val WX_RE = Regex("^(-|\\+|VC)?(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PO|SQ|FC|SS|DS)?$")

private val PHENOMENA = mapOf(
    "DZ" to "drizzle", "RA" to "rain", "SN" to "snow", "SG" to "snow grains",
    "IC" to "ice crystals", "PL" to "ice pellets", "GR" to "hail",
    "GS" to "small hail", "UP" to "unknown precipitation",
    "BR" to "mist", "FG" to "fog", "FU" to "smoke", "VA" to "volcanic ash",
    "DU" to "dust", "SA" to "sand", "HZ" to "haze", "PO" to "dust whirls",
    "SQ" to "squalls", "FC" to "funnel cloud", "SS" to "sandstorm",
    "DS" to "duststorm"
)

private fun parseTempToken(t: String): Int? {
    val neg = t.startsWith("M")
    val digits = t.removePrefix("M").toIntOrNull() ?: return null
    return if (neg) -digits else digits
}

// Tries to parse one token as a condition group. Returns true when consumed.
internal fun ConditionsBuilder.tryParseGroup(token: String): Boolean {
    // Wind, e.g. 27012G22KT, VRB03KT, 00000KT
    WIND_RE.matchEntire(token)?.let { m ->
        val dirToken = m.groupValues[1]
        val speed = m.groupValues[2].toInt()
        val gust = m.groupValues[4].toIntOrNull()
        val unit = when (m.groupValues[5]) {
            "MPS" -> SpeedUnit.MPS
            "KMH" -> SpeedUnit.KMH
            else -> SpeedUnit.KT
        }
        wind = if (dirToken == "VRB") {
            Wind(directionDeg = null, variable = true, speed = speed, gust = gust, unit = unit)
        } else {
            Wind(directionDeg = dirToken.toInt(), variable = false, speed = speed, gust = gust, unit = unit)
        }
        return true
    }

    // Visibility in statute miles, e.g. 10SM, 1/2SM, M1/4SM, P6SM.
    // Fractions split across two tokens ("1 1/2SM") are merged beforehand
    // into "1_1/2SM". P prefix means "greater than"; we keep the value.
    VIS_SM_RE.matchEntire(token)?.let { m ->
        val lessThan = m.groupValues[1] == "M"
        val value = parseSmValue(m.groupValues[3]) ?: return@let
        val meters = value * 1609.344
        visibility = Visibility(meters = meters, raw = (if (lessThan) "less than " else "") + token)
        return true
    }

    // Visibility in meters, e.g. 9999, 5000.
    VIS_M_RE.matchEntire(token)?.let { m ->
        val meters = m.groupValues[1].toInt()
        visibility = Visibility(meters = meters.toDouble(), raw = token)
        return true
    }

    // CAVOK: ceiling and visibility OK.
    if (token == "CAVOK") {
        visibility = Visibility(meters = 10000.0, raw = token, cavok = true)
        return true
    }

    // Present weather, e.g. -RA, +TSRA, BR, VCTS, NSW.
    if (token == "NSW") {
        weather.add(PresentWeather(raw = token, description = "no significant weather"))
        return true
    }
    WX_RE.matchEntire(token)?.let { m ->
        val intensity = m.groupValues[1]
        val descriptor = m.groupValues[2]
        val phenom = m.groupValues[3]
        if (descriptor.isEmpty() && phenom.isEmpty()) return@let
        val desc = describeWeather(intensity, descriptor, phenom) ?: return@let
        weather.add(PresentWeather(raw = token, description = desc))
        return true
    }

    // Sky layers, e.g. FEW030, BKN045CB, OVC060.
    SKY_RE.matchEntire(token)?.let { m ->
        sky.add(
            SkyLayer(
                coverage = m.groupValues[1],
                heightFeet = m.groupValues[2].toInt() * 100,
                cloudType = m.groupValues[3].ifEmpty { null }
            )
        )
        return true
    }
    VV_RE.matchEntire(token)?.let { m ->
        val h = m.groupValues[1]
        sky.add(
            SkyLayer(
                coverage = "VV",
                heightFeet = if (h == "///") null else h.toInt() * 100,
                cloudType = null
            )
        )
        return true
    }
    CLEAR_RE.matchEntire(token)?.let { m ->
        sky.add(SkyLayer(coverage = m.groupValues[1], heightFeet = null, cloudType = null))
        return true
    }

    // Temperature / dewpoint, e.g. 18/16, M02/M05.
    TEMP_RE.matchEntire(token)?.let { m ->
        temperatureC = parseTempToken(m.groupValues[1])
        dewpointC = parseTempToken(m.groupValues[2])
        return true
    }

    // Altimeter: A2992 (inHg x 100) or Q1008 (hPa).
    ALT_A_RE.matchEntire(token)?.let { m ->
        altimeterInHg = m.groupValues[1].toInt() / 100.0
        return true
    }
    ALT_Q_RE.matchEntire(token)?.let { m ->
        qnhHpa = m.groupValues[1].toInt()
        return true
    }

    return false
}

private fun parseSmValue(token: String): Double? {
    // token like "10", "1/2", "1_1/2"
    val parts = token.split("_")
    var total = 0.0
    for (p in parts) {
        if (p.contains("/")) {
            val f = p.split("/")
            if (f.size != 2) return null
            val n = f[0].toDoubleOrNull() ?: return null
            val d = f[1].toDoubleOrNull() ?: return null
            if (d == 0.0) return null
            total += n / d
        } else {
            total += p.toDoubleOrNull() ?: return null
        }
    }
    return total
}

private fun describeWeather(intensity: String, descriptor: String, phenom: String): String? {
    val phenomText = PHENOMENA[phenom] ?: return null
    val intensityText = when (intensity) {
        "-" -> "light "
        "+" -> "heavy "
        else -> ""
    }
    if (intensity == "VC") {
        val core = when (descriptor) {
            "TS" -> "thunderstorm"
            "SH" -> "$phenomText showers"
            "FZ" -> "freezing $phenomText"
            "BL" -> "blowing $phenomText"
            "MI" -> "shallow $phenomText"
            "BC" -> "patches of $phenomText"
            "DR" -> "low drifting $phenomText"
            "" -> phenomText
            else -> phenomText
        }
        return "$core in the vicinity"
    }
    return when (descriptor) {
        "TS" -> "thunderstorm with $intensityText$phenomText"
        "SH" -> "$intensityText$phenomText showers"
        "FZ" -> "$intensityText" + "freezing $phenomText"
        "BL" -> "$intensityText" + "blowing $phenomText"
        "MI" -> "$intensityText" + "shallow $phenomText"
        "BC" -> "$intensityText" + "patches of $phenomText"
        "DR" -> "$intensityText" + "low drifting $phenomText"
        "" -> "$intensityText$phenomText"
        else -> "$intensityText$phenomText"
    }
}

// Merges visibility fractions split across tokens: "1" + "1/2SM" -> "1_1/2SM".
internal fun mergeFractionTokens(tokens: List<String>): List<String> {
    val out = ArrayList<String>(tokens.size)
    var i = 0
    while (i < tokens.size) {
        val cur = tokens[i]
        val next = tokens.getOrNull(i + 1)
        if (cur.matches(Regex("^\\d+$")) && next != null && next.matches(Regex("^\\d+/\\d+SM$"))) {
            out.add(cur + "_" + next)
            i += 2
        } else {
            out.add(cur)
            i += 1
        }
    }
    return out
}

internal fun normalizeReport(raw: String): List<String> {
    val cleaned = raw.trim().replace("\\s+".toRegex(), " ").uppercase()
    require(cleaned.isNotEmpty()) { "Empty report" }
    return mergeFractionTokens(cleaned.split(" "))
}

// "021251Z" -> "day 02 at 12:51 UTC"
internal fun describeZuluTime(token: String): String? {
    val m = Regex("^(\\d{2})(\\d{2})(\\d{2})Z$").matchEntire(token) ?: return null
    return "day ${m.groupValues[1]} at ${m.groupValues[2]}:${m.groupValues[3]} UTC"
}

// "0212/0318" -> "day 02 12:00 UTC to day 03 18:00 UTC"
internal fun describeValidPeriod(token: String): String? {
    val m = Regex("^(\\d{2})(\\d{2})/(\\d{2})(\\d{2})$").matchEntire(token) ?: return null
    return "day ${m.groupValues[1]} ${m.groupValues[2]}:00 UTC to day ${m.groupValues[3]} ${m.groupValues[4]}:00 UTC"
}
