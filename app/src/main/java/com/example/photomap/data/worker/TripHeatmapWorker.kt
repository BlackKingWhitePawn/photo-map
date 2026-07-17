package com.example.photomap.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.photomap.core.permissions.PhotoPermissionManager
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.media.AndroidMediaStorePhotoReader
import com.example.photomap.data.trip.TripStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class TripHeatmapWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!PhotoPermissionManager.checkStatus(applicationContext).canReadImages) {
            Log.d(Tag, "Skip trip heatmap worker without image permission")
            return Result.success()
        }

        return try {
            val photos = AndroidMediaStorePhotoReader(applicationContext).readIndexedPhotos()
            TripStore(applicationContext).rebuildTrips(photos)
            val message = "Trip heatmap worker completed: photos=${photos.size}"
            Log.d(Tag, message)
            AppDiagnostics.record(Tag, message)
            Result.success()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: RuntimeException) {
            Log.w(Tag, "Trip heatmap worker failed", exception)
            AppDiagnostics.record(Tag, "Trip heatmap worker failed", exception)
            Result.retry()
        }
    }

    companion object {
        private const val Tag = "PhotoMapTripHeat"
        private const val OneTimeWorkName = "trip_heatmap_rebuild"
        private const val PeriodicWorkName = "trip_heatmap_periodic_rebuild"

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<TripHeatmapWorker>()
                .addTag(Tag)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                OneTimeWorkName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<TripHeatmapWorker>(1, TimeUnit.DAYS)
                .addTag(Tag)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PeriodicWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
