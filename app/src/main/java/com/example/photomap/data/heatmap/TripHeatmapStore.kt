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
        val resolution = heatResolutionForZoom(zoom)
        val expandedBounds = bounds.expanded(ViewportPaddingFactor)
        val cells = database.getVisibleTripHeatCells(
            resolution = resolution,
            bounds = expandedBounds
        )
        val maxIntensity = database.getTripHeatMaxIntensity(resolution)
            .coerceAtLeast(MinNormalizationIntensity)
        val visibleCells = cells.map { cell ->
            cell.toVisibleTripHeatCell(maxIntensity)
        }
        val message = "Trip heatmap query: resolution=$resolution, zoom=$zoom, " +
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

    companion object {
        private const val Tag = "PhotoMapTripHeat"
        private const val ViewportPaddingFactor = 0.45
        private const val MinNormalizationIntensity = 0.0001
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
