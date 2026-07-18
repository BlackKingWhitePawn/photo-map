package com.example.photomap.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.core.util.AppDiagnostics
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
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
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
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
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

class PhotoMapLayerController {
    private var configuredSourceKey: PhotoMapSourceKey? = null
    private var isPhotoHeatmapConfigured: Boolean = false
    private var isSelectedPhotoConfigured: Boolean = false
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
                "Recreate Traverse layers: features=${featureCollection.features()?.size}, " +
                    "radius=${sourceKey.radiusPx}, minPoints=${sourceKey.minPoints}, " +
                    "markerScale=${sourceKey.markerScalePercent}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Recreate Traverse layers: features=${featureCollection.features()?.size}, " +
                    "radius=${sourceKey.radiusPx}, minPoints=${sourceKey.minPoints}, " +
                    "markerScale=${sourceKey.markerScalePercent}"
            )
            style.recreatePhotoMapLayers(sourceKey, featureCollection, colors)
            configuredSourceKey = sourceKey
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update Traverse source: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Update Traverse source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
            style.updatePhotoMapLayerColors(colors)
        }
    }

    fun reset() {
        configuredSourceKey = null
        isPhotoHeatmapConfigured = false
        isSelectedPhotoConfigured = false
        isTripHeatmapConfigured = false
    }

    fun updatePhotoHeatmap(
        style: Style,
        featureCollection: FeatureCollection
    ) {
        val source = style.getSourceAs<GeoJsonSource>(PHOTO_HEATMAP_SOURCE_ID)
        if (source == null ||
            !isPhotoHeatmapConfigured ||
            style.getLayer(PHOTO_COVERAGE_LAYER_ID) == null ||
            style.getLayer(PHOTO_DENSITY_HEATMAP_LAYER_ID) == null
        ) {
            Log.d(
                PhotoMapLayerLogTag,
                "Recreate photo heatmap layers: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Recreate photo heatmap layers: features=${featureCollection.features()?.size}"
            )
            style.recreatePhotoHeatmapLayer(featureCollection)
            isPhotoHeatmapConfigured = true
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update photo heatmap source: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Update photo heatmap source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
        }
    }

    fun updateSelectedPhotoMarker(
        style: Style,
        featureCollection: FeatureCollection
    ) {
        val source = style.getSourceAs<GeoJsonSource>(SELECTED_PHOTO_SOURCE_ID)
        if (source == null ||
            !isSelectedPhotoConfigured ||
            style.getLayer(SELECTED_PHOTO_SYMBOL_LAYER_ID) == null
        ) {
            Log.d(
                PhotoMapLayerLogTag,
                "Recreate selected photo marker layers: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Recreate selected photo marker layers: features=${featureCollection.features()?.size}"
            )
            style.recreateSelectedPhotoMarkerLayers(featureCollection)
            isSelectedPhotoConfigured = true
        } else {
            Log.d(
                PhotoMapLayerLogTag,
                "Update selected photo marker source: features=${featureCollection.features()?.size}"
            )
            AppDiagnostics.record(
                PhotoMapLayerLogTag,
                "Update selected photo marker source: features=${featureCollection.features()?.size}"
            )
            source.setGeoJson(featureCollection)
        }
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

    fun setDisplayMode(
        style: Style,
        mode: PhotoMapDisplayMode
    ) {
        val showPhotos = mode == PhotoMapDisplayMode.Photos
        val showHeatmap = mode == PhotoMapDisplayMode.Heatmap
        PhotoMapLayerIds.forEach { layerId ->
            style.setLayerVisible(layerId = layerId, visible = showPhotos)
        }
        PhotoHeatmapLayerIds.forEach { layerId ->
            style.setLayerVisible(layerId = layerId, visible = showHeatmap)
        }
        SelectedPhotoLayerIds.forEach { layerId ->
            style.setLayerVisible(layerId = layerId, visible = showHeatmap)
        }
        style.setLayerVisible(layerId = TRIP_HEATMAP_LAYER_ID, visible = false)
    }
}

