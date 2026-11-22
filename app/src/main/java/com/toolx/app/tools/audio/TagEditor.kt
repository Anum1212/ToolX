package com.toolx.app.tools.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberDisplayName
import com.toolx.app.ui.common.rememberMediaPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.roundToInt

/** The tags people usually edit. */
data class Tags(
    val title: String = "", val artist: String = "", val album: String = "", val albumArtist: String = "",
    val year: String = "", val track: String = "", val genre: String = "", val comment: String = "",
)

/** Reads and writes audio tags with jaudiotagger, on a cache copy (the original isn't touched). */
internal object TagEngine {

    private val fields = listOf<Pair<FieldKey, (Tags) -> String>>(
        FieldKey.TITLE to { it.title }, FieldKey.ARTIST to { it.artist }, FieldKey.ALBUM to { it.album },
        FieldKey.ALBUM_ARTIST to { it.albumArtist }, FieldKey.YEAR to { it.year }, FieldKey.TRACK to { it.track },
        FieldKey.GENRE to { it.genre }, FieldKey.COMMENT to { it.comment },
    )

    init {
        // Android has no javax.imageio; this mode makes artwork handling use Android classes.
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** The format comes from the file extension, so the copy keeps the right one. */
    private fun extension(context: Context, uri: Uri): String {
        val name = MediaSaver.displayNameOf(context, uri).orEmpty()
        name.substringAfterLast('.', "").lowercase().takeIf { it in setOf("mp3", "m4a", "mp4", "flac", "ogg", "wav", "aac", "opus") }?.let { return it }
        return when (context.contentResolver.getType(uri)) {
            "audio/mpeg" -> "mp3"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "m4a"
        }
    }

    private fun copy(context: Context, uri: Uri): File {
        val f = File(context.cacheDir, "tags_${System.nanoTime()}.${extension(context, uri)}")
        context.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    data class Loaded(val tags: Tags, val cover: ByteArray?)

    suspend fun read(context: Context, uri: Uri): Loaded = withContext(Dispatchers.IO) {
        val f = copy(context, uri)
        try {
            val af = AudioFileIO.read(f)
            val tag = af.tag
            if (tag == null) Loaded(Tags(), null) else Loaded(
                Tags(
                    tag.getFirst(FieldKey.TITLE), tag.getFirst(FieldKey.ARTIST), tag.getFirst(FieldKey.ALBUM),
                    tag.getFirst(FieldKey.ALBUM_ARTIST), tag.getFirst(FieldKey.YEAR), tag.getFirst(FieldKey.TRACK),
                    tag.getFirst(FieldKey.GENRE), tag.getFirst(FieldKey.COMMENT),
                ),
                tag.firstArtwork?.binaryData,
            )
        } finally {
            f.delete()
        }
    }

    /**
     * Writes [tags] and the cover into a copy and saves it to Download/ToolX/Audio under the original
     * name. [cover] null keeps the existing art; [removeCover] deletes it.
     */
    suspend fun write(context: Context, uri: Uri, tags: Tags, cover: ByteArray?, removeCover: Boolean): SavedMedia = withContext(Dispatchers.IO) {
        val f = copy(context, uri)
        try {
            val af = AudioFileIO.read(f)
            val tag = af.tagOrCreateAndSetDefault
            for ((key, get) in fields) {
                val v = get(tags).trim()
                if (v.isEmpty()) runCatching { tag.deleteField(key) } else tag.setField(key, v)
            }
            if (removeCover || cover != null) tag.deleteArtworkField()
            if (cover != null) {
                val art = ArtworkFactory.getNew()
                art.binaryData = cover
                art.mimeType = "image/jpeg"
                art.pictureType = PictureTypes.DEFAULT_ID
                tag.setField(art)
            }
            af.commit()
            val name = MediaSaver.displayNameOf(context, uri) ?: MediaSaver.fileName("tagged", f.extension)
            val mime = context.contentResolver.getType(uri) ?: "audio/*"
            MediaSaver.saveFile(context, f, name, mime, MediaKind.AUDIO)
        } finally {
            f.delete()
        }
    }

    /** A square-ish JPEG cover, longest side 800 px (what players show). */
    suspend fun coverFrom(context: Context, image: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { d, info, _ ->
            d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val long = max(info.size.width, info.size.height)
            if (long > 800) { val k = 800f / long; d.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt()) }
        }
        ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out); bmp.recycle(); out.toByteArray() }
    }
}

@Composable
fun TagEditorScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var tags by remember { mutableStateOf<Tags?>(null) }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var newCover by remember { mutableStateOf<ByteArray?>(null) }
    var removeCover by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberMediaPicker(PickType.AUDIO) { uri = it; vm.reset() }
    val scope = rememberCoroutineScope()
    val pickCover = rememberMediaPicker(PickType.IMAGE) { img ->
        vm.reset()
        scope.launch {
            runCatching { TagEngine.coverFrom(context, img) }.onSuccess { newCover = it; removeCover = false }
        }
    }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        tags = null; cover = null; newCover = null; removeCover = false; error = null
        runCatching { TagEngine.read(context, u) }
            .onSuccess { tags = it.tags; cover = it.cover }
            .onFailure { error = "Couldn't read the tags of this file (${it.message ?: "unsupported format"})."; tags = Tags() }
    }

    ToolScaffold(tool.title, onBack) {
        val u = uri
        if (u == null) {
            PickPrompt("Choose a song", Icons.Rounded.LibraryMusic, onClick = pick)
            Note("MP3, M4A, FLAC, OGG and WAV. A tagged copy is saved to Download/ToolX/Audio; the original isn't changed.")
            return@ToolScaffold
        }
        SelectedInfo(u, rememberDisplayName(u), onChange = pick)
        error?.let { Note(it, error = true) }
        val t = tags
        if (t == null) { CircularProgressIndicator(); return@ToolScaffold }

        val shown = when {
            newCover != null -> newCover
            removeCover -> null
            else -> cover
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val bmp = remember(shown) { shown?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp.asImageBitmap(), "Cover art", Modifier.size(112.dp), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = pickCover) { Text(if (shown == null) "Add cover" else "Change cover") }
                if (shown != null) OutlinedButton(onClick = { newCover = null; removeCover = true; vm.reset() }) { Text("Remove cover") }
            }
        }
        fun edit(f: (Tags) -> Tags) { tags = f(t); vm.reset() }
        TextInput(t.title, { v -> edit { it.copy(title = v) } }, "Title")
        TextInput(t.artist, { v -> edit { it.copy(artist = v) } }, "Artist")
        TextInput(t.album, { v -> edit { it.copy(album = v) } }, "Album")
        TextInput(t.albumArtist, { v -> edit { it.copy(albumArtist = v) } }, "Album artist")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(t.year, { v -> edit { it.copy(year = v.filter(Char::isDigit).take(4)) } }, "Year", Modifier.weight(1f))
            TextInput(t.track, { v -> edit { it.copy(track = v.filter(Char::isDigit).take(3)) } }, "Track", Modifier.weight(1f))
        }
        TextInput(t.genre, { v -> edit { it.copy(genre = v) } }, "Genre")
        TextInput(t.comment, { v -> edit { it.copy(comment = v) } }, "Comment", singleLine = false)
        ExportButton("Save tagged copy", state) {
            val tg = t; val nc = newCover; val rc = removeCover
            vm.run { ctx, _ -> TagEngine.write(ctx, u, tg, nc, rc) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}
