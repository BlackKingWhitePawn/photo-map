package com.example.photomap.data.trip

import android.content.Context
import android.util.Log
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.local.PhotoIndexDatabase
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.trip.TripMapMarker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val segmenter: TripSegmenter = HeuristicTripSegmenter()
) {
    private val database = PhotoIndexDatabase(context.applicationContext)

    suspend fun rebuildTrips(
        photos: Collection<DevicePhoto>
    ): List<TripMapMarker> {
        val result = withContext(computeDispatcher) {
            segmenter.segmentTrips(photos)
        }
        withContext(ioDispatcher) {
            database.replaceTrips(result.trips)
        }
        val message = "Trip segmentation rebuilt: trips=${result.trips.size}, " +
            "bases=${result.baseRegions.size}, skipped=${result.skippedPhotos}"
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
