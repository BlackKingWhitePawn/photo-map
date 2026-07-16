package com.example.photomap.data.cluster

import kotlin.math.max
import kotlin.math.min

const val CLUSTERING_VERSION = 4

val StoredClusterLevels = listOf(4, 5, 6, 7, 9)

data class PhotoMapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    fun expanded(factor: Double): PhotoMapBounds {
        val latitudePadding = (north - south).coerceAtLeast(0.0001) * factor
        val longitudePadding = (east - west).coerceAtLeast(0.0001) * factor
        return PhotoMapBounds(
            south = (south - latitudePadding).coerceAtLeast(-90.0),
            west = (west - longitudePadding).coerceAtLeast(-180.0),
            north = (north + latitudePadding).coerceAtMost(90.0),
            east = (east + longitudePadding).coerceAtMost(180.0)
        )
    }

    fun contains(other: PhotoMapBounds): Boolean {
        return south <= other.south &&
            west <= other.west &&
            north >= other.north &&
            east >= other.east
    }

    companion object {
        fun enclosing(points: List<ClusterPoint>): PhotoMapBounds {
            return PhotoMapBounds(
                south = points.minOf { point -> point.latitude },
                west = points.minOf { point -> point.longitude },
                north = points.maxOf { point -> point.latitude },
                east = points.maxOf { point -> point.longitude }
            )
        }
    }
}

data class StoredPhotoCluster(
    val clusterId: String,
    val level: Int,
    val h3Index: String,
    val parentClusterId: String?,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val priorityScore: Double,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val coverPhotoId: Long?,
    val updatedAt: Long,
    val version: Int = CLUSTERING_VERSION
)

data class PhotoClusterLink(
    val photoId: Long,
    val clusterId: String,
    val level: Int
)

data class VisiblePhotoMapItem(
    val id: String,
    val level: Int,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val priorityScore: Double,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val coverPhotoId: Long?,
    val photoIds: List<Long>
) {
    val isAggregate: Boolean
        get() = level > 0 || photoCount > 1
}

data class VisiblePhotoMapContent(
    val level: Int,
    val loadedBounds: PhotoMapBounds,
    val items: List<VisiblePhotoMapItem>
)

data class ClusterPoint(
    val photoId: Long,
    val latitude: Double,
    val longitude: Double,
    val takenAt: Long?
)

fun clusterLevelForZoom(zoom: Double): Int {
    return when {
        zoom < 5.0 -> 4
        zoom < 8.0 -> 5
        zoom < 11.0 -> 6
        zoom < 14.0 -> 7
        zoom < 17.0 -> 9
        else -> 0
    }
}

fun StoredPhotoCluster.toVisiblePhotoMapItem(photoIds: List<Long> = emptyList()): VisiblePhotoMapItem {
    return VisiblePhotoMapItem(
        id = clusterId,
        level = level,
        latitude = latitude,
        longitude = longitude,
        photoCount = photoCount,
        priorityScore = priorityScore,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        coverPhotoId = coverPhotoId,
        photoIds = photoIds
    )
}

fun ClusterPoint.toVisiblePhotoMapItem(): VisiblePhotoMapItem {
    return VisiblePhotoMapItem(
        id = "photo-$photoId",
        level = 0,
        latitude = latitude,
        longitude = longitude,
        photoCount = 1,
        priorityScore = 1.0,
        minLatitude = latitude,
        maxLatitude = latitude,
        minLongitude = longitude,
        maxLongitude = longitude,
        coverPhotoId = photoId,
        photoIds = listOf(photoId)
    )
}

fun boundsIntersect(
    firstMinLatitude: Double,
    firstMaxLatitude: Double,
    firstMinLongitude: Double,
    firstMaxLongitude: Double,
    second: PhotoMapBounds
): Boolean {
    return firstMaxLatitude >= second.south &&
        firstMinLatitude <= second.north &&
        firstMaxLongitude >= second.west &&
        firstMinLongitude <= second.east
}

internal fun mergedBounds(points: List<ClusterPoint>): PhotoMapBounds {
    var south = 90.0
    var north = -90.0
    var west = 180.0
    var east = -180.0
    points.forEach { point ->
        south = min(south, point.latitude)
        north = max(north, point.latitude)
        west = min(west, point.longitude)
        east = max(east, point.longitude)
    }
    return PhotoMapBounds(south = south, west = west, north = north, east = east)
}
