package com.example.photomap.data.heatmap

import android.content.Context
import android.util.Log
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.local.PhotoIndexDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripHeatmapStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val database = PhotoIndexDatabase(context.applicationContext)

    suspend fun loadVisibleHeatmap(
        bounds: PhotoMapBounds,
        zoom: Double
    ): VisibleTripHeatmapContent = withContext(ioDispatcher) {
        val requestedResolution = heatResolutionForZoom(zoom)
        val expandedBounds = bounds.expanded(ViewportPaddingFactor)
        val resolution = selectVisibleResolution(
            requestedResolution = requestedResolution,
            bounds = expandedBounds
        )
        val cells = database.getVisibleTripHeatCells(
            resolution = resolution,
            bounds = expandedBounds
        )
        val maxIntensity = database.getTripHeatMaxIntensity(resolution)
            .coerceAtLeast(MinNormalizationIntensity)
        val visibleCells = cells.map { cell ->
            cell.toVisibleTripHeatCell(maxIntensity)
        }
        val message = "Trip heatmap query: requestedResolution=$requestedResolution, resolution=$resolution, zoom=$zoom, " +
            "cells=${visibleCells.size}, bounds=${expandedBounds.south}," +
            "${expandedBounds.west},${expandedBounds.north},${expandedBounds.east}"
        Log.d(Tag, message)
        AppDiagnostics.record(Tag, message)
        VisibleTripHeatmapContent(
            resolution = resolution,
            loadedBounds = expandedBounds,
            cells = visibleCells,
            dataVersion = database.getTripHeatDataVersion()
        )
    }

    private fun selectVisibleResolution(
        requestedResolution: Int,
        bounds: PhotoMapBounds
    ): Int {
        var resolution = requestedResolution
        while (resolution > MinFallbackResolution) {
            val count = database.getVisibleTripHeatCells(
                resolution = resolution,
                bounds = bounds
            ).size
            if (count >= MinVisibleCellsForResolution) {
                return resolution
            }
            resolution -= 1
        }
        return resolution
    }

    companion object {
        private const val Tag = "PhotoMapTripHeat"
        private const val ViewportPaddingFactor = 0.85
        private const val MinNormalizationIntensity = 0.0001
        private const val MinFallbackResolution = 4
        private const val MinVisibleCellsForResolution = 6
    }
}

private fun TripHeatCellEntity.toVisibleTripHeatCell(maxIntensity: Double): VisibleTripHeatCell {
    return VisibleTripHeatCell(
        h3Index = h3Index,
        resolution = resolution,
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        tripCount = tripCount,
        activeDays = activeDays,
        daysSpent = daysSpent,
        sessionCount = sessionCount,
        latestTripAt = latestTripAt,
        intensity = intensity,
        normalizedIntensity = (intensity / maxIntensity).coerceIn(0.0, 1.0)
    )
}
