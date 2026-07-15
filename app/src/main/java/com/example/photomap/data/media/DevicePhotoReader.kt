package com.example.photomap.data.media

import com.example.photomap.domain.model.DevicePhoto

interface DevicePhotoReader {
    suspend fun readPhotos(
        readExifLocation: Boolean,
        onProgress: (PhotoReadProgress) -> Unit = {}
    ): PhotoReadResult

    suspend fun getIndexStats(): PhotoIndexStats
}

data class PhotoReadProgress(
    val processed: Int,
    val total: Int,
    val indexedLocationScanned: Int,
    val indexedTotal: Int
)

data class PhotoReadResult(
    val photos: List<DevicePhoto>,
    val indexStats: PhotoIndexStats
)

data class PhotoIndexStats(
    val totalCount: Int = 0,
    val locationScannedCount: Int = 0,
    val locationCount: Int = 0
)
