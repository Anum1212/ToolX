package com.toolx.app.tools.video

import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.toolx.app.ui.common.PlaybackTuning
import com.toolx.app.ui.common.rememberPreviewPlayer

/**
 * Inline ExoPlayer preview. When [seekToMs] changes the player jumps there,
 * which the trim screen uses to show the selected start/end frame.
 * [speed], [pitch] and [volume] let tools preview their effect live.
 */
@Composable
fun VideoPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    seekToMs: Long? = null,
    loop: Boolean = false,
    speed: Float = 1f,
    pitch: Float = 1f,
    volume: Float = 1f,
) {
    val player = rememberPreviewPlayer(uri, loop)
    PlaybackTuning(player, speed, pitch, volume)

    LaunchedEffect(player, seekToMs) {
        seekToMs?.let { player.seekTo(it) }
    }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp)),
    )
}
