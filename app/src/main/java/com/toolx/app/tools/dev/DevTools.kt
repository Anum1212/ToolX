package com.toolx.app.tools.dev

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import java.math.BigInteger

object DevTools : ToolModule {
    override val tools = listOf(
        Tool(
            "dev_base", "Base converter", "Binary, octal, decimal, hex, any base", Icons.Rounded.DataArray, ToolCategory.DEV,
            available = true, keywords = listOf("binary", "hex", "hexadecimal", "octal", "decimal", "radix", "base"),
        ),
    )
    override val screens: Map<String, ToolScreenFn> = mapOf("dev_base" to { tool, back -> BaseConverterScreen(tool, back) })
}

private val bases = listOf(2 to "Binary", 8 to "Octal", 10 to "Decimal", 16 to "Hex")

/** Groups digits from the right (e.g. nibbles for binary) for readability. */
private fun group(s: String, size: Int): String {
    val neg = s.startsWith("-")
    val digits = s.removePrefix("-")
    val grouped = digits.reversed().chunked(size).joinToString(" ").reversed()
    return (if (neg) "-" else "") + grouped
}

@Composable
private fun BaseConverterScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("255") }
    var from by rememberSaveable { mutableIntStateOf(10) }
    var custom by rememberSaveable { mutableStateOf("36") }
    val customBase = custom.toIntOrNull()?.takeIf { it in 2..36 }
    val cleaned = input.trim().replace(" ", "").replace("_", "").let {
        // Accept common prefixes like 0x, 0b, 0o.
        when {
            from == 16 && it.startsWith("0x", true) -> it.substring(2)
            from == 2 && it.startsWith("0b", true) -> it.substring(2)
            from == 8 && it.startsWith("0o", true) -> it.substring(2)
            else -> it
        }
    }
    val value = runCatching { BigInteger(cleaned, from) }.getOrNull()

    ToolScaffold(tool.title, onBack) {
        SectionLabel("Input base")
        OptionChips(bases + listOfNotNull(customBase?.takeIf { b -> bases.none { it.first == b } }?.let { it to "Base $it" }), bases.firstOrNull { it.first == from } ?: (from to "Base $from"), label = { it.second }) { from = it.first }
        TextInput(input, { input = it }, "Number in base $from")
        if (value == null && cleaned.isNotEmpty()) Note("That isn't a valid base-$from number.", error = true)
        if (value != null) {
            ResultCard(
                listOfNotNull(
                    "Binary" to group(value.toString(2), 4),
                    "Octal" to value.toString(8),
                    "Decimal" to value.toString(10),
                    "Hex" to group(value.toString(16).uppercase(), 4),
                    customBase?.let { "Base $it" to value.toString(it).uppercase() },
                    "Bits needed" to (value.abs().bitLength().coerceAtLeast(1)).toString(),
                ),
            )
            Note("Tap a row to copy it. Binary and hex are grouped in fours for readability.")
        }
        NumberField(custom, { custom = it }, "Custom base (2–36)", integerOnly = true, allowNegative = false)
    }
}
