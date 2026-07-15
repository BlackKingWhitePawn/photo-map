package com.example.photomap.core.settings

data class PhotoClusterSettings(
    val radiusPx: Int = PHOTO_CLUSTER_RADIUS,
    val minPoints: Int = PHOTO_CLUSTER_MIN_POINTS,
    val leavesPageSize: Int = PHOTO_CLUSTER_LEAVES_PAGE_SIZE,
    val maxDistanceKm: Int = PHOTO_CLUSTER_MAX_DISTANCE_KM,
    val markerScalePercent: Int = PHOTO_CLUSTER_MARKER_SCALE_PERCENT,
    val thumbnailCellSizePx: Int = PHOTO_THUMBNAIL_CELL_SIZE_PX,
    val maxVisibleThumbnails: Int = PHOTO_MAX_VISIBLE_THUMBNAILS,
    val thumbnailPreloadPaddingPx: Int = PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
) {
    fun normalized(): PhotoClusterSettings {
        return copy(
            radiusPx = radiusPx.coerceIn(MIN_PHOTO_CLUSTER_RADIUS, MAX_PHOTO_CLUSTER_RADIUS),
            minPoints = minPoints.coerceIn(MIN_PHOTO_CLUSTER_MIN_POINTS, MAX_PHOTO_CLUSTER_MIN_POINTS),
            leavesPageSize = leavesPageSize.coerceIn(
                MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE,
                MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
            ),
            maxDistanceKm = maxDistanceKm.coerceIn(
                MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM,
                MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM
            ),
            markerScalePercent = markerScalePercent.coerceIn(
                MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT,
                MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
            ),
            thumbnailCellSizePx = thumbnailCellSizePx.coerceIn(
                MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX,
                MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX
            ),
            maxVisibleThumbnails = maxVisibleThumbnails.coerceIn(
                MIN_PHOTO_MAX_VISIBLE_THUMBNAILS,
                MAX_PHOTO_MAX_VISIBLE_THUMBNAILS
            ),
            thumbnailPreloadPaddingPx = thumbnailPreloadPaddingPx.coerceIn(
                MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX,
                MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
            )
        )
    }
}

const val PHOTO_CLUSTER_RADIUS = 110
const val PHOTO_CLUSTER_MIN_POINTS = 2
const val PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 100
const val PHOTO_CLUSTER_MAX_DISTANCE_KM = 50
const val PHOTO_CLUSTER_MARKER_SCALE_PERCENT = 125
const val PHOTO_THUMBNAIL_CELL_SIZE_PX = 132
const val PHOTO_MAX_VISIBLE_THUMBNAILS = 90
const val PHOTO_THUMBNAIL_PRELOAD_PADDING_PX = 96

const val MIN_PHOTO_CLUSTER_RADIUS = 40
const val MAX_PHOTO_CLUSTER_RADIUS = 320
const val PHOTO_CLUSTER_RADIUS_STEP = 10

const val MIN_PHOTO_CLUSTER_MIN_POINTS = 2
const val MAX_PHOTO_CLUSTER_MIN_POINTS = 10

const val MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 25
const val MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 250
const val PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP = 25

const val MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM = 5
const val MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM = 50
const val PHOTO_CLUSTER_MAX_DISTANCE_KM_STEP = 5

const val MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT = 80
const val MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT = 200
const val PHOTO_CLUSTER_MARKER_SCALE_PERCENT_STEP = 10

const val MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX = 72
const val MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX = 260
const val PHOTO_THUMBNAIL_CELL_SIZE_PX_STEP = 12

const val MIN_PHOTO_MAX_VISIBLE_THUMBNAILS = 20
const val MAX_PHOTO_MAX_VISIBLE_THUMBNAILS = 240
const val PHOTO_MAX_VISIBLE_THUMBNAILS_STEP = 10

const val MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX = 0
const val MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX = 240
const val PHOTO_THUMBNAIL_PRELOAD_PADDING_PX_STEP = 16
