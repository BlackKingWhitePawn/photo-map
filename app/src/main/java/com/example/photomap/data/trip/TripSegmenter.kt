package com.example.photomap.data.trip

import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.trip.TripSegmentationResult

interface TripSegmenter {
    suspend fun segmentTrips(
        photos: Collection<DevicePhoto>
    ): TripSegmentationResult
}

