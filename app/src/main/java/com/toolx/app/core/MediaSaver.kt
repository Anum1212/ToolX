package com.toolx.app.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedMedia(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    /** Human-readable folder, e.g. "Movies/ToolX". */
    val location: String,
    val sizeBytes: Long?,
)

enum class MediaKind(val baseDir: String) {
    VIDEO(Environment.DIRECTORY_MOVIES),
    IMAGE(Environment.DIRECTORY_PICTURES),
    AUDIO(Environment.DIRECTORY_MUSIC),
}

/** Writes results into shared storage (Movies/ToolX, Pictures/ToolX, Music/ToolX). */
object MediaSaver {

    private const val APP_FOLDER = "ToolX"

    fun fileName(tag: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "ToolX_${tag}_$stamp.$extension"
    }

    fun saveFile(context: Context, source: File, displayName: String, mimeType: String, kind: MediaKind): SavedMedia =
        write(context, displayName, mimeType, kind) { out -> source.inputStream().use { it.copyTo(out) } }

    /** Must be called off the main thread. */
    fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        kind: MediaKind,
        writer: (OutputStream) -> Unit,
    ): SavedMedia {
        val resolver = context.contentResolver
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val location = "${kind.baseDir}/$APP_FOLDER"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, location)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create the output file")
        try {
            val stream = resolver.openOutputStream(uri) ?: error("Couldn't open the output file")
            stream.use(writer)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return SavedMedia(uri, mimeType, displayName, location, sizeOf(context, uri))
    }

    /** Size of any content Uri, or null if the provider doesn't report it. */
    fun sizeOf(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()
}
