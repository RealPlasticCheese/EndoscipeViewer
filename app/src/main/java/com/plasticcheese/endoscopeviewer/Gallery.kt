package com.plasticcheese.endoscopeviewer

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)

internal fun savePhoto(resolver: ContentResolver, photo: Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Endoscope_${LocalDateTime.now().format(timestampFormat)}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Endoscope Viewer")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create gallery image")
    try {
        val saved = resolver.openOutputStream(uri)?.use {
            photo.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        if (saved != true) throw IOException("Could not write gallery image")

        val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        if (resolver.update(uri, published, null, null) != 1) {
            throw IOException("Could not publish gallery image")
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }.onFailure(e::addSuppressed)
        throw e
    }
}
