package com.example.photomap.ui.map

import android.util.Log
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.core.util.AppDiagnostics
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory.heatmapColor
import org.maplibre.android.style.layers.PropertyFactory.heatmapIntensity
import org.maplibre.android.style.layers.PropertyFactory.heatmapOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapRadius
import org.maplibre.android.style.layers.PropertyFactory.heatmapWeight
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

class PhotoMapLayerController {
    private var configuredSourceKey: PhotoMapSourceKey? = null
    private var isTripHeatmapConfigured: Boolean = false

    fun update(
        style: Style,
        featureCollection: FeatureCollection,
        settings: PhotoClusterSettings,
        colors: PhotoMapLayerColors
    ) {
        val sourceKey = PhotoMapSourceKey(
            radiusPx = settings.radiusPx,
            minPoints = settings.minPoints,
            markerScalePercent = settings.markerScalePercent
        )
        val source = style.getSourceAs<GeoJsonSource>(PHOTO_MAP_SOURCE_ID)
        if (source == null || configuredSourceKey != sourceKey || !style.hasPhotoMapLayers()) {
            Log.d(
                PhotoMapLayerLogTag,
                "Recreate photo map layers: features=${featureCollection.features()?.size}, " +
                    "radius=${sourceKey.radiusPx}, minPoints=${sourceKey.minPoints}, " +
                    "markerScale=${sourceKey.markerScalePercent}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Recreate photo map layers: features=${featureCollection.features()?.size}, " +
                    "radius=${sourceKey.radiusPx}, minPoints=${sourceKey.minPoints}, " +
                    "markerScale=${sourceKey.markerScalePercent}"
            )
            style.recreatePhotoMapLayers(sourceKey, featureCollection, colors)
            configuredSourceKey = sourceKey
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update photo map source: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Update photo map source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
            style.updatePhotoMapLayerColors(colors)
        }
    }

    fun reset() {
        configuredSourceKey = null
        isTripHeatmapConfigured = false
    }

    fun updateTripHeatmap(
        style: Style,
        featureCollection: FeatureCollection
    ) {
        val source = style.getSourceAs<GeoJsonSource>(TRIP_HEATMAP_SOURCE_ID)
        if (source == null || !isTripHeatmapConfigured || style.getLayer(TRIP_HEATMAP_LAYER_ID) == null) {
            Log.d(
                PhotoMapLayerLogTag,
                "Recreate trip heatmap layer: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Recreate trip heatmap layer: features=${featureCollection.features()?.size}"
            )
            style.recreateTripHeatmapLayer(featureCollection)
            isTripHeatmapConfigured = true
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update trip heatmap source: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Update trip heatmap source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
        }
    }

    fun updateVisibleThumbnails(
        style: Style,
        featureCollection: FeatureCollection
    ) {
        val source = style.getSourceAs<GeoJsonSource>(PHOTO_THUMBNAIL_SOURCE_ID)
        if (source == null) {
            Log.w(
                PhotoMapLayerLogTag,
                "Visible thumbnail source is missing: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Visible thumbnail source is missing: features=${featureCollection.features()?.size}"
            )
            return
        }

        Log.d(
            PhotoMapLayerLogTag,
            "Update visible thumbnail source: features=${featureCollection.features()?.size}"
        )
        AppDiagnostics.record(
            PhotoMapLayerLogTag,
            "Update visible thumbnail source: features=${featureCollection.features()?.size}"
        )
        source.setGeoJson(featureCollection)
    }
}

data class PhotoMapLayerColors(
    val clusterSmall: Int,
    val clusterMedium: Int,
    val clusterLarge: Int,
    val clusterHuge: Int,
    val clusterText: Int,
    val clusterTextHalo: Int,
    val photo: Int,
    val photoStroke: Int
)

