package com.toolx.app.tools.general

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Looks6
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.BigResult
import com.toolx.app.ui.common.ChipRow
import com.toolx.app.ui.common.DateField
import com.toolx.app.ui.common.Dropdown
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import com.toolx.app.ui.common.fmt
import com.toolx.app.ui.common.formatDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeneralTools : ToolModule {

    private fun t(id: String, title: String, desc: String, icon: ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.GENERAL, available = true, keywords = kw.toList())

    override val tools = listOf(
        t("gen_ruler", "Ruler", "Measure with your screen, cm and inches", Icons.Rounded.Straighten, "measure", "cm", "inch", "length"),
        t("gen_morse", "Morse code", "Text ↔ Morse, with sound", Icons.Rounded.LineWeight, "morse", "sos", "telegraph"),
        t("gen_roman", "Roman numerals", "2024 ↔ MMXXIV", Icons.Rounded.Looks6, "roman", "numerals", "latin"),
        t("gen_words", "Number to words", "1234 → one thousand two hundred…", Icons.Rounded.Translate, "spell", "cheque", "words", "lakh", "crore"),
        t("gen_compass", "Compass", "Heading from the phone's sensors", Icons.Rounded.Explore, "north", "direction", "qibla", "heading"),
        t("gen_bytes", "Text ↔ bytes", "UTF-8 bytes as hex, binary or Base64", Icons.Rounded.Code, "string", "byte", "hex", "base64", "utf8", "encode"),
        t("gen_timezone", "Time zones", "Convert a time across zones", Icons.Rounded.Language, "timezone", "utc", "gmt", "world clock"),
        t("gen_level", "Level", "Bubble level and tilt angles", Icons.Rounded.ControlCamera, "spirit level", "bubble", "tilt", "angle", "flat"),
        t("gen_days", "Day counter", "Days between dates, or add days", Icons.Rounded.CalendarMonth, "date", "countdown", "days", "business days"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "gen_ruler" to { tool, back -> RulerScreen(tool, back) },
        "gen_morse" to { tool, back -> MorseScreen(tool, back) },
        "gen_roman" to { tool, back -> RomanScreen(tool, back) },
        "gen_words" to { tool, back -> WordsScreen(tool, back) },
        "gen_compass" to { tool, back -> CompassScreen(tool, back) },
        "gen_bytes" to { tool, back -> BytesScreen(tool, back) },
        "gen_timezone" to { tool, back -> TimezoneScreen(tool, back) },
        "gen_level" to { tool, back -> LevelScreen(tool, back) },
        "gen_days" to { tool, back -> DayCounterScreen(tool, back) },
    )
}

/** Like ToolScaffold but without scrolling, for full-screen canvases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedToolScaffold(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

private fun textPaint(color: Color, sizePx: Float, bold: Boolean = false) = android.graphics.Paint().apply {
    this.color = color.toArgb(); textSize = sizePx; isAntiAlias = true
    if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
}

// ---------- Ruler ----------

@Composable
private fun RulerScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ruler", Context.MODE_PRIVATE) }
    var calibration by remember { mutableFloatStateOf(prefs.getFloat("calibration", 1f)) }
    var markerY by rememberSaveable { mutableFloatStateOf(-1f) }
    val ydpi = context.resources.displayMetrics.ydpi
    val pxPerInch = ydpi * calibration
    val pxPerMm = pxPerInch / 25.4f
    val line = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    FixedToolScaffold(tool.title, onBack) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { markerY = it.y }
            }.pointerInput(Unit) {
                detectDragGestures { change, _ -> markerY = change.position.y.coerceAtLeast(0f) }
            },
        ) {
            val mmTick = with(density) { 10.dp.toPx() }
            val label = textPaint(line, with(density) { 13.dp.toPx() })
            // Centimetres on the left edge, starting exactly at the top of the canvas.
            var mm = 0
            while (mm * pxPerMm <= size.height) {
                val y = mm * pxPerMm
                val len = when { mm % 10 == 0 -> mmTick * 2.6f; mm % 5 == 0 -> mmTick * 1.8f; else -> mmTick }
                drawLine(line, Offset(0f, y), Offset(len, y), strokeWidth = if (mm % 10 == 0) 2.5f else 1.5f)
                if (mm % 10 == 0 && mm > 0) drawContext.canvas.nativeCanvas.drawText("${mm / 10}", len + 8f, y + label.textSize / 3, label)
                mm++
            }
            // Inches on the right edge, in sixteenths.
            var s = 0
            while (s * pxPerInch / 16 <= size.height) {
                val y = s * pxPerInch / 16
                val len = when { s % 16 == 0 -> mmTick * 2.6f; s % 8 == 0 -> mmTick * 2f; s % 4 == 0 -> mmTick * 1.5f; else -> mmTick * 0.9f }
                drawLine(line, Offset(size.width - len, y), Offset(size.width, y), strokeWidth = if (s % 16 == 0) 2.5f else 1.5f)
                if (s % 16 == 0 && s > 0) {
                    val text = "${s / 16}"
                    drawContext.canvas.nativeCanvas.drawText(text, size.width - len - 8f - label.measureText(text), y + label.textSize / 3, label)
                }
                s++
            }
            if (markerY >= 0) drawLine(accent, Offset(0f, markerY), Offset(size.width, markerY), strokeWidth = 3f)
        }
        // Fixed panel at the bottom, between the two scales, so the readout never overlaps the help text.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 72.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (markerY >= 0) "%.1f cm  ·  %.2f in".format(markerY / pxPerMm / 10, markerY / pxPerInch) else "Tap to measure",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Note("Line the object up with the top edge of this area. Tap or drag to measure.")
            Text("Calibration ${(calibration * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = calibration,
                onValueChange = { calibration = it; prefs.edit().putFloat("calibration", it).apply() },
                valueRange = 0.8f..1.2f,
            )
            Note("If a real ruler disagrees, adjust until they match. It's saved for next time.")
        }
    }
}

// ---------- Morse ----------

@Composable
private fun MorseScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("SOS") }
    val isMorse = GeneralOps.looksLikeMorse(input)
    val output = if (isMorse) GeneralOps.fromMorse(input) else GeneralOps.toMorse(input)
    val morseToPlay = if (isMorse) input else output
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf<Job?>(null) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { playing?.cancel(); tone?.release() } }

    ToolScaffold(tool.title, onBack) {
        OutlinedTextField(input, { input = it }, label = { Text("Text or Morse") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Note(if (isMorse) "Reading Morse → text. Separate letters with spaces and words with /." else "Writing text → Morse.")
        BigResultText(output) { copyToClipboard(context, output) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    playing?.cancel()
                    playing = scope.launch {
                        val unit = 90L
                        for (c in morseToPlay) {
                            when (c) {
                                '.' -> { tone?.startTone(ToneGenerator.TONE_DTMF_9, unit.toInt()); delay(unit * 2) }
                                '-' -> { tone?.startTone(ToneGenerator.TONE_DTMF_9, (unit * 3).toInt()); delay(unit * 4) }
                                ' ' -> delay(unit * 2)
                                '/' -> delay(unit * 4)
                            }
                        }
                        playing = null
                    }
                },
                enabled = tone != null && morseToPlay.isNotBlank(),
            ) { Text(if (playing != null) "Playing…" else "Play sound") }
            if (playing != null) OutlinedButton(onClick = { playing?.cancel(); playing = null }) { Text("Stop") }
        }
    }
}

@Composable
private fun BigResultText(text: String, onCopy: () -> Unit) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text.ifEmpty { "—" }, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onCopy, enabled = text.isNotEmpty()) { Text("Copy") }
        }
    }
}

// ---------- Roman ----------

@Composable
private fun RomanScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("2026") }
    ToolScaffold(tool.title, onBack) {
        TextInput(input, { input = it }, "Number (1–3999) or Roman numeral")
        val trimmed = input.trim()
        val n = trimmed.toIntOrNull()
        when {
            trimmed.isEmpty() -> Unit
            n != null -> GeneralOps.toRoman(n)?.let { BigResult(it, "$n in Roman numerals") } ?: Note("Roman numerals go from 1 to 3999.", error = true)
            else -> GeneralOps.fromRoman(trimmed)?.let { BigResult(it.toString(), "${trimmed.uppercase()} =") } ?: Note("That isn't a valid Roman numeral.", error = true)
        }
        ResultCard(listOf("I" to "1", "V" to "5", "X" to "10", "L" to "50", "C" to "100", "D" to "500", "M" to "1000"), title = "Symbols")
    }
}

// ---------- Number to words ----------

@Composable
private fun WordsScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("1250000") }
    var lakh by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    ToolScaffold(tool.title, onBack) {
        NumberField(input, { input = it }, "Number")
        OptionChips(listOf(false, true), lakh, label = { if (it) "Lakh & crore" else "Million & billion" }) { lakh = it }
        val words = GeneralOps.toWords(input, lakh)
        if (words != null) BigResultText(words) { copyToClipboard(context, words) }
        else if (input.isNotBlank()) Note("Enter a number below about 4.6 quintillion.", error = true)
    }
}

// ---------- Sensors ----------

/** Listens to a sensor while on screen; returns null if the device doesn't have it. */
@Composable
private fun rememberSensor(type: Int, onValues: (FloatArray) -> Unit): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(type) { manager.getDefaultSensor(type) }
    DisposableEffect(sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onValues(event.values)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { manager.unregisterListener(listener) }
    }
    return sensor != null
}

