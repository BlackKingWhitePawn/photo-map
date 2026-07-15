package com.example.photomap.data.media

import com.example.photomap.domain.model.DevicePhoto

interface DevicePhotoReader {
    suspend fun readPhotos(
        readExifLocation: Boolean,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DevicePhoto>
}
