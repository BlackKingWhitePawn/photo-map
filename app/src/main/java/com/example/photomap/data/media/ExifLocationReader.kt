package com.example.photomap.data.media

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.photomap.core.util.GeoCoordinates
import com.example.photomap.core.util.LocationValidator
import java.io.IOException

class ExifLocationReader(
    private val contentResolver: ContentResolver
) {
    fun readLocation(uri: Uri, canReadOriginalLocation: Boolean): GeoCoordinates? {
        val readableUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canReadOriginalLocation) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }

        return try {
            contentResolver.openInputStream(readableUri)?.use { inputStream ->
                val latLong = FloatArray(2)
                if (ExifInterface(inputStream).getLatLong(latLong)) {
                    LocationValidator.normalize(
                        latitude = latLong[0].toDouble(),
                        longitude = latLong[1].toDouble()
                    )
                } else {
                    null
                }
            }
        } catch (securityException: SecurityException) {
            Log.w(Tag, "No access to original EXIF location for $uri", securityException)
            null
        } catch (exception: IOException) {
            Log.w(Tag, "Unable to read EXIF location for $uri", exception)
            null
        } catch (exception: RuntimeException) {
            Log.w(Tag, "Unsupported EXIF data for $uri", exception)
            null
        }
    }

    private companion object {
        const val Tag = "PhotoMapExif"
    }
}
