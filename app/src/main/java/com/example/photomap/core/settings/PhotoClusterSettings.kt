package com.example.photomap.core.settings

data class PhotoClusterSettings(
    val radiusPx: Int = PHOTO_CLUSTER_RADIUS,
    val minPoints: Int = PHOTO_CLUSTER_MIN_POINTS,
    val leavesPageSize: Int = PHOTO_CLUSTER_LEAVES_PAGE_SIZE
) {
    fun normalized(): PhotoClusterSettings {
        return copy(
            radiusPx = radiusPx.coerceIn(MIN_PHOTO_CLUSTER_RADIUS, MAX_PHOTO_CLUSTER_RADIUS),
            minPoints = minPoints.coerceIn(MIN_PHOTO_CLUSTER_MIN_POINTS, MAX_PHOTO_CLUSTER_MIN_POINTS),
            leavesPageSize = leavesPageSize.coerceIn(
                MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE,
                MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
            )
        )
    }
}

const val PHOTO_CLUSTER_RADIUS = 50
const val PHOTO_CLUSTER_MIN_POINTS = 2
const val PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 100

const val MIN_PHOTO_CLUSTER_RADIUS = 20
const val MAX_PHOTO_CLUSTER_RADIUS = 120
const val PHOTO_CLUSTER_RADIUS_STEP = 5

const val MIN_PHOTO_CLUSTER_MIN_POINTS = 2
const val MAX_PHOTO_CLUSTER_MIN_POINTS = 10

const val MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 25
const val MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE = 250
const val PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP = 25
