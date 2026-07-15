package com.example.photomap.ui.map

import com.example.photomap.domain.model.DevicePhoto
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object PhotoMapFeatureMapper {
    fun toFeatureCollection(photos: List<DevicePhoto>): FeatureCollection {
        val uniquePhotosById = LinkedHashMap<Long, DevicePhoto>()
        photos.forEach { photo -> uniquePhotosById.putIfAbsent(photo.mediaId, photo) }

        val features = uniquePhotosById.values.mapNotNull { photo ->
            photo.toMapPoint()?.toFeature()
        }
        return FeatureCollection.fromFeatures(features)
    }
}

internal fun DevicePhoto.toMapPoint(): PhotoMapPoint? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) {
        return null
    }
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }

    return PhotoMapPoint(
        photoId = mediaId,
        contentUri = uri,
        latitude = latitude,
        longitude = longitude,
        takenAt = dateTaken
    )
}

internal fun PhotoMapPoint.toFeature(): Feature {
    return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
        addStringProperty(PHOTO_ID_PROPERTY, photoId.toString())
        addStringProperty(CONTENT_URI_PROPERTY, contentUri)
        addStringProperty(PHOTO_THUMBNAIL_KEY_PROPERTY, photoThumbnailImageKey(photoId))
        takenAt?.let { value -> addNumberProperty(TAKEN_AT_PROPERTY, value) }
    }
}

internal fun Feature.photoId(): Long? {
    return getStringProperty(PHOTO_ID_PROPERTY)?.toLongOrNull()
        ?: getNumberProperty(PHOTO_ID_PROPERTY)?.toLong()
}

internal const val PHOTO_ID_PROPERTY = "photo_id"
internal const val CONTENT_URI_PROPERTY = "content_uri"
internal const val PHOTO_THUMBNAIL_KEY_PROPERTY = "thumbnail_key"
internal const val TAKEN_AT_PROPERTY = "taken_at"
internal const val PHOTO_CLUSTER_ID_PROPERTY = "cluster_id"
internal const val PHOTO_CLUSTER_COUNT_PROPERTY = "point_count"
internal const val PHOTO_CLUSTER_COUNT_ABBREVIATED_PROPERTY = "point_count_abbreviated"

internal fun photoThumbnailImageKey(photoId: Long): String {
    return "photo-thumb-$photoId"
}
