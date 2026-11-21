package com.toolx.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolx.app.core.AppSettings
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolRegistry
import com.toolx.app.ui.theme.categoryColor
import com.toolx.app.ui.theme.categoryContainer
import kotlinx.coroutines.launch

/** One swipeable page of the home screen. */
private sealed interface HomePage {
    val key: String
    val title: String

    data object Recent : HomePage { override val key = "recent"; override val title = "Recent" }
    data object All : HomePage { override val key = "all"; override val title = "All" }
    data class Category(val category: ToolCategory) : HomePage {
        override val key = category.name
        override val title = category.title
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (Tool) -> Unit, onOpenSettings: () -> Unit) {
    val hiddenCats = AppSettings.hiddenCategories
    val hiddenTools = AppSettings.hiddenTools
    val visible = remember(hiddenCats, hiddenTools) { ToolRegistry.search("", null, hiddenCats, hiddenTools) }
    val byCategory = remember(visible) { visible.groupBy { it.category } }
    val recent = remember(AppSettings.recentTools, visible) {
        val ids = visible.associateBy { it.id }
        AppSettings.recentTools.mapNotNull { ids[it] }
    }
    // Recent, All, then every category that still has visible tools.
    val pages: List<HomePage> = remember(byCategory) {
        listOf(HomePage.Recent, HomePage.All) + ToolCategory.entries.filter { it in byCategory }.map { HomePage.Category(it) }
    }

    var savedKey by rememberSaveable { mutableStateOf(HomePage.All.key) }
    val startPage = pages.indexOfFirst { it.key == savedKey }.takeIf { it >= 0 } ?: 1
    val pager = rememberPagerState(initialPage = startPage) { pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage, pages) { pages.getOrNull(pager.currentPage)?.let { savedKey = it.key } }
    fun goTo(key: String) {
        val i = pages.indexOfFirst { it.key == key }
        if (i >= 0) scope.launch { pager.animateScrollToPage(i) }
    }

    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(query, visible) { ToolRegistry.search(query, null, tools = visible) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && searching) { query = "" }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Swiping is for pages; the drawer opens from the menu button (and closes by swipe).
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            HomeDrawer(
                pages = pages,
                current = pages.getOrNull(pager.currentPage)?.key,
                onPage = { key -> query = ""; goTo(key); scope.launch { drawer.close() } },
                onSettings = { scope.launch { drawer.close() }; onOpenSettings() },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, contentDescription = "Menu") }
                    },
                    title = { Text("ToolX", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { BottomSearch(query) { query = it } },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (searching) {
                    SearchResults(query, results, onOpenTool)
                } else {
                    PageChips(pages, pager.currentPage) { goTo(pages[it].key) }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pages[it].key }) { index ->
                        when (val page = pages[index]) {
                            HomePage.Recent -> RecentPage(recent, onOpenTool)
                            HomePage.All -> ToolGrid(onOpenTool) {
                                byCategory.forEach { (cat, tools) -> section(cat, tools, onOpenTool) }
                            }
                            is HomePage.Category -> ToolGrid(onOpenTool) {
                                items(byCategory[page.category].orEmpty(), key = { it.id }) { ToolCard(it) { onOpenTool(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- pages ----------

@Composable
private fun ToolGrid(onOpenTool: (Tool) -> Unit, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun LazyGridScope.section(category: ToolCategory, tools: List<Tool>, onOpenTool: (Tool) -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "header-${category.name}") { CategoryHeader(category, tools.size) }
    items(tools, key = { it.id }) { ToolCard(it) { onOpenTool(it) } }
}

@Composable
private fun RecentPage(recent: List<Tool>, onOpenTool: (Tool) -> Unit) {
    if (recent.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.History, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Tools you open will show up here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    ToolGrid(onOpenTool) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "recent-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recently used", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { AppSettings.clearRecents() }) { Text("Clear") }
            }
        }
        items(recent, key = { "recent-${it.id}" }) { ToolCard(it, showCategory = true) { onOpenTool(it) } }
    }
}

@Composable
private fun SearchResults(query: String, results: List<Tool>, onOpenTool: (Tool) -> Unit) {
    val state = rememberLazyGridState()
    LaunchedEffect(query) { state.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (results.isEmpty()) "No tools match \"$query\"" else "${results.size} result" + if (results.size == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(results, key = { it.id }) { ToolCard(it, showCategory = true) { onOpenTool(it) } }
    }
}

// ---------- chips, drawer, search ----------

/** Chip row that mirrors the pager: tap to jump, and it follows when you swipe. */
@Composable
private fun PageChips(pages: List<HomePage>, current: Int, onSelect: (Int) -> Unit) {
    val rowState = rememberLazyListState()
    LaunchedEffect(current) {
        // Keep the selected chip in view, with its neighbour peeking on the left.
        rowState.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(pages, key = { _, p -> p.key }) { i, page ->
            val selected = i == current
            when (page) {
                is HomePage.Category -> FilterChip(
                    selected = selected, onClick = { onSelect(i) }, label = { Text(page.title) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(page.category))) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = categoryContainer(page.category)),
                )
                HomePage.Recent -> FilterChip(
                    selected = selected, onClick = { onSelect(i) }, label = { Text(page.title) },
                    leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                )
                HomePage.All -> FilterChip(selected = selected, onClick = { onSelect(i) }, label = { Text(page.title) })
            }
        }
    }
}

@Composable
private fun HomeDrawer(pages: List<HomePage>, current: String?, onPage: (String) -> Unit, onSettings: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Text("ToolX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            // The category list can be long; let it scroll above a fixed Settings entry.
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                items(pages.size, key = { pages[it].key }) { i ->
                    val page = pages[i]
                    NavigationDrawerItem(
                        selected = page.key == current,
                        onClick = { onPage(page.key) },
                        label = { Text(if (page == HomePage.All) "All tools" else page.title) },
                        icon = {
                            when (page) {
                                HomePage.Recent -> Icon(Icons.Rounded.History, null)
                                HomePage.All -> Icon(Icons.Rounded.Apps, null)
                                is HomePage.Category -> Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(page.category)))
                                }
                            }
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                selected = false,
                onClick = onSettings,
                label = { Text("Settings") },
                icon = { Icon(Icons.Rounded.Settings, null) },
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun BottomSearch(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                // Above the gesture bar normally, above the keyboard while typing (whichever is taller).
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            placeholder = { Text("Search all tools") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange(""); focus.clearFocus() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

// ---------- cards ----------

@Composable
private fun CategoryHeader(category: ToolCategory, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(categoryColor(category)))
        Spacer(Modifier.width(10.dp))
        Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tool tile; [showCategory] adds a coloured category label (Recent and search mix categories). */
@Composable
private fun ToolCard(tool: Tool, showCategory: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp).heightIn(min = 112.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(categoryContainer(tool.category)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tool.icon, contentDescription = null, tint = categoryColor(tool.category))
                }
                Spacer(Modifier.weight(1f))
                if (!tool.available) {
                    Text(
                        "Soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).background(colors.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else if (showCategory) {
                    Text(
                        tool.category.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor(tool.category),
                        modifier = Modifier.clip(CircleShape).background(categoryContainer(tool.category)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(tool.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
