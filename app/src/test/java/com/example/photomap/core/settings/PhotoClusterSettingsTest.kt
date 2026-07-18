package com.example.photomap.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoClusterSettingsTest {
    @Test
    fun defaultsMatchConstants() {
        assertEquals(
            PhotoClusterSettings(
                radiusPx = PHOTO_CLUSTER_RADIUS,
                minPoints = PHOTO_CLUSTER_MIN_POINTS,
                leavesPageSize = PHOTO_CLUSTER_LEAVES_PAGE_SIZE,
                maxDistanceKm = PHOTO_CLUSTER_MAX_DISTANCE_KM,
                densityCoefficientPercent = PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT,
                markerScalePercent = PHOTO_CLUSTER_MARKER_SCALE_PERCENT,
                thumbnailCellSizePx = PHOTO_THUMBNAIL_CELL_SIZE_PX,
                maxVisibleThumbnails = PHOTO_MAX_VISIBLE_THUMBNAILS,
                thumbnailPreloadPaddingPx = PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
            ),
            PhotoClusterSettings()
        )
    }

    @Test
    fun normalizedClampsValuesBelowMinimums() {
        val normalized = PhotoClusterSettings(
            radiusPx = Int.MIN_VALUE,
            minPoints = Int.MIN_VALUE,
            leavesPageSize = Int.MIN_VALUE,
            maxDistanceKm = Int.MIN_VALUE,
            densityCoefficientPercent = Int.MIN_VALUE,
            markerScalePercent = Int.MIN_VALUE,
            thumbnailCellSizePx = Int.MIN_VALUE,
            maxVisibleThumbnails = Int.MIN_VALUE,
            thumbnailPreloadPaddingPx = Int.MIN_VALUE
        ).normalized()

        assertEquals(MIN_PHOTO_CLUSTER_RADIUS, normalized.radiusPx)
        assertEquals(MIN_PHOTO_CLUSTER_MIN_POINTS, normalized.minPoints)
        assertEquals(MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE, normalized.leavesPageSize)
        assertEquals(MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM, normalized.maxDistanceKm)
        assertEquals(MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT, normalized.densityCoefficientPercent)
        assertEquals(MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT, normalized.markerScalePercent)
        assertEquals(MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX, normalized.thumbnailCellSizePx)
        assertEquals(MIN_PHOTO_MAX_VISIBLE_THUMBNAILS, normalized.maxVisibleThumbnails)
        assertEquals(MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX, normalized.thumbnailPreloadPaddingPx)
    }

    @Test
    fun normalizedClampsValuesAboveMaximums() {
        val normalized = PhotoClusterSettings(
            radiusPx = Int.MAX_VALUE,
            minPoints = Int.MAX_VALUE,
            leavesPageSize = Int.MAX_VALUE,
            maxDistanceKm = Int.MAX_VALUE,
            densityCoefficientPercent = Int.MAX_VALUE,
            markerScalePercent = Int.MAX_VALUE,
            thumbnailCellSizePx = Int.MAX_VALUE,
            maxVisibleThumbnails = Int.MAX_VALUE,
            thumbnailPreloadPaddingPx = Int.MAX_VALUE
        ).normalized()

        assertEquals(MAX_PHOTO_CLUSTER_RADIUS, normalized.radiusPx)
        assertEquals(MAX_PHOTO_CLUSTER_MIN_POINTS, normalized.minPoints)
        assertEquals(MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE, normalized.leavesPageSize)
        assertEquals(MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM, normalized.maxDistanceKm)
        assertEquals(MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT, normalized.densityCoefficientPercent)
        assertEquals(MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT, normalized.markerScalePercent)
        assertEquals(MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX, normalized.thumbnailCellSizePx)
        assertEquals(MAX_PHOTO_MAX_VISIBLE_THUMBNAILS, normalized.maxVisibleThumbnails)
        assertEquals(MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX, normalized.thumbnailPreloadPaddingPx)
    }

    @Test
    fun normalizedKeepsValuesInsideRange() {
        val settings = PhotoClusterSettings(
            radiusPx = MIN_PHOTO_CLUSTER_RADIUS + PHOTO_CLUSTER_RADIUS_STEP,
            minPoints = MIN_PHOTO_CLUSTER_MIN_POINTS + 1,
            leavesPageSize = MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE + PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP,
            maxDistanceKm = MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM + PHOTO_CLUSTER_MAX_DISTANCE_KM_STEP,
            densityCoefficientPercent = MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT + PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP,
            markerScalePercent = MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT + PHOTO_CLUSTER_MARKER_SCALE_PERCENT_STEP,
            thumbnailCellSizePx = MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX + PHOTO_THUMBNAIL_CELL_SIZE_PX_STEP,
            maxVisibleThumbnails = MIN_PHOTO_MAX_VISIBLE_THUMBNAILS + PHOTO_MAX_VISIBLE_THUMBNAILS_STEP,
            thumbnailPreloadPaddingPx = MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX + PHOTO_THUMBNAIL_PRELOAD_PADDING_PX_STEP
        )

        assertEquals(settings, settings.normalized())
    }
}