private val DIRECTIONS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

@Composable
private fun CompassScreen(tool: Tool, onBack: () -> Unit) {
    var azimuth by remember { mutableFloatStateOf(0f) }
    val matrix = remember { FloatArray(9) }
    val orientation = remember { FloatArray(3) }
    val available = rememberSensor(Sensor.TYPE_ROTATION_VECTOR) { v ->
        SensorManager.getRotationMatrixFromVector(matrix, v)
        SensorManager.getOrientation(matrix, orientation)
        val target = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
        // Smooth, taking the short way around 359° → 0°.
        var delta = target - azimuth
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        azimuth = (azimuth + delta * 0.2f + 360) % 360
    }
    val line = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.error
    val density = LocalDensity.current

    ToolScaffold(tool.title, onBack) {
        if (!available) {
            Note("This device has no rotation sensor, so the compass can't work.", error = true)
            return@ToolScaffold
        }
        val heading = azimuth.roundToInt() % 360
        BigResult("$heading° ${DIRECTIONS[((azimuth + 22.5f) / 45).toInt() % 8]}", "Heading")
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)) {
            val r = min(size.width, size.height) / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(line, r, c, style = Stroke(3f))
            rotate(-azimuth, c) {
                for (deg in 0 until 360 step 5) {
                    val rad = Math.toRadians(deg.toDouble())
                    val inner = if (deg % 30 == 0) r * 0.85f else if (deg % 10 == 0) r * 0.9f else r * 0.94f
                    drawLine(line, Offset(c.x + inner * sin(rad).toFloat(), c.y - inner * cos(rad).toFloat()), Offset(c.x + r * sin(rad).toFloat(), c.y - r * cos(rad).toFloat()), strokeWidth = if (deg % 30 == 0) 3f else 1.5f)
                }
                val paint = textPaint(line, with(density) { 22.dp.toPx() }, bold = true)
                listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
                    val rad = Math.toRadians(deg.toDouble())
                    val p = textPaint(if (label == "N") accent else line, paint.textSize, bold = true)
                    val x = c.x + r * 0.68f * sin(rad).toFloat() - p.measureText(label) / 2
                    val y = c.y - r * 0.68f * cos(rad).toFloat() + p.textSize / 3
                    drawContext.canvas.nativeCanvas.drawText(label, x, y, p)
                }
            }
            // Fixed pointer: where the top of the phone is facing.
            drawLine(accent, Offset(c.x, c.y - r * 1.02f), Offset(c.x, c.y - r * 0.75f), strokeWidth = 8f)
            drawCircle(accent, 8f, c)
        }
        Note("Hold the phone flat. Keep it away from magnets and metal, and wave it in a figure-8 if the heading seems off. This shows magnetic north.")
    }
}

