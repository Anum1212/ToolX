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
import com.toolx.app.core.AppSettings
import com.toolx.app.ui.home.HomeScreen
import com.toolx.app.ui.settings.SettingsScreen
import com.toolx.app.ui.theme.CategoryTheme

private const val HOME = "home"
private const val TOOL = "tool/{id}"
private const val SETTINGS = "settings"

@Composable
fun ToolXNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onOpenTool = { tool ->
                    AppSettings.recordRecent(tool)
                    nav.navigate("tool/${tool.id}")
                },
                onOpenSettings = { nav.navigate(SETTINGS) },
            )
        }
        composable(SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(TOOL) { entry ->
            val tool = ToolRegistry.find(entry.arguments?.getString("id"))
            val back: () -> Unit = { nav.popBackStack() }
            if (tool == null) {
                LaunchedEffect(Unit) { back() }
            } else {
                // Each tool screen takes its category's colour (buttons, chips, sliders).
                CategoryTheme(tool.category) { ToolScreen(tool, back) }
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
        ToolIds.VIDEO_WATERMARK -> com.toolx.app.tools.video.VideoWatermarkScreen(tool, onBack)
        ToolIds.VIDEO_ADD_MUSIC -> com.toolx.app.tools.video.VideoAddMusicScreen(tool, onBack)
        ToolIds.VIDEO_CONVERT -> com.toolx.app.tools.video.VideoConvertScreen(tool, onBack)
        ToolIds.VIDEO_REVERSE -> com.toolx.app.tools.video.VideoReverseScreen(tool, onBack)
        ToolIds.VIDEO_TO_GIF -> com.toolx.app.tools.video.VideoToGifScreen(tool, onBack)
        ToolIds.AUDIO_TRIM -> AudioTrimScreen(tool, onBack)
        ToolIds.AUDIO_CUT -> AudioCutScreen(tool, onBack)
        ToolIds.AUDIO_MERGE -> AudioMergeScreen(tool, onBack)
        ToolIds.AUDIO_SPEED -> AudioSpeedScreen(tool, onBack)
        ToolIds.AUDIO_VOLUME -> AudioVolumeScreen(tool, onBack)
        ToolIds.AUDIO_REVERSE -> com.toolx.app.tools.audio.AudioReverseScreen(tool, onBack)
        ToolIds.AUDIO_NOISE -> com.toolx.app.tools.audio.NoiseReductionScreen(tool, onBack)
        ToolIds.AUDIO_CONVERT -> com.toolx.app.tools.audio.AudioConvertScreen(tool, onBack)
        ToolIds.AUDIO_METADATA -> com.toolx.app.tools.audio.TagEditorScreen(tool, onBack)
        ToolIds.IMAGE_RESIZE -> ImageResizeScreen(tool, onBack)
        ToolIds.IMAGE_CONVERT -> ImageConvertScreen(tool, onBack)
        ToolIds.IMAGE_ROTATE -> ImageRotateScreen(tool, onBack)
        ToolIds.IMAGE_CROP -> ImageCropScreen(tool, onBack)
        ToolIds.IMAGE_REMOVE_BG -> RemoveBackgroundScreen(tool, onBack)
        ToolIds.IMAGE_PASSPORT -> PassportPhotoScreen(tool, onBack)
        ToolIds.IMAGE_ROUND -> com.toolx.app.tools.image.RoundCornersScreen(tool, onBack)
        ToolIds.IMAGE_COLOR_PICKER -> com.toolx.app.tools.image.ColorPickerScreen(tool, onBack)
        ToolIds.IMAGE_WATERMARK -> com.toolx.app.tools.image.WatermarkImageScreen(tool, onBack)
        else -> ToolRegistry.moduleScreen(tool.id)?.invoke(tool, onBack) ?: ComingSoonScreen(tool, onBack)
    }
}
