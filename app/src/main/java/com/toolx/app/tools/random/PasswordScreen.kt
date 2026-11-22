package com.toolx.app.tools.random

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.random.PasswordGen.CharOptions
import com.toolx.app.tools.random.PasswordGen.Strength
import com.toolx.app.tools.random.PasswordGen.WordOptions
import com.toolx.app.ui.common.ChipRow
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import androidx.compose.foundation.systemGestureExclusion
import java.security.SecureRandom
import kotlin.math.roundToInt

private enum class PwMode(val label: String) { RANDOM("Random"), EASY("Easy to type"), PIN("PIN") }

/** Copies as sensitive, so Android 13+ hides it in the clipboard preview and keyboard suggestions. */
private fun copySecret(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Password", text)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    else clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun PasswordScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val rng = remember { SecureRandom() }
    var mode by rememberSaveable { mutableStateOf(PwMode.RANDOM) }
    var length by rememberSaveable { mutableIntStateOf(16) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var avoid by rememberSaveable { mutableStateOf(false) }
    var words by rememberSaveable { mutableIntStateOf(4) }
    var separator by rememberSaveable { mutableStateOf("-") }
    var pinLength by rememberSaveable { mutableIntStateOf(6) }
    var seed by remember { mutableIntStateOf(0) }

    val charOptions = CharOptions(length, lower, upper, digits, symbols, avoid)
    val wordOptions = WordOptions(words = words, separator = separator)
    val bits = when (mode) {
        PwMode.RANDOM -> PasswordGen.bits(charOptions)
        PwMode.EASY -> PasswordGen.bits(wordOptions)
        PwMode.PIN -> PasswordGen.pinBits(pinLength)
    }
    fun make(): String = when (mode) {
        PwMode.RANDOM -> PasswordGen.characters(charOptions, rng)
        PwMode.EASY -> PasswordGen.easy(wordOptions, rng)
        PwMode.PIN -> PasswordGen.pin(pinLength, rng)
    }
    // A new set whenever an option changes or "New" is tapped. Nothing is stored.
    var results by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(mode, charOptions, wordOptions, pinLength, seed) { results = List(5) { make() } }

    ToolScaffold(tool.title, onBack) {
        OptionChips(PwMode.entries, mode, label = { it.label }) { mode = it }
        val main = results.firstOrNull().orEmpty()
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    main.ifEmpty { "Choose at least one kind of character" },
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                StrengthBar(bits)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { copySecret(context, main) }, enabled = main.isNotEmpty()) { Text("Copy") }
                    OutlinedButton(onClick = { seed++ }) { Text("New") }
                }
            }
        }

        when (mode) {
            PwMode.RANDOM -> {
                SectionLabel("Length $length")
                Slider(length.toFloat(), { length = it.roundToInt() }, valueRange = 6f..64f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
                ChipRow {
                    ToggleChip("a–z", lower) { lower = it }
                    ToggleChip("A–Z", upper) { upper = it }
                    ToggleChip("0–9", digits) { digits = it }
                    ToggleChip("!@#", symbols) { symbols = it }
                }
                ToggleChip("Avoid look-alikes (I l 1 O 0 o)", avoid) { avoid = it }
            }
            PwMode.EASY -> {
                SectionLabel("Words $words")
                Slider(words.toFloat(), { words = it.roundToInt() }, valueRange = 2f..8f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
                SectionLabel("Between words")
                OptionChips(listOf("-", ".", "_", " "), separator, label = { if (it == " ") "Space" else it }) { separator = it }
                Note("Made-up words of three syllables, easy to read out and type on a phone, plus a two-digit number.")
            }
            PwMode.PIN -> {
                SectionLabel("Digits $pinLength")
                Slider(pinLength.toFloat(), { pinLength = it.roundToInt() }, valueRange = 4f..12f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
                Note("PINs are only safe where the device locks after a few wrong tries, like a phone or bank card.")
            }
        }

        if (results.size > 1 && main.isNotEmpty()) {
            SectionLabel("More options (tap to copy)")
            results.drop(1).forEach { p ->
                Text(
                    p,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { copySecret(context, p) }.padding(vertical = 8.dp),
                )
            }
        }
        Note("Made on your phone with a secure random generator. Nothing is saved or sent anywhere.")
    }
}

@Composable
private fun StrengthBar(bits: Double) {
    val s = PasswordGen.strength(bits)
    val color = when (s) {
        Strength.WEAK -> Color(0xFFD32F2F)
        Strength.FAIR -> Color(0xFFF9A825)
        Strength.STRONG -> Color(0xFF388E3C)
        Strength.VERY_STRONG -> Color(0xFF1B5E20)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { (bits / 100.0).toFloat().coerceIn(0.05f, 1f) }, color = color, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text("  ·  ${bits.roundToInt()} bits", style = MaterialTheme.typography.labelMedium)
        }
    }
}
