package com.toolx.app.tools.general

import com.toolx.app.tools.general.GeneralOps.ByteFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GeneralOpsTest {

    // ---------- Morse ----------

    @Test fun morseKnownValues() {
        assertEquals("... --- ...", GeneralOps.toMorse("SOS"))
        assertEquals(".... .. / - .... . .-. .", GeneralOps.toMorse("Hi there"))
        assertEquals(".---- ..--- ...--", GeneralOps.toMorse("123"))
    }

    @Test fun morseDecode() {
        assertEquals("HI THERE", GeneralOps.fromMorse(".... .. / - .... . .-. ."))
        assertEquals("SOS", GeneralOps.fromMorse("...   ---   ...".replace("   ", " ")))
    }

    @Test fun morseRoundTrip() {
        val text = "HELLO WORLD 2026, OK?"
        assertEquals(text, GeneralOps.fromMorse(GeneralOps.toMorse(text)))
    }

    @Test fun morseDetection() {
        assertTrue(GeneralOps.looksLikeMorse("... --- ..."))
        assertTrue(GeneralOps.looksLikeMorse(".- / -..."))
        assertFalse(GeneralOps.looksLikeMorse("SOS"))
        assertFalse(GeneralOps.looksLikeMorse("   "))
    }

    // ---------- Roman ----------

    @Test fun romanKnownValues() {
        val cases = mapOf(1 to "I", 4 to "IV", 9 to "IX", 14 to "XIV", 40 to "XL", 90 to "XC", 400 to "CD",
            1994 to "MCMXCIV", 2026 to "MMXXVI", 3999 to "MMMCMXCIX", 444 to "CDXLIV", 1666 to "MDCLXVI")
        cases.forEach { (n, r) ->
            assertEquals(r, GeneralOps.toRoman(n))
            assertEquals(n, GeneralOps.fromRoman(r))
        }
    }

    @Test fun romanBoundsAndInvalid() {
        assertNull(GeneralOps.toRoman(0))
        assertNull(GeneralOps.toRoman(4000))
        assertNull(GeneralOps.fromRoman("IIII"))
        assertNull(GeneralOps.fromRoman("IC"))
        assertNull(GeneralOps.fromRoman("VV"))
        assertNull(GeneralOps.fromRoman(""))
        assertEquals(2026, GeneralOps.fromRoman(" mmxxvi "))
    }

    @Test fun romanRoundTripAll() {
        for (n in 1..3999) assertEquals(n, GeneralOps.fromRoman(GeneralOps.toRoman(n)!!))
    }

    // ---------- Number to words ----------

    @Test fun wordsInternational() {
        val cases = mapOf(
            "0" to "Zero", "7" to "Seven", "15" to "Fifteen", "21" to "Twenty-one", "100" to "One hundred",
            "101" to "One hundred one", "999" to "Nine hundred ninety-nine", "1000" to "One thousand", "1001" to "One thousand one",
            "1234567" to "One million two hundred thirty-four thousand five hundred sixty-seven",
            "1000000000" to "One billion", "-42" to "Minus forty-two", "3.14" to "Three point one four",
            "1,000" to "One thousand", "2000000" to "Two million", "10.5" to "Ten point five",
        )
        cases.forEach { (input, expected) -> assertEquals("for $input", expected, GeneralOps.toWords(input, lakhCrore = false)) }
    }

    @Test fun wordsLakhCrore() {
        val cases = mapOf(
            "100000" to "One lakh", "1250000" to "Twelve lakh fifty thousand", "10000000" to "One crore",
            "123456789" to "Twelve crore thirty-four lakh fifty-six thousand seven hundred eighty-nine",
            "1000000000000" to "One lakh crore", "5500" to "Five thousand five hundred", "99999" to "Ninety-nine thousand nine hundred ninety-nine",
        )
        cases.forEach { (input, expected) -> assertEquals("for $input", expected, GeneralOps.toWords(input, lakhCrore = true)) }
    }

    @Test fun wordsInvalid() {
        assertNull(GeneralOps.toWords("abc", false))
        assertNull(GeneralOps.toWords("", false))
    }

    // ---------- Bytes ----------

    @Test fun bytesFormats() {
        val hi = "Hi".toByteArray()
        assertEquals("48 69", GeneralOps.bytesToString(hi, ByteFormat.HEX))
        assertEquals("01001000 01101001", GeneralOps.bytesToString(hi, ByteFormat.BINARY))
        assertEquals("72 105", GeneralOps.bytesToString(hi, ByteFormat.DECIMAL))
        assertEquals("SGk=", GeneralOps.bytesToString(hi, ByteFormat.BASE64))
        assertEquals("E2 9C 93", GeneralOps.bytesToString("✓".toByteArray(Charsets.UTF_8), ByteFormat.HEX))
        assertEquals("255", GeneralOps.bytesToString(byteArrayOf(-1), ByteFormat.DECIMAL))
    }

    @Test fun bytesParse() {
        val hi = "Hi".toByteArray()
        assertArrayEquals(hi, GeneralOps.parseBytes("48 69", ByteFormat.HEX))
        assertArrayEquals(hi, GeneralOps.parseBytes("0x48,0x69", ByteFormat.HEX))
        assertArrayEquals(hi, GeneralOps.parseBytes("4869", ByteFormat.HEX))
        assertArrayEquals(hi, GeneralOps.parseBytes("SGk=", ByteFormat.BASE64))
        assertArrayEquals(hi, GeneralOps.parseBytes("72 105", ByteFormat.DECIMAL))
        assertArrayEquals(hi, GeneralOps.parseBytes("01001000 01101001", ByteFormat.BINARY))
        assertNull(GeneralOps.parseBytes("4", ByteFormat.HEX))
        assertNull(GeneralOps.parseBytes("300", ByteFormat.DECIMAL))
        assertNull(GeneralOps.parseBytes("zz", ByteFormat.HEX))
    }

    // ---------- Day counter ----------

    @Test fun weekdays() {
        // 2026-11-16 is a Monday, 2026-11-21 a Saturday, 2026-01-01 a Thursday.
        assertEquals(5, weekdaysBetween(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23)))
        assertEquals(0, weekdaysBetween(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 23)))
        assertEquals(261, weekdaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
        assertEquals(262, weekdaysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1))) // leap year starting Monday
        assertEquals(0, weekdaysBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1)))
    }
}
