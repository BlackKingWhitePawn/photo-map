package com.example.photomap.data.heatmap

import android.util.Log
import com.example.photomap.core.util.LocationValidator
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.BaseRegion
import com.example.photomap.domain.trip.DetectedTrip
import com.example.photomap.domain.trip.DetectedTripDestination
import com.example.photomap.domain.trip.TripSegmentationResult
import com.uber.h3core.H3Core
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class TripHeatmapBuilder {
    private val h3Core: H3Core? by lazy {
        runCatching { H3Core.newInstance() }
            .onFailure { error -> Log.w(Tag, "H3 initialization failed, falling back to local grid", error) }
            .getOrNull()
    }

    suspend fun build(
        photos: Collection<DevicePhoto>,
        segmentationResult: TripSegmentationResult,
        updatedAt: Long = System.currentTimeMillis()
    ): BuiltTripHeatmap {
        if (segmentationResult.trips.isEmpty()) {
            return BuiltTripHeatmap(
                cells = emptyList(),
                contributions = emptyList(),
                dataVersion = updatedAt
            )
        }

        val photosById = photos.associateBy { photo -> photo.mediaId }
        val rawContributions = mutableListOf<RawTripHeatContribution>()
        segmentationResult.trips.forEachIndexed { index, trip ->
            if (index % CancellationCheckInterval == 0) {
                currentCoroutineContext().ensureActive()
            }
            rawContributions += trip.toRawContributions(
                photosById = photosById,
                baseRegions = segmentationResult.baseRegions
            )
        }

        val contributions = rawContributions
            .groupBy { contribution ->
                TripHeatContributionKey(
                    tripId = contribution.tripId,
                    h3Index = contribution.h3Index,
                    resolution = contribution.resolution
                )
            }
            .map { (key, values) ->
                TripHeatContributionEntity(
                    tripId = key.tripId,
                    h3Index = key.h3Index,
                    resolution = key.resolution,
                    activeDays = values.sumOf { contribution -> contribution.activeDays },
                    daysSpent = values.sumOf { contribution -> contribution.daysSpent },
                    sessionCount = values.sumOf { contribution -> contribution.sessionCount },
                    weight = values.sumOf { contribution -> contribution.weight }
                )
            }
            .sortedWith(compareBy<TripHeatContributionEntity> { contribution -> contribution.resolution }
                .thenBy { contribution -> contribution.h3Index }
                .thenBy { contribution -> contribution.tripId })

        val rawByCell = rawContributions.groupBy { contribution ->
            TripHeatCellKey(
                h3Index = contribution.h3Index,
                resolution = contribution.resolution
            )
        }
        val cells = contributions
            .groupBy { contribution ->
                TripHeatCellKey(
                    h3Index = contribution.h3Index,
                    resolution = contribution.resolution
                )
            }
            .map { (key, cellContributions) ->
                val centerSamples = rawByCell[key].orEmpty()
                val totalCenterWeight = centerSamples
                    .sumOf { contribution -> contribution.weight }
                    .coerceAtLeast(MinCenterWeight)
                val centerLatitude = centerSamples
                    .sumOf { contribution -> contribution.latitude * contribution.weight } / totalCenterWeight
                val centerLongitude = centerSamples
                    .sumOf { contribution -> contribution.longitude * contribution.weight } / totalCenterWeight
                val tripCount = cellContributions.map { contribution -> contribution.tripId }.distinct().size
                val activeDays = cellContributions.sumOf { contribution -> contribution.activeDays }
                val daysSpent = cellContributions.sumOf { contribution -> contribution.daysSpent }
                val sessionCount = cellContributions.sumOf { contribution -> contribution.sessionCount }

                TripHeatCellEntity(
                    h3Index = key.h3Index,
                    resolution = key.resolution,
                    centerLatitude = centerLatitude,
                    centerLongitude = centerLongitude,
                    tripCount = tripCount,
                    activeDays = activeDays,
                    daysSpent = daysSpent,
                    sessionCount = sessionCount,
                    latestTripAt = centerSamples.maxOfOrNull { contribution -> contribution.latestTripAt },
                    intensity = tripHeatIntensity(
                        tripCount = tripCount,
                        daysSpent = daysSpent,
                        activeDays = activeDays,
                        sessionCount = sessionCount
                    ),
                    algorithmVersion = HEATMAP_ALGORITHM_VERSION,
                    updatedAt = updatedAt
                )
            }
            .sortedWith(compareBy<TripHeatCellEntity> { cell -> cell.resolution }
                .thenByDescending { cell -> cell.intensity }
                .thenBy { cell -> cell.h3Index })

        return BuiltTripHeatmap(
            cells = cells,
            contributions = contributions,
            dataVersion = updatedAt
        )
    }

    private fun DetectedTrip.toRawContributions(
        photosById: Map<Long, DevicePhoto>,
        baseRegions: List<BaseRegion>
    ): List<RawTripHeatContribution> {
        val destinations = destinations.ifEmpty { return emptyList() }
        val tripPhotos = photoIds
            .mapNotNull { photoId -> photosById[photoId]?.toHeatPhoto() }
            .sortedWith(compareBy<HeatPhoto> { photo -> photo.takenAtMillis }.thenBy { photo -> photo.mediaId })
        val photosByDestination = assignPhotosToDestinations(
            photos = tripPhotos,
            destinations = destinations
        )
        val latestTripAt = endDay * MillisPerDay
        val tripPhotoCount = photoCount.coerceAtLeast(1)

        return destinations.flatMap { destination ->
            if (destination.isNearBaseRegion(baseRegions)) {
                return@flatMap emptyList()
            }
            val destinationPhotos = photosByDestination[destination.sortOrder].orEmpty()
            val stats = destination.toHeatDestinationStats(
                photos = destinationPhotos,
                tripPhotoCount = tripPhotoCount
            )
            TripHeatmapResolutions.mapNotNull { resolution ->
                val h3Index = h3IndexFor(
                    latitude = destination.centerLatitude,
                    longitude = destination.centerLongitude,
                    resolution = resolution
                ) ?: return@mapNotNull null
                RawTripHeatContribution(
                    tripId = id,
                    h3Index = h3Index,
                    resolution = resolution,
                    latitude = destination.centerLatitude,
                    longitude = destination.centerLongitude,
                    activeDays = stats.activeDays,
                    daysSpent = stats.daysSpent,
                    sessionCount = stats.sessionCount,
                    latestTripAt = latestTripAt,
                    weight = stats.weight
                )
            }
        }
    }

    private fun assignPhotosToDestinations(
        photos: List<HeatPhoto>,
        destinations: List<DetectedTripDestination>
    ): Map<Int, List<HeatPhoto>> {
        if (photos.isEmpty() || destinations.isEmpty()) {
            return emptyMap()
        }

        val assignments = mutableMapOf<Int, MutableList<HeatPhoto>>()
        photos.forEach { photo ->
            val nearest = destinations.minByOrNull { destination ->
                haversineKm(
                    firstLatitude = photo.latitude,
                    firstLongitude = photo.longitude,
                    secondLatitude = destination.centerLatitude,
                    secondLongitude = destination.centerLongitude
                )
            } ?: return@forEach
            val distanceKm = haversineKm(
                firstLatitude = photo.latitude,
                firstLongitude = photo.longitude,
                secondLatitude = nearest.centerLatitude,
                secondLongitude = nearest.centerLongitude
            )
            val assignmentRadiusKm = max(
                DestinationAssignmentRadiusKm,
                nearest.radiusKm * DestinationAssignmentRadiusMultiplier
            )
            if (distanceKm <= assignmentRadiusKm) {
                assignments.getOrPut(nearest.sortOrder) { mutableListOf() } += photo
            }
        }
        return assignments
    }

    private fun DetectedTripDestination.toHeatDestinationStats(
        photos: List<HeatPhoto>,
        tripPhotoCount: Int
    ): HeatDestinationStats {
        val fallbackDaysSpent = (lastDay - firstDay + 1).toInt().coerceAtLeast(1)
        val activeDays = photos
            .map { photo -> photo.day }
            .distinct()
            .size
            .takeIf { count -> count > 0 }
            ?: fallbackDaysSpent
        val daysSpent = if (photos.isNotEmpty()) {
            val days = photos.map { photo -> photo.day }
            ((days.maxOrNull() ?: lastDay) - (days.minOrNull() ?: firstDay) + 1)
                .toInt()
                .coerceAtLeast(1)
        } else {
            fallbackDaysSpent
        }
        val sessionCount = photos.toSessionCount().takeIf { count -> count > 0 } ?: 1
        val share = if (photos.isNotEmpty()) {
            photos.size.toDouble() / tripPhotoCount.toDouble()
        } else {
            photoCount.toDouble() / tripPhotoCount.toDouble()
        }.coerceIn(0.0, 1.0)
        val baseWeight = tripHeatIntensity(
            tripCount = 1,
            daysSpent = daysSpent,
            activeDays = activeDays,
            sessionCount = sessionCount
        )
        return HeatDestinationStats(
            activeDays = activeDays,
            daysSpent = daysSpent,
            sessionCount = sessionCount,
            weight = baseWeight * share.coerceAtLeast(MinDestinationShareWeight)
        )
    }

    private fun List<HeatPhoto>.toSessionCount(): Int {
        if (isEmpty()) {
            return 0
        }

        val sortedPhotos = sortedWith(compareBy<HeatPhoto> { photo -> photo.takenAtMillis }.thenBy { photo -> photo.mediaId })
        var sessionCount = 1
        var previous = sortedPhotos.first()
        sortedPhotos.drop(1).forEach { photo ->
            val startsNewSession = photo.takenAtMillis - previous.takenAtMillis > SessionGapMillis ||
                haversineKm(
                    firstLatitude = previous.latitude,
                    firstLongitude = previous.longitude,
                    secondLatitude = photo.latitude,
                    secondLongitude = photo.longitude
                ) > SessionDistanceKm
            if (startsNewSession) {
                sessionCount += 1
            }
            previous = photo
        }
        return sessionCount
    }

    private fun DetectedTripDestination.isNearBaseRegion(baseRegions: List<BaseRegion>): Boolean {
        return baseRegions.any { baseRegion ->
            haversineKm(
                firstLatitude = centerLatitude,
                firstLongitude = centerLongitude,
                secondLatitude = baseRegion.latitude,
                secondLongitude = baseRegion.longitude
            ) < HomeRegionExclusionRadiusKm
        }
    }

    private fun DevicePhoto.toHeatPhoto(): HeatPhoto? {
        val location = LocationValidator.normalize(latitude, longitude) ?: return null
        val takenAtMillis = photoDateMillis() ?: return null
        return HeatPhoto(
            mediaId = mediaId,
            latitude = location.latitude,
            longitude = location.longitude,
            takenAtMillis = takenAtMillis,
            day = floor(takenAtMillis.toDouble() / MillisPerDay.toDouble()).toLong()
        )
    }

    private fun h3IndexFor(
        latitude: Double,
        longitude: Double,
        resolution: Int
    ): String? {
        val normalized = LocationValidator.normalize(latitude, longitude) ?: return null
        return h3Core?.let { h3 ->
            runCatching {
                h3.latLngToCellAddress(normalized.latitude, normalized.longitude, resolution)
            }.onFailure { error ->
                Log.w(Tag, "H3 cell calculation failed for resolution=$resolution", error)
            }.getOrNull()
        } ?: localFallbackCellIndex(
            latitude = normalized.latitude,
            longitude = normalized.longitude,
            resolution = resolution
        )
    }

    private fun localFallbackCellIndex(
        latitude: Double,
        longitude: Double,
        resolution: Int
    ): String {
        val latitudeSize = localFallbackLatitudeCellSizeDegrees(resolution)
        val latitudeBucket = floor((latitude + 90.0) / latitudeSize).toInt()
        val middleLatitude = -90.0 + (latitudeBucket + 0.5) * latitudeSize
        val longitudeSize = (latitudeSize / cos(Math.toRadians(middleLatitude)).coerceAtLeast(0.25))
            .coerceIn(latitudeSize, 360.0)
        val longitudeBucket = floor((longitude + 180.0) / longitudeSize).toInt()
        return "h3-fallback-r$resolution-$latitudeBucket-$longitudeBucket"
    }

    private fun localFallbackLatitudeCellSizeDegrees(resolution: Int): Double {
        return when (resolution) {
            4 -> 1.0
            5 -> 0.42
            6 -> 0.16
            7 -> 0.06
            8 -> 0.022
            9 -> 0.008
            else -> 0.06
        }
    }

    private companion object {
        const val Tag = "PhotoMapTripHeat"
        const val CancellationCheckInterval = 32
        const val HomeRegionExclusionRadiusKm = 50.0
        const val DestinationAssignmentRadiusKm = 50.0
        const val DestinationAssignmentRadiusMultiplier = 1.5
        const val MinDestinationShareWeight = 0.15
        const val MinCenterWeight = 0.0001
        const val SessionGapMillis = 3L * 60L * 60L * 1000L
        const val SessionDistanceKm = 20.0
        const val MillisPerDay = 24L * 60L * 60L * 1000L
    }
}

