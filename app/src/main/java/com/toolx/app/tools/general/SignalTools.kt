package com.toolx.app.tools.general


import android.content.Context

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ChipRow
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn

/** The back camera's flash used as a torch. setTorchMode needs no CAMERA permission. */
private class Torch(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    val id: String? = runCatching {
        val withFlash = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }.getOrNull()

    val available get() = id != null

    /** Brightness steps the flash supports (Android 13+); 1 means on/off only. */
    val maxLevel: Int = if (Build.VERSION.SDK_INT >= 33) {
        id?.let { runCatching { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) }.getOrNull() } ?: 1
    } else 1

    /** False if the camera is busy (another app has it) or there's no flash. */
    fun set(on: Boolean): Boolean { val cam = id ?: return false; return runCatching { manager.setTorchMode(cam, on) }.isSuccess }

    /** On at [level] (1..[maxLevel]); falls back to plain on where levels aren't supported. */
    fun on(level: Int): Boolean {
        val cam = id ?: return false
        return if (Build.VERSION.SDK_INT >= 33 && maxLevel > 1) {
            runCatching { manager.turnOnTorchWithStrengthLevel(cam, level.coerceIn(1, maxLevel)) }.isSuccess
        } else set(true)
    }
}

// ---------- Flashlight ----------

@Composable
internal fun FlashlightScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val torch = remember { Torch(context.applicationContext) }
    var on by remember { mutableStateOf(false) }
    var strobe by rememberSaveable { mutableStateOf(false) }
    var hz by rememberSaveable { mutableIntStateOf(5) }
    var level by rememberSaveable { mutableIntStateOf(torch.maxLevel) }
    var screen by rememberSaveable { mutableStateOf(!torch.available) }
    var lit by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val view = LocalView.current

    // One effect owns the torch: steady light, or a clock-timed strobe at 50% duty.
    LaunchedEffect(on, strobe, hz, level, screen) {
        failed = false
        if (!on) { lit = false; torch.set(false); return@LaunchedEffect }
        val useTorch = !screen && torch.available
        try {
            if (!strobe) {
                lit = true
                if (useTorch && !torch.on(level)) failed = true
                awaitCancellation()
            }
            withContext(Dispatchers.Default) {
                val half = 500L / hz
                var next = SystemClock.elapsedRealtime()
                while (true) {
                    lit = !lit
                    if (useTorch) { if (lit) torch.on(level) else torch.set(false) }
                    next += half
                    delay(next - SystemClock.elapsedRealtime())
                }
            }
        } finally {
            lit = false
            torch.set(false)
        }
    }
    DisposableEffect(Unit) { onDispose { torch.set(false); view.keepScreenOn = false } }
    view.keepScreenOn = on

    ToolScaffold(tool.title, onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = { on = !on },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.size(200.dp),
            ) {
                androidx.compose.material3.Icon(
                    if (lit) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = if (on) "Turn off" else "Turn on",
                    modifier = Modifier.size(96.dp),
                )
            }
        }
        SectionLabel("Light from")
        ChipRow {
            if (torch.available) ToggleChip("Flashlight", !screen) { screen = !it }
            ToggleChip("Screen", screen) { screen = it }
            ToggleChip("Strobe", strobe) { strobe = it }
        }
        if (strobe) {
            Text("Strobe $hz flashes per second", style = MaterialTheme.typography.labelMedium)
            Slider(hz.toFloat(), { hz = it.roundToInt() }, valueRange = 1f..15f)
            Note("Strobe light can trigger seizures in people with photosensitive epilepsy.", error = true)
        }
        if (!screen && torch.maxLevel > 1) {
            Text("Brightness $level of ${torch.maxLevel}", style = MaterialTheme.typography.labelMedium)
            Slider(level.toFloat(), { level = it.roundToInt() }, valueRange = 1f..torch.maxLevel.toFloat())
        }
        if (failed) Note("Couldn't turn on the flashlight. Close any app using the camera and try again.", error = true)
        if (!torch.available) Note("This phone has no flashlight, so the screen is used as the light.")
    }

    if (on && screen) ScreenLight(lit) { on = false }
}

/** Full-screen white light at full brightness. Tap or Back calls [onStop]. */
@Composable
private fun ScreenLight(lit: Boolean, onStop: () -> Unit) {
    Dialog(onDismissRequest = onStop, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog is the top window, so its brightness is the one that counts.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w ->
                w.attributes = w.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {}
        }
        Box(
            Modifier.fillMaxSize().background(if (lit) Color.White else Color.Black).clickable { onStop() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text("Tap to stop", color = Color.Gray, modifier = Modifier.padding(bottom = 72.dp))
        }
    }
}

/**
 * Plays Morse [GeneralOps.Pulse]s on any mix of torch, tone and screen. [lit] drives the on-screen
 * lamp. Timing is measured against the clock, so long messages don't drift.
 */
@Stable
internal class Signal(context: Context, private val scope: CoroutineScope, stream: Int, volume: Int, private val toneType: Int) {
    private val torch = Torch(context)
    private val tone = runCatching { ToneGenerator(stream, volume) }.getOrNull()
    val hasTorch get() = torch.available
    val hasSound get() = tone != null

    var lit by mutableStateOf(false); private set
    var job by mutableStateOf<Job?>(null); private set
    var torchFailed by mutableStateOf(false); private set
    val running get() = job != null

