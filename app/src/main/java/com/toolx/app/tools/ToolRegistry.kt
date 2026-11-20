package com.toolx.app.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BrandingWatermark
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.VideoSettings

object ToolIds {
    // Video
    const val VIDEO_TRIM = "video_trim"
    const val VIDEO_COMPRESS = "video_compress"
    const val VIDEO_MUTE = "video_mute"
    const val VIDEO_EXTRACT_AUDIO = "video_extract_audio"
    const val VIDEO_MERGE = "video_merge"
    const val VIDEO_SPEED = "video_speed"
    const val VIDEO_REVERSE = "video_reverse"
    const val VIDEO_TO_GIF = "video_to_gif"
    const val VIDEO_CONVERT = "video_convert"
    const val VIDEO_ADD_MUSIC = "video_add_music"
    const val VIDEO_WATERMARK = "video_watermark"

    // Image
    const val IMAGE_RESIZE = "image_resize"
    const val IMAGE_CONVERT = "image_convert"
    const val IMAGE_ROTATE = "image_rotate"
    const val IMAGE_CROP = "image_crop"
    const val IMAGE_ROUND = "image_round"
    const val IMAGE_COLOR_PICKER = "image_color_picker"
    const val IMAGE_WATERMARK = "image_watermark"
    const val IMAGES_TO_PDF = "images_to_pdf"
}

/** Single source of truth for every tool shown in the app. Add new tools here. */
object ToolRegistry {

    val all: List<Tool> = listOf(
        // ---- Video ----
        Tool(ToolIds.VIDEO_TRIM, "Trim", "Cut a video to the part you want", Icons.Rounded.ContentCut, ToolCategory.VIDEO, available = true, keywords = listOf("cut", "clip")),
        Tool(ToolIds.VIDEO_COMPRESS, "Compress video", "Shrink file size by lowering resolution", Icons.Rounded.Compress, ToolCategory.VIDEO, available = true, keywords = listOf("reduce", "size", "resolution")),
        Tool(ToolIds.VIDEO_MUTE, "Mute", "Remove the audio track", Icons.AutoMirrored.Rounded.VolumeOff, ToolCategory.VIDEO, available = true, keywords = listOf("silent", "remove audio")),
        Tool(ToolIds.VIDEO_EXTRACT_AUDIO, "Extract audio", "Save the soundtrack as an M4A file", Icons.Rounded.Audiotrack, ToolCategory.VIDEO, available = true, keywords = listOf("mp3", "m4a", "sound")),
        Tool(ToolIds.VIDEO_MERGE, "Merge", "Join several videos into one", Icons.AutoMirrored.Rounded.MergeType, ToolCategory.VIDEO, keywords = listOf("join", "combine")),
        Tool(ToolIds.VIDEO_SPEED, "Speed", "Speed up or slow down a video", Icons.Rounded.Speed, ToolCategory.VIDEO, keywords = listOf("slow motion", "fast")),
        Tool(ToolIds.VIDEO_REVERSE, "Reverse", "Play a video backwards", Icons.Rounded.FastRewind, ToolCategory.VIDEO, keywords = listOf("rewind", "backwards")),
        Tool(ToolIds.VIDEO_TO_GIF, "Video to GIF", "Turn a clip into an animated GIF", Icons.Rounded.Gif, ToolCategory.VIDEO, keywords = listOf("animation")),
        Tool(ToolIds.VIDEO_CONVERT, "Convert video", "Change container or codec", Icons.Rounded.VideoSettings, ToolCategory.VIDEO, keywords = listOf("mp4", "mkv", "mov", "format")),
        Tool(ToolIds.VIDEO_ADD_MUSIC, "Add music", "Put a soundtrack under a video", Icons.Rounded.LibraryMusic, ToolCategory.VIDEO, keywords = listOf("song", "soundtrack")),
        Tool(ToolIds.VIDEO_WATERMARK, "Watermark video", "Stamp text or a logo on a video", Icons.AutoMirrored.Rounded.BrandingWatermark, ToolCategory.VIDEO, keywords = listOf("logo", "text")),

        // ---- Image ----
        Tool(ToolIds.IMAGE_RESIZE, "Resize", "Change image dimensions", Icons.Rounded.PhotoSizeSelectLarge, ToolCategory.IMAGE, available = true, keywords = listOf("scale", "dimensions", "width", "height")),
        Tool(ToolIds.IMAGE_CONVERT, "Compress & convert", "JPG, PNG or WebP at any quality", Icons.Rounded.Transform, ToolCategory.IMAGE, available = true, keywords = listOf("jpg", "png", "webp", "quality", "format", "compress")),
        Tool(ToolIds.IMAGE_ROTATE, "Rotate & flip", "Turn or mirror an image", Icons.Rounded.Rotate90DegreesCw, ToolCategory.IMAGE, available = true, keywords = listOf("mirror", "flip")),
        Tool(ToolIds.IMAGE_CROP, "Crop", "Cut an image to a region or ratio", Icons.Rounded.Crop, ToolCategory.IMAGE, keywords = listOf("aspect", "ratio")),
        Tool(ToolIds.IMAGE_ROUND, "Round corners", "Give an image rounded corners", Icons.Rounded.RoundedCorner, ToolCategory.IMAGE, keywords = listOf("circle", "radius")),
        Tool(ToolIds.IMAGE_COLOR_PICKER, "Color picker", "Pick colors from a photo", Icons.Rounded.Colorize, ToolCategory.IMAGE, keywords = listOf("hex", "rgb", "palette")),
        Tool(ToolIds.IMAGE_WATERMARK, "Watermark image", "Stamp text or a logo on a photo", Icons.AutoMirrored.Rounded.BrandingWatermark, ToolCategory.IMAGE, keywords = listOf("logo", "text")),
        Tool(ToolIds.IMAGES_TO_PDF, "Images to PDF", "Combine photos into one PDF", Icons.Rounded.PictureAsPdf, ToolCategory.IMAGE, keywords = listOf("document", "scan")),
    )

    fun find(id: String?): Tool? = all.firstOrNull { it.id == id }

    fun search(query: String, category: ToolCategory?): List<Tool> {
        val q = query.trim().lowercase()
        return all.filter { tool ->
            (category == null || tool.category == category) &&
                (q.isEmpty() ||
                    tool.title.lowercase().contains(q) ||
                    tool.description.lowercase().contains(q) ||
                    tool.keywords.any { it.contains(q) })
        }
    }
}
