package com.example.photomap.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import com.example.photomap.domain.model.DevicePhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidMediaStorePhotoReader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DevicePhotoReader {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun readPhotos(): List<DevicePhoto> = withContext(ioDispatcher) {
        val photos = mutableListOf<DevicePhoto>()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val photo = cursor.toDevicePhoto()
                    photos += photo
                    Log.d(Tag, "MediaStore photo: id=${photo.mediaId}, name=${photo.displayName}, uri=${photo.uri}")
                }
            }
        } catch (securityException: SecurityException) {
            Log.w(Tag, "No permission to read images from MediaStore", securityException)
        } catch (exception: RuntimeException) {
            Log.w(Tag, "Unable to read images from MediaStore", exception)
        }

        Log.i(Tag, "MediaStore scan finished: ${photos.size} photos")
        photos
    }

    private fun Cursor.toDevicePhoto(): DevicePhoto {
        val id = getLongValue(MediaStore.Images.Media._ID) ?: error("MediaStore image row without _ID")
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

        return DevicePhoto(
            mediaId = id,
            uri = uri.toString(),
            displayName = getStringValue(MediaStore.Images.Media.DISPLAY_NAME),
            mimeType = getStringValue(MediaStore.Images.Media.MIME_TYPE),
            dateAdded = getLongValue(MediaStore.Images.Media.DATE_ADDED),
            dateModified = getLongValue(MediaStore.Images.Media.DATE_MODIFIED),
            dateTaken = getLongValue(MediaStore.Images.Media.DATE_TAKEN),
            width = getIntValue(MediaStore.Images.Media.WIDTH),
            height = getIntValue(MediaStore.Images.Media.HEIGHT),
            size = getLongValue(MediaStore.Images.Media.SIZE),
            orientation = getIntValue(MediaStore.Images.Media.ORIENTATION)
        )
    }

    private fun Cursor.getStringValue(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getLongValue(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun Cursor.getIntValue(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }

    private companion object {
        const val Tag = "PhotoMapMediaStore"

        val Projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.ORIENTATION
        )
    }
}
