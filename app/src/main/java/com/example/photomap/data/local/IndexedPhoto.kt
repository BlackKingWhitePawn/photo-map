package com.example.photomap.data.local

import com.example.photomap.domain.model.DevicePhoto

data class IndexedPhoto(
    val mediaId: Long,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val dateTaken: Long?,
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val orientation: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val locationScanned: Boolean,
    val indexedAt: Long
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    fun hasSameMediaRevision(photo: DevicePhoto): Boolean {
        return mediaId == photo.mediaId &&
            dateModified == photo.dateModified &&
            size == photo.size
    }
}

fun IndexedPhoto.toDevicePhoto(): DevicePhoto {
    return DevicePhoto(
        mediaId = mediaId,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        dateTaken = dateTaken,
        width = width,
        height = height,
        size = size,
        orientation = orientation,
        latitude = latitude,
        longitude = longitude
    )
}

fun DevicePhoto.toIndexedPhoto(
    locationScanned: Boolean,
    indexedAt: Long
): IndexedPhoto {
    return IndexedPhoto(
        mediaId = mediaId,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        dateTaken = dateTaken,
        width = width,
        height = height,
        size = size,
        orientation = orientation,
        latitude = latitude,
        longitude = longitude,
        locationScanned = locationScanned,
        indexedAt = indexedAt
    )
}
