package com.toolx.app.tools.text

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextRotationNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.ChipRow
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import com.toolx.app.ui.common.shareText

object TextTools : ToolModule {

    private fun t(id: String, title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.TEXT, available = true, keywords = kw.toList() + "text")

    override val tools = listOf(
        t("text_case", "Text cases", "UPPER, lower, Title, camelCase…", Icons.Rounded.TextFields, "case", "upper", "lower", "title", "camel", "snake"),
        t("text_find_replace", "Find & replace", "Replace words, with regex support", Icons.Rounded.FindReplace, "search", "regex", "substitute"),
        t("text_sort", "Sort lines", "A–Z, by length, numeric or shuffle", Icons.AutoMirrored.Rounded.Sort, "order", "alphabetical", "shuffle"),
        t("text_counter", "Text counter", "Characters, words, lines, reading time", Icons.Rounded.Numbers, "count", "words", "characters", "length"),
        t("text_repeat", "Repeat text", "Repeat text N times", Icons.Rounded.Repeat, "duplicate", "copy", "multiply"),
        t("text_spaces", "Remove white spaces", "Collapse, trim or strip spaces", Icons.Rounded.SpaceBar, "whitespace", "trim", "spaces"),
        t("text_empty_lines", "Remove empty lines", "Delete blank lines", Icons.Rounded.CleaningServices, "blank", "lines"),
        t("text_dup_lines", "Remove duplicate lines", "Keep each line once", Icons.Rounded.PlaylistRemove, "unique", "dedupe", "duplicates"),
        t("text_dup_words", "Remove duplicate words", "Keep each word once", Icons.Rounded.Spellcheck, "unique", "dedupe", "duplicates"),
        t("text_join_lines", "Remove line breaks", "Join lines into one", Icons.AutoMirrored.Rounded.ShortText, "join", "newline", "merge lines"),
        t("text_prefix_suffix", "Prefix / suffix", "Add text to the start or end of each line", Icons.Rounded.FormatSize, "prepend", "append", "lines"),
        t("text_number_lines", "Number lines", "Add 1. 2. 3. to each line", Icons.Rounded.FormatListNumbered, "numbering", "list", "lines"),
        t("text_reverse", "Reverse text", "Flip characters back to front", Icons.Rounded.SwapHoriz, "backwards", "mirror"),
        t("text_reverse_lines", "Reverse lines", "Last line first", Icons.Rounded.SwapVert, "backwards", "flip", "order"),
        t("text_reverse_words", "Reverse words", "Last word first", Icons.Rounded.TextRotationNone, "backwards", "flip"),
        t("text_add_breaks", "Add line breaks", "Wrap by words, width or sentence", Icons.AutoMirrored.Rounded.WrapText, "wrap", "newline", "split"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "text_case" to { tool, back -> CaseScreen(tool, back) },
        "text_find_replace" to { tool, back -> FindReplaceScreen(tool, back) },
        "text_sort" to { tool, back -> SortScreen(tool, back) },
        "text_counter" to { tool, back -> CounterScreen(tool, back) },
        "text_repeat" to { tool, back -> RepeatScreen(tool, back) },
        "text_spaces" to { tool, back -> SpacesScreen(tool, back) },
        "text_empty_lines" to { tool, back -> EmptyLinesScreen(tool, back) },
        "text_dup_lines" to { tool, back -> DupLinesScreen(tool, back) },
        "text_dup_words" to { tool, back -> DupWordsScreen(tool, back) },
        "text_join_lines" to { tool, back -> JoinLinesScreen(tool, back) },
        "text_prefix_suffix" to { tool, back -> PrefixSuffixScreen(tool, back) },
        "text_number_lines" to { tool, back -> NumberLinesScreen(tool, back) },
        "text_reverse" to { tool, back -> TextToolFrame(tool, back, transform = TextOps::reverseText) },
        "text_reverse_lines" to { tool, back -> TextToolFrame(tool, back, transform = TextOps::reverseLines) },
        "text_reverse_words" to { tool, back -> ReverseWordsScreen(tool, back) },
        "text_add_breaks" to { tool, back -> AddBreaksScreen(tool, back) },
    )
}

// ---------- Shared frame ----------

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

/** Input box with Paste / Clear. */
@Composable
private fun TextInputBox(input: String, onInput: (String) -> Unit) {
    val context = LocalContext.current
    Column {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text("Text") },
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { clipboardText(context)?.let(onInput) }) { Text("Paste") }
            TextButton(onClick = { onInput("") }, enabled = input.isNotEmpty()) { Text("Clear") }
        }
    }
}

/**
 * Shared layout: input → options → live result. The result updates as you type,
 * so there's nothing to "run"; Copy/Share/Use-as-input act on it.
 */
@Composable
private fun TextToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    transform: (String) -> String,
    info: (input: String, output: String) -> String? = { _, _ -> null },
    options: @Composable () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    val output = transform(input)
    ToolScaffold(title = tool.title, onBack = onBack) {
        TextInputBox(input) { input = it }
        options()
        if (input.isNotEmpty()) OutputCard(output, info(input, output), onUseAsInput = { input = output })
    }
}

