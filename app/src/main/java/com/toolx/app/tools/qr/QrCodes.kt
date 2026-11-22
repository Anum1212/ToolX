package com.toolx.app.tools.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLDecoder
import java.net.URLEncoder

/** Pure QR helpers: building payloads, reading scanned ones, and encoding. No Android. */
object QrCodes {

    // ---------- Building ----------

    /** Wi-Fi QR fields escape \ ; , : and " with a backslash. */
    private fun esc(s: String) = s.replace(Regex("""([\\;,:"])"""), """\\$1""")

    enum class WifiSecurity(val label: String, val code: String) { WPA("WPA/WPA2/WPA3", "WPA"), WEP("WEP", "WEP"), OPEN("None", "nopass") }

    fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security.code).append(";S:").append(esc(ssid)).append(';')
        if (security != WifiSecurity.OPEN) append("P:").append(esc(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

    /** Adds https:// when there's no scheme, so phones open it as a link. */
    fun link(url: String): String {
        val u = url.trim()
        return if (u.isEmpty() || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(u)) u else "https://$u"
    }

    fun phone(number: String) = "tel:" + number.filter { it.isDigit() || it == '+' }

    private fun pct(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun email(to: String, subject: String, body: String): String {
        val q = listOfNotNull(subject.takeIf { it.isNotBlank() }?.let { "subject=" + pct(it) }, body.takeIf { it.isNotBlank() }?.let { "body=" + pct(it) })
        return "mailto:" + to.trim() + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }

    fun sms(number: String, message: String) = "SMSTO:" + number.filter { it.isDigit() || it == '+' } + ":" + message

    /** vCard 3.0 values escape \ , ; and newlines. */
    private fun vesc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    fun contact(name: String, phone: String, email: String, org: String): String = buildString {
        append("BEGIN:VCARD\nVERSION:3.0\n")
        append("N:").append(vesc(name.trim())).append(";;;;\n")
        append("FN:").append(vesc(name.trim())).append('\n')
        if (org.isNotBlank()) append("ORG:").append(vesc(org.trim())).append('\n')
        if (phone.isNotBlank()) append("TEL:").append(phone.trim()).append('\n')
        if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append('\n')
        append("END:VCARD")
    }

    // ---------- Encoding ----------

    enum class Ecc(val label: String, val level: ErrorCorrectionLevel, val note: String) {
        L("Low", ErrorCorrectionLevel.L, "7% can be damaged; smallest code"),
        M("Medium", ErrorCorrectionLevel.M, "15% can be damaged"),
        Q("High", ErrorCorrectionLevel.Q, "25% can be damaged"),
        H("Highest", ErrorCorrectionLevel.H, "30% can be damaged; biggest code"),
    }

    /** The bare module grid (no quiet zone). Throws WriterException when the text is too long. */
    fun matrix(text: String, ecc: Ecc): BitMatrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ecc.level, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0),
    )

    // ---------- Reading ----------

    sealed interface Content {
        data class Link(val url: String) : Content
        data class Wifi(val ssid: String, val password: String?, val security: String, val hidden: Boolean) : Content
        data class Phone(val number: String) : Content
        data class Email(val to: String, val subject: String, val body: String) : Content
        data class Sms(val number: String, val message: String) : Content
        data class Contact(val name: String, val phones: List<String>, val emails: List<String>, val org: String) : Content
        data class Geo(val lat: Double, val lon: Double) : Content
        data class Text(val text: String) : Content
    }

    /** Splits "K:v;K:v;;" honouring backslash escapes. */
    private fun fields(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val cur = StringBuilder()
        var i = 0
        fun flush() {
            val s = cur.toString(); cur.clear()
            val k = s.indexOf(':')
            // The marker is found on the raw text, so an escaped \: in a value is never a separator.
            if (k > 0) out.putIfAbsent(s.substring(0, k).uppercase(), unescape(s.substring(k + 1)))
        }
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) { cur.append(c).append(body[i + 1]); i += 2; continue }
            if (c == ';') flush() else cur.append(c)
            i++
        }
        flush()
        return out
    }

    private fun unescape(s: String) = Regex("""\\(.)""").replace(s) { it.groupValues[1] }

    private fun vunescape(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace(Regex("""\\([\\,;])"""), "$1")

    private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    fun parse(raw: String): Content {
        val s = raw.trim()
        val upper = s.uppercase()
        return when {
            upper.startsWith("WIFI:") -> {
                val f = fields(s.substring(5))
                val type = f["T"].orEmpty()
                Content.Wifi(f["S"].orEmpty(), f["P"]?.takeIf { type.lowercase() != "nopass" && it.isNotEmpty() }, type.ifEmpty { "nopass" }, f["H"].equals("true", true))
            }
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> Content.Link(s)
            upper.startsWith("WWW.") && ' ' !in s -> Content.Link("https://$s")
            upper.startsWith("TEL:") -> Content.Phone(s.substring(4))
            upper.startsWith("MAILTO:") -> {
                val rest = s.substring(7)
                val to = rest.substringBefore('?')
                val q = rest.substringAfter('?', "").split('&').filter { '=' in it }
                    .associate { it.substringBefore('=').lowercase() to decode(it.substringAfter('=')) }
                Content.Email(decode(to), q["subject"].orEmpty(), q["body"].orEmpty())
            }
            upper.startsWith("MATMSG:") -> {
                val f = fields(s.substring(7))
                Content.Email(f["TO"].orEmpty(), f["SUB"].orEmpty(), f["BODY"].orEmpty())
            }
            upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> {
                val rest = s.substringAfter(':')
                Content.Sms(rest.substringBefore(':').substringBefore('?'), rest.substringAfter(':', "").ifEmpty { decode(rest.substringAfter("body=", "")) })
            }
            upper.startsWith("BEGIN:VCARD") -> {
                // Unfold continuation lines, then read NAME;PARAMS:VALUE.
                val lines = s.replace(Regex("\r?\n[ \t]"), "").lines()
                fun values(key: String) = lines.filter { it.substringBefore(':').substringBefore(';').equals(key, true) && ':' in it }
                    .map { vunescape(it.substringAfter(':').trim()) }
                val fn = values("FN").firstOrNull()
                val n = values("N").firstOrNull()?.split(';')?.let { p -> listOf(p.getOrNull(1), p.getOrNull(2), p.getOrNull(0)).filter { !it.isNullOrBlank() }.joinToString(" ") }
                Content.Contact(fn ?: n.orEmpty(), values("TEL"), values("EMAIL"), values("ORG").firstOrNull()?.replace(";", " ")?.trim().orEmpty())
            }
            upper.startsWith("MECARD:") -> {
                val f = fields(s.substring(7))
                val n = f["N"].orEmpty().split(',').reversed().joinToString(" ").trim()
                Content.Contact(n, listOfNotNull(f["TEL"]), listOfNotNull(f["EMAIL"]), f["ORG"].orEmpty())
            }
            upper.startsWith("GEO:") -> {
                val p = s.substring(4).substringBefore('?').substringBefore(';').split(',')
                val lat = p.getOrNull(0)?.toDoubleOrNull(); val lon = p.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) Content.Geo(lat, lon) else Content.Text(s)
            }
            else -> Content.Text(raw)
        }
    }
}
