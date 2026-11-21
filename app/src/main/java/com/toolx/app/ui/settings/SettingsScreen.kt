package com.toolx.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolx.app.core.AppSettings
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolRegistry
import com.toolx.app.ui.theme.AppTheme
import com.toolx.app.ui.theme.DYNAMIC_THEME
import com.toolx.app.ui.theme.SYSTEM_THEME
import com.toolx.app.ui.theme.ThemeGroup
import com.toolx.app.ui.theme.Themes
import com.toolx.app.ui.theme.categoryColor

/** Settings as its own screen, opened from the home menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> SettingsContent(padding) }
}

@Composable
private fun SettingsContent(padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ThemeSection()
        VisibilitySection()
        AboutSection()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

// ---------- Themes ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection() {
    val current = AppSettings.themeId
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Theme")
        ThemeGroup.entries.forEach { group ->
            Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (group == ThemeGroup.BASIC) {
                    SystemSwatch(selected = current == SYSTEM_THEME) { AppSettings.setTheme(SYSTEM_THEME) }
                }
                Themes.all.filter { it.group == group }.forEach { t ->
                    Swatch(t.name, selected = current == t.id, onClick = { AppSettings.setTheme(t.id) }) { PaletteArt(t) }
                }
                if (group == ThemeGroup.BASIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    DynamicSwatch(selected = current == DYNAMIC_THEME) { AppSettings.setTheme(DYNAMIC_THEME) }
                }
            }
        }
    }
}

/** A tappable theme preview card with its name underneath. */
@Composable
private fun Swatch(name: String, selected: Boolean, onClick: () -> Unit, art: @Composable () -> Unit) {
    val ring = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier
            .width(98.dp)
            .clickable(role = Role.RadioButton, onClickLabel = "Use $name") { onClick() }
            .semantics { contentDescription = "$name theme"; stateDescription = if (selected) "Selected" else "Not selected" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(98.dp, 70.dp).clip(RoundedCornerShape(14.dp)).border(ring, RoundedCornerShape(14.dp))) {
            art()
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Miniature of the app in [t]: background, a card with the primary bar, and category dots. */
@Composable
private fun PaletteArt(t: AppTheme) {
    Box(Modifier.fillMaxSize().background(t.background).padding(8.dp)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(t.surface).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.width(38.dp).height(6.dp).clip(CircleShape).background(t.primary))
            Box(Modifier.width(52.dp).height(4.dp).clip(CircleShape).background(t.muted.copy(alpha = 0.6f)))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(ToolCategory.VIDEO, ToolCategory.AUDIO, ToolCategory.IMAGE, ToolCategory.PDF, ToolCategory.TEXT, ToolCategory.CALC)
                    .forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(t.accent(it))) }
            }
        }
    }
}

@Composable
private fun SystemSwatch(selected: Boolean, onClick: () -> Unit) {
    Swatch("System", selected, onClick) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) { PaletteArt(Themes.Light) }
            Box(Modifier.weight(1f).fillMaxSize()) { PaletteArt(Themes.Dark) }
        }
    }
}

@Composable
private fun DynamicSwatch(selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = remember(dark) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
    Swatch("Material You", selected, onClick) {
        Box(Modifier.fillMaxSize().background(scheme.surface), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wallpaper, null, tint = scheme.primary)
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(it))
                }
            }
        }
    }
}

// ---------- Visibility ----------

@Composable
private fun VisibilitySection() {
    val hiddenCats = AppSettings.hiddenCategories
    val hiddenTools = AppSettings.hiddenTools
    val expanded = remember { mutableStateListOf<ToolCategory>() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Categories & tools")
                Text(
                    "Hide what you don't use. Tap a category to choose single tools.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hiddenCats.isNotEmpty() || hiddenTools.isNotEmpty()) {
                TextButton(onClick = { AppSettings.showEverything() }) { Text("Show all") }
            }
        }
        ToolCategory.entries.forEach { cat ->
            val tools = ToolRegistry.all.filter { it.category == cat }
            if (tools.isEmpty()) return@forEach
            val catHidden = cat.name in hiddenCats
            val shown = tools.count { it.id !in hiddenTools }
            val open = cat in expanded
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = if (open) "Collapse" else "Choose tools") { if (open) expanded.remove(cat) else expanded.add(cat) }
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(cat)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (catHidden) "Hidden" else "$shown of ${tools.size} tools shown",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = !catHidden,
                        onCheckedChange = { AppSettings.setCategoryHidden(cat, hidden = !it) },
                        modifier = Modifier.semantics { contentDescription = "Show ${cat.title}" },
                    )
                }
                if (open) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    tools.forEach { tool ->
                        val toolShown = tool.id !in hiddenTools
                        Row(
                            Modifier.fillMaxWidth().padding(start = 38.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(tool.icon, null, Modifier.size(18.dp), tint = if (catHidden) Color.Gray else categoryColor(cat))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                tool.title + if (!tool.available) " (soon)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (catHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = toolShown,
                                enabled = !catHidden,
                                onCheckedChange = { AppSettings.setToolHidden(tool, hidden = !it) },
                                modifier = Modifier.semantics { contentDescription = "Show ${tool.title}" },
                            )
                        }
                    }
                    if (catHidden) {
                        Text(
                            "Turn the category on to choose single tools.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 38.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- About ----------

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("About")
        Text("ToolX 0.1.0", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Everything runs on your phone. The app has no internet permission, no ads and no tracking.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
