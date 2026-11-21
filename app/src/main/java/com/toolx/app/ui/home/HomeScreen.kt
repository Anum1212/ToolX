package com.toolx.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (Tool) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ToolCategory?>(null) }
    val grouped = remember(query, category) {
        ToolRegistry.search(query, category).groupBy { it.category }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ToolX", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(query = query, onQueryChange = { query = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryChips(selected = category, onSelect = { category = it })
            }
            if (grouped.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No tools match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            grouped.forEach { (cat, tools) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${cat.name}") {
                    Text(
                        cat.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(tools, key = { it.id }) { tool ->
                    ToolCard(tool = tool, onClick = { onOpenTool(tool) })
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search tools") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun CategoryChips(selected: ToolCategory?, onSelect: (ToolCategory?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        ToolCategory.entries.forEach { cat ->
            FilterChip(selected = selected == cat, onClick = { onSelect(cat) }, label = { Text(cat.title) })
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, onContainer) = when (tool.category) {
        ToolCategory.VIDEO, ToolCategory.TEXT, ToolCategory.FINANCE -> colors.primaryContainer to colors.onPrimaryContainer
        ToolCategory.AUDIO, ToolCategory.UNITS, ToolCategory.RANDOM, ToolCategory.PDF -> colors.secondaryContainer to colors.onSecondaryContainer
        ToolCategory.IMAGE, ToolCategory.CALC, ToolCategory.GENERAL, ToolCategory.DEV -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .heightIn(min = 112.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tool.icon, contentDescription = null, tint = onContainer)
                }
                Spacer(Modifier.weight(1f))
                if (!tool.available) {
                    Text(
                        "Soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
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
