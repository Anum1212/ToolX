package com.toolx.app.tools.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.toolx.app.tools.qr.QrCodes.Content
import com.toolx.app.tools.qr.QrCodes.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrCodesTest {

    // ---------- Building (formats from the ZXing "barcode contents" wiki and RFC 6350/6068) ----------

    @Test fun wifiEscapesSpecialCharacters() {
        assertEquals("""WIFI:T:WPA;S:My\;Net;P:pa\:ss\\w\"rd;;""", QrCodes.wifi("My;Net", """pa:ss\w"rd""", WifiSecurity.WPA, false))
        assertEquals("WIFI:T:nopass;S:Cafe;H:true;;", QrCodes.wifi("Cafe", "ignored", WifiSecurity.OPEN, true))
    }

    @Test fun linkAddsSchemeOnlyWhenMissing() {
        assertEquals("https://example.com", QrCodes.link(" example.com "))
        assertEquals("http://example.com", QrCodes.link("http://example.com"))
        assertEquals("mailto:a@b.c", QrCodes.link("mailto:a@b.c"))
    }

    @Test fun phoneEmailSms() {
        assertEquals("tel:+923001234567", QrCodes.phone("+92 300 123-4567"))
        assertEquals("mailto:a@b.com?subject=Hi%20there&body=Line%201", QrCodes.email("a@b.com", "Hi there", "Line 1"))
        assertEquals("mailto:a@b.com", QrCodes.email("a@b.com", "", ""))
        assertEquals("SMSTO:+441234:Hello", QrCodes.sms("+44 1234", "Hello"))
    }

    @Test fun contactIsVCard3() {
        assertEquals(
            "BEGIN:VCARD\nVERSION:3.0\nN:Ali\\, Khan;;;;\nFN:Ali\\, Khan\nORG:Acme\nTEL:+1 555\nEMAIL:a@b.c\nEND:VCARD",
            QrCodes.contact("Ali, Khan", "+1 555", "a@b.c", "Acme"),
        )
    }

    // ---------- Reading ----------

    @Test fun parsesWhatItBuilds() {
        assertEquals(Content.Wifi("My;Net", """pa:ss\w"rd""", "WPA", false), QrCodes.parse(QrCodes.wifi("My;Net", """pa:ss\w"rd""", WifiSecurity.WPA, false)))
        assertEquals(Content.Wifi("Cafe", null, "nopass", true), QrCodes.parse(QrCodes.wifi("Cafe", "", WifiSecurity.OPEN, true)))
        assertEquals(Content.Email("a@b.com", "Hi there", "Line 1"), QrCodes.parse(QrCodes.email("a@b.com", "Hi there", "Line 1")))
        assertEquals(Content.Sms("+441234", "Hello: world"), QrCodes.parse(QrCodes.sms("+44 1234", "Hello: world")))
        assertEquals(Content.Phone("+923001234567"), QrCodes.parse(QrCodes.phone("+92 300 1234567")))
        assertEquals(Content.Contact("Ali, Khan", listOf("+1 555"), listOf("a@b.c"), "Acme"), QrCodes.parse(QrCodes.contact("Ali, Khan", "+1 555", "a@b.c", "Acme")))
    }

    @Test fun parsesCommonForeignFormats() {
        assertEquals(Content.Link("https://www.example.com"), QrCodes.parse("www.example.com"))
        assertEquals(Content.Contact("Sean Owen", listOf("+12125551212"), listOf("srowen@example.com"), ""), QrCodes.parse("MECARD:N:Owen,Sean;TEL:+12125551212;EMAIL:srowen@example.com;;"))
        assertEquals(Content.Email("a@b.c", "Sub", "Body"), QrCodes.parse("MATMSG:TO:a@b.c;SUB:Sub;BODY:Body;;"))
        assertEquals(Content.Geo(40.71872, -73.98905), QrCodes.parse("geo:40.71872,-73.98905,100"))
        // vCard with typed TEL params, a folded line and N but no FN.
        val v = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;Jane;;;\r\nTEL;TYPE=CELL:+1 222\r\nEMAIL;TYPE=WORK:jane@exam\r\n ple.com\r\nEND:VCARD"
        assertEquals(Content.Contact("Jane Doe", listOf("+1 222"), listOf("jane@example.com"), ""), QrCodes.parse(v))
        assertEquals(Content.Text("4006381333931"), QrCodes.parse("4006381333931"))
    }

    // ---------- Encoding ----------

    private fun decode(text: String, ecc: QrCodes.Ecc): String {
        val m = QrCodes.matrix(text, ecc)
        val q = 4; val scale = 4
        val side = (m.width + 2 * q) * scale
        val px = IntArray(side * side) { i ->
            val x = (i % side) / scale - q; val y = (i / side) / scale - q
            if (x in 0 until m.width && y in 0 until m.height && m[x, y]) 0xFF000000.toInt() else -1
        }
        val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, px)))
        return QRCodeReader().decode(bmp, mapOf(DecodeHintType.PURE_BARCODE to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }

    @Test fun encodedCodesDecodeBack() {
        val wifi = QrCodes.wifi("Home ☕", "s3cret;pass", WifiSecurity.WPA, false)
        assertEquals(wifi, decode(wifi, QrCodes.Ecc.M))
        assertEquals("نماز کا وقت", decode("نماز کا وقت", QrCodes.Ecc.H))
        assertEquals("https://example.com/a?b=c", decode("https://example.com/a?b=c", QrCodes.Ecc.L))
    }

    @Test fun versionGrowsWithErrorCorrection() {
        // "HELLO WORLD" fits version 1 (21×21) even at Q; 200 bytes at H needs a much bigger grid.
        assertEquals(21, QrCodes.matrix("HELLO WORLD", QrCodes.Ecc.Q).width)
        val text = "x".repeat(200)
        assert(QrCodes.matrix(text, QrCodes.Ecc.H).width > QrCodes.matrix(text, QrCodes.Ecc.L).width)
    }

    @Test fun tooLongThrows() {
        // Version 40-L holds 2,953 bytes; H holds 1,273.
        assertThrows(WriterException::class.java) { QrCodes.matrix("x".repeat(1300), QrCodes.Ecc.H) }
        assertEquals(177, QrCodes.matrix("x".repeat(2900), QrCodes.Ecc.L).width)
    }
}
