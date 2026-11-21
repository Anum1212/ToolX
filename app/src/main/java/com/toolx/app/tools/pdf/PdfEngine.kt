package com.toolx.app.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.image.CropRect
import com.toolx.app.tools.image.ImageEngine
import com.toolx.app.tools.image.OutputFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Basic facts about a picked PDF. [locked] = needs a password to open. */
data class PdfInfo(val pageCount: Int, val locked: Boolean)

/** One page in an Organize result: which source page, plus extra clockwise rotation. */
data class PageEdit(val source: Int, val rotation: Int = 0)

/** A rectangle drawn on the visible page, normalized 0..1 with a top-left origin. */
typealias VisualRect = CropRect

class PdfLockedException : Exception("This PDF is password-protected. Unlock it first with Unlock PDF.")

/**
 * PDF operations with PdfBox-Android. Every operation works on a local copy of the picked
 * file (PdfBox needs random access; content Uris don't give it) and runs off the main thread.
 */
internal object PdfEngine {

    private const val MIME = "application/pdf"
    private const val MARGIN = 24f // ≈ 8.5 mm from the page edge

    @Volatile private var ready = false

    fun init(context: Context) {
        if (!ready) synchronized(this) {
            if (!ready) { PDFBoxResourceLoader.init(context.applicationContext); ready = true }
        }
    }

    // ---------- files ----------

