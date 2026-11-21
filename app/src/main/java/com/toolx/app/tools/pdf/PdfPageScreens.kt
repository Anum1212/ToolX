package com.toolx.app.tools.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.CropCanvas
import com.toolx.app.tools.image.CropRect
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.UriListSaver
import com.toolx.app.ui.common.rememberDisplayName
import com.toolx.app.ui.common.rememberMultiMediaPicker
import kotlin.math.roundToInt

// ---------- Merge ----------

/** Up / down / remove controls shared by the file lists of Merge and Images to PDF. */
@Composable
internal fun ReorderControls(index: Int, last: Int, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
    IconButton(onClick = { onMove(1) }, enabled = index < last) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") }
}

internal fun <T> List<T>.moved(index: Int, delta: Int): List<T> = toMutableList().apply { add(index + delta, removeAt(index)) }

@Composable
fun PdfMergeScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var uris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>>(emptyList()) }
    val counts = remember { mutableStateMapOf<Uri, Int>() }
    val add = rememberMultiMediaPicker(PickType.PDF) { picked -> uris = uris + picked.filter { it !in uris }; vm.reset() }

    ToolScaffold(title = tool.title, onBack = onBack) {
        if (uris.isEmpty()) {
            PickPrompt("Choose PDFs", Icons.Rounded.LibraryAdd, onClick = add)
            Note("They're joined in the order shown; you can reorder them next.")
            return@ToolScaffold
        }
        val total = uris.sumOf { counts[it] ?: 0 }
        Text("${uris.size} files · $total pages", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        uris.forEachIndexed { i, uri -> key(uri) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val pdf = rememberPdf(uri)
                    when (pdf) {
                        is PdfState.Ready -> {
                            LaunchedEffect(pdf) { counts[uri] = pdf.pages.pageCount }
                            PageImage(pdf.pages, 0, 44.dp)
                        }
                        else -> Icon(Icons.Rounded.SwapVert, null, Modifier.size(44.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(rememberDisplayName(uri) ?: "PDF ${i + 1}", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            when (pdf) {
                                is PdfState.Ready -> "${pdf.pages.pageCount} pages"
                                is PdfState.Locked -> "Password-protected: unlock it first"
                                is PdfState.Failed -> "Can't open: try Repair PDF"
                                PdfState.Loading -> "Opening…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pdf is PdfState.Locked || pdf is PdfState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ReorderControls(i, uris.lastIndex, { d -> uris = uris.moved(i, d); vm.reset() }, { uris = uris - uri; counts.remove(uri); vm.reset() })
                }
            }
        } }
        OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Text("Add more", Modifier.padding(start = 8.dp))
        }
        ExportButton("Merge ${uris.size} PDFs", state, enabled = uris.size >= 2) {
            val list = uris
            vm.run { ctx, _ -> PdfEngine.merge(ctx, list.map { PdfEngine.localCopy(ctx, it) }) }
        }
        if (uris.size < 2) Note("Add at least two PDFs.")
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Split ----------

@Composable
fun PdfSplitScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var mode by rememberSaveable { mutableStateOf(PdfOps.SplitMode.RANGES) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var everyText by rememberSaveable { mutableStateOf("1") }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val count = ready.pages.pageCount
        SectionLabel("How to split")
        OptionChips(PdfOps.SplitMode.entries, mode, label = {
            when (it) {
                PdfOps.SplitMode.RANGES -> "By ranges"
                PdfOps.SplitMode.EVERY_N -> "Every N pages"
                PdfOps.SplitMode.EXTRACT -> "Extract pages"
            }
        }) { mode = it; vm.reset() }

        val ranges = PdfOps.parsePageRanges(rangesText, count)
        if (mode == PdfOps.SplitMode.EVERY_N) {
            NumberField(everyText, { everyText = it; vm.reset() }, "Pages per file", integerOnly = true, allowNegative = false)
        } else {
            TextInput(rangesText, { rangesText = it; vm.reset() }, if (mode == PdfOps.SplitMode.RANGES) "Ranges, e.g. 1-3, 4-6, 7-" else "Pages, e.g. 1, 3, 5-8")
            if (rangesText.isNotBlank() && ranges == null) Note("Use page numbers from 1 to $count, like 1-3, 5, 8-.", error = true)
        }
        val groups = PdfOps.splitGroups(mode, count, ranges, everyText.toIntOrNull() ?: 0)
        val fileOf = remember(groups) {
            HashMap<Int, Int>().apply { groups?.forEachIndexed { k, g -> g.forEach { putIfAbsent(it, k) } } }
        }
        groups?.let { g ->
            Text(
                if (g.size == 1) "Creates 1 PDF with ${g[0].size} pages" else "Creates ${g.size} PDFs",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
        PageGrid(
            ready.pages,
            dimmed = { groups != null && it !in fileOf },
            highlight = { it in fileOf },
            label = { _, p -> fileOf[p]?.let { k -> if ((groups?.size ?: 0) > 1) "${p + 1} → file ${k + 1}" else "${p + 1}" } ?: "${p + 1}" },
        )
        ExportButton("Split", state, enabled = groups != null) {
            val g = groups ?: return@ExportButton
            vm.runAll { ctx, _ -> PdfEngine.split(ctx, ready.file, g) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Rotate ----------

@Composable
fun PdfRotateScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val rotations = remember { mutableStateMapOf<Int, Int>() }
    val selected = remember { mutableStateListOf<Int>() }

    PdfToolFrame(tool, onBack, onNewFile = { rotations.clear(); selected.clear(); vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val targets = if (selected.isEmpty()) (0 until ready.pages.pageCount).toList() else selected.toList()
        fun turn(deg: Int) { targets.forEach { rotations[it] = PdfOps.normalizeRotation((rotations[it] ?: 0) + deg) }; vm.reset() }
        Note(if (selected.isEmpty()) "Rotating all pages. Tap pages to rotate only some." else "Rotating ${selected.size} selected page(s).")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { turn(270) }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.RotateLeft, null); Text(" Left") }
            FilledTonalButton(onClick = { turn(90) }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.RotateRight, null); Text(" Right") }
        }
        PageGrid(
            ready.pages,
            rotationOf = { rotations[it] ?: 0 },
            highlight = { it in selected },
            onClick = { _, p -> if (p in selected) selected.remove(p) else selected.add(p) },
        )
        val changed = rotations.filterValues { it != 0 }
        ExportButton("Save", state, enabled = changed.isNotEmpty()) {
            val r = changed.toMap()
            vm.run { ctx, _ -> PdfEngine.rotate(ctx, ready.file, r) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Organize ----------

@Composable
fun PdfOrganizeScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val order = remember { mutableStateListOf<PageEdit>() }

    PdfToolFrame(tool, onBack, onNewFile = { order.clear(); vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val count = ready.pages.pageCount
        LaunchedEffect(ready) { if (order.isEmpty()) order.addAll((0 until count).map { PageEdit(it) }) }
        val deleted = count - order.size
        Note("Move, rotate or delete pages with the buttons under each one.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { order.reverse(); vm.reset() }) { Text("Reverse order") }
            OutlinedButton(onClick = { order.clear(); order.addAll((0 until count).map { PageEdit(it) }); vm.reset() }) { Text("Reset") }
        }
        val rotationOf = order.associate { it.source to it.rotation }
        PageGrid(
            ready.pages,
            order = order.map { it.source },
            rotationOf = { rotationOf[it] ?: 0 },
            label = { pos, p -> if (pos != p) "${pos + 1} (was ${p + 1})" else "${pos + 1}" },
            footer = { pos, _ ->
                Row {
                    IconButton(onClick = { val e = order.removeAt(pos); order.add(pos - 1, e); vm.reset() }, enabled = pos > 0, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.ChevronLeft, "Move earlier")
                    }
                    IconButton(onClick = { order[pos] = order[pos].let { it.copy(rotation = PdfOps.normalizeRotation(it.rotation + 90)) }; vm.reset() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate")
                    }
                    IconButton(onClick = { order.removeAt(pos); vm.reset() }, enabled = order.size > 1, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Delete, "Delete page")
                    }
                    IconButton(onClick = { val e = order.removeAt(pos); order.add(pos + 1, e); vm.reset() }, enabled = pos < order.lastIndex, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.ChevronRight, "Move later")
                    }
                }
            },
        )
        val unchanged = order.size == count && order.withIndex().all { (i, e) -> e.source == i && e.rotation == 0 }
        Text(
            "${order.size} pages" + (if (deleted > 0) " · $deleted deleted" else ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        )
        ExportButton("Save", state, enabled = !unchanged && order.isNotEmpty()) {
            val o = order.toList()
            vm.run { ctx, _ -> PdfEngine.organize(ctx, ready.file, o) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Crop ----------

@Composable
fun PdfCropScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf<CropRect?>(null) }
    var allPages by rememberSaveable { mutableStateOf(true) }

    PdfToolFrame(tool, onBack, onNewFile = { page = 0; crop = null; vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val widthPx = 1000
        var bmp by remember(ready, page) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(ready, page) { bmp = ready.pages.render(page, widthPx) }
        // Start trimmed to the content, which is what "crop" usually means. For all pages the box
        // must cover every page's content (up to 20 pages sampled), or it would cut some off.
        val scope = rememberCoroutineScope()
        fun bounds(b: Bitmap): FloatArray? {
            val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            return PdfOps.contentBounds(px, b.width, b.height)
        }
        fun autoTrim() = scope.launch {
            val pagesToCheck = if (allPages) (0 until minOf(ready.pages.pageCount, 20)) else (page..page)
            var u: FloatArray? = null
            for (i in pagesToCheck) {
                val b = ready.pages.render(i, 400) ?: continue
                val r = bounds(b) ?: continue
                u = u?.let { floatArrayOf(minOf(it[0], r[0]), minOf(it[1], r[1]), maxOf(it[2], r[2]), maxOf(it[3], r[3])) } ?: r
            }
            crop = u?.let { CropRect(it[0], it[1], it[2], it[3]) } ?: CropRect.Full
            vm.reset()
        }
        LaunchedEffect(ready) { if (crop == null) autoTrim() }
        if (ready.pages.pageCount > 1) PageStepper(page, ready.pages.pageCount) { page = it }
        val b = bmp
        val c = crop
        if (b == null || c == null) Busy("Rendering page…") else {
            CropCanvas(b, Size(b.width, b.height), c, ratio = null, onCropChange = { crop = it; vm.reset() })
            OutlinedButton(onClick = { autoTrim() }) { Text(if (allPages) "Trim to content of all pages" else "Trim to this page's content") }
            var mm by remember(ready, page) { mutableStateOf<Pair<Int, Int>?>(null) }
            LaunchedEffect(ready, page, c) {
                val s = ready.pages.size(page)
                mm = ((s.width * c.width) / 72f * 25.4f).roundToInt() to ((s.height * c.height) / 72f * 25.4f).roundToInt()
            }
            mm?.let { Note("New page size: ${it.first} × ${it.second} mm. Drag the corners; drag inside to move.") }
        }
        SectionLabel("Apply to")
        OptionChips(listOf(true, false), allPages, label = { if (it) "All pages" else "This page only" }) { allPages = it; vm.reset() }
        if (allPages) Note("The same box is used on every page, so check pages with different layouts.")
        Note("Cropping hides the outside area in every viewer; the content is still in the file. Use Redact to remove content.")
        ExportButton("Crop", state, enabled = c != null && c != CropRect.Full) {
            val box = c ?: return@ExportButton
            val pages = if (allPages) null else setOf(page)
            vm.run { ctx, _ -> PdfEngine.crop(ctx, ready.file, box, pages) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}
