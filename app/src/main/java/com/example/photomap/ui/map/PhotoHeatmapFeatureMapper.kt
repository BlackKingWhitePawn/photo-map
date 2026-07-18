package com.example.photomap.ui.map

import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.matchesPhotoDateFilter
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

data class PhotoHeatmapViewportRequest(
    val requestId: Long,
    val zoom: Double,
    val bounds: PhotoMapBounds,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val density: Float,
    val dateFilter: PhotoDateFilter
)

data class PhotoHeatmapCell(
    val id: String,
    val zoom: Int,
    val tileX: Int,
    val tileY: Int,
    val cellX: Int,
    val cellY: Int,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val minTakenAt: Long?,
    val maxTakenAt: Long?,
    val weight: Double
)

data class PhotoHeatmapRenderState(
    val requestId: Long,
    val visiblePhotoCount: Int,
    val cells: List<PhotoHeatmapCell>,
    val featureCollection: FeatureCollection
) {
    companion object {
        val Empty = PhotoHeatmapRenderState(
            requestId = 0L,
            visiblePhotoCount = 0,
            cells = emptyList(),
            featureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())
        )
    }
}

object PhotoHeatmapFeatureMapper {
    suspend fun build(
        photos: Collection<DevicePhoto>,
        request: PhotoHeatmapViewportRequest
    ): PhotoHeatmapRenderState {
        if (request.viewportWidthPx <= 0 || request.viewportHeightPx <= 0 || photos.isEmpty()) {
            return PhotoHeatmapRenderState.Empty.copy(requestId = request.requestId)
        }

        val queryBounds = request.bounds.expanded(PhotoHeatmapViewportPaddingFactor)
        val dataZoom = photoHeatmapDataZoom(request.zoom)
        val worldSizePx = PhotoHeatmapTileSizePx * 2.0.pow(dataZoom)
        val cellsByKey = LinkedHashMap<PhotoHeatmapCellKey, MutablePhotoHeatmapCell>()
        var visiblePhotoCount = 0

        photos.forEachIndexed { index, photo ->
            if (index % PhotoHeatmapCancellationCheckInterval == 0) {
                currentCoroutineContext().ensureActive()
            }
            if (!photo.matchesPhotoDateFilter(request.dateFilter)) {
                return@forEachIndexed
            }
            val point = photo.toHeatmapPoint() ?: return@forEachIndexed
            if (!queryBounds.contains(point.latitude, point.longitude)) {
                return@forEachIndexed
            }
            if (request.bounds.contains(point.latitude, point.longitude)) {
                visiblePhotoCount += 1
            }

            val x = longitudeToMercatorX(point.longitude, worldSizePx)
            val y = latitudeToMercatorY(point.latitude, worldSizePx)
            val key = PhotoHeatmapCellKey.fromMercator(
                zoom = dataZoom,
                x = x,
                y = y
            )
            cellsByKey.getOrPut(key) {
                MutablePhotoHeatmapCell(
                    key = key,
                    latitude = mercatorYToLatitude(key.centerY, worldSizePx),
                    longitude = mercatorXToLongitude(key.centerX, worldSizePx)
                )
            }.add(point)
        }

        val cells = HeatmapNormalizer.normalize(
            cells = cellsByKey.values.map { cell -> cell.toCell() }
        )
        return PhotoHeatmapRenderState(
            requestId = request.requestId,
            visiblePhotoCount = visiblePhotoCount,
            cells = cells,
            featureCollection = FeatureCollection.fromFeatures(
                cells.map { cell -> cell.toFeature() }
            )
        )
    }
}

object HeatmapNormalizer {
    fun normalize(
        cells: List<PhotoHeatmapCell>
    ): List<PhotoHeatmapCell> {
        if (cells.isEmpty()) {
            return emptyList()
        }

        val localReference = percentile(
            values = cells.map { cell -> cell.photoCount },
            percentile = PhotoHeatmapLocalReferencePercentile
        ).coerceAtLeast(PhotoHeatmapMinLocalReferenceCount)
        return cells.map { cell ->
            val finalWeight = logarithmicWeight(
                count = cell.photoCount,
                reference = localReference
            ).coerceIn(PhotoHeatmapMinWeight, 1.0)

            cell.copy(weight = finalWeight)
        }
    }

    private fun logarithmicWeight(
        count: Int,
        reference: Double
    ): Double {
        val safeReference = reference.coerceAtLeast(1.0)
        return (ln(1.0 + count.coerceAtLeast(0)) / ln(1.0 + safeReference))
            .coerceIn(0.0, 1.0)
    }

