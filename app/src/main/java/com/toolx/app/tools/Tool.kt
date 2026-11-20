package com.toolx.app.tools

import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String) {
    VIDEO("Video"),
    IMAGE("Image"),
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