    /**
     * Copies [uri] into the cache; later calls reuse the copy. The key includes the size, so
     * a file that changed under the same Uri is copied again.
     */
    suspend fun localCopy(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val size = MediaSaver.sizeOf(context, uri) ?: -1L
        val file = File(dir, "in_${uri.toString().hashCode().toUInt()}_$size.pdf")
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(dir, "${file.name}.part")
            (context.contentResolver.openInputStream(uri) ?: error("Couldn't open the PDF")).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(file)
        }
        file
    }

    fun tempFile(context: Context, tag: String): File =
        File(File(context.cacheDir, "pdf").apply { mkdirs() }, "${tag}_${UUID.randomUUID()}.pdf")

    suspend fun info(file: File): PdfInfo = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { PdfInfo(it.pageCount, locked = false) }
            }
        }.getOrElse { e ->
            if (e is SecurityException) PdfInfo(0, locked = true) else throw e
        }
    }

    private fun load(context: Context, file: File, password: String? = null): PDDocument {
        init(context)
        return try {
            PDDocument.load(file, password ?: "", MemoryUsageSetting.setupMixed(64L * 1024 * 1024))
        } catch (e: InvalidPasswordException) {
            throw if (password == null) PdfLockedException() else IllegalArgumentException("Wrong password")
        }
    }

    private fun saveDoc(context: Context, doc: PDDocument, tag: String): SavedMedia =
        MediaSaver.write(context, MediaSaver.fileName(tag, "pdf"), MIME, MediaKind.DOCUMENT) { out -> doc.save(out) }

    private inline fun <T> edit(context: Context, file: File, block: (PDDocument) -> T): T =
        load(context, file).use(block)

    /**
     * Copies [page] into [dst]. Size, resources and rotation may be *inherited* from the
     * source's page tree, so they are copied explicitly (as PdfBox's own Splitter does);
     * otherwise pages can lose their size or fonts.
     */
    private fun copyPage(dst: PDDocument, page: PDPage): PDPage = dst.importPage(page).also {
        it.mediaBox = page.mediaBox
        it.cropBox = page.cropBox
        it.resources = usedResources(page)
        it.rotation = page.rotation
    }

    /**
     * The page's resources trimmed to the images/forms and fonts its content actually uses.
     * Many PDFs give every page one shared resource list; copied as-is, a split-off text page
     * would carry every photo of the original (seen: a 2-page split kept a 340 KB unused photo).
     * Other resource kinds (colour spaces, patterns, graphics states) are small and kept whole.
     */
    private fun usedResources(page: PDPage): PDResources? {
        val res = page.resources ?: return null
        val tokens = runCatching { PDFStreamParser(page).apply { parse() }.tokens }.getOrNull() ?: return res
        val xobjects = HashSet<COSName>()
        val fonts = HashSet<COSName>()
        tokens.forEachIndexed { i, t ->
            when ((t as? Operator)?.name) {
                "Do" -> (tokens.getOrNull(i - 1) as? COSName)?.let(xobjects::add)
                "Tf" -> (tokens.getOrNull(i - 2) as? COSName)?.let(fonts::add)
            }
        }
        val dict = COSDictionary(res.cosObject)
        fun keepOnly(key: COSName, used: Set<COSName>) {
            val sub = res.cosObject.getDictionaryObject(key) as? COSDictionary ?: return
            dict.setItem(key, COSDictionary().apply { used.forEach { n -> sub.getItem(n)?.let { setItem(n, it) } } })
        }
        keepOnly(COSName.XOBJECT, xobjects)
        keepOnly(COSName.FONT, fonts)
        return PDResources(dict)
    }

    // ---------- page tools ----------

    suspend fun merge(context: Context, files: List<File>): SavedMedia = withContext(Dispatchers.IO) {
        init(context)
        files.forEach { if (info(it).locked) throw PdfLockedException() }
        MediaSaver.write(context, MediaSaver.fileName("merged", "pdf"), MIME, MediaKind.DOCUMENT) { out ->
            PDFMergerUtility().apply {
                files.forEach { addSource(it) }
                destinationStream = out
                mergeDocuments(MemoryUsageSetting.setupMixed(64L * 1024 * 1024))
            }
        }
    }

    /** One output file per group of 0-based pages. */
    suspend fun split(context: Context, file: File, groups: List<List<Int>>): List<SavedMedia> = withContext(Dispatchers.IO) {
        edit(context, file) { src ->
            groups.mapIndexed { k, pages ->
                coroutineContext.ensureActive()
                PDDocument().use { out ->
                    pages.forEach { copyPage(out, src.getPage(it)) }
                    saveDoc(context, out, if (groups.size == 1) "extract" else "split_${k + 1}")
                }
            }
        }
    }

    /** Extra clockwise rotation per 0-based page (pages not in the map keep theirs). */
    suspend fun rotate(context: Context, file: File, rotations: Map<Int, Int>): SavedMedia = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            rotations.forEach { (i, deg) -> doc.getPage(i).let { it.rotation = PdfOps.normalizeRotation(it.rotation + deg) } }
            saveDoc(context, doc, "rotated")
        }
    }

    /** New page order (pages left out are deleted), each with an extra rotation. */
    suspend fun organize(context: Context, file: File, order: List<PageEdit>): SavedMedia = withContext(Dispatchers.IO) {
        require(order.isNotEmpty()) { "Keep at least one page" }
        edit(context, file) { src ->
            PDDocument().use { out ->
                order.forEach { e ->
                    val page = copyPage(out, src.getPage(e.source))
                    page.rotation = PdfOps.normalizeRotation(page.rotation + e.rotation)
                }
                saveDoc(context, out, "organized")
            }
        }
    }

    /** Applies the same visible-page crop to [pages] (null = every page), each relative to its own box. */
    suspend fun crop(context: Context, file: File, crop: CropRect, pages: Set<Int>?): SavedMedia = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            doc.pages.forEachIndexed { i, page ->
                if (pages != null && i !in pages) return@forEachIndexed
                val cb = page.cropBox
                val b = PdfOps.cropToUser(crop.left, crop.top, crop.right, crop.bottom, cb.lowerLeftX, cb.lowerLeftY, cb.width, cb.height, page.rotation)
                page.cropBox = PDRectangle(b.x, b.y, b.w, b.h)
            }
            saveDoc(context, doc, "cropped")
        }
    }

    // ---------- conversion ----------

    /** Renders [pages] (0-based) to images at [dpi]. */
    suspend fun toImages(context: Context, file: File, pages: List<Int>, dpi: Int, format: OutputFormat, onProgress: (Float) -> Unit): List<SavedMedia> =
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { r ->
                    pages.mapIndexed { k, i ->
                        coroutineContext.ensureActive()
                        val bmp = r.openPage(i).use { p -> renderPage(p, dpi) }
                        onProgress((k + 1f) / pages.size)
                        ImageEngine.save(context, bmp, "page_${i + 1}", format, 92)
                    }
                }
            }
        }

    /** Longest side of any rendered page image; keeps a 300 dpi poster from exhausting memory. */
    private const val MAX_RENDER_SIDE = 6000

    internal fun renderPage(page: PdfRenderer.Page, dpi: Int, maxSide: Int = MAX_RENDER_SIDE): Bitmap {
        var k = dpi / 72f
        val long = max(page.width, page.height) * k
        if (long > maxSide) k *= maxSide / long
        val bmp = Bitmap.createBitmap(max(1, (page.width * k).roundToInt()), max(1, (page.height * k).roundToInt()), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE) // PDF pages are transparent by default
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        return bmp
    }

    /** One page per image, in order. Photos are embedded as JPEG, PNGs losslessly. */
    suspend fun imagesToPdf(
        context: Context,
        images: List<Uri>,
        paper: PdfOps.PaperSize,
        orientation: PdfOps.Orientation,
        margin: PdfOps.Margin,
        onProgress: (Float) -> Unit,
    ): SavedMedia = withContext(Dispatchers.IO) {
        init(context)
        PDDocument().use { doc ->
            images.forEachIndexed { k, uri ->
                coroutineContext.ensureActive()
                val bmp = decodeForPdf(context, uri)
                val png = context.contentResolver.getType(uri) == "image/png"
                val image = if (png) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, 0.9f)
                val (pw, ph) = PdfOps.pageFor(paper, orientation, bmp.width, bmp.height, margin.pt)
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val box = PdfOps.fitImage(bmp.width, bmp.height, pw, ph, margin.pt)
                PDPageContentStream(doc, page).use { it.drawImage(image, box.x, box.y, box.w, box.h) }
                bmp.recycle()
                onProgress((k + 1f) / images.size)
            }
            saveDoc(context, doc, "images")
        }
    }

    /** Decodes with EXIF rotation applied, long side capped at 3508 px (A4 at 300 dpi). */
    private fun decodeForPdf(context: Context, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val long = max(info.size.width, info.size.height)
            if (long > 3508) {
                val k = 3508f / long
                d.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
            }
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }

    /** Plain text, or Markdown rebuilt from font sizes and line gaps. */
    suspend fun extractText(context: Context, file: File, markdown: Boolean): String = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            if (!markdown) return@edit PDFTextStripper().apply { sortByPosition = true }.getText(doc).trim()
            val lines = mutableListOf<PdfOps.TextLine>()
            var lastY = Float.NaN
            var lastSize = 0f
            var newPage = true
            val stripper = object : PDFTextStripper() {
                override fun startPage(page: PDPage?) { newPage = true; super.startPage(page) }
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    if (positions.isEmpty()) return
                    val size = positions.map { it.fontSizeInPt }.average().toFloat()
                    val y = positions.first().yDirAdj
                    val gap = newPage || (!lastY.isNaN() && y - lastY > max(size, lastSize) * 1.6f)
                    lines += PdfOps.TextLine(text, size, gap)
                    lastY = y
                    lastSize = size
                    newPage = false
                }
            }
            stripper.sortByPosition = true
            stripper.getText(doc)
            PdfOps.toMarkdown(lines)
        }
    }

    suspend fun saveText(context: Context, text: String, markdown: Boolean): SavedMedia = withContext(Dispatchers.IO) {
        val ext = if (markdown) "md" else "txt"
        MediaSaver.write(context, MediaSaver.fileName("text", ext), if (markdown) "text/markdown" else "text/plain", MediaKind.DOCUMENT) {
            it.write(text.toByteArray())
        }
    }

    // ---------- stamps ----------

    data class NumberStyle(
        val format: PdfOps.NumberFormat,
        val spot: PdfOps.Spot,
        val startAt: Int,
        val fontSize: Float,
        val skipFirst: Boolean,
    )

    data class WatermarkStyle(
        val text: String,
        val fontSize: Float,
        val opacity: Float,
        val angle: Int,
        val color: Int,
        val tiled: Boolean,
    )

    /** Draws page numbers on [page], which is page [index] of [total]. */
    internal fun stampNumber(doc: PDDocument, page: PDPage, index: Int, total: Int, s: NumberStyle) {
        if (s.skipFirst && index == 0) return
        val numbered = if (s.skipFirst) index else index + 1
        val last = if (s.skipFirst) total - 1 else total
        val label = PdfOps.pageLabel(s.format, s.startAt + numbered - 1, s.startAt + last - 1)
        val font = PDType1Font.HELVETICA
        val textW = font.getStringWidth(label) / 1000f * s.fontSize
        val cb = page.cropBox
        val (vw, vh) = PdfOps.visualSize(cb.width, cb.height, page.rotation)
        val (vx, vy) = PdfOps.anchor(s.spot, vw, vh, textW, s.fontSize, MARGIN)
        val (ux, uy) = PdfOps.visualToUser(vx, vy, cb.width, cb.height, page.rotation, cb.lowerLeftX, cb.lowerLeftY)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setFont(font, s.fontSize)
            cs.setNonStrokingColor(0.15f, 0.15f, 0.15f)
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(PdfOps.normalizeRotation(page.rotation).toDouble()), ux, uy))
            cs.showText(label)
            cs.endText()
        }
    }

    suspend fun pageNumbers(context: Context, file: File, style: NumberStyle): SavedMedia = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, page -> stampNumber(doc, page, i, total, style) }
            saveDoc(context, doc, "numbered")
        }
    }

    /**
     * Text watermark, centred or tiled, in Helvetica Bold. Text Helvetica can't encode
     * (e.g. Urdu, Hindi, emoji) is drawn as an image instead, so any script works.
     */
    internal fun stampWatermark(doc: PDDocument, page: PDPage, s: WatermarkStyle) {
        if (s.text.isBlank()) return
        val font = PDType1Font.HELVETICA_BOLD
        val vector = runCatching { font.getStringWidth(s.text) }.isSuccess
        val textW = if (vector) font.getStringWidth(s.text) / 1000f * s.fontSize else textBitmapWidth(s)
        val cb = page.cropBox
        val rot = PdfOps.normalizeRotation(page.rotation)
        val (vw, vh) = PdfOps.visualSize(cb.width, cb.height, rot)
        val centres = if (!s.tiled) listOf(vw / 2f to vh / 2f) else buildList {
            val stepX = textW + s.fontSize * 3f
            val stepY = s.fontSize * 5f
            var y = stepY / 2f
            var row = 0
            while (y < vh + stepY) {
                var x = if (row % 2 == 0) stepX / 2f else 0f
                while (x < vw + stepX) { add(x to y); x += stepX }
                y += stepY; row++
            }
        }
        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = s.opacity; strokingAlphaConstant = s.opacity }
        val image = if (vector) null else LosslessFactory.createFromImage(doc, textBitmap(s))
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setGraphicsStateParameters(gs)
            val r = Color.red(s.color) / 255f; val g = Color.green(s.color) / 255f; val b = Color.blue(s.color) / 255f
            cs.setNonStrokingColor(r, g, b)
            for ((cx, cy) in centres) {
                val (ux, uy) = PdfOps.visualToUser(cx, cy, cb.width, cb.height, rot, cb.lowerLeftX, cb.lowerLeftY)
                // Page rotation keeps it upright; the watermark angle tilts it counter-clockwise.
                val m = Matrix.getRotateInstance(Math.toRadians((rot + s.angle).toDouble()), ux, uy)
                if (image == null) {
                    m.translate(-textW / 2f, -s.fontSize * 0.35f)
                    cs.beginText()
                    cs.setFont(font, s.fontSize)
                    cs.setTextMatrix(m)
                    cs.showText(s.text)
                    cs.endText()
                } else {
                    val h = s.fontSize * 1.3f
                    m.translate(-textW / 2f, -h / 2f)
                    m.scale(textW, h)
                    cs.drawImage(image, m)
                }
            }
        }
    }

    private fun textPaint(s: WatermarkStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = s.color or 0xFF000000.toInt()
        textSize = s.fontSize * 4f // render at 4× for crisp print
    }

    private fun textBitmapWidth(s: WatermarkStyle): Float = textPaint(s).measureText(s.text) / 4f

    private fun textBitmap(s: WatermarkStyle): Bitmap {
        val p = textPaint(s)
        val w = max(1, p.measureText(s.text).roundToInt())
        val h = max(1, (s.fontSize * 1.3f * 4f).roundToInt())
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            Canvas(bmp).drawText(s.text, 0f, h * 0.78f, p)
        }
    }

    suspend fun watermark(context: Context, file: File, style: WatermarkStyle): SavedMedia = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            doc.pages.forEach { stampWatermark(doc, it, style) }
            saveDoc(context, doc, "watermarked")
        }
    }

    /** Draws [image] upright into a visible-page rectangle, on any page rotation. */
    private fun drawUpright(doc: PDDocument, page: PDPage, image: PDImageXObject, rect: VisualRect) {
        val cb = page.cropBox
        val rot = PdfOps.normalizeRotation(page.rotation)
        val (vw, vh) = PdfOps.visualSize(cb.width, cb.height, rot)
        val w = rect.width * vw
        val h = rect.height * vh
        // The image's bottom-left corner as the reader sees it.
        val (ux, uy) = PdfOps.visualToUser(rect.left * vw, rect.bottom * vh, cb.width, cb.height, rot, cb.lowerLeftX, cb.lowerLeftY)
        val m = Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), ux, uy)
        m.scale(w, h)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { it.drawImage(image, m) }
    }

    /** Places a transparent [signature] image on page [pageIndex] in [rect]. */
    suspend fun sign(context: Context, file: File, signature: Bitmap, pageIndex: Int, rect: VisualRect): SavedMedia = withContext(Dispatchers.IO) {
        edit(context, file) { doc ->
            val image = LosslessFactory.createFromImage(doc, signature)
            drawUpright(doc, doc.getPage(pageIndex), image, rect)
            saveDoc(context, doc, "signed")
        }
    }

    /**
     * True redaction: every page with boxes is rendered to an image at [dpi], the boxes are
     * painted black into the pixels, and the page is replaced by that image. The text, images
     * and links under (and around) the boxes on those pages no longer exist in the file.
     */
    suspend fun redact(context: Context, file: File, boxes: Map<Int, List<VisualRect>>, dpi: Int = 200): SavedMedia = withContext(Dispatchers.IO) {
        val todo = boxes.filterValues { it.isNotEmpty() }
        require(todo.isNotEmpty()) { "Draw at least one box" }
        // Render first (PdfRenderer), then rebuild with PdfBox.
        val rendered = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                todo.mapValues { (i, rects) ->
                    coroutineContext.ensureActive()
                    val bmp = r.openPage(i).use { renderPage(it, dpi, maxSide = 4000) }
                    val c = Canvas(bmp)
                    val black = Paint().apply { color = Color.BLACK }
                    rects.forEach { c.drawRect(it.left * bmp.width, it.top * bmp.height, it.right * bmp.width, it.bottom * bmp.height, black) }
                    bmp
                }
            }
        }
        edit(context, file) { doc ->
            rendered.forEach { (i, bmp) ->
                val old = doc.getPage(i)
                val cb = old.cropBox
                val (vw, vh) = PdfOps.visualSize(cb.width, cb.height, old.rotation)
                val page = PDPage(PDRectangle(vw, vh))
                val image = JPEGFactory.createFromImage(doc, bmp, 0.92f)
                PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, vw, vh) }
                doc.pages.insertBefore(page, old)
                doc.removePage(old)
                bmp.recycle()
            }
            // Hidden copies of the text can also live in metadata and bookmarks.
            doc.documentInformation.apply { title = null; subject = null; keywords = null }
            saveDoc(context, doc, "redacted")
        }
    }

    // ---------- security ----------

    /**
     * AES-256 encryption with an open password. The owner password is random, so the chosen
     * permissions can't be lifted by anyone, including you; keep the original if you need it.
     */
    suspend fun protect(context: Context, file: File, password: String, allowPrint: Boolean, allowCopy: Boolean): SavedMedia =
        withContext(Dispatchers.IO) {
            require(password.isNotEmpty()) { "Enter a password" }
            edit(context, file) { doc ->
                val ap = AccessPermission().apply {
                    setCanPrint(allowPrint)
                    setCanPrintFaithful(allowPrint)
                    setCanExtractContent(allowCopy)
                    setCanExtractForAccessibility(true)
                }
                doc.protect(StandardProtectionPolicy(UUID.randomUUID().toString(), password, ap).apply { encryptionKeyLength = 256 })
                saveDoc(context, doc, "protected")
            }
        }

    /** Opens with [password] (empty for restriction-only PDFs) and saves without any encryption. */
    suspend fun unlock(context: Context, file: File, password: String): SavedMedia = withContext(Dispatchers.IO) {
        load(context, file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            saveDoc(context, doc, "unlocked")
        }
    }

    suspend fun isEncrypted(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { load(context, file).use { it.isEncrypted } }.getOrElse { it is PdfLockedException }
    }

    // ---------- compress / repair ----------

    /**
     * Result of compressing into a temp file; [file] is saved only if the user confirms.
     * [images] counts distinct images (one image shown on many pages counts once);
     * [firstImagePage] is where to look to judge the quality (0-based, or 0 if none).
     */
    data class Compressed(val file: File, val before: Long, val after: Long, val images: Int, val firstImagePage: Int)

    /**
     * Re-encodes page images as JPEG at the level's quality, downscaling ones finer than its
     * dpi. Shared images are converted once. Images with transparency are left alone.
     */
    suspend fun compress(context: Context, file: File, level: PdfOps.Compression): Compressed = withContext(Dispatchers.IO) {
        val out = tempFile(context, "compressed_${level.name}")
        var firstPage = -1
        val done = HashMap<COSStream, PDImageXObject?>()
        edit(context, file) { doc ->
            doc.pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                val res = page.resources ?: return@forEachIndexed
                val pageLong = max(page.cropBox.width, page.cropBox.height)
                for (name in res.xObjectNames.toList()) {
                    val x = res.getXObject(name) as? PDImageXObject ?: continue
                    val replacement = done.getOrPut(x.cosObject) { recompress(doc, x, pageLong, level) }
                    if (replacement != null) {
                        res.put(name, replacement)
                        // Pages often *list* shared images they never draw; check the drawing commands.
                        if (firstPage < 0 && draws(page, name)) firstPage = i
                    }
                }
            }
            doc.save(out)
        }
        Compressed(out, file.length(), out.length(), done.values.count { it != null }, firstPage.coerceAtLeast(0))
    }

    /** True if [page]'s content stream paints XObject [name] (a "/name Do" command). */
    private fun draws(page: PDPage, name: COSName): Boolean = runCatching {
        val tokens = PDFStreamParser(page).apply { parse() }.tokens
        tokens.indices.any { i -> (tokens[i] as? Operator)?.name == "Do" && i > 0 && tokens[i - 1] == name }
    }.getOrDefault(false)

    private fun recompress(doc: PDDocument, x: PDImageXObject, pageLongPt: Float, level: PdfOps.Compression): PDImageXObject? {
        if (x.isStencil || x.cosObject.containsKey(COSName.SMASK) || x.cosObject.containsKey(COSName.MASK)) return null
        if (x.width * x.height < 64 * 64) return null // icons: not worth it
        val target = PdfOps.downscaleTarget(x.width, x.height, pageLongPt, level.dpi)
        val src = runCatching { x.image }.getOrNull() ?: return null
        val scaled = target?.let { (w, h) -> Bitmap.createScaledBitmap(src, w, h, true) } ?: src
        val opaque = if (scaled.hasAlpha()) flatten(scaled) else scaled
        val jpeg = JPEGFactory.createFromImage(doc, opaque, level.quality)
        if (opaque !== scaled) opaque.recycle()
        if (scaled !== src) scaled.recycle()
        src.recycle()
        // Keep the original if re-encoding didn't help.
        return jpeg.takeIf { it.cosObject.length < x.cosObject.length }
    }

    private fun flatten(src: Bitmap): Bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
    }

    suspend fun saveCompressed(context: Context, c: Compressed): SavedMedia = withContext(Dispatchers.IO) {
        MediaSaver.saveFile(context, c.file, MediaSaver.fileName("compressed", "pdf"), MIME, MediaKind.DOCUMENT)
    }

    /**
     * PdfBox's lenient parser rebuilds a broken cross-reference table; saving writes a clean file.
     * If even that fails (e.g. a damaged "%PDF" header), it retries with a fresh header in front;
     * the old bytes are then skipped like any other junk.
     */
    suspend fun repair(context: Context, file: File): Pair<SavedMedia, Int> = withContext(Dispatchers.IO) {
        fun attempt(f: File) = edit(context, f) { doc ->
            require(doc.numberOfPages > 0) { "No pages could be recovered" }
            saveDoc(context, doc, "repaired") to doc.numberOfPages
        }
        try {
            attempt(file)
        } catch (e: PdfLockedException) {
            throw e
        } catch (first: Exception) {
            val patched = tempFile(context, "patched")
            try {
                patched.outputStream().use { out -> out.write("%PDF-1.7\n".toByteArray()); file.inputStream().use { it.copyTo(out) } }
                attempt(patched)
            } catch (_: Exception) {
                throw IllegalStateException("This file is too damaged to recover any pages.")
            } finally {
                patched.delete()
            }
        }
    }

    // ---------- live preview ----------

    /**
     * Renders page [index] with [op] applied, without touching the original: the page is copied
     * into a one-page document, edited, saved to a temp file and rendered. [total] is the
     * real page count, for labels like "3 of 10".
     */
    suspend fun previewPage(
        context: Context,
        file: File,
        index: Int,
        widthPx: Int,
        op: (doc: PDDocument, page: PDPage) -> Unit,
    ): Bitmap = withContext(Dispatchers.IO) {
        val tmp = tempFile(context, "preview")
        try {
            edit(context, file) { src ->
                PDDocument().use { one ->
                    op(one, copyPage(one, src.getPage(index)))
                    one.save(tmp)
                }
            }
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { r -> r.openPage(0).use { p -> renderPage(p, 72 * widthPx / max(1, p.width), maxSide = 2400) } }
            }
        } finally {
            tmp.delete()
        }
    }
}
