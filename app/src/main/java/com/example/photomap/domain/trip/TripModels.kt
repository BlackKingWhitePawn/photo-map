package com.example.photomap.domain.trip

enum class TripType {
    SINGLE_DESTINATION_TRIP,
    MULTI_DESTINATION_TRIP,
    UNKNOWN
}

data class TripMapMarker(
    val tripId: Long,
    val coverPhotoId: Long?,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double?,
    val photoCount: Int,
    val activeDayCount: Int,
    val startDay: Long,
    val endDay: Long,
    val confidence: Double,
    val type: TripType
)

data class DetectedTrip(
    val id: Long,
    val startDay: Long,
    val endDay: Long,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusKm: Double,
    val photoCount: Int,
    val activeDayCount: Int,
    val confidence: Double,
    val type: TripType,
    val coverPhotoId: Long?,
    val photoIds: List<Long>,
    val destinations: List<DetectedTripDestination>
)

data class DetectedTripDestination(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusKm: Double,
    val photoCount: Int,
    val firstDay: Long,
    val lastDay: Long,
    val sortOrder: Int
)

data class BaseRegion(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val uniqueVisitedDays: Int,
    val activeMonths: Int,
    val score: Double
)

data class TripSegmentationResult(
    val trips: List<DetectedTrip>,
    val baseRegions: List<BaseRegion>,
    val skippedPhotos: Int
)