@Composable
private fun OutputCard(output: String, info: String?, onUseAsInput: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Result", style = MaterialTheme.typography.titleSmall)
            info?.let { Note(it) }
            SelectionContainer(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(output.ifEmpty { "(empty)" }, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Default)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { copyToClipboard(context, output) }) { Text("Copy") }
                OutlinedButton(onClick = { shareText(context, output) }) { Text("Share") }
                TextButton(onClick = onUseAsInput) { Text("Use as input") }
            }
        }
    }
}

// ---------- Screens ----------

@Composable
private fun CaseScreen(tool: Tool, onBack: () -> Unit) {
    var case by rememberSaveable { mutableStateOf(TextOps.Case.UPPER) }
    TextToolFrame(tool, onBack, transform = { TextOps.changeCase(it, case) }) {
        SectionLabel("Convert to")
        OptionChips(TextOps.Case.entries, case, label = { it.label }) { case = it }
    }
}

@Composable
private fun FindReplaceScreen(tool: Tool, onBack: () -> Unit) {
    var find by rememberSaveable { mutableStateOf("") }
    var replace by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var regex by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    fun run(s: String) = TextOps.findReplace(s, find, replace, matchCase, regex, wholeWord)
    TextToolFrame(
        tool, onBack,
        transform = { run(it).text },
        info = { input, _ ->
            val r = run(input)
            r.error?.let { "Pattern error: $it" } ?: if (find.isEmpty()) "Type something to find." else "${r.count} replacement${if (r.count == 1) "" else "s"}"
        },
    ) {
        TextInput(find, { find = it }, "Find")
        TextInput(replace, { replace = it }, "Replace with")
        ChipRow {
            ToggleChip("Match case", matchCase) { matchCase = it }
            ToggleChip("Whole word", wholeWord) { wholeWord = it }
            ToggleChip("Regex", regex) { regex = it }
        }
    }
}

@Composable
private fun SortScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TextOps.SortMode.AZ) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var seed by rememberSaveable { mutableLongStateOf(1L) }
    TextToolFrame(tool, onBack, transform = { TextOps.sortLines(it, mode, ignoreCase, seed) }) {
        OptionChips(TextOps.SortMode.entries, mode, label = { it.label }) { mode = it }
        ChipRow {
            ToggleChip("Ignore case", ignoreCase) { ignoreCase = it }
            if (mode == TextOps.SortMode.SHUFFLE) TextButton(onClick = { seed++ }) { Text("Shuffle again") }
        }
    }
}

@Composable
private fun CounterScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val s = TextOps.stats(input)
    fun mmss(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min ${sec % 60} s"
    ToolScaffold(title = tool.title, onBack = onBack) {
        TextInputBox(input) { input = it }
        ResultCard(
            listOf(
                "Characters" to s.characters.toString(),
                "Characters (no spaces)" to s.charactersNoSpaces.toString(),
                "Words" to s.words.toString(),
                "Sentences" to s.sentences.toString(),
                "Lines" to s.lines.toString(),
                "Paragraphs" to s.paragraphs.toString(),
                "Reading time" to mmss(s.readingSeconds),
                "Speaking time" to mmss(s.speakingSeconds),
            ),
        )
        Note("Tap a row to copy it. Reading time assumes 238 words per minute; speaking, 140.")
    }
}

private enum class Separator(val label: String, val value: String) {
    NEWLINE("New line", "\n"), SPACE("Space", " "), NONE("None", ""), COMMA("Comma", ", "), CUSTOM("Custom", "")
}

@Composable
private fun RepeatScreen(tool: Tool, onBack: () -> Unit) {
    var times by rememberSaveable { mutableStateOf("3") }
    var sep by rememberSaveable { mutableStateOf(Separator.NEWLINE) }
    var custom by rememberSaveable { mutableStateOf(" | ") }
    val n = times.toIntOrNull() ?: 0
    TextToolFrame(
        tool, onBack,
        transform = { TextOps.repeat(it, n, if (sep == Separator.CUSTOM) custom else sep.value) },
        info = { _, _ -> if (n > TextOps.MAX_REPEAT) "Capped at ${TextOps.MAX_REPEAT} repeats." else null },
    ) {
        NumberField(times, { times = it }, "Times", integerOnly = true, allowNegative = false)
        SectionLabel("Separator")
        OptionChips(Separator.entries, sep, label = { it.label }) { sep = it }
        if (sep == Separator.CUSTOM) TextInput(custom, { custom = it }, "Custom separator")
    }
}

@Composable
private fun SpacesScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TextOps.SpaceMode.COLLAPSE) }
    TextToolFrame(
        tool, onBack,
        transform = { TextOps.removeSpaces(it, mode) },
        info = { i, o -> "${i.length - o.length} characters removed" },
    ) {
        OptionChips(TextOps.SpaceMode.entries, mode, label = { it.label }) { mode = it }
    }
}

