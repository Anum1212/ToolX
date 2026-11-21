package com.toolx.app.tools.general

import java.math.BigDecimal

/** Pure helpers for the general tools. */
object GeneralOps {

    // ---------- Morse ----------

    val MORSE: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
        'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
        'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.", '.' to ".-.-.-", ',' to "--..--",
        '?' to "..--..", '\'' to ".----.", '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-", '&' to ".-...",
        ':' to "---...", ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
        '$' to "...-..-", '@' to ".--.-.",
    )
    private val FROM_MORSE = MORSE.entries.associate { (k, v) -> v to k }

    /** True when the text looks like Morse (only dots, dashes, slashes and spaces). */
    fun looksLikeMorse(s: String) = s.isNotBlank() && s.all { it in ".-/ \n·–_" }

    fun toMorse(s: String): String = s.uppercase().lines().joinToString("\n") { line ->
        line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" / ") { word ->
            word.mapNotNull { MORSE[it] }.joinToString(" ")
        }
    }

    fun fromMorse(s: String): String = s.replace('·', '.').replace('–', '-').replace('_', '-').lines().joinToString("\n") { line ->
        line.trim().split(Regex("\\s*/\\s*|\\s{3,}")).joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { FROM_MORSE[it]?.toString() ?: "?" }
        }
    }

    // ---------- Roman numerals ----------

    private val ROMAN = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    private val VALID_ROMAN = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")

    fun toRoman(n0: Int): String? {
        if (n0 !in 1..3999) return null
        var n = n0
        return buildString { ROMAN.forEach { (v, s) -> while (n >= v) { append(s); n -= v } } }
    }

    fun fromRoman(s0: String): Int? {
        val s = s0.trim().uppercase()
        if (s.isEmpty() || !VALID_ROMAN.matches(s)) return null
        var i = 0; var total = 0
        ROMAN.forEach { (v, sym) -> while (s.startsWith(sym, i)) { total += v; i += sym.length } }
        return total
    }

    // ---------- Number to words ----------

    private val ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun under1000(n: Int): String {
        val parts = mutableListOf<String>()
        if (n >= 100) parts += "${ONES[n / 100]} hundred"
        val r = n % 100
        if (r > 0) parts += if (r < 20) ONES[r] else TENS[r / 10] + (if (r % 10 > 0) "-" + ONES[r % 10] else "")
        return parts.joinToString(" ")
    }

    private fun international(n: Long): String {
        if (n == 0L) return "zero"
        val scales = listOf("", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion")
        val parts = mutableListOf<String>()
        var x = n; var i = 0
        while (x > 0) {
            val chunk = (x % 1000).toInt()
            if (chunk > 0) parts.add(0, under1000(chunk) + if (scales[i].isNotEmpty()) " ${scales[i]}" else "")
            x /= 1000; i++
        }
        return parts.joinToString(" ")
    }

    /** South Asian grouping: thousand, lakh (10⁵), crore (10⁷), then crores of crores. */
    private fun southAsian(n: Long): String {
        if (n == 0L) return "zero"
        if (n >= 10_000_000L) {
            val crores = n / 10_000_000L
            val rest = n % 10_000_000L
            return southAsian(crores) + " crore" + if (rest > 0) " " + southAsian(rest) else ""
        }
        val parts = mutableListOf<String>()
        var x = n
        val lakh = (x / 100_000).toInt(); x %= 100_000
        val thousand = (x / 1000).toInt(); x %= 1000
        if (lakh > 0) parts += under1000(lakh) + " lakh"
        if (thousand > 0) parts += under1000(thousand) + " thousand"
        if (x > 0) parts += under1000(x.toInt())
        return parts.joinToString(" ")
    }

    fun toWords(input: String, lakhCrore: Boolean): String? {
        val bd = input.trim().replace(",", "").toBigDecimalOrNull() ?: return null
        val neg = bd.signum() < 0
        val abs = bd.abs()
        val whole = abs.toBigInteger()
        if (whole.bitLength() > 62) return null
        val words = if (lakhCrore) southAsian(whole.toLong()) else international(whole.toLong())
        val fraction = abs.subtract(BigDecimal(whole)).stripTrailingZeros()
        val decimals = if (fraction.signum() == 0) "" else " point " + fraction.toPlainString().substringAfter('.').map { ONES[it - '0'] }.joinToString(" ")
        return ((if (neg) "minus " else "") + words + decimals).replaceFirstChar { it.uppercase() }
    }

    // ---------- Bytes ----------

    enum class ByteFormat(val label: String) { HEX("Hex"), BINARY("Binary"), DECIMAL("Decimal"), BASE64("Base64") }

    fun bytesToString(bytes: ByteArray, format: ByteFormat): String = when (format) {
        ByteFormat.HEX -> bytes.joinToString(" ") { "%02X".format(it) }
        ByteFormat.BINARY -> bytes.joinToString(" ") { Integer.toBinaryString(it.toInt() and 0xFF).padStart(8, '0') }
        ByteFormat.DECIMAL -> bytes.joinToString(" ") { (it.toInt() and 0xFF).toString() }
        ByteFormat.BASE64 -> java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun parseBytes(s: String, format: ByteFormat): ByteArray? = runCatching {
        when (format) {
            ByteFormat.BASE64 -> java.util.Base64.getDecoder().decode(s.trim())
            ByteFormat.HEX -> {
                val clean = s.replace(Regex("0x|[\\s,]"), "")
                require(clean.length % 2 == 0)
                ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }
            ByteFormat.BINARY -> s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toInt(2).also { v -> require(v in 0..255) }.toByte() }.toByteArray()
            ByteFormat.DECIMAL -> s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toInt().also { v -> require(v in 0..255) }.toByte() }.toByteArray()
        }
    }.getOrNull()
}
