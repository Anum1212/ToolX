package com.toolx.app.tools.qr

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.common.BitMatrix
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.ProcessingState
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.tools.qr.QrCodes.WifiSecurity
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class QrType(val label: String) { TEXT("Text"), LINK("Link"), WIFI("Wi-Fi"), CONTACT("Contact"), PHONE("Phone"), EMAIL("Email"), SMS("SMS") }

/** Quiet zone of 4 modules on each side, as the QR spec asks. */
private const val QUIET = 4

private fun renderPng(context: Context, m: BitMatrix, sizePx: Int): SavedMedia {
    val modules = m.width + QUIET * 2
    val scale = (sizePx / modules).coerceAtLeast(1)
    val side = modules * scale
    val pixels = IntArray(side * side) { android.graphics.Color.WHITE }
    for (y in 0 until m.height) for (x in 0 until m.width) {
        if (!m[x, y]) continue
        for (dy in 0 until scale) {
            val row = ((y + QUIET) * scale + dy) * side
            pixels.fill(android.graphics.Color.BLACK, row + (x + QUIET) * scale, row + (x + QUIET + 1) * scale)
        }
    }
    val bmp = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    return try {
        MediaSaver.write(context, MediaSaver.fileName("qr", "png"), "image/png", MediaKind.IMAGE) { out ->
            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Couldn't encode the image" }
        }
    } finally {
        bmp.recycle()
    }
}

@Composable
internal fun QrMakerScreen(tool: Tool, onBack: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(QrType.LINK) }
    var text by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.WPA) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var ecc by rememberSaveable { mutableStateOf(QrCodes.Ecc.M) }
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()

    val payload = when (type) {
        QrType.TEXT -> text
        QrType.LINK -> QrCodes.link(url)
        QrType.WIFI -> if (ssid.isBlank()) "" else QrCodes.wifi(ssid, password, security, hidden)
        QrType.CONTACT -> if (name.isBlank() && phone.isBlank() && email.isBlank()) "" else QrCodes.contact(name, phone, email, org)
        QrType.PHONE -> if (phone.isBlank()) "" else QrCodes.phone(phone)
        QrType.EMAIL -> if (email.isBlank()) "" else QrCodes.email(email, subject, message)
        QrType.SMS -> if (phone.isBlank()) "" else QrCodes.sms(phone, message)
    }
    val matrix = remember(payload, ecc) { if (payload.isEmpty()) null else runCatching { QrCodes.matrix(payload, ecc) } }

    @Composable
    fun field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1, secret: Boolean = false) {
        OutlinedTextField(
            value, { onChange(it); if (state is ProcessingState.Done) vm.cancel() },
            label = { Text(label) }, singleLine = lines == 1, minLines = lines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ToolScaffold(tool.title, onBack) {
        OptionChips(QrType.entries, type, label = { it.label }) { type = it; vm.cancel() }
        when (type) {
            QrType.TEXT -> field(text, { text = it }, "Text", lines = 3)
            QrType.LINK -> field(url, { url = it }, "Website address", KeyboardType.Uri)
            QrType.WIFI -> {
                field(ssid, { ssid = it }, "Network name (SSID)")
                SectionLabel("Security")
                OptionChips(WifiSecurity.entries, security, label = { it.label }) { security = it }
                if (security != WifiSecurity.OPEN) field(password, { password = it }, "Password", KeyboardType.Password)
                ToggleChip("Hidden network", hidden) { hidden = it }
            }
            QrType.CONTACT -> {
                field(name, { name = it }, "Name")
                field(phone, { phone = it }, "Phone", KeyboardType.Phone)
                field(email, { email = it }, "Email", KeyboardType.Email)
                field(org, { org = it }, "Company (optional)")
            }
            QrType.PHONE -> field(phone, { phone = it }, "Phone number", KeyboardType.Phone)
            QrType.EMAIL -> {
                field(email, { email = it }, "To", KeyboardType.Email)
                field(subject, { subject = it }, "Subject (optional)")
                field(message, { message = it }, "Message (optional)", lines = 3)
            }
            QrType.SMS -> {
                field(phone, { phone = it }, "Phone number", KeyboardType.Phone)
                field(message, { message = it }, "Message (optional)", lines = 3)
            }
        }

        val m = matrix?.getOrNull()
        when {
            matrix == null -> Note("Fill in the details and the QR code appears here.")
            m == null -> Note("That's too much to fit in a QR code. Shorten it, or lower the error correction.", error = true)
            else -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(m, Modifier.widthIn(max = 320.dp).fillMaxWidth()) }
                Note("${m.width} × ${m.width} modules · ${payload.toByteArray().size} bytes")
            }
        }
        SectionLabel("Error correction")
        OptionChips(QrCodes.Ecc.entries, ecc, label = { it.label }) { ecc = it; vm.cancel() }
        Note(ecc.note + ". Higher survives smudges and logos but makes the code denser.")
        Button(
            onClick = { val mm = m ?: return@Button; vm.run { ctx, _ -> withContext(Dispatchers.IO) { renderPng(ctx, mm, 1024) } } },
            enabled = m != null && state !is ProcessingState.Running,
        ) { Text("Save as PNG") }
        ProcessingStatus(state, vm::cancel)
    }
}

/** Draws the code with its quiet zone, always black on white so every scanner can read it. */
@Composable
internal fun QrImage(m: BitMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val modules = m.width + QUIET * 2
        val cell = size.width / modules
        drawRect(Color.White)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            // Slight overlap avoids hairline gaps between neighbouring modules.
            if (m[x, y]) drawRect(Color.Black, Offset((x + QUIET) * cell, (y + QUIET) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
