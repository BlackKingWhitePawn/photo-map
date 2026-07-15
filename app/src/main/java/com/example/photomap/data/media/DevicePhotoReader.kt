package com.example.photomap.data.media

import com.example.photomap.domain.model.DevicePhoto

interface DevicePhotoReader {
    suspend fun readPhotos(): List<DevicePhoto>
}
