package com.example.photomap.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.example.photomap.core.permissions.PhotoPermissionManager
import com.example.photomap.core.util.GeoCoordinates
import com.example.photomap.core.util.LocationValidator
import com.example.photomap.data.local.IndexedPhoto
import com.example.photomap.data.local.PhotoIndexDatabase
import com.example.photomap.data.local.toDevicePhoto
import com.example.photomap.data.local.toIndexedPhoto
import com.example.photomap.domain.model.DevicePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidMediaStorePhotoReader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DevicePhotoReader {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val exifLocationReader = ExifLocationReader(contentResolver)
    private val photoIndexDatabase = PhotoIndexDatabase(appContext)
    private val scanState = appContext.getSharedPreferences(ScanStateName, Context.MODE_PRIVATE)

    override suspend fun readPhotos(
        readExifLocation: Boolean,
        scanControl: PhotoReadControl,
        onBatchIndexed: (List<DevicePhoto>) -> Unit,
        onProgress: (PhotoReadProgress) -> Unit
    ): PhotoReadResult = withContext(ioDispatcher) {
        val photosToPersist = mutableListOf<IndexedPhoto>()
        val currentMediaIds = mutableSetOf<Long>()
        val cachedPhotos = photoIndexDatabase.getAllPhotosById()
        val startIndexStats = photoIndexDatabase.getStats()
        val canReadOriginalLocation = readExifLocation &&
            PhotoPermissionManager.checkStatus(appContext).canReadOriginalLocation
        var currentLocationScannedCount = startIndexStats.locationScannedCount
        var lastProgressReportAt = 0L
        var processed = 0
        var total = 0

        fun reportProgress(processed: Int, total: Int, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (force || now - lastProgressReportAt >= ProgressReportIntervalMs) {
                onProgress(
                    PhotoReadProgress(
                        processed = processed,
                        total = total,
                        indexedLocationScanned = currentLocationScannedCount.coerceAtMost(total),
                        indexedTotal = total
                    )
                )
                lastProgressReportAt = now
            }
        }

        fun persistBatch() {
            if (photosToPersist.isEmpty()) {
                return
            }

            val indexedBatch = photosToPersist.toList()
            photoIndexDatabase.upsertPhotos(indexedBatch)
            photosToPersist.clear()
            if (readExifLocation) {
                saveExifScanResumeState(
                    processed = processed,
                    total = total,
                    lastMediaId = indexedBatch.lastOrNull()?.mediaId
                )
            }
            onBatchIndexed(indexedBatch.map { photo -> photo.toDevicePhoto() })
        }

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                total = cursor.count
                val resumeStartPosition = cursor.findExifResumeStartPosition(
                    resumeState = if (readExifLocation) readExifScanResumeState() else null,
                    total = total
                )
                processed = resumeStartPosition
                reportProgress(processed = processed, total = total, force = true)

                cursor.moveToPosition(-1)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    scanControl.awaitIfPaused()
                    currentCoroutineContext().ensureActive()

                    val currentPhoto = cursor.toDevicePhotoMetadata()
                    currentMediaIds += currentPhoto.mediaId
                    if (cursor.position < resumeStartPosition) {
                        continue
                    }

                    val cachedPhoto = cachedPhotos[currentPhoto.mediaId]
                    val indexedPhoto = currentPhoto.resolveIndexedPhoto(
                        cachedPhoto = cachedPhoto,
                        readExifLocation = readExifLocation,
                        canReadOriginalLocation = canReadOriginalLocation
                    )
                    photosToPersist += indexedPhoto
                    processed += 1

                    if (indexedPhoto.locationScanned && cachedPhoto?.locationScanned != true) {
                        currentLocationScannedCount += 1
                    }

                    if (photosToPersist.size >= PersistBatchSize) {
                        persistBatch()
                    }

                    reportProgress(processed = processed, total = total, force = processed == total)
                }

                persistBatch()
                photoIndexDatabase.deleteMissingPhotos(currentMediaIds)
                if (readExifLocation) {
                    clearExifScanResumeState()
                }
            }
        } catch (cancellationException: CancellationException) {
            persistBatch()
            throw cancellationException
        } catch (securityException: SecurityException) {
            persistBatch()
            Log.w(Tag, "No permission to read images from MediaStore", securityException)
        } catch (exception: RuntimeException) {
            persistBatch()
            Log.w(Tag, "Unable to read images from MediaStore", exception)
        }

        val indexedPhotos = photoIndexDatabase.getAllPhotosById()
            .values
            .map { photo -> photo.toDevicePhoto() }
            .sortedByNewestFirst()
        Log.i(
            Tag,
            "MediaStore scan finished: ${indexedPhotos.size} photos, withLocation=${indexedPhotos.count { it.hasLocation }}, readExifLocation=$readExifLocation"
        )
        PhotoReadResult(
            photos = indexedPhotos,
            indexStats = photoIndexDatabase.getStats()
        )
    }

    override suspend fun readIndexedPhotos(): List<DevicePhoto> = withContext(ioDispatcher) {
        photoIndexDatabase.getAllPhotosById()
            .values
            .map { photo -> photo.toDevicePhoto() }
            .sortedByNewestFirst()
    }

    override suspend fun getIndexStats(): PhotoIndexStats = withContext(ioDispatcher) {
        photoIndexDatabase.getStats()
    }

    private fun Cursor.toDevicePhotoMetadata(): DevicePhoto {
        val id = getLongValue(MediaStore.Images.Media._ID) ?: error("MediaStore image row without _ID")
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val mediaStoreLocation = readMediaStoreLocation()

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
            orientation = getIntValue(MediaStore.Images.Media.ORIENTATION),
            latitude = mediaStoreLocation?.latitude,
            longitude = mediaStoreLocation?.longitude
        )
    }

    private fun DevicePhoto.resolveIndexedPhoto(
        cachedPhoto: IndexedPhoto?,
        readExifLocation: Boolean,
        canReadOriginalLocation: Boolean
    ): IndexedPhoto {
        val mediaStoreLocation = LocationValidator.normalize(latitude, longitude)
        val cachedRevisionMatches = cachedPhoto?.hasSameMediaRevision(this) == true
        val location = when {
            mediaStoreLocation != null -> mediaStoreLocation
            cachedRevisionMatches && cachedPhoto?.hasLocation == true -> GeoCoordinates(
                latitude = requireNotNull(cachedPhoto.latitude),
                longitude = requireNotNull(cachedPhoto.longitude)
            )
            readExifLocation -> exifLocationReader.readLocation(
                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId),
                canReadOriginalLocation = canReadOriginalLocation
            )
            else -> null
        }
        val locationScanned = mediaStoreLocation != null ||
            (cachedRevisionMatches && cachedPhoto?.locationScanned == true) ||
            (readExifLocation && canReadOriginalLocation)

        return copy(
            latitude = location?.latitude,
            longitude = location?.longitude
        ).toIndexedPhoto(
            locationScanned = locationScanned,
            indexedAt = System.currentTimeMillis()
        )
    }

    @Suppress("DEPRECATION")
    private fun Cursor.readMediaStoreLocation(): GeoCoordinates? {
        return LocationValidator.normalize(
            latitude = getDoubleValue(MediaStore.Images.Media.LATITUDE),
            longitude = getDoubleValue(MediaStore.Images.Media.LONGITUDE)
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

    private fun Cursor.getDoubleValue(columnName: String): Double? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getDouble(index) else null
    }

    private fun Cursor.findExifResumeStartPosition(
        resumeState: ExifScanResumeState?,
        total: Int
    ): Int {
        val state = resumeState ?: return 0
        if (state.total != total || state.processed <= 0) {
            return 0
        }

        val processed = state.processed.coerceAtMost(total)
        if (processed >= total) {
            return total
        }

        val expectedPosition = processed - 1
        if (!moveToPosition(expectedPosition)) {
            return 0
        }

        val mediaId = getLongValue(MediaStore.Images.Media._ID)
        return if (mediaId == state.lastMediaId) processed else 0
    }

    private fun readExifScanResumeState(): ExifScanResumeState? {
        if (!scanState.contains(ExifResumeProcessedKey)) {
            return null
        }

        val processed = scanState.getInt(ExifResumeProcessedKey, 0)
        val total = scanState.getInt(ExifResumeTotalKey, 0)
        val lastMediaId = scanState.getLong(ExifResumeLastMediaIdKey, MissingMediaId)
        return if (processed > 0 && total > 0 && lastMediaId != MissingMediaId) {
            ExifScanResumeState(
                processed = processed,
                total = total,
                lastMediaId = lastMediaId
            )
        } else {
            null
        }
    }

    private fun saveExifScanResumeState(
        processed: Int,
        total: Int,
        lastMediaId: Long?
    ) {
        if (processed <= 0 || total <= 0 || lastMediaId == null) {
            return
        }

        scanState.edit()
            .putInt(ExifResumeProcessedKey, processed.coerceAtMost(total))
            .putInt(ExifResumeTotalKey, total)
            .putLong(ExifResumeLastMediaIdKey, lastMediaId)
            .commit()
    }

    private fun clearExifScanResumeState() {
        scanState.edit()
            .remove(ExifResumeProcessedKey)
            .remove(ExifResumeTotalKey)
            .remove(ExifResumeLastMediaIdKey)
            .commit()
    }

    private companion object {
        const val Tag = "PhotoMapMediaStore"
        const val ScanStateName = "photo_map_scan_state"
        const val ExifResumeProcessedKey = "exif_resume_processed"
        const val ExifResumeTotalKey = "exif_resume_total"
        const val ExifResumeLastMediaIdKey = "exif_resume_last_media_id"
        const val MissingMediaId = -1L

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
            MediaStore.Images.Media.ORIENTATION,
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.LATITUDE,
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.LONGITUDE
        )

        const val PersistBatchSize = 50
        const val ProgressReportIntervalMs = 100L
    }
}

private data class ExifScanResumeState(
    val processed: Int,
    val total: Int,
    val lastMediaId: Long
)

private fun List<DevicePhoto>.sortedByNewestFirst(): List<DevicePhoto> {
    return sortedWith(
        compareByDescending<DevicePhoto> { photo ->
            photo.dateTaken ?: photo.dateModified ?: photo.dateAdded ?: 0L
        }.thenByDescending { photo -> photo.mediaId }
    )
}
