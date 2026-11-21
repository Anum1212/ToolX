package com.toolx.app.tools.text

import java.util.Locale

/** Pure text transformations used by the text tools. Kept UI-free so they're easy to test. */
object TextOps {

    private fun lines(s: String) = s.split("\n")

    // ---------- Cases ----------

    enum class Case(val label: String) {
        UPPER("UPPERCASE"), LOWER("lowercase"), TITLE("Title Case"), SENTENCE("Sentence case"),
        CAMEL("camelCase"), PASCAL("PascalCase"), SNAKE("snake_case"), KEBAB("kebab-case"),
        CONSTANT("CONSTANT_CASE"), ALTERNATING("aLtErNaTiNg"), INVERSE("iNVERSE cASE"),
    }

    private fun words(s: String): List<String> =
        s.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun String.cap() = lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun changeCase(s: String, case: Case): String = when (case) {
        Case.UPPER -> s.uppercase()
        Case.LOWER -> s.lowercase()
        // Capitalize after start, space, hyphen or bracket, optionally through an opening quote,
        // but not after an apostrophe inside a word ("don't" stays "Don't").
        Case.TITLE -> s.lowercase().replace(Regex("(^|[\\s\\-(\\[])([\"'“‘]?)(\\p{L})")) { it.groupValues[1] + it.groupValues[2] + it.groupValues[3].uppercase() }
        Case.SENTENCE -> s.lowercase().replace(Regex("(^\\s*|[.!?]\\s+)(\\p{L})")) { it.groupValues[1] + it.groupValues[2].uppercase() }
        Case.CAMEL -> lines(s).joinToString("\n") { l -> words(l).mapIndexed { i, w -> if (i == 0) w.lowercase() else w.cap() }.joinToString("") }
        Case.PASCAL -> lines(s).joinToString("\n") { l -> words(l).joinToString("") { it.cap() } }
        Case.SNAKE -> lines(s).joinToString("\n") { l -> words(l).joinToString("_") { it.lowercase() } }
        Case.KEBAB -> lines(s).joinToString("\n") { l -> words(l).joinToString("-") { it.lowercase() } }
        Case.CONSTANT -> lines(s).joinToString("\n") { l -> words(l).joinToString("_") { it.uppercase() } }
        Case.ALTERNATING -> {
            var i = 0
            s.map { c -> if (c.isLetter()) (if (i++ % 2 == 0) c.lowercaseChar() else c.uppercaseChar()) else c }.joinToString("")
        }
        Case.INVERSE -> s.map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.joinToString("")
    }

    // ---------- Find & replace ----------

    data class ReplaceResult(val text: String, val count: Int, val error: String? = null)

    fun findReplace(s: String, find: String, replace: String, matchCase: Boolean, regex: Boolean, wholeWord: Boolean): ReplaceResult {
        if (find.isEmpty()) return ReplaceResult(s, 0)
        return try {
            var pattern = if (regex) find else Regex.escape(find)
            if (wholeWord) pattern = "\\b(?:$pattern)\\b"
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val r = Regex(pattern, options)
            val count = r.findAll(s).count()
            // In literal mode, the replacement must not interpret $ or \.
            val out = if (regex) r.replace(s, replace) else r.replace(s) { replace }
            ReplaceResult(out, count)
        } catch (e: Exception) {
            ReplaceResult(s, 0, e.message?.lineSequence()?.firstOrNull() ?: "Invalid pattern")
        }
    }

    // ---------- Sort ----------

    enum class SortMode(val label: String) { AZ("A → Z"), ZA("Z → A"), SHORT("Shortest first"), LONG("Longest first"), NUMERIC("Numeric"), SHUFFLE("Shuffle") }

    fun sortLines(s: String, mode: SortMode, ignoreCase: Boolean, seed: Long = 0): String {
        val ls = lines(s)
        val key: (String) -> String = { if (ignoreCase) it.lowercase() else it }
        val sorted = when (mode) {
            SortMode.AZ -> ls.sortedWith(compareBy(key))
            SortMode.ZA -> ls.sortedWith(compareByDescending(key))
            SortMode.SHORT -> ls.sortedBy { it.length }
            SortMode.LONG -> ls.sortedByDescending { it.length }
            SortMode.NUMERIC -> ls.sortedWith(compareBy<String> { Regex("-?\\d+(\\.\\d+)?").find(it)?.value?.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy(key))
            SortMode.SHUFFLE -> ls.shuffled(kotlin.random.Random(seed))
        }
        return sorted.joinToString("\n")
    }

    // ---------- Counter ----------

    data class Stats(
        val characters: Int, val charactersNoSpaces: Int, val words: Int, val lines: Int,
        val sentences: Int, val paragraphs: Int, val readingSeconds: Int, val speakingSeconds: Int,
    )

    fun stats(s: String): Stats {
        val words = s.split(Regex("\\s+")).count { it.isNotBlank() }
        return Stats(
            characters = s.length,
            charactersNoSpaces = s.count { !it.isWhitespace() },
            words = words,
            lines = if (s.isEmpty()) 0 else lines(s).size,
            sentences = Regex("[^.!?]+[.!?]+|[^.!?]+$").findAll(s.trim()).count { it.value.isNotBlank() },
            paragraphs = s.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() },
            readingSeconds = (words * 60.0 / 238).toInt(),  // average silent reading speed
            speakingSeconds = (words * 60.0 / 140).toInt(),
        )
    }