private fun Style.recreatePhotoMapLayers(
    sourceKey: PhotoMapSourceKey,
    featureCollection: FeatureCollection,
    colors: PhotoMapLayerColors
) {
    removeLayer(PHOTO_THUMBNAIL_LAYER_ID)
    removeLayer(PHOTO_UNCLUSTERED_LAYER_ID)
    removeLayer(PHOTO_CLUSTER_COUNT_LAYER_ID)
    removeLayer(PHOTO_CLUSTER_THUMBNAIL_LAYER_ID)
    removeLayer(PHOTO_CLUSTER_LAYER_ID)
    removeSource(PHOTO_THUMBNAIL_SOURCE_ID)
    removeSource(PHOTO_MAP_SOURCE_ID)

    addSource(
        GeoJsonSource(
            PHOTO_MAP_SOURCE_ID,
            featureCollection
        )
    )

    addSource(
        GeoJsonSource(
            PHOTO_THUMBNAIL_SOURCE_ID,
            FeatureCollection.fromFeatures(emptyList<Feature>())
        )
    )

    val topBaseLayerId = layers.lastOrNull()?.id
    val clusterLayer = CircleLayer(PHOTO_CLUSTER_LAYER_ID, PHOTO_MAP_SOURCE_ID)
        .withProperties(
            circleRadius(36f * sourceKey.markerScale),
            circleColor(colors.clusterSmall),
            circleOpacity(0.92f),
            circleStrokeColor(colors.photoStroke),
            circleStrokeWidth(3f)
        )
    if (topBaseLayerId == null) {
        addLayer(clusterLayer)
    } else {
        addLayerAbove(clusterLayer, topBaseLayerId)
    }

    addLayerAbove(
        SymbolLayer(PHOTO_CLUSTER_THUMBNAIL_LAYER_ID, PHOTO_MAP_SOURCE_ID)
            .withProperties(
                iconImage("{$PHOTO_CLUSTER_THUMBNAIL_KEY_PROPERTY}"),
                iconSize(1.0f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            ),
        PHOTO_CLUSTER_LAYER_ID
    )

    addLayerAbove(
        SymbolLayer(PHOTO_CLUSTER_COUNT_LAYER_ID, PHOTO_MAP_SOURCE_ID)
            .withProperties(
                textField("{$PHOTO_CLUSTER_COUNT_ABBREVIATED_PROPERTY}"),
                textSize(14f * sourceKey.markerScale.coerceAtMost(1.4f)),
                textColor(colors.clusterText),
                textHaloColor(colors.clusterTextHalo),
                textHaloWidth(1.2f),
                textAllowOverlap(true),
                textIgnorePlacement(true)
            ),
        PHOTO_CLUSTER_THUMBNAIL_LAYER_ID
    )

    addLayerAbove(
        CircleLayer(PHOTO_UNCLUSTERED_LAYER_ID, PHOTO_THUMBNAIL_SOURCE_ID)
            .withProperties(
                circleRadius(9f),
                circleColor(colors.photo),
                circleStrokeColor(colors.photoStroke),
                circleStrokeWidth(3f),
                circleOpacity(0.78f)
            ),
        PHOTO_CLUSTER_COUNT_LAYER_ID
    )

    addLayerAbove(
        SymbolLayer(PHOTO_THUMBNAIL_LAYER_ID, PHOTO_THUMBNAIL_SOURCE_ID)
            .withProperties(
                iconImage("{$PHOTO_THUMBNAIL_KEY_PROPERTY}"),
                iconSize(1.0f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            ),
        PHOTO_UNCLUSTERED_LAYER_ID
    )
}

private fun Style.recreateTripHeatmapLayer(featureCollection: FeatureCollection) {
    removeLayer(TRIP_HEATMAP_LAYER_ID)
    removeSource(TRIP_HEATMAP_SOURCE_ID)

    addSource(
        GeoJsonSource(
            TRIP_HEATMAP_SOURCE_ID,
            featureCollection
        )
    )

    val heatmapLayer = HeatmapLayer(TRIP_HEATMAP_LAYER_ID, TRIP_HEATMAP_SOURCE_ID)
        .withProperties(
            heatmapWeight(Expression.get(TRIP_HEATMAP_WEIGHT_PROPERTY)),
            heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.stop(0.00f, Expression.rgba(45, 212, 191, 0.0f)),
                    Expression.stop(0.20f, Expression.rgba(45, 212, 191, 0.28f)),
                    Expression.stop(0.45f, Expression.rgba(255, 209, 102, 0.62f)),
                    Expression.stop(0.70f, Expression.rgba(255, 122, 89, 0.78f)),
                    Expression.stop(1.00f, Expression.rgba(255, 92, 122, 0.92f))
                )
            ),
            heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 0.55f),
                    Expression.stop(10, 1.0f),
                    Expression.stop(16, 1.35f)
                )
            ),
            heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 16f),
                    Expression.stop(10, 28f),
                    Expression.stop(16, 44f)
                )
            ),
            heatmapOpacity(0.55f)
        )

    val photoLayerId = listOf(
        PHOTO_CLUSTER_LAYER_ID,
        PHOTO_CLUSTER_THUMBNAIL_LAYER_ID,
        PHOTO_CLUSTER_COUNT_LAYER_ID,
        PHOTO_UNCLUSTERED_LAYER_ID,
        PHOTO_THUMBNAIL_LAYER_ID
    ).firstOrNull { layerId -> getLayer(layerId) != null }
    if (photoLayerId != null) {
        addLayerBelow(heatmapLayer, photoLayerId)
        return
    }

    val topBaseLayerId = layers.lastOrNull()?.id
    if (topBaseLayerId == null) {
        addLayer(heatmapLayer)
    } else {
        addLayerAbove(heatmapLayer, topBaseLayerId)
    }
}

