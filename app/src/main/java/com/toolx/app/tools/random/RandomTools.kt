package com.toolx.app.tools.random

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.ThumbsUpDown
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object RandomTools : ToolModule {

    private fun t(id: String, title: String, desc: String, icon: ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.RANDOM, available = true, keywords = kw.toList() + listOf("random", "decide", "game"))

    override val tools = listOf(
        t("rand_wheel", "Lucky wheel", "Spin a wheel of your own options", Icons.Rounded.Loop, "spin", "wheel", "picker", "choose"),
        t("rand_coin", "Flip a coin", "Heads or tails", Icons.Rounded.Toll, "coin", "toss", "heads", "tails"),
        t("rand_yesno", "Yes or no", "Let chance decide", Icons.Rounded.ThumbsUpDown, "yes", "no", "answer", "8 ball"),
        t("rand_rps", "Rock paper scissors", "Play against the phone", Icons.Rounded.BackHand, "rps", "rock", "paper", "scissors"),
        t("rand_dice", "Roll dice", "1–6 dice, d4 to d20", Icons.Rounded.Casino, "dice", "die", "d20", "roll"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "rand_wheel" to { tool, back -> WheelScreen(tool, back) },
        "rand_coin" to { tool, back -> CoinScreen(tool, back) },
        "rand_yesno" to { tool, back -> YesNoScreen(tool, back) },
        "rand_rps" to { tool, back -> RpsScreen(tool, back) },
        "rand_dice" to { tool, back -> DiceScreen(tool, back) },
    )
}

@Composable
private fun BigAnswer(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

// ---------- Lucky wheel ----------

private val WHEEL_COLORS = listOf(0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFCA28, 0xFFAB47BC, 0xFF26C6DA, 0xFFFF7043, 0xFF8D6E63).map { Color(it) }

@Composable
private fun WheelScreen(tool: Tool, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("Pizza\nBurger\nSushi\nTacos\nBiryani\nPasta") }
    var removeWinner by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val options = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface

    ToolScaffold(tool.title, onBack) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)) {
                val r = min(size.width, size.height) / 2
                val c = Offset(size.width / 2, size.height / 2)
                if (options.isEmpty()) return@Canvas
                val sweep = 360f / options.size
                rotate(rotation.value, c) {
                    options.forEachIndexed { i, label ->
                        // Segment i spans [i*sweep, (i+1)*sweep) measured clockwise from the top.
                        drawArc(WHEEL_COLORS[i % WHEEL_COLORS.size], -90f + i * sweep, sweep, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                        val mid = Math.toRadians((-90f + (i + 0.5f) * sweep).toDouble())
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE; isAntiAlias = true; textSize = with(density) { 15.sp.toPx() }
                            typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val shown = if (label.length > 12) label.take(11) + "…" else label
                        drawContext.canvas.nativeCanvas.drawText(shown, c.x + r * 0.62f * cos(mid).toFloat(), c.y + r * 0.62f * sin(mid).toFloat() + paint.textSize / 3, paint)
                    }
                }
                drawCircle(onSurface, r, c, style = Stroke(4f))
                drawCircle(Color.White, r * 0.08f, c)
                // Pointer at the top.
                val path = Path().apply { moveTo(c.x - 18f, c.y - r - 14f); lineTo(c.x + 18f, c.y - r - 14f); lineTo(c.x, c.y - r + 22f); close() }
                drawPath(path, onSurface)
            }
        }
        result?.let { BigAnswer(it) }
        Button(
            onClick = {
                if (rotation.isRunning || options.size < 2) return@Button
                result = null
                scope.launch {
                    val target = rotation.value + 360f * Random.nextInt(4, 7) + Random.nextFloat() * 360f
                    rotation.animateTo(target, tween(3500, easing = FastOutSlowInEasing))
                    val sweep = 360f / options.size
                    // The wheel turned clockwise by `target`, so the pointer now sits over this wheel angle.
                    val underPointer = ((360f - target % 360f) % 360f)
                    val winner = options[(underPointer / sweep).toInt().coerceIn(0, options.lastIndex)]
                    result = winner
                    if (removeWinner) text = options.filterIndexed { i, _ -> i != options.indexOf(winner) }.joinToString("\n")
                }
            },
            enabled = options.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Spin") }
        OutlinedTextField(text, { text = it }, label = { Text("Options, one per line") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        ToggleChip("Remove the winner after each spin", removeWinner) { removeWinner = it }
        if (options.size < 2) Note("Add at least two options.", error = true)
    }
}

// ---------- Coin ----------

@Composable
private fun CoinScreen(tool: Tool, onBack: () -> Unit) {
    var heads by rememberSaveable { mutableStateOf(true) }
    var headsCount by rememberSaveable { mutableIntStateOf(0) }
    var tailsCount by rememberSaveable { mutableIntStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    ToolScaffold(tool.title, onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            // Show the face for whichever side is toward the viewer mid-flip.
            val angle = spin.value % 360f
            val showingHeads = if (spin.isRunning) (angle < 90f || angle > 270f) else heads
            Box(
                Modifier.size(180.dp).graphicsLayer { rotationY = spin.value; cameraDistance = 12 * density }
                    .clip(CircleShape).background(if (showingHeads) Color(0xFFFFC107) else Color(0xFFB0BEC5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (showingHeads) "H" else "T", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
            }
        }
        if (flipped && !spin.isRunning) BigAnswer(if (heads) "Heads" else "Tails")
        Button(
            onClick = {
                if (spin.isRunning) return@Button
                val result = Random.nextBoolean()
                scope.launch {
                    // Land so the right face is up: heads at multiples of 360, tails at +180.
                    val base = (spin.value / 360f).toInt() * 360f + 360f * 5
                    spin.animateTo(base + if (result) 0f else 180f, tween(1200, easing = FastOutSlowInEasing))
                    heads = result; flipped = true
                    if (result) headsCount++ else tailsCount++
                    // Reset to face-on; the colour/letter come from `heads` once at rest.
                    spin.snapTo(0f)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Flip") }
        if (headsCount + tailsCount > 0) {
            ResultCard(listOf("Heads" to headsCount.toString(), "Tails" to tailsCount.toString(), "Flips" to (headsCount + tailsCount).toString()))
            TextButton(onClick = { headsCount = 0; tailsCount = 0; flipped = false }) { Text("Reset count") }
        }
    }
}

// ---------- Yes / No ----------

@Composable
private fun YesNoScreen(tool: Tool, onBack: () -> Unit) {
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var maybe by rememberSaveable { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ToolScaffold(tool.title, onBack) {
        Note("Think of a yes-or-no question, then tap Ask.")
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            val color = when (answer) { "Yes" -> Color(0xFF2E7D32); "No" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary }
            BigAnswer(if (thinking) "…" else answer ?: "?", color)
        }
        Button(
            onClick = {
                if (thinking) return@Button
                scope.launch {
                    thinking = true
                    delay(600)
                    answer = (if (maybe) listOf("Yes", "No", "Maybe") else listOf("Yes", "No")).random()
                    thinking = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ask") }
        ToggleChip("Allow \"Maybe\"", maybe) { maybe = it }
    }
}

// ---------- Rock paper scissors ----------

private enum class Hand(val label: String, val emoji: String) {
    ROCK("Rock", "✊"), PAPER("Paper", "✋"), SCISSORS("Scissors", "✌️");
    fun beats(other: Hand) = (this == ROCK && other == SCISSORS) || (this == PAPER && other == ROCK) || (this == SCISSORS && other == PAPER)
}

@Composable
private fun RpsScreen(tool: Tool, onBack: () -> Unit) {
    var you by rememberSaveable { mutableStateOf<Hand?>(null) }
    var phone by rememberSaveable { mutableStateOf<Hand?>(null) }
    var wins by rememberSaveable { mutableIntStateOf(0) }
    var losses by rememberSaveable { mutableIntStateOf(0) }
    var draws by rememberSaveable { mutableIntStateOf(0) }
    ToolScaffold(tool.title, onBack) {
        val y = you; val p = phone
        if (y != null && p != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(y.emoji, fontSize = 64.sp); Text("You") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(p.emoji, fontSize = 64.sp); Text("Phone") }
            }
            BigAnswer(
                when { y == p -> "Draw"; y.beats(p) -> "You win!"; else -> "You lose" },
                when { y == p -> MaterialTheme.colorScheme.tertiary; y.beats(p) -> Color(0xFF2E7D32); else -> MaterialTheme.colorScheme.error },
            )
        } else {
            Note("Pick your hand. The phone picks at the same moment.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Hand.entries.forEach { h ->
                FilledTonalButton(
                    onClick = {
                        val c = Hand.entries.random()
                        you = h; phone = c
                        when { h == c -> draws++; h.beats(c) -> wins++; else -> losses++ }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("${h.emoji} ${h.label}", maxLines = 1) }
            }
        }
        if (wins + losses + draws > 0) {
            ResultCard(listOf("Wins" to wins.toString(), "Losses" to losses.toString(), "Draws" to draws.toString()))
            TextButton(onClick = { wins = 0; losses = 0; draws = 0; you = null; phone = null }) { Text("Reset score") }
        }
    }
}

// ---------- Dice ----------

@Composable
private fun DiceScreen(tool: Tool, onBack: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var sides by rememberSaveable { mutableIntStateOf(6) }
    var values by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var rolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ToolScaffold(tool.title, onBack) {
        SectionLabel("Dice")
        OptionChips((1..6).toList(), count, label = { "$it" }) { count = it; values = emptyList() }
        SectionLabel("Sides")
        OptionChips(listOf(4, 6, 8, 10, 12, 20, 100), sides, label = { "d$it" }) { sides = it; values = emptyList() }
        if (values.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                values.forEach { v -> Die(v, sides, Modifier.weight(1f, fill = false)) }
            }
            if (!rolling && values.size > 1) BigAnswer("Total ${values.sum()}")
        }
        Button(
            onClick = {
                if (rolling) return@Button
                scope.launch {
                    rolling = true
                    repeat(10) { values = List(count) { Random.nextInt(1, sides + 1) }; delay(60) }
                    values = List(count) { Random.nextInt(1, sides + 1) }
                    rolling = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Roll") }
    }
}

private val PIPS = mapOf(
    1 to listOf(1 to 1), 2 to listOf(0 to 0, 2 to 2), 3 to listOf(0 to 0, 1 to 1, 2 to 2),
    4 to listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2), 5 to listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2),
    6 to listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2),
)

@Composable
private fun Die(value: Int, sides: Int, modifier: Modifier = Modifier) {
    val face = MaterialTheme.colorScheme.surfaceVariant
    val pip = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(face), contentAlignment = Alignment.Center) {
        if (sides == 6) {
            Canvas(Modifier.size(56.dp).padding(10.dp)) {
                val step = size.width / 2
                PIPS[value]?.forEach { (cx, cy) -> drawCircle(pip, size.width / 10, Offset(cx * step, cy * step)) }
            }
        } else {
            Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
