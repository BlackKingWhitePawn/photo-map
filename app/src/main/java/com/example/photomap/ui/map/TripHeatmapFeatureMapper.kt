package com.example.photomap.ui.map

import com.example.photomap.data.heatmap.VisibleTripHeatCell
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object TripHeatmapFeatureMapper {
    fun toFeatureCollection(cells: List<VisibleTripHeatCell>): FeatureCollection {
        val features = cells.mapNotNull { cell -> cell.toFeature() }
        return FeatureCollection.fromFeatures(features)
    }
}

private fun VisibleTripHeatCell.toFeature(): Feature? {
    if (!centerLatitude.isFinite() || !centerLongitude.isFinite()) {
        return null
    }
    if (centerLatitude !in -90.0..90.0 || centerLongitude !in -180.0..180.0) {
        return null
    }

    return Feature.fromGeometry(Point.fromLngLat(centerLongitude, centerLatitude)).apply {
        addNumberProperty(TRIP_HEATMAP_WEIGHT_PROPERTY, normalizedIntensity.coerceIn(0.0, 1.0))
        addNumberProperty(TRIP_HEATMAP_INTENSITY_PROPERTY, intensity)
        addNumberProperty(TRIP_HEATMAP_TRIP_COUNT_PROPERTY, tripCount)
        addNumberProperty(TRIP_HEATMAP_DAYS_SPENT_PROPERTY, daysSpent)
        addNumberProperty(TRIP_HEATMAP_ACTIVE_DAYS_PROPERTY, activeDays)
        addNumberProperty(TRIP_HEATMAP_SESSION_COUNT_PROPERTY, sessionCount)
        addNumberProperty(TRIP_HEATMAP_RESOLUTION_PROPERTY, resolution)
        addStringProperty(TRIP_HEATMAP_H3_INDEX_PROPERTY, h3Index)
    }
}

internal const val TRIP_HEATMAP_WEIGHT_PROPERTY = "weight"
internal const val TRIP_HEATMAP_INTENSITY_PROPERTY = "intensity"
internal const val TRIP_HEATMAP_TRIP_COUNT_PROPERTY = "trip_count"
internal const val TRIP_HEATMAP_DAYS_SPENT_PROPERTY = "days_spent"
internal const val TRIP_HEATMAP_ACTIVE_DAYS_PROPERTY = "active_days"
internal const val TRIP_HEATMAP_SESSION_COUNT_PROPERTY = "session_count"
internal const val TRIP_HEATMAP_RESOLUTION_PROPERTY = "resolution"
internal const val TRIP_HEATMAP_H3_INDEX_PROPERTY = "h3_index"
