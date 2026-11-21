package com.toolx.app.tools.text

import com.toolx.app.tools.text.TextOps.BreakMode
import com.toolx.app.tools.text.TextOps.Case
import com.toolx.app.tools.text.TextOps.NumberStyle
import com.toolx.app.tools.text.TextOps.SortMode
import com.toolx.app.tools.text.TextOps.SpaceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextOpsTest {

    // ---------- Cases ----------

    @Test fun upperLower() {
        assertEquals("HELLO WORLD", TextOps.changeCase("Hello World", Case.UPPER))
        assertEquals("hello world", TextOps.changeCase("Hello World", Case.LOWER))
    }

    @Test fun titleCase() {
        assertEquals("The Quick Brown Fox", TextOps.changeCase("the quick BROWN fox", Case.TITLE))
        assertEquals("Don't Stop Me Now", TextOps.changeCase("don't stop me now", Case.TITLE))
        assertEquals("It's A Well-Known Fact", TextOps.changeCase("it's a well-known fact", Case.TITLE))
        assertEquals("He Said \"Hello\"", TextOps.changeCase("he said \"hello\"", Case.TITLE))
    }

    @Test fun sentenceCase() {
        assertEquals("Hello. How are you? Fine!", TextOps.changeCase("HELLO. HOW ARE YOU? FINE!", Case.SENTENCE))
        assertEquals("First line.\nSecond line.", TextOps.changeCase("first line.\nsecond line.", Case.SENTENCE))
    }

    @Test fun programmerCases() {
        assertEquals("helloWorldFoo", TextOps.changeCase("hello world foo", Case.CAMEL))
        assertEquals("helloWorldFoo", TextOps.changeCase("Hello_World-foo", Case.CAMEL))
        assertEquals("HelloWorld", TextOps.changeCase("hello world", Case.PASCAL))
        assertEquals("hello_world", TextOps.changeCase("Hello World", Case.SNAKE))
        assertEquals("hello_world", TextOps.changeCase("helloWorld", Case.SNAKE))
        assertEquals("user_id_2", TextOps.changeCase("userId 2", Case.SNAKE))
        assertEquals("hello-world", TextOps.changeCase("Hello World", Case.KEBAB))
        assertEquals("HELLO_WORLD", TextOps.changeCase("hello world", Case.CONSTANT))
    }

    @Test fun funCases() {
        assertEquals("hElLo", TextOps.changeCase("hello", Case.ALTERNATING))
        // Alternation continues across words (letters only are counted).
        assertEquals("hElLo WoRlD", TextOps.changeCase("hello world", Case.ALTERNATING))
        assertEquals("hELLO wORLD", TextOps.changeCase("Hello World", Case.INVERSE))
    }

    // ---------- Find & replace ----------

    @Test fun findReplaceLiteral() {
        val r = TextOps.findReplace("a.b a.b axb", ".", "!", matchCase = false, regex = false, wholeWord = false)
        assertEquals("a!b a!b axb", r.text)
        assertEquals(2, r.count)
    }

    @Test fun findReplaceLiteralDollarIsNotABackreference() {
        assertEquals("cost: $5", TextOps.findReplace("cost: X", "X", "$5", false, false, false).text)
    }

    @Test fun findReplaceCaseAndWholeWord() {
        assertEquals("dog dog", TextOps.findReplace("Cat cat", "cat", "dog", false, false, false).text)
        val sensitive = TextOps.findReplace("Cat cat", "cat", "dog", true, false, false)
        assertEquals("Cat dog", sensitive.text)
        assertEquals(1, sensitive.count)
        val whole = TextOps.findReplace("cat category bobcat cat.", "cat", "dog", false, false, true)
        assertEquals("dog category bobcat dog.", whole.text)
        assertEquals(2, whole.count)
    }

    @Test fun findReplaceRegex() {
        val r = TextOps.findReplace("a1b22c333", "\\d+", "#", false, true, false)
        assertEquals("a#b#c#", r.text)
        assertEquals(3, r.count)
        assertEquals("Smith, John", TextOps.findReplace("John Smith", "(\\w+) (\\w+)", "$2, $1", false, true, false).text)
    }

    @Test fun findReplaceBadRegexReportsError() {
        val r = TextOps.findReplace("abc", "(", "x", false, true, false)
        assertNotNull(r.error)
        assertEquals("abc", r.text)
    }

    @Test fun findEmptyChangesNothing() {
        assertEquals(0, TextOps.findReplace("abc", "", "x", false, false, false).count)
    }

    // ---------- Sort ----------

    @Test fun sortAlphabetical() {
        assertEquals("Apple\nbanana\ncherry", TextOps.sortLines("banana\nApple\ncherry", SortMode.AZ, ignoreCase = true))
        assertEquals("cherry\nbanana\nApple", TextOps.sortLines("banana\nApple\ncherry", SortMode.ZA, ignoreCase = true))
        assertEquals("A\nB\na", TextOps.sortLines("a\nB\nA", SortMode.AZ, ignoreCase = false))
    }

    @Test fun sortNumericAndLength() {
        assertEquals("item 1\nitem 2\nitem 10", TextOps.sortLines("item 10\nitem 2\nitem 1", SortMode.NUMERIC, true))
        assertEquals("-5\n3.5\n20", TextOps.sortLines("20\n-5\n3.5", SortMode.NUMERIC, true))
        assertEquals("a\nbbb\ncc".let { TextOps.sortLines(it, SortMode.SHORT, true) }, "a\ncc\nbbb")
        assertEquals("bbb\ncc\na", TextOps.sortLines("a\nbbb\ncc", SortMode.LONG, true))
    }

    @Test fun shuffleKeepsAllLines() {
        val input = (1..20).joinToString("\n")
        val out = TextOps.sortLines(input, SortMode.SHUFFLE, true, seed = 42)
        assertEquals(input.lines().sorted(), out.lines().sorted())
    }

    // ---------- Counter ----------

    @Test fun counterRealParagraph() {
        val s = TextOps.stats("Hello world. This is a test.\n\nSecond paragraph here!")
        assertEquals(52, s.characters)
        assertEquals(43, s.charactersNoSpaces)
        assertEquals(9, s.words)
        assertEquals(3, s.sentences)
        assertEquals(3, s.lines)
        assertEquals(2, s.paragraphs)
    }

    @Test fun counterEmpty() {
        val s = TextOps.stats("")
        assertEquals(0, s.characters); assertEquals(0, s.words); assertEquals(0, s.lines); assertEquals(0, s.sentences); assertEquals(0, s.paragraphs)
    }

    @Test fun readingTime() {
        // 238 words read in exactly one minute.
        val s = TextOps.stats(List(238) { "word" }.joinToString(" "))
        assertEquals(60, s.readingSeconds)
    }

    // ---------- Repeat / remove ----------

    @Test fun repeat() {
        assertEquals("ab-ab-ab", TextOps.repeat("ab", 3, "-"))
        assertEquals("", TextOps.repeat("ab", 0, "-"))
        assertEquals(TextOps.MAX_REPEAT, TextOps.repeat("x", 50_000, "").length)
    }

    @Test fun whitespace() {
        assertEquals("a b\nc d", TextOps.removeSpaces("  a   b  \n c\td ", SpaceMode.COLLAPSE))
        assertEquals("abc", TextOps.removeSpaces("a b\tc", SpaceMode.ALL))
        assertEquals("a  b\nc", TextOps.removeSpaces("  a  b  \n  c", SpaceMode.TRIM))
        assertEquals("    x", TextOps.removeSpaces("\tx", SpaceMode.TABS))
    }

    @Test fun emptyLines() {
        assertEquals("a\nb", TextOps.removeEmptyLines("a\n\n  \nb", includeWhitespaceOnly = true))
        assertEquals("a\n  \nb", TextOps.removeEmptyLines("a\n\n  \nb", includeWhitespaceOnly = false))
    }

    @Test fun duplicateLines() {
        val (out, removed) = TextOps.removeDuplicateLines("a\nb\nA\na \nc", ignoreCase = true, trim = true)
        assertEquals("a\nb\nc", out)
        assertEquals(2, removed)
        assertEquals(0, TextOps.removeDuplicateLines("a\nA\na ", ignoreCase = false, trim = false).second)
    }

    @Test fun duplicateWords() {
        val (out, removed) = TextOps.removeDuplicateWords("the cat and the dog and The bird", ignoreCase = true)
        assertEquals("the cat and dog bird", out)
        assertEquals(3, removed)
    }

    @Test fun duplicateWordsIgnoresPunctuation() {
        assertEquals("Hello, world!", TextOps.removeDuplicateWords("Hello, world! hello", true).first)
    }

    @Test fun lineBreaks() {
        assertEquals("a b c d", TextOps.removeLineBreaks("a\nb\n\nc\nd", " ", keepParagraphs = false))
        assertEquals("a b\n\nc d", TextOps.removeLineBreaks("a\nb\n\nc\nd", " ", keepParagraphs = true))
        assertEquals("x, y", TextOps.removeLineBreaks("x\n y ", ", ", false))
    }

    // ---------- Edit ----------

    @Test fun prefixSuffix() {
        assertEquals("- a;\n\n- b;", TextOps.prefixSuffix("a\n\nb", "- ", ";", skipEmpty = true))
        assertEquals("<a>\n<>", TextOps.prefixSuffix("a\n", "<", ">", skipEmpty = false))
    }

    @Test fun numberLines() {
        assertEquals("1. x\n2. y\n\n3. z", TextOps.numberLines("x\ny\n\nz", 1, NumberStyle.DOT, skipEmpty = true))
        assertEquals("10) a\n11) b", TextOps.numberLines("a\nb", 10, NumberStyle.PAREN, true))
        assertEquals("[1] a\n[2] ", TextOps.numberLines("a\n", 1, NumberStyle.BRACKET, skipEmpty = false))
    }

    @Test fun reverseKeepsEmojiAndAccents() {
        assertEquals("cba", TextOps.reverseText("abc"))
        assertEquals("👍cba", TextOps.reverseText("abc👍"))
        assertEquals("olléh", TextOps.reverseText("héllo"))
    }

    @Test fun reverseLinesAndWords() {
        assertEquals("3\n2\n1", TextOps.reverseLines("1\n2\n3"))
        assertEquals("three two one\nfive four", TextOps.reverseWords("one two three\nfour five", perLine = true))
        assertEquals("five four three two one", TextOps.reverseWords("one two three\nfour five", perLine = false))
    }

    @Test fun addBreaks() {
        assertEquals("a b\nc d\ne", TextOps.addBreaks("a b c d e", BreakMode.WORDS, 2, ""))
        assertEquals("the quick\nbrown fox", TextOps.addBreaks("the quick brown fox", BreakMode.CHARS, 10, ""))
        assertEquals("abcde\nfghij\nkl", TextOps.addBreaks("abcdefghijkl", BreakMode.CHARS, 5, ""))
        assertEquals("Hi.\nHow are you?\nGood", TextOps.addBreaks("Hi. How are you? Good", BreakMode.SENTENCES, 0, ""))
        assertEquals("a,\nb,\nc", TextOps.addBreaks("a,b,c", BreakMode.CHARACTER, 0, ","))
    }

    @Test fun wrapNeverExceedsWidth() {
        val text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore"
        TextOps.addBreaks(text, BreakMode.CHARS, 20, "").lines().forEach { assert(it.length <= 20) { "Line too long: '$it'" } }
    }
}
