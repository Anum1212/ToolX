package com.toolx.app.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BrandingWatermark
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SwapHoriz
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
    const val VIDEO_VOLUME = "video_volume"
    const val VIDEO_SPLIT = "video_split"
    const val VIDEO_CROP = "video_crop"

    // Audio
    const val AUDIO_TRIM = "audio_trim"
    const val AUDIO_CUT = "audio_cut"
    const val AUDIO_MERGE = "audio_merge"
    const val AUDIO_SPEED = "audio_speed"
    const val AUDIO_VOLUME = "audio_volume"
    const val AUDIO_CONVERT = "audio_convert"
    const val AUDIO_REVERSE = "audio_reverse"
    const val AUDIO_NOISE = "audio_noise"
    const val AUDIO_METADATA = "audio_metadata"

    // Image
    const val IMAGE_RESIZE = "image_resize"
    const val IMAGE_CONVERT = "image_convert"
    const val IMAGE_ROTATE = "image_rotate"
    const val IMAGE_CROP = "image_crop"
    const val IMAGE_REMOVE_BG = "image_remove_bg"
    const val IMAGE_PASSPORT = "image_passport"
    const val IMAGE_ROUND = "image_round"
    const val IMAGE_COLOR_PICKER = "image_color_picker"
    const val IMAGE_WATERMARK = "image_watermark"
    const val IMAGES_TO_PDF = "images_to_pdf"
}

/** Single source of truth for every tool shown in the app. Add new tools here. */
object ToolRegistry {

    /** Tool families that bring their own screens (text, units, calculators…). */
    val modules: List<ToolModule> = listOf(
        com.toolx.app.tools.pdf.PdfTools,
        com.toolx.app.tools.text.TextTools,
        com.toolx.app.tools.units.UnitTools,
        com.toolx.app.tools.calc.CalcTools,
        com.toolx.app.tools.math.MathTools,
        com.toolx.app.tools.units.ShoeTools,
        com.toolx.app.tools.general.GeneralTools,
        com.toolx.app.tools.finance.FinanceTools,
        com.toolx.app.tools.random.RandomTools,
        com.toolx.app.tools.dev.DevTools,
    )

    fun moduleScreen(id: String): ToolScreenFn? = modules.firstNotNullOfOrNull { it.screens[id] }

    val all: List<Tool> by lazy { mediaTools + modules.flatMap { it.tools } }

