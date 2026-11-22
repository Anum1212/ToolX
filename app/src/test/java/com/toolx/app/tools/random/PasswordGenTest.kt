package com.toolx.app.tools.random

import com.toolx.app.tools.random.PasswordGen.CharOptions
import com.toolx.app.tools.random.PasswordGen.Strength
import com.toolx.app.tools.random.PasswordGen.WordOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PasswordGenTest {
    private val rng = SecureRandom()

    @Test fun lengthAndEveryChosenClassPresent() {
        repeat(200) {
            val p = PasswordGen.characters(CharOptions(length = 8), rng)
            assertEquals(8, p.length)
            assertTrue(p, p.any { it in PasswordGen.LOWER })
            assertTrue(p, p.any { it in PasswordGen.UPPER })
            assertTrue(p, p.any { it in PasswordGen.DIGITS })
            assertTrue(p, p.any { it in PasswordGen.SYMBOLS })
        }
    }

    @Test fun onlyChosenClassesAndNoLookAlikes() {
        repeat(200) {
            val p = PasswordGen.characters(CharOptions(length = 30, symbols = false, digits = true, avoidLookAlikes = true), rng)
            assertTrue(p, p.all { it.isLetterOrDigit() })
            assertTrue(p, p.none { it in PasswordGen.LOOK_ALIKES })
        }
        assertEquals("", PasswordGen.characters(CharOptions(lower = false, upper = false, digits = false, symbols = false), rng))
    }

    @Test fun easyPasswordsFollowThePattern() {
        val p = PasswordGen.easy(WordOptions(words = 4, separator = "-"), rng)
        // Four capitalised consonant-vowel words of three syllables, then two digits.
        assertTrue(p, Regex("^([A-Z][aeiou]([bdfghjklmnprstvwz][aeiou]){2}-){4}\\d{2}$").matches(p))
    }

    @Test fun pinIsDigitsOnly() {
        val p = PasswordGen.pin(6, rng)
        assertEquals(6, p.length); assertTrue(p.all { it.isDigit() })
    }

    @Test fun entropyMatchesTheCounting() {
        // 16 chars from 26+26+10+17 = 79 symbols: 16·log2(79) ≈ 100.9 bits.
        assertEquals(100.86, PasswordGen.bits(CharOptions()), 0.01)
        // 4 words × 3 syllables from 17·5 = 85 options, plus 2 digits: 12·log2(85) + 2·log2(10) ≈ 83.6.
        assertEquals(83.56, PasswordGen.bits(WordOptions()), 0.01)
        assertEquals(19.93, PasswordGen.pinBits(6), 0.01)
        assertEquals(Strength.WEAK, PasswordGen.strength(19.9))
        assertEquals(Strength.VERY_STRONG, PasswordGen.strength(100.0))
    }

    @Test fun notTheSameTwice() {
        val seen = HashSet<String>()
        repeat(1000) { assertTrue(seen.add(PasswordGen.characters(CharOptions(length = 12), rng))) }
        assertFalse(seen.isEmpty())
    }
}
