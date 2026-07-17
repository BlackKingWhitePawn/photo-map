package com.example.photomap.data.heatmap

import com.example.photomap.data.cluster.PhotoMapBounds

const val HEATMAP_ALGORITHM_VERSION = 1

val TripHeatmapResolutions = listOf(4, 5, 6, 7, 8, 9)

data class TripHeatCellEntity(
    val h3Index: String,
    val resolution: Int,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val tripCount: Int,
    val activeDays: Int,
    val daysSpent: Int,
    val sessionCount: Int,
    val latestTripAt: Long?,
    val intensity: Double,
    val algorithmVersion: Int,
    val updatedAt: Long
)

data class TripHeatContributionEntity(
    val tripId: Long,
    val h3Index: String,
    val resolution: Int,
    val activeDays: Int,
    val daysSpent: Int,
    val sessionCount: Int,
    val weight: Double
)

data class VisibleTripHeatCell(
    val h3Index: String,
    val resolution: Int,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val tripCount: Int,
    val activeDays: Int,
    val daysSpent: Int,
    val sessionCount: Int,
    val latestTripAt: Long?,
    val intensity: Double,
    val normalizedIntensity: Double
)

data class VisibleTripHeatmapContent(
    val resolution: Int,
    val loadedBounds: PhotoMapBounds,
    val cells: List<VisibleTripHeatCell>,
    val dataVersion: Long
)

data class BuiltTripHeatmap(
    val cells: List<TripHeatCellEntity>,
    val contributions: List<TripHeatContributionEntity>,
    val dataVersion: Long
)

fun heatResolutionForZoom(zoom: Double): Int {
    return when {
        zoom < 5.0 -> 4
        zoom < 8.0 -> 5
        zoom < 11.0 -> 6
        zoom < 14.0 -> 7
        zoom < 17.0 -> 8
        else -> 9
    }
}