private fun Style.updatePhotoMapLayerColors(colors: PhotoMapLayerColors) {
    getLayerAs<CircleLayer>(PHOTO_CLUSTER_LAYER_ID)?.setProperties(
        circleColor(colors.clusterSmall),
        circleStrokeColor(colors.photoStroke)
    )
    getLayerAs<SymbolLayer>(PHOTO_CLUSTER_COUNT_LAYER_ID)?.setProperties(
        textColor(colors.clusterText),
        textHaloColor(colors.clusterTextHalo)
    )
    getLayerAs<CircleLayer>(PHOTO_UNCLUSTERED_LAYER_ID)?.setProperties(
        circleColor(colors.photo),
        circleStrokeColor(colors.photoStroke)
    )
}

private fun Style.hasPhotoMapLayers(): Boolean {
    return getLayer(PHOTO_CLUSTER_LAYER_ID) != null &&
        getLayer(PHOTO_CLUSTER_THUMBNAIL_LAYER_ID) != null &&
        getLayer(PHOTO_CLUSTER_COUNT_LAYER_ID) != null &&
        getLayer(PHOTO_UNCLUSTERED_LAYER_ID) != null &&
        getLayer(PHOTO_THUMBNAIL_LAYER_ID) != null
}

private data class PhotoMapSourceKey(
    val radiusPx: Int,
    val minPoints: Int,
    val markerScalePercent: Int
) {
    val markerScale: Float
        get() = markerScalePercent.coerceIn(80, 200) / 100f
}

const val PHOTO_MAP_SOURCE_ID = "photo-map-source"
const val PHOTO_THUMBNAIL_SOURCE_ID = "photo-thumbnail-source"
const val PHOTO_CLUSTER_LAYER_ID = "photo-cluster-layer"
const val PHOTO_CLUSTER_THUMBNAIL_LAYER_ID = "photo-cluster-thumbnail-layer"
const val PHOTO_CLUSTER_COUNT_LAYER_ID = "photo-cluster-count-layer"
const val PHOTO_UNCLUSTERED_LAYER_ID = "photo-unclustered-layer"
const val PHOTO_THUMBNAIL_LAYER_ID = "photo-thumbnail-layer"
const val TRIP_HEATMAP_SOURCE_ID = "trip-heatmap-source"
const val TRIP_HEATMAP_LAYER_ID = "trip-heatmap-layer"

private const val PhotoMapLayerLogTag = "PhotoMapMap"
