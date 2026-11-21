package com.toolx.app.ui.common

import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.toolx.app.core.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.roundToInt

/** An ExoPlayer for previews: released when it leaves composition, paused when the app goes to the background. */
@Composable
fun rememberPreviewPlayer(uri: Uri, loop: Boolean = false): ExoPlayer {
    val context = LocalContext.current
    val player = remember(uri, loop) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            if (loop) {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
            prepare()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/**
 * Applies the export's speed, pitch and volume to a preview player so the user
 * hears the result before saving. Volume above 1 uses the platform
 * LoudnessEnhancer, since player volume alone can't go past 100%.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlaybackTuning(player: ExoPlayer, speed: Float = 1f, pitch: Float = 1f, volume: Float = 1f) {
    LaunchedEffect(player, speed, pitch) {
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }
    val enhancer = remember(player) {
        runCatching { LoudnessEnhancer(player.audioSessionId) }.getOrNull()
    }
    DisposableEffect(enhancer) { onDispose { enhancer?.release() } }
    LaunchedEffect(player, enhancer, volume) {
        player.volume = volume.coerceIn(0f, 1f)
        enhancer?.runCatching {
            if (volume > 1f) {
                setTargetGain((2000 * log10(volume)).roundToInt()) // millibels
                enabled = true
            } else {
                enabled = false
            }
        }
    }
}

/** Compact audio player: play/pause, a seek bar and times. */
@Composable
fun AudioPreview(
    uri: Uri,
    seekToMs: Long? = null,
    speed: Float = 1f,
    pitch: Float = 1f,
    volume: Float = 1f,
) {
    val player = rememberPreviewPlayer(uri)
    PlaybackTuning(player, speed, pitch, volume)
    LaunchedEffect(player, seekToMs) { seekToMs?.let { player.seekTo(it) } }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(200)
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledIconButton(onClick = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            }) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
            Column(Modifier.weight(1f)) {
                Slider(
                    value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { f ->
                        if (duration > 0) {
                            position = (f * duration).toLong()
                            player.seekTo(position)
                        }
                    },
                    enabled = duration > 0,
                )
                Row {
                    Text(formatDuration(position), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
