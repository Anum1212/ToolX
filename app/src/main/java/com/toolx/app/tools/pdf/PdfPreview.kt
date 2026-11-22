package com.toolx.app.tools.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.SizeF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberDisplayName
import com.toolx.app.ui.common.rememberMediaPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders pages of one PDF with the platform PdfRenderer, which allows only one open page at
 * a time, hence the lock. Recently rendered bitmaps are cached by page and width.
 */
class PdfPages(val file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    val pageCount: Int = renderer.pageCount
    private val sizes = arrayOfNulls<SizeF>(pageCount)
    @Volatile private var closed = false

    /** Page size in points as the reader sees it (rotation applied). */
    suspend fun size(index: Int): SizeF = sizes[index] ?: lock.withLock {
        withContext(Dispatchers.IO) { renderer.openPage(index).use { SizeF(it.width.toFloat(), it.height.toFloat()) } }
    }.also { sizes[index] = it }

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return it }
        return lock.withLock {
            if (closed) return@withLock null
            withContext(Dispatchers.IO) {
                renderer.openPage(index).use { p ->
                    sizes[index] = SizeF(p.width.toFloat(), p.height.toFloat())
                    PdfEngine.renderPage(p, dpi = 72 * widthPx / maxOf(1, p.width), maxSide = 2400)
                }
            }
        }?.also { cache.put(key, it) }
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }
}

sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val file: File, val pages: PdfPages) : PdfState
    /** Needs a password to open; only Unlock can work with it. */
    data class Locked(val file: File) : PdfState
    /** Couldn't be opened; [file] is the local copy if there is one (Repair works on it). */
    data class Failed(val message: String, val file: File?) : PdfState
}

/** Copies the picked PDF locally and opens it for rendering; closes the renderer when done. */
@Composable
fun rememberPdf(uri: Uri): PdfState {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf<PdfState>(PdfState.Loading) }
    LaunchedEffect(uri) {
        var file: File? = null
        state = try {
            file = PdfEngine.localCopy(context, uri)
            val info = PdfEngine.info(file)
            if (info.locked) PdfState.Locked(file) else PdfState.Ready(file, withContext(Dispatchers.IO) { PdfPages(file) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PdfState.Failed("This PDF couldn't be opened; it may be damaged. Try Repair PDF.", file)
        }
    }
    DisposableEffect(state) {
        val s = state
        onDispose { (s as? PdfState.Ready)?.pages?.close() }
    }
    return state
}

/** A page rendered to fit [width]; [extraRotation] turns it on screen (clockwise degrees). */
@Composable
fun PageImage(pages: PdfPages, index: Int, width: Dp, modifier: Modifier = Modifier, extraRotation: Int = 0) {
    val px = with(LocalDensity.current) { width.roundToPx() }
    var bmp by remember(pages, index, px) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pages, index, px) { bmp = pages.render(index, px) }
    val b = bmp
    val aspect = b?.let { it.width.toFloat() / it.height } ?: (1f / 1.414f)
    val sideways = PdfOps.normalizeRotation(extraRotation) % 180 != 0
    Box(
        modifier.width(width).aspectRatio(if (sideways) 1f / aspect else aspect),
        contentAlignment = Alignment.Center,
    ) {
        if (b == null) {
            Box(Modifier.fillMaxSize().background(Color.White))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                // Rotation doesn't change layout, so a sideways page is laid out at its unrotated
                // shape (box height × box width) and then turned to fill the box exactly.
                modifier = Modifier
                    .then(if (sideways) Modifier.requiredSize(width * aspect, width) else Modifier.fillMaxSize())
                    .graphicsLayer { rotationZ = extraRotation.toFloat() },
            )
        }
    }
}

/**
 * Grid of page thumbnails. [label] puts a badge under each page; [highlight] outlines it;
 * long documents show the first [initial] pages until "Show all" is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageGrid(
    pages: PdfPages,
    order: List<Int> = (0 until pages.pageCount).toList(),
    thumbWidth: Dp = 96.dp,
    initial: Int = 48,
    rotationOf: (Int) -> Int = { 0 },
    highlight: (Int) -> Boolean = { false },
    dimmed: (Int) -> Boolean = { false },
    label: (pos: Int, page: Int) -> String = { _, page -> "${page + 1}" },
    onClick: ((pos: Int, page: Int) -> Unit)? = null,
    footer: (@Composable (pos: Int, page: Int) -> Unit)? = null,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) order else order.take(initial)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEachIndexed { pos, page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val outline = if (highlight(page)) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                // Every cell is portrait-sized, so landscape pages don't shift the buttons
                // under them (and taps don't land on the wrong page after a delete).
                Box(Modifier.size(thumbWidth, thumbWidth * 1.42f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(outline, RoundedCornerShape(6.dp))
                            .then(if (onClick != null) Modifier.clickable { onClick(pos, page) } else Modifier)
                            .graphicsLayer { alpha = if (dimmed(page)) 0.3f else 1f },
                    ) {
                        PageImage(pages, page, thumbWidth, extraRotation = rotationOf(page))
                    }
                }
                Text(label(pos, page), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.width(thumbWidth).padding(top = 2.dp))
                footer?.invoke(pos, page)
            }
        }
    }
    if (order.size > initial) {
        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all ${order.size} pages") }
    }
}

/** "‹ Page 3 of 12 ›" control for tools that work on one page at a time. */
@Composable
fun PageStepper(index: Int, count: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onChange(index - 1) }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous page")
        }
        Text("Page ${index + 1} of $count", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onChange(index + 1) }, enabled = index < count - 1) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next page")
        }
    }
}

@Composable
fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 24.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shared frame: pick one PDF, open it, then show [content]. A password-protected file is
 * passed through only when [acceptLocked] (Unlock); other tools explain what to do.
 */
@Composable
fun PdfToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    onNewFile: () -> Unit = {},
    acceptLocked: Boolean = false,
    acceptBroken: Boolean = false,
    content: @Composable ColumnScope.(uri: Uri, state: PdfState) -> Unit,
) {
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pick = rememberMediaPicker(PickType.PDF) { uri = it; onNewFile() }
    ToolScaffold(title = tool.title, onBack = onBack) {
        val current = uri
        if (current == null) {
            PickPrompt("Choose a PDF", Icons.Rounded.PictureAsPdf, onClick = pick)
            Note("Everything happens on your phone. Results are saved to Download/ToolX/PDF.")
            return@ToolScaffold
        }
        val name = rememberDisplayName(current)
        val state = rememberPdf(current)
        val pagesText = (state as? PdfState.Ready)?.pages?.pageCount?.let { "$it page" + if (it == 1) "" else "s" }
        Text(name ?: "PDF", style = MaterialTheme.typography.titleSmall)
        SelectedInfo(current, if (state is PdfState.Locked) "password-protected" else pagesText, onChange = pick)
        when (state) {
            PdfState.Loading -> Busy("Opening…")
            is PdfState.Failed -> if (acceptBroken && state.file != null) content(current, state) else Note(state.message, error = true)
            is PdfState.Locked -> if (acceptLocked) content(current, state) else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Note("This PDF needs a password to open. Remove it first with Unlock PDF, then come back.", error = true)
                }
            }
            is PdfState.Ready -> content(current, state)
        }
    }
}

/** Width in px a [Dp] maps to; handy for render calls outside composition. */
@Composable
fun Dp.px(): Int = with(LocalDensity.current) { roundToPx() }