enum class PhotoMapDisplayMode {
    Heatmap,
    Photos
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

private fun Style.recreatePhotoHeatmapLayer(featureCollection: FeatureCollection) {
    removeLayer(PHOTO_DENSITY_HEATMAP_LAYER_ID)
    removeLayer(PHOTO_COVERAGE_LAYER_ID)
    removeLayer(PHOTO_HEATMAP_LAYER_ID)
    removeSource(PHOTO_HEATMAP_SOURCE_ID)

    addSource(
        GeoJsonSource(
            PHOTO_HEATMAP_SOURCE_ID,
            featureCollection
        )
    )

    val coverageLayer = HeatmapLayer(PHOTO_COVERAGE_LAYER_ID, PHOTO_HEATMAP_SOURCE_ID)
        .withProperties(
            heatmapWeight(0.18f),
            heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.stop(0.00f, Expression.rgba(124, 58, 237, 0.0f)),
                    Expression.stop(0.01f, Expression.rgba(124, 58, 237, 0.16f)),
                    Expression.stop(0.18f, Expression.rgba(124, 58, 237, 0.30f)),
                    Expression.stop(1.00f, Expression.rgba(124, 58, 237, 0.42f))
                )
            ),
            heatmapIntensity(1.0f),
            heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 20f),
                    Expression.stop(8, 25f),
                    Expression.stop(13, 30f),
                    Expression.stop(17, 34f)
                )
            ),
            heatmapOpacity(0.68f)
        )

    val heatmapLayer = HeatmapLayer(PHOTO_DENSITY_HEATMAP_LAYER_ID, PHOTO_HEATMAP_SOURCE_ID)
        .withProperties(
            heatmapWeight(Expression.get(PHOTO_HEATMAP_WEIGHT_PROPERTY)),
            heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.stop(0.00f, Expression.rgba(70, 30, 180, 0.0f)),
                    Expression.stop(0.12f, Expression.rgba(110, 50, 210, 0.75f)),
                    Expression.stop(0.35f, Expression.rgba(20, 180, 210, 0.85f)),
                    Expression.stop(0.58f, Expression.rgba(80, 200, 110, 0.90f)),
                    Expression.stop(0.78f, Expression.rgba(240, 190, 40, 0.95f)),
                    Expression.stop(1.00f, Expression.rgba(210, 55, 145, 1.0f))
                )
            ),
            heatmapIntensity(1.0f),
            heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 26f),
                    Expression.stop(8, 30f),
                    Expression.stop(13, 34f),
                    Expression.stop(17, 38f)
                )
            ),
            heatmapOpacity(0.82f)
        )

    val photoLayerId = PhotoMapLayerIds.firstOrNull { layerId -> getLayer(layerId) != null }
    if (photoLayerId != null) {
        addLayerBelow(coverageLayer, photoLayerId)
        addLayerAbove(heatmapLayer, PHOTO_COVERAGE_LAYER_ID)
        return
    }

    val selectedLayerId = SelectedPhotoLayerIds.firstOrNull { layerId -> getLayer(layerId) != null }
    if (selectedLayerId != null) {
        addLayerBelow(coverageLayer, selectedLayerId)
        addLayerAbove(heatmapLayer, PHOTO_COVERAGE_LAYER_ID)
        return
    }

    val topBaseLayerId = layers.lastOrNull()?.id
    if (topBaseLayerId == null) {
        addLayer(coverageLayer)
    } else {
        addLayerAbove(coverageLayer, topBaseLayerId)
    }
    addLayerAbove(heatmapLayer, PHOTO_COVERAGE_LAYER_ID)
}

private fun Style.recreateSelectedPhotoMarkerLayers(featureCollection: FeatureCollection) {
    removeLayer(SELECTED_PHOTO_SYMBOL_LAYER_ID)
    removeLayer(SELECTED_PHOTO_HALO_LAYER_ID)
    removeSource(SELECTED_PHOTO_SOURCE_ID)
    ensureSelectedPhotoMarkerIcon()

    addSource(
        GeoJsonSource(
            SELECTED_PHOTO_SOURCE_ID,
            featureCollection
        )
    )

    val symbolLayer = SymbolLayer(SELECTED_PHOTO_SYMBOL_LAYER_ID, SELECTED_PHOTO_SOURCE_ID)
        .withProperties(
            iconImage(SELECTED_PHOTO_MARKER_ICON_ID),
            iconSize(1.0f),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )

    val anchorLayerId = listOf(
        PHOTO_DENSITY_HEATMAP_LAYER_ID,
        PHOTO_COVERAGE_LAYER_ID,
        PHOTO_HEATMAP_LAYER_ID,
        PHOTO_CLUSTER_LAYER_ID,
        PHOTO_CLUSTER_THUMBNAIL_LAYER_ID,
        PHOTO_CLUSTER_COUNT_LAYER_ID,
        PHOTO_UNCLUSTERED_LAYER_ID,
        PHOTO_THUMBNAIL_LAYER_ID
    ).firstOrNull { layerId -> getLayer(layerId) != null }

    if (anchorLayerId == null) {
        val topBaseLayerId = layers.lastOrNull()?.id
        if (topBaseLayerId == null) {
            addLayer(symbolLayer)
        } else {
            addLayerAbove(symbolLayer, topBaseLayerId)
        }
    } else {
        addLayerAbove(symbolLayer, anchorLayerId)
    }
}

private fun Style.ensureSelectedPhotoMarkerIcon() {
    runCatching { removeImage(SELECTED_PHOTO_MARKER_ICON_ID) }
    addImage(SELECTED_PHOTO_MARKER_ICON_ID, createSelectedPhotoMarkerIcon())
}

