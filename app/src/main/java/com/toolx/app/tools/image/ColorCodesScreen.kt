package com.toolx.app.tools.image

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import kotlin.math.roundToInt

private const val PREFS = "color_codes"
private const val MAX_FAVOURITES = 16

private fun loadFavourites(context: Context): List<Int> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("favourites", "").orEmpty()
        .split(',').mapNotNull { ColorMath.parseHex(it) }

private fun saveFavourites(context: Context, list: List<Int>) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("favourites", list.joinToString(",") { ColorMath.hex(it) }).apply()

@Composable
fun ColorCodesScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    // HSV is the source of truth, so dragging through greys doesn't lose the hue.
    var hue by rememberSaveable { mutableFloatStateOf(211f) }
    var sat by rememberSaveable { mutableFloatStateOf(0.78f) }
    var value by rememberSaveable { mutableFloatStateOf(0.85f) }
    val color = ColorMath.fromHsv(hue, sat, value)
    var hexText by rememberSaveable { mutableStateOf(ColorMath.hex(color)) }
    var rText by rememberSaveable { mutableStateOf(ColorMath.r(color).toString()) }
    var gText by rememberSaveable { mutableStateOf(ColorMath.g(color).toString()) }
    var bText by rememberSaveable { mutableStateOf(ColorMath.b(color).toString()) }
    var favourites by remember { mutableStateOf(loadFavourites(context)) }

    /** Moves the picker to [c] and refreshes every text field. */
    fun select(c: Int, keepHue: Boolean = false) {
        val hsv = ColorMath.toHsv(c)
        if (!keepHue || hsv[1] > 0f) hue = hsv[0]
        sat = hsv[1]; value = hsv[2]
        hexText = ColorMath.hex(c); rText = ColorMath.r(c).toString(); gText = ColorMath.g(c).toString(); bText = ColorMath.b(c).toString()
    }
    /** After a drag on the square or hue bar: rewrite the text fields from the new HSV. */
    fun fromPicker() {
        val c = ColorMath.fromHsv(hue, sat, value)
        hexText = ColorMath.hex(c); rText = ColorMath.r(c).toString(); gText = ColorMath.g(c).toString(); bText = ColorMath.b(c).toString()
    }

    ToolScaffold(tool.title, onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)))
            Column {
                Text(ColorMath.hex(color), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("rgb(${ColorMath.r(color)}, ${ColorMath.g(color)}, ${ColorMath.b(color)})", fontFamily = FontFamily.Monospace)
            }
        }

        SatValSquare(hue, sat, value) { s, v -> sat = s; value = v; fromPicker() }
        HueBar(hue) { hue = it; fromPicker() }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                hexText,
                { t -> hexText = t; ColorMath.parseHex(t)?.let { c -> select(c, keepHue = true); hexText = t } },
                label = { Text("HEX") }, singleLine = true,
                isError = ColorMath.parseHex(hexText) == null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1.6f),
            )
            listOf(Triple("R", rText, 0), Triple("G", gText, 1), Triple("B", bText, 2)).forEach { (label, text, i) ->
                OutlinedTextField(
                    text,
                    { t ->
                        val clean = t.filter { it.isDigit() }.take(3)
                        when (i) { 0 -> rText = clean; 1 -> gText = clean; else -> bText = clean }
                        val r = rText.toIntOrNull(); val g = gText.toIntOrNull(); val b = bText.toIntOrNull()
                        if (r != null && g != null && b != null && r <= 255 && g <= 255 && b <= 255) {
                            val c = ColorMath.rgb(r, g, b)
                            val keep = Triple(rText, gText, bText)
                            select(c, keepHue = true)
                            rText = keep.first; gText = keep.second; bText = keep.third
                        }
                    },
                    label = { Text(label) }, singleLine = true,
                    isError = text.toIntOrNull()?.let { it > 255 } ?: true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ResultCard(
            listOf(
                "HEX" to ColorMath.hex(color),
                "RGB" to ImageOps.rgb(color),
                "HSL" to ImageOps.hslText(color),
                "CMYK" to ImageOps.cmyk(color),
                "Android / Flutter" to "0xFF" + ColorMath.hex(color).drop(1),
            ),
            title = "Tap a row to copy it",
        )

        SectionLabel("Text on this colour")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFFFFFFFF.toInt() to "White", 0xFF000000.toInt() to "Black").forEach { (fg, name) ->
                val ratio = ColorMath.contrast(color, fg)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(color)).padding(12.dp),
                ) {
                    Text("$name text", color = Color(fg), fontWeight = FontWeight.Bold)
                    Text("%.1f : 1 · %s".format(ratio, if (ratio >= 4.5) "readable" else if (ratio >= 3) "large text only" else "hard to read"), color = Color(fg), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        SectionLabel("Lighter and darker (tap to pick)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorMath.scale(color).forEach { c ->
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(c))
                        .then(if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { select(c) },
                )
            }
        }

        SectionLabel("Saved colours")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val saved = color in favourites
            OutlinedButton(onClick = {
                favourites = if (saved) favourites - color else (listOf(color) + favourites).take(MAX_FAVOURITES)
                saveFavourites(context, favourites)
            }) { Text(if (saved) "Remove" else "Save") }
            favourites.forEach { c ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(c)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .pointerInput(c) { detectTapGestures(onTap = { select(c) }, onLongPress = { copyToClipboard(context, ColorMath.hex(c)) }) },
                )
            }
        }
        Note(if (favourites.isEmpty()) "Save colours you use often. They stay on this phone." else "Tap a saved colour to pick it; hold it to copy its HEX code.")
        Note("To get a colour from a photo, use Image › Color picker.")
    }
}

/** Saturation left→right, brightness top→bottom, for the current hue. */
@Composable
private fun SatValSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val update by rememberUpdatedState(onChange)
    val pure = Color(ColorMath.fromHsv(hue, 1f, 1f))
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun send(o: Offset) = update((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                detectTapGestures { send(it) }
            }
            .pointerInput(Unit) {
                fun send(o: Offset) = update((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                detectDragGestures(onDragStart = { send(it) }) { change, _ -> change.consume(); send(change.position) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val c = Offset(sat * size.width, (1 - value) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.5f), 13.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val update by rememberUpdatedState(onChange)
    val rainbow = remember { (0..6).map { Color(ColorMath.fromHsv(it * 60f, 1f, 1f)) } }
    Canvas(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGestures { update((it.x / size.width).coerceIn(0f, 1f) * 359.9f) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { update((it.x / size.width).coerceIn(0f, 1f) * 359.9f) }) { change, _ ->
                    change.consume(); update((change.position.x / size.width).coerceIn(0f, 1f) * 359.9f)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(rainbow))
        val x = hue / 360f * size.width
        drawCircle(Color.White, size.height / 2 - 3.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
