package com.toolx.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolIds
import com.toolx.app.tools.ToolRegistry
import com.toolx.app.tools.audio.AudioCutScreen
import com.toolx.app.tools.audio.AudioMergeScreen
import com.toolx.app.tools.audio.AudioSpeedScreen
import com.toolx.app.tools.audio.AudioTrimScreen
import com.toolx.app.tools.audio.AudioVolumeScreen
import com.toolx.app.tools.video.VideoCropScreen
import com.toolx.app.tools.video.VideoMergeScreen
import com.toolx.app.tools.video.VideoSpeedScreen
import com.toolx.app.tools.video.VideoSplitScreen
import com.toolx.app.tools.video.VideoVolumeScreen
import com.toolx.app.tools.image.ImageConvertScreen
import com.toolx.app.tools.image.ImageCropScreen
import com.toolx.app.tools.image.PassportPhotoScreen
import com.toolx.app.tools.image.RemoveBackgroundScreen
import com.toolx.app.tools.image.ImageResizeScreen
import com.toolx.app.tools.image.ImageRotateScreen
import com.toolx.app.tools.video.VideoCompressScreen
import com.toolx.app.tools.video.VideoExtractAudioScreen
import com.toolx.app.tools.video.VideoMuteScreen
import com.toolx.app.tools.video.VideoTrimScreen
import com.toolx.app.ui.common.ComingSoonScreen
import com.toolx.app.ui.home.HomeScreen

private const val HOME = "home"
private const val TOOL = "tool/{id}"

@Composable
fun ToolXNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onOpenTool = { nav.navigate("tool/${it.id}") })
        }
        composable(TOOL) { entry ->
            val tool = ToolRegistry.find(entry.arguments?.getString("id"))
            val back: () -> Unit = { nav.popBackStack() }
            if (tool == null) {
                LaunchedEffect(Unit) { back() }
            } else {
                ToolScreen(tool, back)
            }
        }
    }
}

/** Maps a tool id to its screen. Tools without a screen yet get the placeholder. */
@Composable
private fun ToolScreen(tool: Tool, onBack: () -> Unit) {
    when (tool.id) {
        ToolIds.VIDEO_TRIM -> VideoTrimScreen(tool, onBack)
        ToolIds.VIDEO_COMPRESS -> VideoCompressScreen(tool, onBack)
        ToolIds.VIDEO_MUTE -> VideoMuteScreen(tool, onBack)
        ToolIds.VIDEO_EXTRACT_AUDIO -> VideoExtractAudioScreen(tool, onBack)
        ToolIds.VIDEO_VOLUME -> VideoVolumeScreen(tool, onBack)
        ToolIds.VIDEO_SPEED -> VideoSpeedScreen(tool, onBack)
        ToolIds.VIDEO_CROP -> VideoCropScreen(tool, onBack)
        ToolIds.VIDEO_SPLIT -> VideoSplitScreen(tool, onBack)
        ToolIds.VIDEO_MERGE -> VideoMergeScreen(tool, onBack)
        ToolIds.AUDIO_TRIM -> AudioTrimScreen(tool, onBack)
        ToolIds.AUDIO_CUT -> AudioCutScreen(tool, onBack)
        ToolIds.AUDIO_MERGE -> AudioMergeScreen(tool, onBack)
        ToolIds.AUDIO_SPEED -> AudioSpeedScreen(tool, onBack)
        ToolIds.AUDIO_VOLUME -> AudioVolumeScreen(tool, onBack)
        ToolIds.IMAGE_RESIZE -> ImageResizeScreen(tool, onBack)
        ToolIds.IMAGE_CONVERT -> ImageConvertScreen(tool, onBack)
        ToolIds.IMAGE_ROTATE -> ImageRotateScreen(tool, onBack)
        ToolIds.IMAGE_CROP -> ImageCropScreen(tool, onBack)
        ToolIds.IMAGE_REMOVE_BG -> RemoveBackgroundScreen(tool, onBack)
        ToolIds.IMAGE_PASSPORT -> PassportPhotoScreen(tool, onBack)
        else -> ToolRegistry.moduleScreen(tool.id)?.invoke(tool, onBack) ?: ComingSoonScreen(tool, onBack)
    }
}
