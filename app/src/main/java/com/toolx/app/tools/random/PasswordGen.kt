package com.toolx.app.tools.random

import java.security.SecureRandom
import kotlin.math.ln

/** Password, passphrase and PIN generation with SecureRandom. Pure, so it's unit-tested. */
object PasswordGen {

    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!@#$%^&*-_=+?.:;~"
    /** Characters people misread: I l 1 | O 0 o. */
    const val LOOK_ALIKES = "Il1O0o"

    data class CharOptions(
        val length: Int = 16,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidLookAlikes: Boolean = false,
    )

    fun classes(o: CharOptions): List<String> = listOfNotNull(
        LOWER.takeIf { o.lower }, UPPER.takeIf { o.upper }, DIGITS.takeIf { o.digits }, SYMBOLS.takeIf { o.symbols },
    ).map { set -> if (o.avoidLookAlikes) set.filterNot { it in LOOK_ALIKES } else set }

    /**
     * Random characters with at least one from each chosen class (when the length allows),
     * placed at random positions. Returns "" when no class is chosen.
     */
    fun characters(o: CharOptions, rng: SecureRandom = SecureRandom()): String {
        val sets = classes(o)
        if (sets.isEmpty() || o.length <= 0) return ""
        val pool = sets.joinToString("")
        val out = CharArray(o.length) { pool[rng.nextInt(pool.length)] }
        if (o.length >= sets.size) {
            // Distinct random slots, one per class.
            val slots = (0 until o.length).shuffled(java.util.Random(rng.nextLong())).take(sets.size)
            sets.forEachIndexed { i, set -> out[slots[i]] = set[rng.nextInt(set.length)] }
        }
        return String(out)
    }

    // ---------- Easy to type ----------

    /** No c/q/x (ambiguous sounds) and no y; every syllable is consonant + vowel. */
    const val CONSONANTS = "bdfghjklmnprstvwz"
    const val VOWELS = "aeiou"

    data class WordOptions(
        val words: Int = 4,
        val syllablesPerWord: Int = 3,
        val separator: String = "-",
        val capitalize: Boolean = true,
        val digits: Int = 2,
    )

    /** E.g. "Kavomi-Tepura-Lodesu-Binaho-47": readable aloud and easy to type on a phone. */
    fun easy(o: WordOptions, rng: SecureRandom = SecureRandom()): String {
        val words = List(o.words.coerceAtLeast(1)) {
            val w = buildString {
                repeat(o.syllablesPerWord.coerceAtLeast(1)) {
                    append(CONSONANTS[rng.nextInt(CONSONANTS.length)]).append(VOWELS[rng.nextInt(VOWELS.length)])
                }
            }
            if (o.capitalize) w.replaceFirstChar { it.uppercase() } else w
        }
        val number = if (o.digits > 0) List(o.digits) { DIGITS[rng.nextInt(10)] }.joinToString("") else null
        return (words + listOfNotNull(number)).joinToString(o.separator)
    }

    fun pin(length: Int, rng: SecureRandom = SecureRandom()): String = List(length.coerceAtLeast(1)) { DIGITS[rng.nextInt(10)] }.joinToString("")

    // ---------- Strength ----------

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** Bits of entropy for the random-character mode (pool size ^ length). */
    fun bits(o: CharOptions): Double = classes(o).sumOf { it.length }.let { if (it == 0) 0.0 else o.length * log2(it.toDouble()) }

    fun bits(o: WordOptions): Double =
        o.words * o.syllablesPerWord * log2((CONSONANTS.length * VOWELS.length).toDouble()) + o.digits * log2(10.0)

    fun pinBits(length: Int): Double = length * log2(10.0)

    enum class Strength(val label: String) { WEAK("Weak"), FAIR("Fair"), STRONG("Strong"), VERY_STRONG("Very strong") }

    fun strength(bits: Double): Strength = when {
        bits < 40 -> Strength.WEAK
        bits < 60 -> Strength.FAIR
        bits < 80 -> Strength.STRONG
        else -> Strength.VERY_STRONG
    }
}
