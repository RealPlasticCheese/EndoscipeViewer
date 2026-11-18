package com.plasticcheese.endoscopeviewer

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
private val photoDirectory = "${Environment.DIRECTORY_PICTURES}/Endoscope Viewer"
private const val photoMimeType = "image/jpeg"

internal data class SavedPhoto(val uri: Uri, val thumbnail: Bitmap?)

internal fun savePhoto(resolver: ContentResolver, photo: Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Endoscope_${LocalDateTime.now().format(timestampFormat)}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, photoMimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, photoDirectory)
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

internal fun findLatestPhoto(resolver: ContentResolver): SavedPhoto? {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val id = MediaStore.Images.Media._ID
    val path = MediaStore.Images.Media.RELATIVE_PATH
    val mimeType = MediaStore.Images.Media.MIME_TYPE
    val pending = MediaStore.Images.Media.IS_PENDING
    val query = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$path = ? AND $mimeType = ? AND $pending = 0")
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("$photoDirectory/", photoMimeType))
        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media.DATE_ADDED} DESC, $id DESC")
        putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
    }
    val uri = resolver.query(collection, arrayOf(id), query, null)?.use { cursor ->
        if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
    } ?: return null

    val thumbnail = try {
        resolver.loadThumbnail(uri, Size(160, 160), null)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
    return SavedPhoto(uri, thumbnail)
}

internal fun openPhoto(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, photoMimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
