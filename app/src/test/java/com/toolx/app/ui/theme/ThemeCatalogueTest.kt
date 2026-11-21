package com.toolx.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.toolx.app.tools.ToolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ThemeCatalogueTest {

    @Test fun contrastMaths() {
        assertEquals(21f, contrast(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrast(Color(0xFF777777), Color(0xFF777777)), 0.001f)
        // Independently known: #767676 on white is the classic 4.54:1 AA grey.
        assertEquals(4.54f, contrast(Color(0xFF767676), Color.White), 0.02f)
        assertEquals(Color.White, onColor(Color(0xFF1A237E)))
        assertEquals(Color(0xFF111111), onColor(Color(0xFFFFEB3B)))
    }

    @Test fun readableReachesTarget() {
        // Light yellow on white starts far below 3:1 and must be darkened to reach it.
        val fixed = readable(Color(0xFFFFEB3B), Color.White, 3f)
        assertTrue(contrast(fixed, Color.White) >= 3f)
        // Already-readable colours are left alone.
        assertEquals(Color(0xFF1A237E), readable(Color(0xFF1A237E), Color.White, 3f))
    }

    @Test fun catalogueShape() {
        val ids = Themes.all.map { it.id }
        assertEquals("theme ids must be unique", ids.size, ids.toSet().size)
        assertEquals(10, Themes.all.count { it.group == ThemeGroup.LIGHT })
        assertEquals(10, Themes.all.count { it.group == ThemeGroup.DARK })
        assertTrue(Themes.find("nord") != null && Themes.find("one_dark_pro") != null && Themes.find("amoled") != null)
        Themes.all.filter { it.group == ThemeGroup.LIGHT }.forEach { assertTrue("${it.name} should be light", !it.dark) }
        Themes.all.filter { it.group == ThemeGroup.DARK }.forEach { assertTrue("${it.name} should be dark", it.dark) }
        assertEquals(Color.Black, Themes.Amoled.background)
    }

    @Test fun oneDistinctColourPerCategory() {
        Themes.all.forEach { t ->
            assertEquals("${t.name}: accents", ToolCategory.entries.size, t.accents.size)
            // No two categories may look alike: minimum RGB distance between any pair.
            for (i in t.accents.indices) for (j in i + 1 until t.accents.size) {
                val a = t.accents[i]; val b = t.accents[j]
                val d = sqrt((a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue))
                assertTrue("${t.name}: ${ToolCategory.entries[i]} and ${ToolCategory.entries[j]} too similar ($d)", d >= 0.06f)
            }
        }
    }

    @Test fun textIsReadableInEveryTheme() {
        Themes.all.forEach { t ->
            assertTrue("${t.name}: body text ${contrast(t.text, t.background)}", contrast(t.text, t.background) >= 4.5f)
            assertTrue("${t.name}: body text on cards", contrast(t.text, t.surface) >= 4.5f)
            assertTrue("${t.name}: secondary text ${contrast(t.muted, t.surface)}", contrast(t.muted, t.surface) >= 3f)
        }
    }

    @Test fun categoryColoursStayReadable() {
        Themes.all.forEach { t ->
            ToolCategory.entries.forEach { cat ->
                listOf(t.surface, t.background).forEach { bg ->
                    val c = readable(t.accent(cat), bg, 3f)
                    assertTrue("${t.name} ${cat.title}: ${contrast(c, bg)}", contrast(c, bg) >= 3f)
                }
            }
        }
    }

    @Test fun buttonsAreReadable() {
        Themes.all.forEach { t ->
            val s = t.toColorScheme()
            assertTrue("${t.name}: button text ${contrast(s.onPrimary, s.primary)}", contrast(s.onPrimary, s.primary) >= 3f)
            assertTrue("${t.name}: primary on background", contrast(s.primary, s.background) >= 3f)
            assertEquals(t.dark, Themes.resolve(t.id, systemDark = !t.dark).dark)
        }
    }

    @Test fun systemThemeFollowsPhone() {
        assertTrue(Themes.resolve(SYSTEM_THEME, systemDark = true).dark)
        assertTrue(!Themes.resolve(SYSTEM_THEME, systemDark = false).dark)
        // Unknown ids (e.g. a theme removed in an update) fall back safely.
        assertEquals(Themes.Dark, Themes.resolve("gone", systemDark = true))
    }
}