@Composable
private fun EmptyLinesScreen(tool: Tool, onBack: () -> Unit) {
    var blank by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(
        tool, onBack,
        transform = { TextOps.removeEmptyLines(it, blank) },
        info = { i, o -> "${i.lines().size - (if (o.isEmpty()) 0 else o.lines().size)} lines removed" },
    ) {
        ToggleChip("Also remove lines with only spaces", blank) { blank = it }
    }
}

@Composable
private fun DupLinesScreen(tool: Tool, onBack: () -> Unit) {
    var ignoreCase by rememberSaveable { mutableStateOf(false) }
    var trim by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(
        tool, onBack,
        transform = { TextOps.removeDuplicateLines(it, ignoreCase, trim).first },
        info = { i, _ -> "${TextOps.removeDuplicateLines(i, ignoreCase, trim).second} duplicates removed" },
    ) {
        ChipRow {
            ToggleChip("Ignore case", ignoreCase) { ignoreCase = it }
            ToggleChip("Ignore surrounding spaces", trim) { trim = it }
        }
    }
}

@Composable
private fun DupWordsScreen(tool: Tool, onBack: () -> Unit) {
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(
        tool, onBack,
        transform = { TextOps.removeDuplicateWords(it, ignoreCase).first },
        info = { i, _ -> "${TextOps.removeDuplicateWords(i, ignoreCase).second} duplicates removed" },
    ) {
        ToggleChip("Ignore case", ignoreCase) { ignoreCase = it }
    }
}

private enum class Joiner(val label: String, val value: String) { SPACE("Space", " "), NONE("Nothing", ""), COMMA("Comma", ", ") }

@Composable
private fun JoinLinesScreen(tool: Tool, onBack: () -> Unit) {
    var joiner by rememberSaveable { mutableStateOf(Joiner.SPACE) }
    var keepParagraphs by rememberSaveable { mutableStateOf(false) }
    TextToolFrame(tool, onBack, transform = { TextOps.removeLineBreaks(it, joiner.value, keepParagraphs) }) {
        SectionLabel("Join lines with")
        OptionChips(Joiner.entries, joiner, label = { it.label }) { joiner = it }
        ToggleChip("Keep paragraphs", keepParagraphs) { keepParagraphs = it }
    }
}

@Composable
private fun PrefixSuffixScreen(tool: Tool, onBack: () -> Unit) {
    var prefix by rememberSaveable { mutableStateOf("") }
    var suffix by rememberSaveable { mutableStateOf("") }
    var skipEmpty by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(tool, onBack, transform = { TextOps.prefixSuffix(it, prefix, suffix, skipEmpty) }) {
        TextInput(prefix, { prefix = it }, "Prefix (start of each line)")
        TextInput(suffix, { suffix = it }, "Suffix (end of each line)")
        ToggleChip("Skip empty lines", skipEmpty) { skipEmpty = it }
    }
}

@Composable
private fun NumberLinesScreen(tool: Tool, onBack: () -> Unit) {
    var start by rememberSaveable { mutableStateOf("1") }
    var style by rememberSaveable { mutableStateOf(TextOps.NumberStyle.DOT) }
    var skipEmpty by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(tool, onBack, transform = { TextOps.numberLines(it, start.toIntOrNull() ?: 1, style, skipEmpty) }) {
        NumberField(start, { start = it }, "Start at", integerOnly = true)
        SectionLabel("Style")
        OptionChips(TextOps.NumberStyle.entries, style, label = { it.label.trim() }) { style = it }
        ToggleChip("Skip empty lines", skipEmpty) { skipEmpty = it }
    }
}

@Composable
private fun ReverseWordsScreen(tool: Tool, onBack: () -> Unit) {
    var perLine by rememberSaveable { mutableStateOf(true) }
    TextToolFrame(tool, onBack, transform = { TextOps.reverseWords(it, perLine) }) {
        ToggleChip("Keep lines (reverse within each line)", perLine) { perLine = it }
    }
}

@Composable
private fun AddBreaksScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TextOps.BreakMode.WORDS) }
    var n by rememberSaveable { mutableIntStateOf(5) }
    var nText by rememberSaveable { mutableStateOf("5") }
    var char by rememberSaveable { mutableStateOf(",") }
    TextToolFrame(tool, onBack, transform = { TextOps.addBreaks(it, mode, n, char) }) {
        OptionChips(TextOps.BreakMode.entries, mode, label = { it.label }) { mode = it }
        when (mode) {
            TextOps.BreakMode.WORDS, TextOps.BreakMode.CHARS ->
                NumberField(nText, { nText = it; n = it.toIntOrNull()?.coerceIn(1, 10_000) ?: n }, if (mode == TextOps.BreakMode.WORDS) "Words per line" else "Characters per line", integerOnly = true, allowNegative = false)
            TextOps.BreakMode.CHARACTER -> TextInput(char, { char = it }, "Break after this character")
            TextOps.BreakMode.SENTENCES -> Unit
        }
    }
}
