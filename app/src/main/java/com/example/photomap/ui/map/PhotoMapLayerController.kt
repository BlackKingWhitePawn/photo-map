package com.example.photomap.ui.map

import android.util.Log
import com.example.photomap.core.settings.PhotoClusterSettings
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toString as expressionToString
import org.maplibre.android.style.layers.CircleLayer
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
            style.recreatePhotoMapLayers(sourceKey, featureCollection, colors)
            configuredSourceKey = sourceKey
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update photo map source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
            style.updatePhotoMapLayerColors(colors)
        }
    }

    fun reset() {
        configuredSourceKey = null
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
            return
        }

        Log.d(
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

    addLayer(
        CircleLayer(PHOTO_CLUSTER_LAYER_ID, PHOTO_MAP_SOURCE_ID)
            .withFilter(has(PHOTO_CLUSTER_COUNT_PROPERTY))
            .withProperties(
                circleRadius(
                    step(
                        get(PHOTO_CLUSTER_COUNT_PROPERTY),
                        24f * sourceKey.markerScale,
                        stop(10, 32f * sourceKey.markerScale),
                        stop(50, 42f * sourceKey.markerScale),
                        stop(200, 54f * sourceKey.markerScale)
                    )
                ),
                circleColor(
                    step(
                        get(PHOTO_CLUSTER_COUNT_PROPERTY),
                        color(colors.clusterSmall),
                        stop(10, color(colors.clusterMedium)),
                        stop(50, color(colors.clusterLarge)),
                        stop(200, color(colors.clusterHuge))
                    )
                ),
                circleOpacity(0.92f),
                circleStrokeColor(colors.photoStroke),
                circleStrokeWidth(3f)
            )
    )

    addLayer(
        SymbolLayer(PHOTO_CLUSTER_COUNT_LAYER_ID, PHOTO_MAP_SOURCE_ID)
            .withFilter(has(PHOTO_CLUSTER_COUNT_PROPERTY))
            .withProperties(
                textField(expressionToString(get(PHOTO_CLUSTER_COUNT_ABBREVIATED_PROPERTY))),
                textSize(14f * sourceKey.markerScale.coerceAtMost(1.4f)),
                textColor(colors.clusterText),
                textHaloColor(colors.clusterTextHalo),
                textHaloWidth(1.2f),
                textAllowOverlap(true),
                textIgnorePlacement(true)
            )
    )

    addLayer(
        CircleLayer(PHOTO_UNCLUSTERED_LAYER_ID, PHOTO_THUMBNAIL_SOURCE_ID)
            .withProperties(
                circleRadius(9f),
                circleColor(colors.photo),
                circleStrokeColor(colors.photoStroke),
                circleStrokeWidth(3f),
                circleOpacity(0.78f)
            )
    )

    addLayer(
        SymbolLayer(PHOTO_THUMBNAIL_LAYER_ID, PHOTO_THUMBNAIL_SOURCE_ID)
            .withProperties(
                iconImage("{$PHOTO_THUMBNAIL_KEY_PROPERTY}"),
                iconSize(1.0f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
    )
}

private fun Style.updatePhotoMapLayerColors(colors: PhotoMapLayerColors) {
    getLayerAs<CircleLayer>(PHOTO_CLUSTER_LAYER_ID)?.setProperties(
        circleColor(
            step(
                get(PHOTO_CLUSTER_COUNT_PROPERTY),
                color(colors.clusterSmall),
                stop(10, color(colors.clusterMedium)),
                stop(50, color(colors.clusterLarge)),
                stop(200, color(colors.clusterHuge))
            )
        ),
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
const val PHOTO_CLUSTER_COUNT_LAYER_ID = "photo-cluster-count-layer"
const val PHOTO_UNCLUSTERED_LAYER_ID = "photo-unclustered-layer"
const val PHOTO_THUMBNAIL_LAYER_ID = "photo-thumbnail-layer"

private const val PhotoMapLayerLogTag = "PhotoMapMap"