    fun start(pulses: List<GeneralOps.Pulse>, unitMs: Long, repeat: Boolean, useTorch: Boolean, useSound: Boolean) {
        stop()
        if (pulses.isEmpty()) return
        torchFailed = false
        job = scope.launch(Dispatchers.Default) {
            try {
                var next = SystemClock.elapsedRealtime()
                do {
                    for (p in pulses) {
                        level(p.on, useTorch, useSound)
                        next += p.units * unitMs
                        delay(next - SystemClock.elapsedRealtime())
                    }
                    level(false, useTorch, useSound)
                    if (repeat) { next += 7 * unitMs; delay(next - SystemClock.elapsedRealtime()) }
                } while (repeat)
            } finally {
                level(false, useTorch, useSound)
                job = null
            }
        }
    }

    private fun level(on: Boolean, useTorch: Boolean, useSound: Boolean) {
        if (on == lit) return
        lit = on
        if (useTorch && !torch.set(on) && on) torchFailed = true
        if (useSound) { if (on) tone?.startTone(toneType) else tone?.stopTone() }
    }

    fun stop() {
        job?.cancel()
        job = null
        lit = false
        torch.set(false)
        tone?.stopTone()
    }

    fun release() { stop(); tone?.release() }
}

@Composable
private fun rememberSignal(stream: Int = AudioManager.STREAM_MUSIC, volume: Int = 80, toneType: Int = ToneGenerator.TONE_DTMF_9): Signal {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signal = remember { Signal(context.applicationContext, scope, stream, volume, toneType) }
    val view = LocalView.current
    DisposableEffect(Unit) { onDispose { signal.release(); view.keepScreenOn = false } }
    view.keepScreenOn = signal.running
    return signal
}

/** A round lamp that lights with the signal. */
@Composable
private fun Lamp(lit: Boolean, size: Int = 56) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
    )
}

// ---------- Morse code ----------

@Composable
internal fun MorseScreen(tool: Tool, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("SOS") }
    var sound by rememberSaveable { mutableStateOf(true) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var wpm by rememberSaveable { mutableIntStateOf(12) }
    val isMorse = GeneralOps.looksLikeMorse(input)
    val output = if (isMorse) GeneralOps.fromMorse(input) else GeneralOps.toMorse(input)
    val pulses = GeneralOps.timeline(if (isMorse) input else output)
    val context = LocalContext.current
    val signal = rememberSignal()

    ToolScaffold(tool.title, onBack) {
        OutlinedTextField(input, { input = it; signal.stop() }, label = { Text("Text or Morse") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Note(if (isMorse) "Reading Morse → text. Separate letters with spaces and words with /." else "Writing text → Morse.")
        BigResultText(output) { copyToClipboard(context, output) }

        SectionLabel("Send it as")
        ChipRow {
            if (signal.hasSound) ToggleChip("Beeps", sound) { sound = it; signal.stop() }
            if (signal.hasTorch) ToggleChip("Flashlight", flash) { flash = it; signal.stop() }
            ToggleChip("Screen", screen) { screen = it; signal.stop() }
            ToggleChip("Repeat", repeat) { repeat = it; signal.stop() }
        }
        Text("Speed $wpm words per minute", style = MaterialTheme.typography.labelMedium)
        Slider(wpm.toFloat(), { wpm = it.toInt(); signal.stop() }, valueRange = 5f..30f)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (signal.running) signal.stop() else signal.start(pulses, GeneralOps.unitMillis(wpm), repeat, flash, sound) },
                enabled = signal.running || (pulses.isNotEmpty() && (sound || flash || screen)),
            ) { Text(if (signal.running) "Stop" else "Play") }
            Lamp(signal.lit, size = 32)
        }
        if (screen) {
            Box(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (signal.lit) Color.White else Color.Black),
            )
        }
        if (signal.torchFailed) Note("Couldn't turn on the flashlight. Close any app using the camera and try again.", error = true)
        if (!signal.hasTorch) Note("This phone has no flashlight, so only beeps and the screen are available.")
    }
}

// ---------- SOS signal ----------

private enum class SosSpeed(val label: String, val unitMs: Long) { SLOW("Slow", 300), NORMAL("Normal", 200), FAST("Fast", 120) }

@Composable
internal fun SosScreen(tool: Tool, onBack: () -> Unit) {
    // Alarm stream at full volume: it's meant to be heard.
    val signal = rememberSignal(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME, ToneGenerator.TONE_DTMF_D)
    var flash by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf(!signal.hasTorch) }
    var sound by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableStateOf(SosSpeed.NORMAL) }
    val pulses = remember { GeneralOps.timeline(GeneralOps.SOS) }
    val useFlash = flash && signal.hasTorch

    fun start() = signal.start(pulses, speed.unitMs, repeat = true, useTorch = useFlash, useSound = sound)

    ToolScaffold(tool.title, onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = { if (signal.running) signal.stop() else start() },
                enabled = signal.running || useFlash || screen || sound,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (signal.lit) Color(0xFFFF5252) else MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.size(200.dp),
            ) {
                Text(if (signal.running) "STOP" else "SOS", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        Note("Repeats ··· ——— ··· until you stop it. The screen stays on while it runs.")
        SectionLabel("Signal with")
        ChipRow {
            if (signal.hasTorch) ToggleChip("Flashlight", flash) { flash = it; signal.stop() }
            ToggleChip("Screen", screen) { screen = it; signal.stop() }
            if (signal.hasSound) ToggleChip("Alarm sound", sound) { sound = it; signal.stop() }
        }
        SectionLabel("Speed")
        OptionChips(SosSpeed.entries, speed, label = { it.label }) { speed = it; if (signal.running) start() }
        if (signal.torchFailed) Note("Couldn't turn on the flashlight. Close any app using the camera and try again.", error = true)
        if (!signal.hasTorch) Note("This phone has no flashlight; the screen flashes instead.")
        if (sound) Note("The alarm plays at alarm volume, even if media is muted.")
    }

    // Full-screen flashing, at full brightness. Tap or Back to stop.
    if (signal.running && screen) ScreenLight(signal.lit) { signal.stop() }
}
