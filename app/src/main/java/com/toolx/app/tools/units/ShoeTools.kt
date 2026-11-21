package com.toolx.app.tools.units

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.tools.units.ShoeSizes.System
import com.toolx.app.ui.common.Dropdown
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.parseNum

/** Shoe size converter, listed under Units. */
object ShoeTools : ToolModule {
    override val tools = listOf(
        Tool(
            "unit_shoe", "Shoe size", "US, UK, EU, Japan, kids and more", Icons.Rounded.DirectionsWalk, ToolCategory.UNITS, true,
            listOf("shoe", "size", "footwear", "us", "uk", "eu", "mondopoint", "kids", "convert", "foot"),
        ),
    )
    override val screens: Map<String, ToolScreenFn> = mapOf("unit_shoe" to { tool, back -> ShoeScreen(tool, back) })
}

private fun System.title() = if (unit.isNotEmpty() && this != System.MONDOPOINT && this != System.JAPAN) "$label ($unit)" else label

@Composable
private fun ShoeScreen(tool: Tool, onBack: () -> Unit) {
    var from by rememberSaveable { mutableStateOf(System.UK) }
    var value by rememberSaveable { mutableStateOf("7") }
    ToolScaffold(tool.title, onBack) {
        Dropdown("I know my size in", System.entries, from, { it.title() }, { from = it })
        NumberField(value, { value = it }, "Size", suffix = from.unit.ifEmpty { null }, allowNegative = false)
        val v = parseNum(value)
        val foot = v?.let { ShoeSizes.toFoot(from, it) }
        if (foot == null || foot !in 5.0..40.0) {
            if (value.isNotBlank()) Note("That size is outside the usual range.", error = true)
        } else {
            System.entries.groupBy { it.group }.forEach { (group, systems) ->
                val rows = systems.filter { it != from }.mapNotNull { s -> ShoeSizes.display(s, foot)?.let { s.label to it } }
                if (rows.isNotEmpty()) ResultCard(rows, title = group)
            }
            Note(
                "Converted through foot length with the standard sizing formulas. Brands differ by about half a size, " +
                    "so measure your foot (heel to longest toe) when in doubt. Kids' sizes show only for small feet.",
            )
        }
    }
}
