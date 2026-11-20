package com.toolx.app.tools.image

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.toolx.app.core.formatBytes
import com.toolx.app.ui.common.rememberContentSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Debounce between the last option change and re-rendering the preview. */
private const val PREVIEW_DEBOUNCE_MS = 150L

/**
 * Live "what you'll get" preview for image tools.
 *
 * Shows the encoded result (real scaling and compression artifacts) and the
 * expected output size. Press and hold the image to compare with the original.
 */
@Composable
fun LiveImagePreview(
    uri: Uri,
    originalSize: Size?,
    targetWidth: Int?,
    targetHeight: Int?,
    format: OutputFormat,
    quality: Int,
) {
    val context = LocalContext.current
    var working by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        working = runCatching { ImageEngine.loadWorkingCopy(context, uri) }.getOrNull()
    }

    var result by remember(uri) { mutableStateOf<ImagePreviewResult?>(null) }
    var rendering by remember { mutableStateOf(false) }
    LaunchedEffect(working, originalSize, targetWidth, targetHeight, format, quality) {
        val w = working ?: return@LaunchedEffect
        val o = originalSize ?: return@LaunchedEffect
        if (targetWidth == null || targetHeight == null || targetWidth <= 0 || targetHeight <= 0) return@LaunchedEffect
        // LaunchedEffect restarts on every change, so this delay doubles as a debounce.
        delay(PREVIEW_DEBOUNCE_MS)
        rendering = true
        try {
            result = runCatching { ImageEngine.renderPreview(w, o, targetWidth, targetHeight, format, quality) }.getOrNull()
        } finally {
            rendering = false
        }
    }

    var showOriginal by remember { mutableStateOf(false) }
    val originalBytes = rememberContentSize(uri)

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
        ) {
            val r = result
            if (showOriginal || r == null) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Original image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Image(
                    bitmap = r.image,
                    contentDescription = "Preview of result",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                if (showOriginal || r == null) "Original" else "Preview · hold to compare",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (rendering) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        result?.let { r -> EstimateLine(r, originalBytes) }
    }
}

@Composable
private fun EstimateLine(r: ImagePreviewResult, originalBytes: Long?) {
    val sizeText = (if (r.exact) "" else "≈ ") + formatBytes(r.bytes)
    val change = originalBytes?.takeIf { it > 0 }?.let { orig ->
        val pct = ((r.bytes - orig) * 100.0 / orig).roundToInt()
        when {
            pct < 0 -> "$pct% smaller".removePrefix("-")
            pct > 0 -> "$pct% larger"
            else -> "same size"
        }
    }
    Text(
        buildString {
            append("Output: ")
            append(sizeText)
            append(" · ${r.width}×${r.height}")
            change?.let { append(" · $it") }
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
