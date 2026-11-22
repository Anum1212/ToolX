package com.toolx.app.tools.qr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.toolx.app.tools.Tool
import com.toolx.app.tools.qr.QrCodes.Content
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import com.toolx.app.ui.common.rememberMediaPicker
import java.util.concurrent.Executors

private fun hasCamera(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Live camera preview that reports the first code it reads. ML Kit's bundled model runs on the device. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(scanner: BarcodeScanner, onCode: (String) -> Unit, onCamera: (Camera?) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
                    .addOnCompleteListener { proxy.close() }
            }
            val selector = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            p.unbindAll()
            onCamera(runCatching { p.bindToLifecycle(owner, selector, preview, analysis) }.getOrNull())
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll(); onCamera(null); executor.shutdown() }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

private fun Context.launch(intent: Intent) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(this, "No app on this phone can open that", Toast.LENGTH_SHORT).show() }
}

@Composable
internal fun QrScannerScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val scanner = remember { BarcodeScanning.getClient() }
    DisposableEffect(Unit) { onDispose { scanner.close() } }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var asked by rememberSaveable { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked++ }
    val pickPhoto = rememberMediaPicker(PickType.IMAGE) { uri ->
        photoMessage = null
        runCatching { InputImage.fromFilePath(context, uri) }.onSuccess { img ->
            scanner.process(img)
                .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let { result = it } ?: run { photoMessage = "No QR code or barcode found in that photo." } }
                .addOnFailureListener { photoMessage = "Couldn't read that photo." }
        }.onFailure { photoMessage = "Couldn't open that photo." }
    }

    ToolScaffold(tool.title, onBack) {
        val code = result
        if (code == null) {
            if (granted) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp))) {
                    CameraScanner(scanner, onCode = { if (result == null) result = it }, onCamera = { camera = it; torch = false })
                }
                Note("Point the camera at a QR code or barcode. It's read on the phone; nothing is sent anywhere.")
            } else {
                Note("Scanning with the camera needs camera permission. You can also scan a code from a photo.")
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                if (asked > 0) Note("Camera permission was denied. You can allow it in the app's settings.", error = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = pickPhoto) { Text("Scan from photo") }
                val cam = camera
                if (granted && cam != null && cam.cameraInfo.hasFlashUnit()) {
                    OutlinedButton(onClick = { torch = !torch; cam.cameraControl.enableTorch(torch) }) { Text(if (torch) "Light off" else "Light on") }
                }
            }
            photoMessage?.let { Note(it, error = true) }
        } else {
            ScanResult(context, code)
            Button(onClick = { result = null; photoMessage = null }) { Text("Scan another") }
        }
    }
}

@Composable
private fun ScanResult(context: Context, raw: String) {
    val content = remember(raw) { QrCodes.parse(raw) }
    val (title, rows) = when (content) {
        is Content.Link -> "Link" to listOf("Address" to content.url)
        is Content.Wifi -> "Wi-Fi network" to listOfNotNull("Network" to content.ssid, content.password?.let { "Password" to it }, "Security" to content.security, if (content.hidden) "Hidden" to "Yes" else null)
        is Content.Phone -> "Phone number" to listOf("Number" to content.number)
        is Content.Email -> "Email" to listOfNotNull("To" to content.to, content.subject.takeIf { it.isNotEmpty() }?.let { "Subject" to it }, content.body.takeIf { it.isNotEmpty() }?.let { "Message" to it })
        is Content.Sms -> "Text message" to listOfNotNull("To" to content.number, content.message.takeIf { it.isNotEmpty() }?.let { "Message" to it })
        is Content.Contact -> "Contact" to (listOf("Name" to content.name) + content.phones.map { "Phone" to it } + content.emails.map { "Email" to it } + listOfNotNull(content.org.takeIf { it.isNotEmpty() }?.let { "Company" to it }))
        is Content.Geo -> "Location" to listOf("Coordinates" to "%.6f, %.6f".format(content.lat, content.lon))
        is Content.Text -> "Text" to emptyList()
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (content is Content.Text) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(raw, style = MaterialTheme.typography.bodyLarge, fontFamily = if (raw.all { it.isDigit() }) FontFamily.Monospace else null)
                }
            }
        }
    }
    if (rows.isNotEmpty()) ResultCard(rows, title = "Tap a row to copy it")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (content) {
            is Content.Link -> Button(onClick = { context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(content.url))) }) { Text("Open link") }
            is Content.Wifi -> Button(onClick = { joinWifi(context, content) }) { Text("Join network") }
            is Content.Phone -> Button(onClick = { context.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + content.number))) }) { Text("Call") }
            is Content.Email -> Button(onClick = {
                context.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + content.to)).putExtra(Intent.EXTRA_SUBJECT, content.subject).putExtra(Intent.EXTRA_TEXT, content.body))
            }) { Text("Write email") }
            is Content.Sms -> Button(onClick = { context.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + content.number)).putExtra("sms_body", content.message)) }) { Text("Write message") }
            is Content.Contact -> Button(onClick = {
                context.launch(
                    Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.NAME, content.name)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, content.phones.firstOrNull())
                        .putExtra(ContactsContract.Intents.Insert.EMAIL, content.emails.firstOrNull())
                        .putExtra(ContactsContract.Intents.Insert.COMPANY, content.org),
                )
            }) { Text("Add contact") }
            is Content.Geo -> Button(onClick = { context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${content.lat},${content.lon}?q=${content.lat},${content.lon}"))) }) { Text("Open in maps") }
            is Content.Text -> Unit
        }
        OutlinedButton(onClick = { copyToClipboard(context, raw) }) { Text("Copy") }
    }
    if (content is Content.Link) Note("Check the address before opening it: QR codes can point anywhere.")
}

/** Android 11+ offers the network in a system "Save network?" sheet; Android 10 opens Wi-Fi settings. */
private fun joinWifi(context: Context, w: Content.Wifi) {
    if (Build.VERSION.SDK_INT >= 30) {
        val b = WifiNetworkSuggestion.Builder().setSsid(w.ssid).setIsHiddenSsid(w.hidden)
        val pass = w.password
        when {
            pass == null -> Unit
            w.security.equals("WEP", true) -> { copyToClipboard(context, pass); context.launch(Intent(Settings.ACTION_WIFI_SETTINGS)); return }
            else -> b.setWpa2Passphrase(pass)
        }
        val list = arrayListOf(runCatching { b.build() }.getOrElse { copyToClipboard(context, pass ?: ""); context.launch(Intent(Settings.ACTION_WIFI_SETTINGS)); return })
        context.launch(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list))
    } else {
        w.password?.let { copyToClipboard(context, it) }
        context.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
