package com.toolx.app.tools.qr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn

/** QR maker (ZXing) and scanner (CameraX + ML Kit's bundled model), both offline. Listed under General. */
object QrTools : ToolModule {
    override val tools = listOf(
        Tool("qr_make", "QR code maker", "Links, Wi-Fi, contacts and more", Icons.Rounded.QrCode2, ToolCategory.GENERAL, available = true,
            keywords = listOf("qr", "qr code", "generate", "create", "wifi", "vcard", "barcode")),
        Tool("qr_scan", "QR scanner", "Scan QR codes and barcodes", Icons.Rounded.QrCodeScanner, ToolCategory.GENERAL, available = true,
            keywords = listOf("qr", "qr code", "scan", "reader", "barcode", "camera", "wifi")),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "qr_make" to { tool, back -> QrMakerScreen(tool, back) },
        "qr_scan" to { tool, back -> QrScannerScreen(tool, back) },
    )
}
