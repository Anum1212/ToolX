package com.toolx.app.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    IMAGE("Image"),
    PDF("PDF"),
    TEXT("Text"),
    UNITS("Units"),
    CALC("Calculate"),
    GENERAL("General"),
    FINANCE("Finance"),
    RANDOM("Random"),
    DEV("Developer"),
}

data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
    /** False shows the tool on the home grid with a "Soon" badge and a placeholder screen. */
    val available: Boolean = false,
    /** Extra words matched by search, e.g. "cut" for Trim. */
    val keywords: List<String> = emptyList(),
)

typealias ToolScreenFn = @Composable (tool: Tool, onBack: () -> Unit) -> Unit

/**
 * A family of tools that registers its own entries and screens, so adding a
 * tool there needs no edits to the registry or navigation.
 */
interface ToolModule {
    val tools: List<Tool>
    val screens: Map<String, ToolScreenFn>
}