@Composable
private fun LevelScreen(tool: Tool, onBack: () -> Unit) {
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var gz by remember { mutableFloatStateOf(9.81f) }
    val available = rememberSensor(Sensor.TYPE_ACCELEROMETER) { v ->
        // Low-pass filter so the bubble doesn't jitter.
        gx += (v[0] - gx) * 0.15f; gy += (v[1] - gy) * 0.15f; gz += (v[2] - gz) * 0.15f
    }
    val g = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
    val tiltX = Math.toDegrees(asin((gx / g).toDouble().coerceIn(-1.0, 1.0))).toFloat()
    val tiltY = Math.toDegrees(asin((gy / g).toDouble().coerceIn(-1.0, 1.0))).toFloat()
    val level = abs(tiltX) < 1f && abs(tiltY) < 1f
    val line = MaterialTheme.colorScheme.onSurface
    val bubbleColor = if (level) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary

    ToolScaffold(tool.title, onBack) {
        if (!available) {
            Note("This device has no accelerometer.", error = true)
            return@ToolScaffold
        }
        BigResult(if (level) "Level ✓" else "%.1f°".format(sqrt(tiltX * tiltX + tiltY * tiltY)), if (level) "Flat within 1°" else "Tilt")
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp)) {
            val r = min(size.width, size.height) / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(line, r, c, style = Stroke(3f))
            drawCircle(line, r * 0.15f, c, style = Stroke(2f))
            drawLine(line.copy(alpha = 0.3f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
            drawLine(line.copy(alpha = 0.3f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
            // The bubble floats to the higher side; 30° of tilt reaches the rim.
            val scale = r / 30f
            val bx = (tiltX * scale).coerceIn(-r, r)
            val by = (-tiltY * scale).coerceIn(-r, r)
            val d = sqrt(bx * bx + by * by)
            val k = if (d > r * 0.88f) r * 0.88f / d else 1f
            drawCircle(bubbleColor.copy(alpha = 0.85f), r * 0.12f, Offset(c.x + bx * k, c.y + by * k))
        }
        ResultCard(listOf("Left / right" to "%.1f°".format(tiltX), "Front / back" to "%.1f°".format(tiltY)))
        Note("Lay the phone flat on the surface. For a vertical edge, stand the phone on its side and watch one axis.")
    }
}

// ---------- Text ↔ bytes ----------

private val CHARSETS = listOf("UTF-8", "UTF-16BE", "ISO-8859-1", "US-ASCII")

@Composable
private fun BytesScreen(tool: Tool, onBack: () -> Unit) {
    var toBytes by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("Hello ✓") }
    var format by rememberSaveable { mutableStateOf(GeneralOps.ByteFormat.HEX) }
    var charset by rememberSaveable { mutableStateOf("UTF-8") }
    val context = LocalContext.current
    ToolScaffold(tool.title, onBack) {
        OptionChips(listOf(true, false), toBytes, label = { if (it) "Text → bytes" else "Bytes → text" }) { toBytes = it }
        OutlinedTextField(input, { input = it }, label = { Text(if (toBytes) "Text" else "Bytes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        SectionLabel("Byte format")
        OptionChips(GeneralOps.ByteFormat.entries, format, label = { it.label }) { format = it }
        SectionLabel("Encoding")
        OptionChips(CHARSETS, charset, label = { it }) { charset = it }
        val cs = Charset.forName(charset)
        if (toBytes) {
            val bytes = input.toByteArray(cs)
            val out = GeneralOps.bytesToString(bytes, format)
            BigResultText(out) { copyToClipboard(context, out) }
            Note("${bytes.size} bytes, ${input.codePointCount(0, input.length)} characters." + if (charset != "UTF-8" && String(bytes, cs) != input) " Some characters can't be encoded in $charset and became '?'." else "")
        } else {
            val bytes = GeneralOps.parseBytes(input, format)
            if (bytes == null) Note("Couldn't read those bytes as ${format.label}.", error = true)
            else { val out = String(bytes, cs); BigResultText(out) { copyToClipboard(context, out) } }
        }
    }
}

// ---------- Time zones ----------

private val zoneIds: List<String> by lazy {
    ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted()
}

private val DEFAULT_ZONES = listOf("UTC", "Europe/London", "America/New_York", "America/Los_Angeles", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata", "Asia/Tokyo", "Australia/Sydney")

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val LocalDateSaver = Saver<LocalDate, String>({ it.toString() }, { LocalDate.parse(it) })

private fun zoneLabel(id: String) = id.substringAfterLast('/').replace('_', ' ') + if ('/' in id) " (${id.substringBefore('/')})" else ""

@Composable
private fun TimezoneScreen(tool: Tool, onBack: () -> Unit) {
    val local = remember { ZoneId.systemDefault().id }
    var fromZone by rememberSaveable { mutableStateOf(local) }
    var date by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().format(timeFmt)) }
    var targets by rememberSaveable { mutableStateOf(DEFAULT_ZONES.joinToString(";")) }
    val targetList = targets.split(";").filter { it.isNotEmpty() }

    ToolScaffold(tool.title, onBack) {
        Dropdown("From time zone", (listOf(local) + zoneIds).distinct(), fromZone, ::zoneLabel, { fromZone = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Date", date, { date = it }, Modifier.weight(1f))
            TextInput(time, { time = it }, "Time (HH:MM)", Modifier.weight(1f))
        }
        TextButton(onClick = { date = LocalDate.now(ZoneId.of(fromZone)); time = LocalTime.now(ZoneId.of(fromZone)).format(timeFmt) }) { Text("Now") }
        val parsed = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull()
        if (parsed == null) {
            Note("Use 24-hour time like 14:30.", error = true)
        } else {
            val source = ZonedDateTime.of(date, parsed, ZoneId.of(fromZone))
            ResultCard(
                targetList.map { id ->
                    val z = source.withZoneSameInstant(ZoneId.of(id))
                    val dayShift = ChronoUnit.DAYS.between(source.toLocalDate(), z.toLocalDate())
                    val shift = when { dayShift > 0 -> " (+$dayShift day)"; dayShift < 0 -> " ($dayShift day)"; else -> "" }
                    "${zoneLabel(id)}  UTC${z.offset.id.replace("Z", "+00:00")}" to z.format(timeFmt) + shift
                },
            )
            Dropdown("Add a zone", zoneIds, zoneIds.first(), ::zoneLabel, { id -> if (id !in targetList) targets = (targetList + id).joinToString(";") })
            if (targetList.size > 1) TextButton(onClick = { targets = targetList.dropLast(1).joinToString(";") }) { Text("Remove last zone") }
        }
    }
}

// ---------- Day counter ----------

/** Mon–Fri days in [a, b): whole weeks arithmetically, then at most 6 leftover days. */
internal fun weekdaysBetween(a: LocalDate, b: LocalDate): Long {
    val total = ChronoUnit.DAYS.between(a, b)
    if (total <= 0) return 0
    var count = total / 7 * 5
    var d = a.plusDays(total / 7 * 7)
    while (d.isBefore(b)) {
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) count++
        d = d.plusDays(1)
    }
    return count
}

@Composable
private fun DayCounterScreen(tool: Tool, onBack: () -> Unit) {
    var between by rememberSaveable { mutableStateOf(true) }
    var start by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    var end by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now().plusDays(30)) }
    var includeEnd by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf("100") }
    var subtract by rememberSaveable { mutableStateOf(false) }
    fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    ToolScaffold(tool.title, onBack) {
        OptionChips(listOf(true, false), between, label = { if (it) "Days between dates" else "Add / subtract days" }) { between = it }
        if (between) {
            DateField("Start date", start, { start = it })
            DateField("End date", end, { end = it })
            ToggleChip("Include the end day", includeEnd) { includeEnd = it }
            val (a, b) = if (end.isBefore(start)) end to start else start to end
            val total = ChronoUnit.DAYS.between(a, b) + if (includeEnd) 1 else 0
            BigResult("%,d days".format(total), if (end.isBefore(start)) "End is before start" else "Between the dates")
            val endExclusive = if (includeEnd) b.plusDays(1) else b
            ResultCard(
                listOf(
                    "Weeks" to "${total / 7} weeks, ${total % 7} days",
                    "Months" to ChronoUnit.MONTHS.between(a, b).toString(),
                    "Weekdays (Mon–Fri)" to "%,d".format(weekdaysBetween(a, endExclusive)),
                    "Weekend days" to "%,d".format(total - weekdaysBetween(a, endExclusive)),
                    "Hours" to "%,d".format(total * 24),
                ),
            )
        } else {
            DateField("Start date", start, { start = it })
            OptionChips(listOf(false, true), subtract, label = { if (it) "− Subtract" else "+ Add" }) { subtract = it }
            NumberField(days, { days = it }, "Days", integerOnly = true, allowNegative = false)
            val n = days.toLongOrNull()
            if (n != null && n <= 3_650_000) {
                val result = if (subtract) start.minusDays(n) else start.plusDays(n)
                BigResult(formatDate(result), dayName(result))
            }
        }
    }
}
