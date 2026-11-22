package com.toolx.app.tools.pdf

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.ImageEngine
import com.toolx.app.tools.image.OutputFormat
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.UriListSaver
import com.toolx.app.ui.common.copyToClipboard
import com.toolx.app.ui.common.rememberMultiMediaPicker
import com.toolx.app.ui.common.shareText
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt

// ---------- PDF to images ----------

@Composable
fun PdfToImagesScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var dpi by rememberSaveable { mutableStateOf(150) }
    var format by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }
    var all by rememberSaveable { mutableStateOf(true) }
    var rangeText by rememberSaveable { mutableStateOf("") }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val count = ready.pages.pageCount
        PageImage(ready.pages, 0, 140.dp)
        SectionLabel("Quality")
        OptionChips(listOf(72, 150, 300), dpi, label = { "$it dpi" + when (it) { 72 -> " · screen"; 150 -> " · standard"; else -> " · print" } }) { dpi = it; vm.reset() }
        SectionLabel("Format")
        OptionChips(listOf(OutputFormat.JPEG, OutputFormat.PNG), format, label = { it.label }) { format = it; vm.reset() }
        SectionLabel("Pages")
        OptionChips(listOf(true, false), all, label = { if (it) "All $count" else "Choose" }) { all = it; vm.reset() }
        val ranges = if (all) listOf(0 until count) else PdfOps.parsePageRanges(rangeText, count)
        if (!all) {
            TextInput(rangeText, { rangeText = it; vm.reset() }, "Pages, e.g. 1-3, 5")
            if (rangeText.isNotBlank() && ranges == null) Note("Use page numbers from 1 to $count.", error = true)
        }
        val pages = ranges?.flatMap { it.toList() }?.distinct().orEmpty()
        var px by remember(ready, dpi) { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(ready, dpi) {
            val s = ready.pages.size(0)
            px = (s.width / 72f * dpi).roundToInt() to (s.height / 72f * dpi).roundToInt()
        }
        px?.let { (w, h) -> Note("${pages.size} image(s), about $w×$h px each, saved to Download/ToolX/Image.") }
        ExportButton("Convert", state, enabled = pages.isNotEmpty()) {
            val p = pages; val d = dpi; val f = format
            vm.runAll { ctx, progress -> PdfEngine.toImages(ctx, ready.file, p, d, f, progress) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Images to PDF ----------

@Composable
fun ImagesToPdfScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var uris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>>(emptyList()) }
    var paper by rememberSaveable { mutableStateOf(PdfOps.PaperSize.A4) }
    var orientation by rememberSaveable { mutableStateOf(PdfOps.Orientation.AUTO) }
    var margin by rememberSaveable { mutableStateOf(PdfOps.Margin.SMALL) }
    val add = rememberMultiMediaPicker(PickType.IMAGE) { picked -> uris = uris + picked.filter { it !in uris }; vm.reset() }

    ToolScaffold(title = tool.title, onBack = onBack) {
        if (uris.isEmpty()) {
            PickPrompt("Choose photos", Icons.Rounded.AddPhotoAlternate, onClick = add)
            Note("Each photo becomes one page, in the order shown.")
            return@ToolScaffold
        }
        // Live layout preview of the first page.
        var first by remember(uris.first()) { mutableStateOf<Size?>(null) }
        LaunchedEffect(uris.first()) { first = ImageEngine.readSize(context, uris.first()) }
        first?.let { s ->
            val (pw, ph) = PdfOps.pageFor(paper, orientation, s.width, s.height, margin.pt)
            val box = PdfOps.fitImage(s.width, s.height, pw, ph, margin.pt)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val maxH = 300.dp
                var w = maxWidth * 0.7f
                var h = w * (ph / pw)
                if (h > maxH) { h = maxH; w = h * (pw / ph) }
                Box(Modifier.size(w, h).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline)) {
                    AsyncImage(
                        uris.first(), contentDescription = "Page 1 preview", contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(start = w * (box.x / pw), top = h * ((ph - box.y - box.h) / ph))
                            .size(w * (box.w / pw), h * (box.h / ph)),
                    )
                }
            }
            Note("Page 1 of ${uris.size} · ${(pw / 72f * 25.4f).roundToInt()} × ${(ph / 72f * 25.4f).roundToInt()} mm")
        }
        SectionLabel("Page size")
        OptionChips(PdfOps.PaperSize.entries, paper, label = { it.label }) { paper = it; vm.reset() }
        if (paper != PdfOps.PaperSize.FIT) {
            SectionLabel("Orientation")
            OptionChips(PdfOps.Orientation.entries, orientation, label = { it.label }) { orientation = it; vm.reset() }
        }
        SectionLabel("Margin")
        OptionChips(PdfOps.Margin.entries, margin, label = { it.label }) { margin = it; vm.reset() }

        SectionLabel(if (uris.size == 1) "1 photo" else "${uris.size} photos")
        uris.forEachIndexed { i, uri -> key(uri) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                    Text("Page ${i + 1}", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    ReorderControls(i, uris.lastIndex, { d -> uris = uris.moved(i, d); vm.reset() }, { uris = uris - uri; vm.reset() })
                }
            }
        } }
        OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Text("Add more", Modifier.padding(start = 8.dp))
        }
        ExportButton("Create PDF", state) {
            val list = uris; val p = paper; val o = orientation; val m = margin
            vm.run { ctx, progress -> PdfEngine.imagesToPdf(ctx, list, p, o, m, progress) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- PDF to text / Markdown ----------

@Composable
fun PdfToTextScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var markdown by rememberSaveable { mutableStateOf(false) }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        var text by remember(ready, markdown) { mutableStateOf<String?>(null) }
        var error by remember(ready, markdown) { mutableStateOf<String?>(null) }
        LaunchedEffect(ready, markdown) {
            try { text = PdfEngine.extractText(context, ready.file, markdown) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { error = e.message ?: "Couldn't read the text" }
        }
        OptionChips(listOf(false, true), markdown, label = { if (it) "Markdown" else "Plain text" }) { markdown = it; vm.reset() }
        if (markdown) Note("Headings come from bigger fonts, plus bullets and paragraphs. Tables and columns stay as plain lines.")
        val t = text
        when {
            error != null -> Note(error!!, error = true)
            t == null -> Busy("Reading text…")
            t.isBlank() -> Note("No text found. This PDF is probably scanned pages (images), which need OCR.", error = true)
            else -> {
                val words = remember(t) { t.split(Regex("\\s+")).count { it.isNotBlank() } }
                Text("$words words · ${t.length} characters", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    val shown = if (t.length > PREVIEW_CHARS) t.take(PREVIEW_CHARS) + "\n\n… (${t.length - PREVIEW_CHARS} more characters in the saved file)" else t
                    SelectionContainer {
                        Text(
                            shown,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (markdown) FontFamily.Monospace else FontFamily.Default,
                            modifier = Modifier.heightIn(max = 420.dp).fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyToClipboard(context, t.take(CLIPBOARD_CHARS)) }) { Text("Copy") }
                    OutlinedButton(onClick = { shareText(context, t.take(CLIPBOARD_CHARS)) }) { Text("Share") }
                }
                if (t.length > CLIPBOARD_CHARS) Note("Copy and Share include the first ${CLIPBOARD_CHARS / 1000}k characters; save the file for everything.")
                ExportButton(if (markdown) "Save as .md" else "Save as .txt", state) {
                    vm.run { ctx, _ -> PdfEngine.saveText(ctx, t, markdown) }
                }
                ProcessingStatus(state, onCancel = vm::cancel)
            }
        }
    }
}

private const val PREVIEW_CHARS = 20_000
/** Android's clipboard and share intents fail past roughly 1 MB of data. */
private const val CLIPBOARD_CHARS = 200_000