fun tripHeatIntensity(
    tripCount: Int,
    daysSpent: Int,
    activeDays: Int,
    sessionCount: Int
): Double {
    return 0.50 * ln(1.0 + tripCount.coerceAtLeast(0).toDouble()) +
        0.25 * ln(1.0 + daysSpent.coerceAtLeast(0).toDouble()) +
        0.15 * ln(1.0 + activeDays.coerceAtLeast(0).toDouble()) +
        0.10 * ln(1.0 + sessionCount.coerceAtLeast(0).toDouble())
}

private data class TripHeatCellKey(
    val h3Index: String,
    val resolution: Int
)

private data class TripHeatContributionKey(
    val tripId: Long,
    val h3Index: String,
    val resolution: Int
)

private data class RawTripHeatContribution(
    val tripId: Long,
    val h3Index: String,
    val resolution: Int,
    val latitude: Double,
    val longitude: Double,
    val activeDays: Int,
    val daysSpent: Int,
    val sessionCount: Int,
    val latestTripAt: Long,
    val weight: Double
)

private data class HeatDestinationStats(
    val activeDays: Int,
    val daysSpent: Int,
    val sessionCount: Int,
    val weight: Double
)

private data class HeatPhoto(
    val mediaId: Long,
    val latitude: Double,
    val longitude: Double,
    val takenAtMillis: Long,
    val day: Long
)

private fun haversineKm(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double
): Double {
    val firstLatitudeRad = Math.toRadians(firstLatitude)
    val secondLatitudeRad = Math.toRadians(secondLatitude)
    val deltaLatitudeRad = Math.toRadians(secondLatitude - firstLatitude)
    val deltaLongitudeRad = Math.toRadians(secondLongitude - firstLongitude)
    val halfChord = sin(deltaLatitudeRad / 2.0) * sin(deltaLatitudeRad / 2.0) +
        cos(firstLatitudeRad) * cos(secondLatitudeRad) *
        sin(deltaLongitudeRad / 2.0) * sin(deltaLongitudeRad / 2.0)
    val normalizedHalfChord = halfChord.coerceIn(0.0, 1.0)
    val angularDistance = 2.0 * atan2(
        sqrt(normalizedHalfChord),
        sqrt(1.0 - normalizedHalfChord)
    )
    return EarthRadiusKm * angularDistance
}

private const val EarthRadiusKm = 6371.0
