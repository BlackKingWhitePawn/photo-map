package com.example.photomap.ui.map

import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.matchesPhotoDateFilter
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
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
        val cellSizePx = photoHeatmapCellSizePx(
            zoom = request.zoom,
            density = request.density
        )
        val worldSizePx = PhotoHeatmapTileSizePx * 2.0.pow(request.zoom)
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
            val key = PhotoHeatmapCellKey(
                x = floor(x / cellSizePx).toInt(),
                y = floor(y / cellSizePx).toInt()
            )
            cellsByKey.getOrPut(key) {
                MutablePhotoHeatmapCell(id = "${key.x}:${key.y}")
            }.add(point)
        }

        val cells = HeatmapNormalizer.normalize(
            cells = cellsByKey.values.map { cell -> cell.toCell() },
            zoom = request.zoom
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
        cells: List<PhotoHeatmapCell>,
        zoom: Double
    ): List<PhotoHeatmapCell> {
        if (cells.isEmpty()) {
            return emptyList()
        }

        val localReference = percentile(
            values = cells.map { cell -> cell.photoCount },
            percentile = PhotoHeatmapLocalReferencePercentile
        ).coerceAtLeast(PhotoHeatmapMinLocalReferenceCount)
        val absoluteReference = photoHeatmapAbsoluteReferenceCount(zoom)
        return cells.map { cell ->
            val localWeight = logarithmicWeight(
                count = cell.photoCount,
                reference = localReference
            )
            val absoluteWeight = logarithmicWeight(
                count = cell.photoCount,
                reference = absoluteReference
            )
            val finalWeight = (
                PhotoHeatmapLocalWeightRatio * localWeight +
                    PhotoHeatmapAbsoluteWeightRatio * absoluteWeight
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
    val x: Int,
    val y: Int
)

private class MutablePhotoHeatmapCell(
    private val id: String
) {
    private var photoCount = 0
    private var weightedLatitude = 0.0
    private var weightedLongitude = 0.0
    private var minTakenAt: Long? = null
    private var maxTakenAt: Long? = null

    fun add(point: PhotoHeatmapPoint) {
        photoCount += 1
        weightedLatitude += point.latitude
        weightedLongitude += point.longitude
        point.takenAt?.let { takenAt ->
            minTakenAt = minTakenAt?.let { current -> minOf(current, takenAt) } ?: takenAt
            maxTakenAt = maxTakenAt?.let { current -> maxOf(current, takenAt) } ?: takenAt
        }
    }

    fun toCell(): PhotoHeatmapCell {
        val safeCount = photoCount.coerceAtLeast(1)
        return PhotoHeatmapCell(
            id = id,
            latitude = weightedLatitude / safeCount,
            longitude = weightedLongitude / safeCount,
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
        addNumberProperty(PHOTO_HEATMAP_PHOTO_COUNT_PROPERTY, photoCount)
        addNumberProperty(PHOTO_HEATMAP_WEIGHT_PROPERTY, weight)
        minTakenAt?.let { value -> addNumberProperty(PHOTO_HEATMAP_MIN_TAKEN_AT_PROPERTY, value) }
        maxTakenAt?.let { value -> addNumberProperty(PHOTO_HEATMAP_MAX_TAKEN_AT_PROPERTY, value) }
    }
}

private fun PhotoMapBounds.contains(latitude: Double, longitude: Double): Boolean {
    return latitude in south..north && longitude in west..east
}

private fun photoHeatmapCellSizePx(
    zoom: Double,
    density: Float
): Double {
    val sizeDp = when {
        zoom < 5.0 -> 36.0
        zoom < 9.0 -> 30.0
        zoom < 13.0 -> 24.0
        else -> 18.0
    }
    return max(1.0, sizeDp * density.coerceAtLeast(1f))
}

private fun photoHeatmapAbsoluteReferenceCount(zoom: Double): Double {
    return when {
        zoom < 5.0 -> 500.0
        zoom < 9.0 -> 150.0
        zoom < 12.0 -> 40.0
        zoom < 15.0 -> 10.0
        else -> 3.0
    }
}

private fun longitudeToMercatorX(longitude: Double, worldSizePx: Double): Double {
    return ((longitude + 180.0) / 360.0) * worldSizePx
}

private fun latitudeToMercatorY(latitude: Double, worldSizePx: Double): Double {
    val clampedLatitude = latitude.coerceIn(-PhotoHeatmapMaxMercatorLatitude, PhotoHeatmapMaxMercatorLatitude)
    val sinLatitude = sin(clampedLatitude * PI / 180.0)
    return (0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)) * worldSizePx
}

internal const val PHOTO_HEATMAP_WEIGHT_PROPERTY = "weight"
internal const val PHOTO_HEATMAP_CELL_ID_PROPERTY = "cell_id"
internal const val PHOTO_HEATMAP_PHOTO_COUNT_PROPERTY = "photo_count"
internal const val PHOTO_HEATMAP_MIN_TAKEN_AT_PROPERTY = "min_taken_at"
internal const val PHOTO_HEATMAP_MAX_TAKEN_AT_PROPERTY = "max_taken_at"

private const val PhotoHeatmapTileSizePx = 256.0
private const val PhotoHeatmapViewportPaddingFactor = 0.15
private const val PhotoHeatmapCancellationCheckInterval = 512
private const val PhotoHeatmapLocalReferencePercentile = 0.95
private const val PhotoHeatmapMinLocalReferenceCount = 2.0
private const val PhotoHeatmapLocalWeightRatio = 0.8
private const val PhotoHeatmapAbsoluteWeightRatio = 0.2
private const val PhotoHeatmapMinWeight = 0.05
private const val PhotoHeatmapMaxMercatorLatitude = 85.05112878