    private val mediaTools: List<Tool> = listOf(
        // ---- Video ----
        Tool(ToolIds.VIDEO_TRIM, "Trim", "Cut a video to the part you want", Icons.Rounded.ContentCut, ToolCategory.VIDEO, available = true, keywords = listOf("cut", "clip")),
        Tool(ToolIds.VIDEO_COMPRESS, "Compress video", "Shrink file size by lowering resolution", Icons.Rounded.Compress, ToolCategory.VIDEO, available = true, keywords = listOf("reduce", "size", "resolution")),
        Tool(ToolIds.VIDEO_MUTE, "Mute", "Remove the audio track", Icons.AutoMirrored.Rounded.VolumeOff, ToolCategory.VIDEO, available = true, keywords = listOf("silent", "remove audio")),
        Tool(ToolIds.VIDEO_EXTRACT_AUDIO, "Extract audio", "Save the soundtrack as an M4A file", Icons.Rounded.Audiotrack, ToolCategory.VIDEO, available = true, keywords = listOf("mp3", "m4a", "sound")),
        Tool(ToolIds.VIDEO_VOLUME, "Volume", "Boost or lower the sound", Icons.AutoMirrored.Rounded.VolumeUp, ToolCategory.VIDEO, available = true, keywords = listOf("loud", "quiet", "boost", "louder")),
        Tool(ToolIds.VIDEO_CROP, "Crop & rotate", "Change framing and orientation", Icons.Rounded.CropRotate, ToolCategory.VIDEO, available = true, keywords = listOf("crop", "rotate", "aspect", "ratio", "square", "portrait")),
        Tool(ToolIds.VIDEO_SPLIT, "Split", "Break a video into parts", Icons.AutoMirrored.Rounded.CallSplit, ToolCategory.VIDEO, available = true, keywords = listOf("parts", "divide", "cut")),
        Tool(ToolIds.VIDEO_MERGE, "Merge", "Join several videos into one", Icons.AutoMirrored.Rounded.MergeType, ToolCategory.VIDEO, available = true, keywords = listOf("join", "combine")),
        Tool(ToolIds.VIDEO_SPEED, "Speed", "Speed up or slow down a video", Icons.Rounded.Speed, ToolCategory.VIDEO, available = true, keywords = listOf("slow motion", "fast", "timelapse")),
        Tool(ToolIds.VIDEO_REVERSE, "Reverse", "Play a video backwards", Icons.Rounded.FastRewind, ToolCategory.VIDEO, keywords = listOf("rewind", "backwards")),
        Tool(ToolIds.VIDEO_TO_GIF, "Video to GIF", "Turn a clip into an animated GIF", Icons.Rounded.Gif, ToolCategory.VIDEO, keywords = listOf("animation")),
        Tool(ToolIds.VIDEO_CONVERT, "Convert video", "Change container or codec", Icons.Rounded.VideoSettings, ToolCategory.VIDEO, keywords = listOf("mp4", "mkv", "mov", "format")),
        Tool(ToolIds.VIDEO_ADD_MUSIC, "Add music", "Put a soundtrack under a video", Icons.Rounded.LibraryMusic, ToolCategory.VIDEO, keywords = listOf("song", "soundtrack")),
        Tool(ToolIds.VIDEO_WATERMARK, "Watermark video", "Stamp text or a logo on a video", Icons.AutoMirrored.Rounded.BrandingWatermark, ToolCategory.VIDEO, keywords = listOf("logo", "text")),

        // ---- Audio ----
        Tool(ToolIds.AUDIO_TRIM, "Trim audio", "Keep just the part you want", Icons.Rounded.ContentCut, ToolCategory.AUDIO, available = true, keywords = listOf("cut", "clip", "ringtone", "mp3")),
        Tool(ToolIds.AUDIO_CUT, "Cut audio", "Remove a section from the middle", Icons.Rounded.DeleteSweep, ToolCategory.AUDIO, available = true, keywords = listOf("remove", "delete", "section")),
        Tool(ToolIds.AUDIO_MERGE, "Merge audio", "Join tracks into one", Icons.AutoMirrored.Rounded.QueueMusic, ToolCategory.AUDIO, available = true, keywords = listOf("join", "combine", "mix")),
        Tool(ToolIds.AUDIO_SPEED, "Audio speed", "Change tempo, with or without pitch", Icons.Rounded.Speed, ToolCategory.AUDIO, available = true, keywords = listOf("tempo", "pitch", "nightcore", "slow")),
        Tool(ToolIds.AUDIO_VOLUME, "Audio volume", "Boost or lower the sound", Icons.AutoMirrored.Rounded.VolumeUp, ToolCategory.AUDIO, available = true, keywords = listOf("loud", "quiet", "boost", "gain")),
        Tool(ToolIds.AUDIO_CONVERT, "Convert audio", "MP3, WAV, FLAC or AAC", Icons.Rounded.SwapHoriz, ToolCategory.AUDIO, keywords = listOf("mp3", "wav", "flac", "aac", "format")),
        Tool(ToolIds.AUDIO_REVERSE, "Reverse audio", "Play audio backwards", Icons.Rounded.FastRewind, ToolCategory.AUDIO, keywords = listOf("backwards", "rewind")),
        Tool(ToolIds.AUDIO_NOISE, "Noise reduction", "Remove background noise", Icons.Rounded.GraphicEq, ToolCategory.AUDIO, keywords = listOf("denoise", "hiss", "clean")),
        Tool(ToolIds.AUDIO_METADATA, "Edit tags", "Title, artist, album and cover", Icons.Rounded.EditNote, ToolCategory.AUDIO, keywords = listOf("metadata", "id3", "artist", "album", "cover")),

        // ---- Image ----
        Tool(ToolIds.IMAGE_RESIZE, "Resize", "Change image dimensions", Icons.Rounded.PhotoSizeSelectLarge, ToolCategory.IMAGE, available = true, keywords = listOf("scale", "dimensions", "width", "height")),
        Tool(ToolIds.IMAGE_CONVERT, "Compress & convert", "JPG, PNG or WebP at any quality", Icons.Rounded.Transform, ToolCategory.IMAGE, available = true, keywords = listOf("jpg", "png", "webp", "quality", "format", "compress")),
        Tool(ToolIds.IMAGE_ROTATE, "Rotate & flip", "Turn or mirror an image", Icons.Rounded.Rotate90DegreesCw, ToolCategory.IMAGE, available = true, keywords = listOf("mirror", "flip")),
        Tool(ToolIds.IMAGE_CROP, "Crop", "Cut an image to a region or ratio", Icons.Rounded.Crop, ToolCategory.IMAGE, available = true, keywords = listOf("aspect", "ratio", "square", "cut")),
        Tool(ToolIds.IMAGE_REMOVE_BG, "Remove background", "Cut out a person, or change the background colour", Icons.Rounded.Wallpaper, ToolCategory.IMAGE, available = true, keywords = listOf("background", "transparent", "cutout", "eraser", "colour", "color", "png")),
        Tool(ToolIds.IMAGE_PASSPORT, "Passport photo", "ID photo with a plain background, sized for printing", Icons.Rounded.AssignmentInd, ToolCategory.IMAGE, available = true, keywords = listOf("passport", "visa", "id", "35x45", "2x2", "background", "print")),
        Tool(ToolIds.IMAGE_ROUND, "Round corners", "Give an image rounded corners", Icons.Rounded.RoundedCorner, ToolCategory.IMAGE, keywords = listOf("circle", "radius")),
        Tool(ToolIds.IMAGE_COLOR_PICKER, "Color picker", "Pick colors from a photo", Icons.Rounded.Colorize, ToolCategory.IMAGE, keywords = listOf("hex", "rgb", "palette")),
        Tool(ToolIds.IMAGE_WATERMARK, "Watermark image", "Stamp text or a logo on a photo", Icons.AutoMirrored.Rounded.BrandingWatermark, ToolCategory.IMAGE, keywords = listOf("logo", "text")),    )

    fun find(id: String?): Tool? = all.firstOrNull { it.id == id }

    /**
     * Tools matching [query] (title, description or keywords) in [category] (null = all),
     * minus anything the user hid: whole categories by name, or single tools by id.
     */
    fun search(
        query: String,
        category: ToolCategory?,
        hiddenCategories: Set<String> = emptySet(),
        hiddenTools: Set<String> = emptySet(),
        tools: List<Tool> = all,
    ): List<Tool> {
        val q = query.trim().lowercase()
        return tools.filter { tool ->
            tool.category.name !in hiddenCategories && tool.id !in hiddenTools &&
                (category == null || tool.category == category) &&
                (q.isEmpty() ||
                    tool.title.lowercase().contains(q) ||
                    tool.description.lowercase().contains(q) ||
                    tool.keywords.any { it.contains(q) })
        }
    }
}
