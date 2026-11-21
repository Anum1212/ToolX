package com.toolx.app.tools.pdf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BrandingWatermark
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn

/** PDF tools. All offline: PdfBox-Android for editing, the platform PdfRenderer for pages. */
object PdfTools : ToolModule {

    private val PDF = ToolCategory.PDF

    override val tools = listOf(
        Tool("pdf_merge", "Merge PDF", "Join PDFs in the order you choose", Icons.AutoMirrored.Rounded.MergeType, PDF, true, listOf("combine", "join")),
        Tool("pdf_split", "Split PDF", "Ranges, every N pages, or extract pages", Icons.AutoMirrored.Rounded.CallSplit, PDF, true, listOf("extract", "separate", "pages")),
        Tool("pdf_compress", "Compress PDF", "Shrink images to make the file smaller", Icons.Rounded.Compress, PDF, true, listOf("reduce", "size", "smaller")),
        Tool("pdf_organize", "Organize PDF", "Reorder, rotate or delete pages", Icons.Rounded.GridView, PDF, true, listOf("reorder", "delete", "remove", "sort", "pages")),
        Tool("pdf_rotate", "Rotate PDF", "Turn all pages or just some", Icons.Rounded.Rotate90DegreesCw, PDF, true, listOf("turn", "orientation")),
        Tool("pdf_crop", "Crop PDF", "Trim page margins", Icons.Rounded.Crop, PDF, true, listOf("margins", "trim")),
        Tool("pdf_to_images", "PDF to JPG", "Save pages as images", Icons.Rounded.Image, PDF, true, listOf("jpg", "png", "image", "convert")),
        Tool("images_to_pdf", "Images to PDF", "Turn photos into a PDF, one per page", Icons.Rounded.AddPhotoAlternate, PDF, true, listOf("jpg", "photo", "scan", "convert", "document")),
        Tool("pdf_to_text", "PDF to text", "Extract text, or rebuild it as Markdown", Icons.AutoMirrored.Rounded.TextSnippet, PDF, true, listOf("markdown", "md", "txt", "extract", "copy")),
        Tool("pdf_page_numbers", "Page numbers", "Number every page", Icons.Rounded.FormatListNumbered, PDF, true, listOf("numbering", "footer")),
        Tool("pdf_watermark", "Watermark PDF", "Stamp text across the pages", Icons.AutoMirrored.Rounded.BrandingWatermark, PDF, true, listOf("stamp", "confidential", "draft")),
        Tool("pdf_sign", "Sign PDF", "Draw your signature onto a page", Icons.Rounded.Draw, PDF, true, listOf("signature", "autograph")),
        Tool("pdf_redact", "Redact PDF", "Black out and truly remove content", Icons.Rounded.HideSource, PDF, true, listOf("black out", "hide", "censor", "privacy")),
        Tool("pdf_protect", "Protect PDF", "Add a password (AES-256)", Icons.Rounded.Lock, PDF, true, listOf("password", "encrypt", "secure")),
        Tool("pdf_unlock", "Unlock PDF", "Remove a password you know", Icons.Rounded.LockOpen, PDF, true, listOf("password", "decrypt", "remove", "restrictions")),
        Tool("pdf_repair", "Repair PDF", "Recover a damaged PDF", Icons.Rounded.Build, PDF, true, listOf("fix", "broken", "corrupt", "damaged")),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "pdf_merge" to { t, b -> PdfMergeScreen(t, b) },
        "pdf_split" to { t, b -> PdfSplitScreen(t, b) },
        "pdf_compress" to { t, b -> PdfCompressScreen(t, b) },
        "pdf_organize" to { t, b -> PdfOrganizeScreen(t, b) },
        "pdf_rotate" to { t, b -> PdfRotateScreen(t, b) },
        "pdf_crop" to { t, b -> PdfCropScreen(t, b) },
        "pdf_to_images" to { t, b -> PdfToImagesScreen(t, b) },
        "images_to_pdf" to { t, b -> ImagesToPdfScreen(t, b) },
        "pdf_to_text" to { t, b -> PdfToTextScreen(t, b) },
        "pdf_page_numbers" to { t, b -> PdfPageNumbersScreen(t, b) },
        "pdf_watermark" to { t, b -> PdfWatermarkScreen(t, b) },
        "pdf_sign" to { t, b -> PdfSignScreen(t, b) },
        "pdf_redact" to { t, b -> PdfRedactScreen(t, b) },
        "pdf_protect" to { t, b -> PdfProtectScreen(t, b) },
        "pdf_unlock" to { t, b -> PdfUnlockScreen(t, b) },
        "pdf_repair" to { t, b -> PdfRepairScreen(t, b) },
    )
}
