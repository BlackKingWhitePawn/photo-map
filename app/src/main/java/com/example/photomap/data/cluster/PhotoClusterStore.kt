package com.example.photomap.data.cluster

import android.content.Context
import android.util.Log
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.local.PhotoIndexDatabase
import com.example.photomap.data.local.toDevicePhoto
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.PhotoDateFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PhotoClusterStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val database = PhotoIndexDatabase(context.applicationContext)
    private val builder = PhotoClusterBuilder()

    suspend fun rebuildClusters(
        photos: Collection<DevicePhoto>,
        settings: PhotoClusterSettings
    ) = withContext(ioDispatcher) {
        val locatedPhotos = photos.filter { photo -> photo.hasLocation }
        val builtClusters = builder.build(
            photos = locatedPhotos,
            settings = settings.normalized()
        )
        currentCoroutineContext().ensureActive()
        database.replaceClusters(
            clusters = builtClusters.clusters,
            links = builtClusters.links
        )
        val message = "Cluster index rebuilt: photos=${locatedPhotos.size}, " +
            "clusters=${builtClusters.clusters.size}, links=${builtClusters.links.size}, " +
            "version=$CLUSTERING_VERSION"
        Log.d(Tag, message)
        AppDiagnostics.record(Tag, message)
    }

    suspend fun rebuildClustersIfOutdated(
        photos: Collection<DevicePhoto>,
        settings: PhotoClusterSettings
    ) = withContext(ioDispatcher) {
        if (database.getStoredClusterVersion() == CLUSTERING_VERSION) {
            return@withContext
        }
        rebuildClusters(photos, settings)
    }

    suspend fun loadVisibleMapContent(
        bounds: PhotoMapBounds,
        zoom: Double,
        settings: PhotoClusterSettings,
        dateFilter: PhotoDateFilter = PhotoDateFilter()
    ): VisiblePhotoMapContent = withContext(ioDispatcher) {
        val level = clusterLevelForZoom(zoom)
        val expandedBounds = bounds.expanded(ViewportPaddingFactor)
        val items = if (level == 0) {
            database.getPhotosInBounds(expandedBounds, dateFilter)
                .asSequence()
                .map { photo -> photo.toDevicePhoto() }
                .mapNotNull { photo ->
                    val latitude = photo.latitude ?: return@mapNotNull null
                    val longitude = photo.longitude ?: return@mapNotNull null
                    ClusterPoint(
                        photoId = photo.mediaId,
                        latitude = latitude,
                        longitude = longitude,
                        takenAt = photo.dateTaken ?: photo.dateModified ?: photo.dateAdded
                    ).toVisiblePhotoMapItem()
                }
                .toList()
        } else {
            database.getClustersInBounds(level, expandedBounds)
                .map { cluster ->
                    cluster.toVisiblePhotoMapItem(
                        photoIds = database.getClusterPhotoIds(cluster.clusterId)
                    )
                }
        }
        val message = "Visible cluster query: level=$level, zoom=$zoom, items=${items.size}, " +
            "bounds=${expandedBounds.south},${expandedBounds.west},${expandedBounds.north},${expandedBounds.east}"
        Log.d(Tag, message)
        AppDiagnostics.record(Tag, message)
        VisiblePhotoMapContent(
            level = level,
            loadedBounds = expandedBounds,
            items = items
        )
    }

    companion object {
        private const val Tag = "PhotoMapCluster"
        private const val ViewportPaddingFactor = 0.45
    }
}
