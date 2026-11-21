package com.toolx.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.ui.theme.SYSTEM_THEME

/**
 * User preferences, kept in SharedPreferences and exposed as Compose state so every screen
 * updates the moment something changes. Call [init] once from the activity.
 */
object AppSettings {

    private const val FILE = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"
    private const val KEY_HIDDEN_TOOLS = "hidden_tools"

    private var prefs: SharedPreferences? = null

    var themeId by mutableStateOf(SYSTEM_THEME)
        private set
    var hiddenCategories by mutableStateOf<Set<String>>(emptySet())
        private set
    var hiddenTools by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Recently opened tool ids, newest first. */
    var recentTools by mutableStateOf<List<String>>(emptyList())
        private set

    private const val KEY_RECENTS = "recent_tools"
    const val MAX_RECENTS = 12

    /** [id] moved to the front, without duplicates, at most [max] long. */
    internal fun pushRecent(list: List<String>, id: String, max: Int = MAX_RECENTS): List<String> =
        (listOf(id) + list.filter { it != id }).take(max)

    fun recordRecent(tool: Tool) {
        recentTools = pushRecent(recentTools, tool.id)
        prefs?.edit()?.putString(KEY_RECENTS, recentTools.joinToString(","))?.apply()
    }

    fun clearRecents() {
        recentTools = emptyList()
        prefs?.edit()?.remove(KEY_RECENTS)?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        themeId = p.getString(KEY_THEME, SYSTEM_THEME) ?: SYSTEM_THEME
        hiddenCategories = p.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()).orEmpty().toSet()
        hiddenTools = p.getStringSet(KEY_HIDDEN_TOOLS, emptySet()).orEmpty().toSet()
        recentTools = p.getString(KEY_RECENTS, "").orEmpty().split(',').filter { it.isNotBlank() }
    }

    fun setTheme(id: String) {
        themeId = id
        prefs?.edit()?.putString(KEY_THEME, id)?.apply()
    }

    fun setCategoryHidden(category: ToolCategory, hidden: Boolean) {
        hiddenCategories = if (hidden) hiddenCategories + category.name else hiddenCategories - category.name
        prefs?.edit()?.putStringSet(KEY_HIDDEN_CATEGORIES, hiddenCategories)?.apply()
    }

    fun setToolHidden(tool: Tool, hidden: Boolean) {
        hiddenTools = if (hidden) hiddenTools + tool.id else hiddenTools - tool.id
        prefs?.edit()?.putStringSet(KEY_HIDDEN_TOOLS, hiddenTools)?.apply()
    }

    fun showEverything() {
        hiddenCategories = emptySet()
        hiddenTools = emptySet()
        prefs?.edit()?.remove(KEY_HIDDEN_CATEGORIES)?.remove(KEY_HIDDEN_TOOLS)?.apply()
    }
}