    // ---------- Repeat ----------

    fun repeat(s: String, times: Int, separator: String): String =
        if (times <= 0) "" else List(times.coerceAtMost(MAX_REPEAT)) { s }.joinToString(separator)

    const val MAX_REPEAT = 10_000

    // ---------- Remove ----------

    enum class SpaceMode(val label: String) { COLLAPSE("Collapse repeated spaces"), TRIM("Trim each line"), ALL("Remove all spaces"), TABS("Tabs to spaces") }

    fun removeSpaces(s: String, mode: SpaceMode): String = when (mode) {
        SpaceMode.COLLAPSE -> lines(s).joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
        SpaceMode.TRIM -> lines(s).joinToString("\n") { it.trim() }
        SpaceMode.ALL -> s.replace(Regex("[ \\t\\u00A0]+"), "")
        SpaceMode.TABS -> s.replace("\t", "    ")
    }

    fun removeEmptyLines(s: String, includeWhitespaceOnly: Boolean): String =
        lines(s).filterNot { if (includeWhitespaceOnly) it.isBlank() else it.isEmpty() }.joinToString("\n")

    fun removeDuplicateLines(s: String, ignoreCase: Boolean, trim: Boolean): Pair<String, Int> {
        val seen = HashSet<String>()
        val ls = lines(s)
        val kept = ls.filter { l ->
            var k = if (trim) l.trim() else l
            if (ignoreCase) k = k.lowercase()
            seen.add(k)
        }
        return kept.joinToString("\n") to (ls.size - kept.size)
    }

    fun removeDuplicateWords(s: String, ignoreCase: Boolean): Pair<String, Int> {
        val seen = HashSet<String>()
        var removed = 0
        val out = lines(s).joinToString("\n") { line ->
            line.split(" ").filter { w ->
                if (w.isEmpty()) return@filter true
                val k = w.trim { !it.isLetterOrDigit() }.let { if (ignoreCase) it.lowercase() else it }
                if (k.isEmpty() || seen.add(k)) true else { removed++; false }
            }.joinToString(" ").replace(Regex(" {2,}"), " ")
        }
        return out to removed
    }

    fun removeLineBreaks(s: String, joiner: String, keepParagraphs: Boolean): String =
        if (keepParagraphs) {
            s.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { p -> p.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(joiner) }
        } else {
            lines(s).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(joiner)
        }

    // ---------- Edit ----------

    fun prefixSuffix(s: String, prefix: String, suffix: String, skipEmpty: Boolean): String =
        lines(s).joinToString("\n") { if (skipEmpty && it.isBlank()) it else prefix + it + suffix }

    enum class NumberStyle(val label: String, val render: (Int) -> String) {
        DOT("1. ", { "$it. " }), PAREN("1) ", { "$it) " }), DASH("1 - ", { "$it - " }), COLON("1: ", { "$it: " }), BRACKET("[1] ", { "[$it] " }),
    }

    fun numberLines(s: String, start: Int, style: NumberStyle, skipEmpty: Boolean): String {
        var n = start
        return lines(s).joinToString("\n") { if (skipEmpty && it.isBlank()) it else style.render(n++) + it }
    }

    /** Reverses by grapheme-ish code points so emoji and accents survive. */
    fun reverseText(s: String): String {
        val cps = s.codePoints().toArray()
        cps.reverse()
        return String(cps, 0, cps.size)
    }

    fun reverseLines(s: String): String = lines(s).reversed().joinToString("\n")

    fun reverseWords(s: String, perLine: Boolean): String =
        if (perLine) lines(s).joinToString("\n") { l -> l.split(" ").reversed().joinToString(" ") }
        else s.split(Regex("\\s+")).filter { it.isNotEmpty() }.reversed().joinToString(" ")

    enum class BreakMode(val label: String) { WORDS("Every N words"), CHARS("Every N characters (wrap)"), SENTENCES("After each sentence"), CHARACTER("After a character") }

    fun addBreaks(s: String, mode: BreakMode, n: Int, char: String): String = when (mode) {
        BreakMode.WORDS -> s.split(Regex("\\s+")).filter { it.isNotEmpty() }.chunked(n.coerceAtLeast(1)).joinToString("\n") { it.joinToString(" ") }
        BreakMode.CHARS -> wrap(s, n.coerceAtLeast(1))
        BreakMode.SENTENCES -> s.replace(Regex("([.!?]+)\\s+"), "$1\n")
        BreakMode.CHARACTER -> if (char.isEmpty()) s else s.replace(char, char + "\n")
    }

    /** Word-wraps each line at [width] characters, breaking long words if needed. */
    private fun wrap(s: String, width: Int): String = lines(s).joinToString("\n") { line ->
        val out = StringBuilder()
        var col = 0
        line.split(" ").forEach { word ->
            var w = word
            while (w.length > width) {
                if (col > 0) { out.append('\n'); col = 0 }
                out.append(w, 0, width).append('\n'); w = w.substring(width)
            }
            if (col > 0 && col + 1 + w.length > width) { out.append('\n'); col = 0 }
            else if (col > 0) { out.append(' '); col++ }
            out.append(w); col += w.length
        }
        out.toString()
    }
}