private fun createSelectedPhotoMarkerIcon(): Bitmap {
    val size = SelectedPhotoMarkerIconPx
    val centerX = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pinPath = Path().apply {
        moveTo(centerX, size - 4f)
        cubicTo(25f, 48f, 12f, 39f, 12f, 25f)
        cubicTo(12f, 12f, 21f, 6f, centerX, 6f)
        cubicTo(43f, 6f, 52f, 12f, 52f, 25f)
        cubicTo(52f, 39f, 39f, 48f, centerX, size - 4f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF7C3AED.toInt()
    }
    canvas.drawPath(pinPath, paint)

    paint.apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xFFFFFFFF.toInt()
    }
    canvas.drawPath(pinPath, paint)

    paint.apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    canvas.drawCircle(centerX, 25f, 11f, paint)

    paint.color = 0xFF14B8A6.toInt()
    canvas.drawCircle(centerX, 25f, 5.5f, paint)

    return bitmap
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
            heatmapWeight(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get(TRIP_HEATMAP_WEIGHT_PROPERTY),
                    Expression.stop(0.0f, 0.28f),
                    Expression.stop(0.35f, 0.72f),
                    Expression.stop(1.0f, 1.0f)
                )
            ),
            heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.stop(0.00f, Expression.rgba(33, 150, 243, 0.0f)),
                    Expression.stop(0.12f, Expression.rgba(45, 212, 191, 0.58f)),
                    Expression.stop(0.34f, Expression.rgba(91, 214, 111, 0.72f)),
                    Expression.stop(0.58f, Expression.rgba(255, 214, 102, 0.86f)),
                    Expression.stop(0.78f, Expression.rgba(255, 137, 77, 0.94f)),
                    Expression.stop(1.00f, Expression.rgba(255, 64, 96, 1.0f))
                )
            ),
            heatmapIntensity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 1.15f),
                    Expression.stop(8, 1.55f),
                    Expression.stop(12, 2.15f),
                    Expression.stop(16, 3.05f)
                )
            ),
            heatmapRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 30f),
                    Expression.stop(8, 48f),
                    Expression.stop(12, 72f),
                    Expression.stop(16, 104f)
                )
            ),
            heatmapOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3, 0.82f),
                    Expression.stop(12, 0.88f),
                    Expression.stop(17, 0.76f)
                )
            )
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

private fun Style.setLayerVisible(layerId: String, visible: Boolean) {
    getLayer(layerId)?.setProperties(
        visibility(if (visible) Property.VISIBLE else Property.NONE)
    )
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
const val PHOTO_HEATMAP_SOURCE_ID = "photo-heatmap-source"
const val SELECTED_PHOTO_SOURCE_ID = "selected-photo-source"
const val SELECTED_PHOTO_MARKER_ICON_ID = "selected-photo-location-icon"
const val PHOTO_CLUSTER_LAYER_ID = "photo-cluster-layer"
const val PHOTO_CLUSTER_THUMBNAIL_LAYER_ID = "photo-cluster-thumbnail-layer"
const val PHOTO_CLUSTER_COUNT_LAYER_ID = "photo-cluster-count-layer"
const val PHOTO_UNCLUSTERED_LAYER_ID = "photo-unclustered-layer"
const val PHOTO_THUMBNAIL_LAYER_ID = "photo-thumbnail-layer"
const val PHOTO_HEATMAP_LAYER_ID = "photo-heatmap-layer"
const val PHOTO_COVERAGE_LAYER_ID = "photo-coverage-layer"
const val PHOTO_DENSITY_HEATMAP_LAYER_ID = "photo-density-heatmap-layer"
const val SELECTED_PHOTO_HALO_LAYER_ID = "selected-photo-halo-layer"
const val SELECTED_PHOTO_SYMBOL_LAYER_ID = "selected-photo-symbol-layer"
const val TRIP_HEATMAP_SOURCE_ID = "trip-heatmap-source"
const val TRIP_HEATMAP_LAYER_ID = "trip-heatmap-layer"

private val PhotoMapLayerIds = listOf(
    PHOTO_CLUSTER_LAYER_ID,
    PHOTO_CLUSTER_THUMBNAIL_LAYER_ID,
    PHOTO_CLUSTER_COUNT_LAYER_ID,
    PHOTO_UNCLUSTERED_LAYER_ID,
    PHOTO_THUMBNAIL_LAYER_ID
)

private val PhotoHeatmapLayerIds = listOf(
    PHOTO_COVERAGE_LAYER_ID,
    PHOTO_DENSITY_HEATMAP_LAYER_ID
)

private val SelectedPhotoLayerIds = listOf(
    SELECTED_PHOTO_SYMBOL_LAYER_ID
)

private const val PhotoMapLayerLogTag = "PhotoMapMap"
private const val SelectedPhotoMarkerIconPx = 64
