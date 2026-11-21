package com.toolx.app.tools

import androidx.compose.material.icons.rounded.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolVisibilityTest {

    private val icon = androidx.compose.material.icons.Icons.Rounded.Build
    private val tools = listOf(
        Tool("a", "Merge PDF", "Join PDFs", icon, ToolCategory.PDF, true, listOf("combine")),
        Tool("b", "Split PDF", "Separate pages", icon, ToolCategory.PDF, true),
        Tool("c", "Trim", "Cut a video", icon, ToolCategory.VIDEO, true, listOf("cut")),
    )

    @Test fun nothingHiddenShowsAll() {
        assertEquals(3, ToolRegistry.search("", null, tools = tools).size)
    }

    @Test fun hiddenCategoryRemovesItsTools() {
        val r = ToolRegistry.search("", null, hiddenCategories = setOf("PDF"), tools = tools)
        assertEquals(listOf("c"), r.map { it.id })
    }

    @Test fun hiddenToolIsGoneFromSearchToo() {
        assertEquals(listOf("b"), ToolRegistry.search("", ToolCategory.PDF, hiddenTools = setOf("a"), tools = tools).map { it.id })
        // Searching by its keyword finds nothing once hidden.
        assertTrue(ToolRegistry.search("combine", null, hiddenTools = setOf("a"), tools = tools).isEmpty())
        assertEquals(listOf("a"), ToolRegistry.search("combine", null, tools = tools).map { it.id })
    }
}
