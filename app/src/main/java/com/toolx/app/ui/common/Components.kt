package com.toolx.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.ProcessingState
import com.toolx.app.core.SavedMedia
import com.toolx.app.core.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Picking ----------

enum class PickType { VIDEO, IMAGE }

/** Returns a function that opens the system photo picker. No permissions needed. */
@Composable
fun rememberMediaPicker(type: PickType, onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val request = when (type) {
        PickType.VIDEO -> PickVisualMediaRequest(PickVisualMedia.VideoOnly)
        PickType.IMAGE -> PickVisualMediaRequest(PickVisualMedia.ImageOnly)
    }
    return { launcher.launch(request) }
}

/** Big tappable card shown before anything is selected. */
@Composable
fun PickPrompt(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** "Original: 12.3 MB · 1920×1080" line with a Change button. */
@Composable
fun SelectedInfo(uri: Uri, details: String?, onChange: () -> Unit) {
    val size = rememberContentSize(uri)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(size?.let(::formatBytes), details).joinToString(" · ").ifEmpty { "Selected" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChange) { Text("Change") }
    }
}

@Composable
fun rememberContentSize(uri: Uri): Long? {
    val context = LocalContext.current
    var size by remember(uri) { mutableStateOf<Long?>(null) }
    LaunchedEffect(uri) { size = withContext(Dispatchers.IO) { MediaSaver.sizeOf(context, uri) } }
    return size
}

@Composable
fun ImagePreview(model: Any, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp)),
    )
}

// ---------- Options ----------

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun <T> OptionChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

// ---------- Export + result ----------

@Composable
fun ExportButton(label: String, state: ProcessingState, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && state !is ProcessingState.Running,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label) }
}

@Composable
fun ProcessingStatus(state: ProcessingState, onCancel: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        ProcessingState.Idle -> Unit

        is ProcessingState.Running -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val p = state.progress
                Text(if (p == null) "Working…" else "Working… ${(p * 100).toInt()}%")
                if (p == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }

        is ProcessingState.Done -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("Saved", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    listOfNotNull(state.media.displayName, state.media.sizeBytes?.let(::formatBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("In ${state.media.location}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openMedia(context, state.media) }) { Text("Open") }
                    OutlinedButton(onClick = { shareMedia(context, state.media) }) { Text("Share") }
                }
            }
        }

        is ProcessingState.Failed -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun openMedia(context: Context, media: SavedMedia) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(media.uri, media.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMedia(context: Context, media: SavedMedia) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(media.mimeType)
        .putExtra(Intent.EXTRA_STREAM, media.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