    private fun percentile(
        values: List<Int>,
        percentile: Double
    ): Double {
        if (values.isEmpty()) {
            return 0.0
        }
        val sortedValues = values.sorted()
        if (sortedValues.size == 1) {
            return sortedValues.first().toDouble()
        }
        val rank = (sortedValues.lastIndex * percentile.coerceIn(0.0, 1.0))
        val lowerIndex = floor(rank).toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(sortedValues.lastIndex)
        val fraction = rank - lowerIndex
        return sortedValues[lowerIndex] + (sortedValues[upperIndex] - sortedValues[lowerIndex]) * fraction
    }
}

object SelectedPhotoMarkerFeatureMapper {
    fun toFeatureCollection(photo: DevicePhoto?): FeatureCollection {
        val point = photo?.toHeatmapPoint()
        if (point == null) {
            return FeatureCollection.fromFeatures(emptyList<Feature>())
        }

        return toFeatureCollection(
            photoId = point.photoId,
            latitude = point.latitude,
            longitude = point.longitude
        )
    }

    fun toFeatureCollection(
        photoId: Long,
        latitude: Double,
        longitude: Double
    ): FeatureCollection {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return FeatureCollection.fromFeatures(emptyList<Feature>())
        }
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return FeatureCollection.fromFeatures(emptyList<Feature>())
        }

        return FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
                    addStringProperty(PHOTO_ID_PROPERTY, photoId.toString())
                }
            )
        )
    }
}

private data class PhotoHeatmapPoint(
    val photoId: Long,
    val latitude: Double,
    val longitude: Double,
    val takenAt: Long?
)

private data class PhotoHeatmapCellKey(
    val zoom: Int,
    val tileX: Int,
    val tileY: Int,
    val cellX: Int,
    val cellY: Int
) {
    val id: String
        get() = "$zoom:$tileX:$tileY:$cellX:$cellY"

    val centerX: Double
        get() = tileX * PhotoHeatmapTileSizePx + (cellX + 0.5) * PhotoHeatmapCellSizePx

    val centerY: Double
        get() = tileY * PhotoHeatmapTileSizePx + (cellY + 0.5) * PhotoHeatmapCellSizePx

    companion object {
        fun fromMercator(
            zoom: Int,
            x: Double,
            y: Double
        ): PhotoHeatmapCellKey {
            val tileCount = 1 shl zoom
            val normalizedX = x.coerceIn(0.0, tileCount * PhotoHeatmapTileSizePx - 1.0)
            val normalizedY = y.coerceIn(0.0, tileCount * PhotoHeatmapTileSizePx - 1.0)
            val tileX = floor(normalizedX / PhotoHeatmapTileSizePx).toInt().coerceIn(0, tileCount - 1)
            val tileY = floor(normalizedY / PhotoHeatmapTileSizePx).toInt().coerceIn(0, tileCount - 1)
            val localX = normalizedX - tileX * PhotoHeatmapTileSizePx
            val localY = normalizedY - tileY * PhotoHeatmapTileSizePx
            val cellX = floor(localX / PhotoHeatmapCellSizePx)
                .toInt()
                .coerceIn(0, PhotoHeatmapCellsPerTile - 1)
            val cellY = floor(localY / PhotoHeatmapCellSizePx)
                .toInt()
                .coerceIn(0, PhotoHeatmapCellsPerTile - 1)
            return PhotoHeatmapCellKey(
                zoom = zoom,
                tileX = tileX,
                tileY = tileY,
                cellX = cellX,
                cellY = cellY
            )
        }
    }
}

private class MutablePhotoHeatmapCell(
    private val key: PhotoHeatmapCellKey,
    private val latitude: Double,
    private val longitude: Double
) {
    private var photoCount = 0
    private var minTakenAt: Long? = null
    private var maxTakenAt: Long? = null

    fun add(point: PhotoHeatmapPoint) {
        photoCount += 1
        point.takenAt?.let { takenAt ->
            minTakenAt = minTakenAt?.let { current -> minOf(current, takenAt) } ?: takenAt
            maxTakenAt = maxTakenAt?.let { current -> maxOf(current, takenAt) } ?: takenAt
        }
    }

    fun toCell(): PhotoHeatmapCell {
        return PhotoHeatmapCell(
            id = key.id,
            zoom = key.zoom,
            tileX = key.tileX,
            tileY = key.tileY,
            cellX = key.cellX,
            cellY = key.cellY,
            latitude = latitude,
            longitude = longitude,
            photoCount = photoCount,
            minTakenAt = minTakenAt,
            maxTakenAt = maxTakenAt,
            weight = 0.0
        )
    }
}

