package com.example.photomap.data.trip

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.heatmap.TripHeatmapBuilder
import com.example.photomap.data.local.PhotoIndexDatabase
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.trip.DetectedTrip
import com.example.photomap.domain.trip.TripMapMarker
import com.example.photomap.domain.trip.TripPlaceNames
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val segmenter: TripSegmenter = HeuristicTripSegmenter()
) {
    private val appContext = context.applicationContext
    private val database = PhotoIndexDatabase(appContext)
    private val heatmapBuilder = TripHeatmapBuilder()

    suspend fun rebuildTrips(
        photos: Collection<DevicePhoto>
    ): List<TripMapMarker> {
        val result = withContext(computeDispatcher) {
            segmenter.segmentTrips(photos)
        }
        val heatmap = withContext(computeDispatcher) {
            heatmapBuilder.build(
                photos = photos,
                segmentationResult = result
            )
        }
        val placeNamesByTripId = withContext(ioDispatcher) {
            appContext.resolveTripPlaceNames(result.trips)
        }
        withContext(ioDispatcher) {
            database.replaceTrips(
                trips = result.trips,
                placeNamesByTripId = placeNamesByTripId
            )
            database.replaceTripHeatmap(
                cells = heatmap.cells,
                contributions = heatmap.contributions,
                dataVersion = heatmap.dataVersion
            )
        }
        val message = "Trip segmentation rebuilt: trips=${result.trips.size}, " +
            "bases=${result.baseRegions.size}, heatCells=${heatmap.cells.size}, " +
            "heatContributions=${heatmap.contributions.size}, skipped=${result.skippedPhotos}"
        Log.d(Tag, message)
        AppDiagnostics.record(Tag, message)
        return loadTripMarkers()
    }

    suspend fun loadTripMarkers(): List<TripMapMarker> = withContext(ioDispatcher) {
        database.getTripMapMarkers()
    }

    suspend fun getTripPhotoIds(tripId: Long): List<Long> = withContext(ioDispatcher) {
        database.getTripPhotoIds(tripId)
    }

    suspend fun getTripPhotoIdsByTripIds(tripIds: Collection<Long>): Map<Long, List<Long>> = withContext(ioDispatcher) {
        tripIds.associateWith { tripId -> database.getTripPhotoIds(tripId) }
    }

    companion object {
        private const val Tag = "PhotoMapTrips"
    }
}

private fun Context.resolveTripPlaceNames(trips: List<DetectedTrip>): Map<Long, TripPlaceNames> {
    if (trips.isEmpty() || !Geocoder.isPresent()) {
        return emptyMap()
    }
    val geocoder = Geocoder(this, Locale.getDefault())
    return trips.associate { trip ->
        trip.id to geocoder.resolveTripPlaceNames(trip)
    }
}

private fun Geocoder.resolveTripPlaceNames(trip: DetectedTrip): TripPlaceNames {
    val destinationNames = trip.destinations
        .sortedBy { destination -> destination.sortOrder }
        .mapNotNull { destination ->
            resolvePlaceName(
                latitude = destination.centerLatitude,
                longitude = destination.centerLongitude
            )?.let { placeName -> destination.sortOrder to placeName }
        }
        .toMap()

    val title = formatTripPlaceTitle(destinationNames.values.toList())
        ?: resolvePlaceName(
            latitude = trip.centerLatitude,
            longitude = trip.centerLongitude
        )

    return TripPlaceNames(
        title = title,
        destinationNamesBySortOrder = destinationNames
    )
}

private fun Geocoder.resolvePlaceName(
    latitude: Double,
    longitude: Double
): String? {
    return runCatching {
        @Suppress("DEPRECATION")
        getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.toShortPlaceName()
    }.onFailure { exception ->
        if (exception is IOException || exception is IllegalArgumentException) {
            Log.w("PhotoMapTrips", "Trip place lookup failed", exception)
        }
    }.getOrNull()
}

private fun Address.toShortPlaceName(): String? {
    return listOfNotNull(
        locality,
        subAdminArea,
        adminArea,
        featureName,
        countryName
    )
        .map { value -> value.trim() }
        .firstOrNull { value -> value.isNotEmpty() }
}

private fun formatTripPlaceTitle(placeNames: List<String>): String? {
    val uniquePlaceNames = placeNames
        .map { placeName -> placeName.trim() }
        .filter { placeName -> placeName.isNotEmpty() }
        .distinct()
    if (uniquePlaceNames.isEmpty()) {
        return null
    }
    return if (uniquePlaceNames.size <= 3) {
        uniquePlaceNames.joinToString(" - ")
    } else {
        uniquePlaceNames.take(3).joinToString(" - ") + " +${uniquePlaceNames.size - 3}"
    }
}
