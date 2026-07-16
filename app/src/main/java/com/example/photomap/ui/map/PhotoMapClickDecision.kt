package com.example.photomap.ui.map

import kotlin.math.min

sealed class PhotoMapTapAction {
    object NoAction : PhotoMapTapAction()
    data class ZoomToCluster(val targetZoom: Double) : PhotoMapTapAction()
    data class ShowClusterPhotos(val pageSize: Int) : PhotoMapTapAction()
    data class OpenPhoto(val photoId: Long) : PhotoMapTapAction()
    data class ShowPhotos(val photoIds: List<Long>) : PhotoMapTapAction()
}

object PhotoMapClickDecision {
    fun forCluster(
        currentZoom: Double,
        expansionZoom: Double?,
        maxZoom: Double,
        pageSize: Int,
        tolerance: Double = ClusterZoomTolerance
    ): PhotoMapTapAction {
        val targetZoom = expansionZoom ?: return PhotoMapTapAction.ShowClusterPhotos(pageSize)
        if (targetZoom <= currentZoom + tolerance || targetZoom > maxZoom) {
            return PhotoMapTapAction.ShowClusterPhotos(pageSize)
        }
        return PhotoMapTapAction.ZoomToCluster(targetZoom = min(targetZoom, maxZoom))
    }

    fun forUnclustered(photoIds: List<Long>): PhotoMapTapAction {
        val uniquePhotoIds = photoIds.distinct()
        return when (uniquePhotoIds.size) {
            0 -> PhotoMapTapAction.NoAction
            1 -> PhotoMapTapAction.OpenPhoto(uniquePhotoIds.first())
            else -> PhotoMapTapAction.ShowPhotos(uniquePhotoIds)
        }
    }
}

private const val ClusterZoomTolerance = 0.01