private fun DevicePhoto.toHeatmapPoint(): PhotoHeatmapPoint? {
    val point = toMapPoint() ?: return null
    if (point.latitude == 0.0 && point.longitude == 0.0) {
        return null
    }
    return PhotoHeatmapPoint(
        photoId = mediaId,
        latitude = point.latitude,
        longitude = point.longitude,
        takenAt = dateTaken ?: dateModified ?: dateAdded
    )
}

private fun PhotoHeatmapCell.toFeature(): Feature {
    return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
        addStringProperty(PHOTO_HEATMAP_CELL_ID_PROPERTY, id)
        addNumberProperty(PHOTO_HEATMAP_ZOOM_PROPERTY, zoom)
        addNumberProperty(PHOTO_HEATMAP_TILE_X_PROPERTY, tileX)
        addNumberProperty(PHOTO_HEATMAP_TILE_Y_PROPERTY, tileY)
        addNumberProperty(PHOTO_HEATMAP_CELL_X_PROPERTY, cellX)
        addNumberProperty(PHOTO_HEATMAP_CELL_Y_PROPERTY, cellY)
        addNumberProperty(PHOTO_HEATMAP_PHOTO_COUNT_PROPERTY, photoCount)
        addNumberProperty(PHOTO_HEATMAP_WEIGHT_PROPERTY, weight)
        minTakenAt?.let { value -> addNumberProperty(PHOTO_HEATMAP_MIN_TAKEN_AT_PROPERTY, value) }
        maxTakenAt?.let { value -> addNumberProperty(PHOTO_HEATMAP_MAX_TAKEN_AT_PROPERTY, value) }
    }
}

private fun PhotoMapBounds.contains(latitude: Double, longitude: Double): Boolean {
    return latitude in south..north && longitude in west..east
}

private fun photoHeatmapDataZoom(zoom: Double): Int {
    return floor(zoom + 0.5).toInt().coerceIn(PhotoHeatmapMinZoom, PhotoHeatmapMaxZoom)
}

private fun longitudeToMercatorX(longitude: Double, worldSizePx: Double): Double {
    return ((longitude + 180.0) / 360.0) * worldSizePx
}

private fun latitudeToMercatorY(latitude: Double, worldSizePx: Double): Double {
    val clampedLatitude = latitude.coerceIn(-PhotoHeatmapMaxMercatorLatitude, PhotoHeatmapMaxMercatorLatitude)
    val sinLatitude = sin(clampedLatitude * PI / 180.0)
    return (0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)) * worldSizePx
}

private fun mercatorXToLongitude(x: Double, worldSizePx: Double): Double {
    return x / worldSizePx * 360.0 - 180.0
}

private fun mercatorYToLatitude(y: Double, worldSizePx: Double): Double {
    val n = PI - 2.0 * PI * y / worldSizePx
    return atan(sinh(n)) * 180.0 / PI
}

internal const val PHOTO_HEATMAP_WEIGHT_PROPERTY = "weight"
internal const val PHOTO_HEATMAP_CELL_ID_PROPERTY = "cell_id"
internal const val PHOTO_HEATMAP_ZOOM_PROPERTY = "zoom"
internal const val PHOTO_HEATMAP_TILE_X_PROPERTY = "tile_x"
internal const val PHOTO_HEATMAP_TILE_Y_PROPERTY = "tile_y"
internal const val PHOTO_HEATMAP_CELL_X_PROPERTY = "cell_x"
internal const val PHOTO_HEATMAP_CELL_Y_PROPERTY = "cell_y"
internal const val PHOTO_HEATMAP_PHOTO_COUNT_PROPERTY = "photo_count"
internal const val PHOTO_HEATMAP_MIN_TAKEN_AT_PROPERTY = "min_taken_at"
internal const val PHOTO_HEATMAP_MAX_TAKEN_AT_PROPERTY = "max_taken_at"

private const val PhotoHeatmapTileSizePx = 256.0
private const val PhotoHeatmapCellsPerTile = 32
private const val PhotoHeatmapCellSizePx = PhotoHeatmapTileSizePx / PhotoHeatmapCellsPerTile
private const val PhotoHeatmapMinZoom = 0
private const val PhotoHeatmapMaxZoom = 20
private const val PhotoHeatmapViewportPaddingFactor = 0.15
private const val PhotoHeatmapCancellationCheckInterval = 512
private const val PhotoHeatmapLocalReferencePercentile = 0.95
private const val PhotoHeatmapMinLocalReferenceCount = 1.0
private const val PhotoHeatmapMinWeight = 0.08
private const val PhotoHeatmapMaxMercatorLatitude = 85.05112878
