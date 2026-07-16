package com.example.photomap.data.cluster

import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.domain.model.DevicePhoto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class PhotoClusterBuilder {
    fun build(
        photos: Collection<DevicePhoto>,
        settings: PhotoClusterSettings,
        updatedAt: Long = System.currentTimeMillis()
    ): BuiltPhotoClusters {
        val points = photos.mapNotNull { photo ->
            val latitude = photo.latitude ?: return@mapNotNull null
            val longitude = photo.longitude ?: return@mapNotNull null
            ClusterPoint(
                photoId = photo.mediaId,
                latitude = latitude,
                longitude = longitude,
                takenAt = photo.dateTaken ?: photo.dateModified ?: photo.dateAdded
            )
        }
        if (points.isEmpty()) {
            return BuiltPhotoClusters(emptyList(), emptyList())
        }

        val clusters = mutableListOf<StoredPhotoCluster>()
        val links = mutableListOf<PhotoClusterLink>()
        var parentByPhotoId = emptyMap<Long, String>()

        StoredClusterLevels.forEach { level ->
            val levelClusters = if (level <= 6) {
                buildCellClusters(
                    level = level,
                    points = points,
                    parentByPhotoId = parentByPhotoId,
                    updatedAt = updatedAt
                )
            } else {
                buildAdaptiveClusters(
                    level = level,
                    points = points,
                    settings = settings,
                    parentByPhotoId = parentByPhotoId,
                    updatedAt = updatedAt
                )
            }
            clusters += levelClusters.clusters
            links += levelClusters.links
            parentByPhotoId = levelClusters.links.associate { link -> link.photoId to link.clusterId }
        }

        return BuiltPhotoClusters(clusters = clusters, links = links)
    }

    private fun buildCellClusters(
        level: Int,
        points: List<ClusterPoint>,
        parentByPhotoId: Map<Long, String>,
        updatedAt: Long
    ): BuiltPhotoClusters {
        val clusters = mutableListOf<StoredPhotoCluster>()
        val links = mutableListOf<PhotoClusterLink>()
        points.groupBy { point -> point.geoCell(level) }
            .toSortedMap(compareBy<GeoCell> { cell -> cell.key })
            .forEach { (cell, cellPoints) ->
                val cluster = cellPoints.toStoredCluster(
                    level = level,
                    h3Index = cell.key,
                    clusterId = "v$CLUSTERING_VERSION-l$level-${cell.key}",
                    parentClusterId = cellPoints.mostCommonParent(parentByPhotoId),
                    updatedAt = updatedAt
                )
                clusters += cluster
                links += cellPoints.map { point ->
                    PhotoClusterLink(
                        photoId = point.photoId,
                        clusterId = cluster.clusterId,
                        level = level
                    )
                }
            }
        return BuiltPhotoClusters(clusters = clusters, links = links)
    }

    private fun buildAdaptiveClusters(
        level: Int,
        points: List<ClusterPoint>,
        settings: PhotoClusterSettings,
        parentByPhotoId: Map<Long, String>,
        updatedAt: Long
    ): BuiltPhotoClusters {
        val clusters = mutableListOf<StoredPhotoCluster>()
        val links = mutableListOf<PhotoClusterLink>()
        val densityCoefficient = settings.densityCoefficientPercent.coerceIn(80, 320) / 100.0
        val maxDiameterKm = maxDiameterKmForLevel(level, densityCoefficient)
        points.groupBy { point -> point.geoCell(level) }
            .toSortedMap(compareBy<GeoCell> { cell -> cell.key })
            .forEach { (cell, cellPoints) ->
                val components = adaptiveComponents(
                    points = cellPoints,
                    densityCoefficient = densityCoefficient,
                    maxDiameterKm = maxDiameterKm,
                    minPoints = settings.minPoints.coerceAtLeast(2)
                )
                components.forEachIndexed { index, component ->
                    val cluster = component.toStoredCluster(
                        level = level,
                        h3Index = cell.key,
                        clusterId = "v$CLUSTERING_VERSION-l$level-${cell.key}-c$index",
                        parentClusterId = component.mostCommonParent(parentByPhotoId),
                        updatedAt = updatedAt
                    )
                    clusters += cluster
                    links += component.map { point ->
                        PhotoClusterLink(
                            photoId = point.photoId,
                            clusterId = cluster.clusterId,
                            level = level
                        )
                    }
                }
            }
        return BuiltPhotoClusters(clusters = clusters, links = links)
    }

    private fun adaptiveComponents(
        points: List<ClusterPoint>,
        densityCoefficient: Double,
        maxDiameterKm: Double,
        minPoints: Int
    ): List<List<ClusterPoint>> {
        if (points.size <= 1) {
            return points.map { point -> listOf(point) }
        }

        val radii = points.mapIndexed { index, point ->
            val thirdNeighborDistance = points.asSequence()
                .filterIndexed { otherIndex, _ -> otherIndex != index }
                .map { other -> point.distanceKmTo(other) }
                .sorted()
                .drop(2)
                .firstOrNull()
                ?: points.asSequence()
                    .filterIndexed { otherIndex, _ -> otherIndex != index }
                    .map { other -> point.distanceKmTo(other) }
                    .minOrNull()
                ?: maxDiameterKm
            adaptiveRadiusKm(thirdNeighborDistance, densityCoefficient, maxDiameterKm)
        }

        val unionFind = UnionFind(points.size)
        for (firstIndex in points.indices) {
            for (secondIndex in firstIndex + 1 until points.size) {
                val distanceKm = points[firstIndex].distanceKmTo(points[secondIndex])
                val thresholdKm = minOf(maxDiameterKm, max(radii[firstIndex], radii[secondIndex]))
                if (distanceKm <= thresholdKm &&
                    unionFind.canMerge(firstIndex, secondIndex, points, maxDiameterKm)
                ) {
                    unionFind.union(firstIndex, secondIndex)
                }
            }
        }

        val components = points.indices
            .groupBy { index -> unionFind.find(index) }
            .values
            .flatMap { indexes ->
                if (indexes.size >= minPoints) {
                    listOf(indexes.map { index -> points[index] })
                } else {
                    indexes.map { index -> listOf(points[index]) }
                }
            }
        return components.sortedWith(
            compareByDescending<List<ClusterPoint>> { component -> component.size }
                .thenBy { component -> component.minOf { point -> point.photoId } }
        )
    }

    private fun adaptiveRadiusKm(
        thirdNeighborDistanceKm: Double,
        densityCoefficient: Double,
        maxDiameterKm: Double
    ): Double {
        val profile = when {
            thirdNeighborDistanceKm <= 0.3 -> RadiusProfile(minKm = 0.05, maxKm = 0.3)
            thirdNeighborDistanceKm <= 1.0 -> RadiusProfile(minKm = 0.1, maxKm = 1.0)
            thirdNeighborDistanceKm <= 5.0 -> RadiusProfile(minKm = 0.5, maxKm = 5.0)
            thirdNeighborDistanceKm <= 20.0 -> RadiusProfile(minKm = 2.0, maxKm = 20.0)
            else -> RadiusProfile(minKm = 5.0, maxKm = maxDiameterKm)
        }
        return (thirdNeighborDistanceKm * densityCoefficient)
            .coerceIn(profile.minKm, minOf(profile.maxKm, maxDiameterKm))
    }
}

data class BuiltPhotoClusters(
    val clusters: List<StoredPhotoCluster>,
    val links: List<PhotoClusterLink>
)

private data class GeoCell(
    val key: String
)

private data class RadiusProfile(
    val minKm: Double,
    val maxKm: Double
)

private fun maxDiameterKmForLevel(level: Int, densityCoefficient: Double): Double {
    val representativeZoom = when (level) {
        4 -> 4.0
        5 -> 7.0
        6 -> 10.0
        7 -> 13.0
        9 -> 16.0
        else -> level.toDouble().coerceAtLeast(1.0)
    }
    return (representativeZoom * densityCoefficient).coerceAtLeast(0.5)
}

private fun ClusterPoint.geoCell(level: Int): GeoCell {
    val latitudeSize = latitudeCellSizeDegrees(level)
    val latitudeBucket = floor((latitude + 90.0) / latitudeSize).toInt()
    val middleLatitude = -90.0 + (latitudeBucket + 0.5) * latitudeSize
    val longitudeSize = (latitudeSize / cos(Math.toRadians(middleLatitude)).coerceAtLeast(0.25))
        .coerceIn(latitudeSize, 360.0)
    val longitudeBucket = floor((longitude + 180.0) / longitudeSize).toInt()
    return GeoCell("geo-$level-$latitudeBucket-$longitudeBucket")
}

private fun latitudeCellSizeDegrees(level: Int): Double {
    return when (level) {
        4 -> 0.45
        5 -> 0.18
        6 -> 0.072
        7 -> 0.027
        9 -> 0.0045
        else -> 0.027
    }
}

private fun List<ClusterPoint>.toStoredCluster(
    level: Int,
    h3Index: String,
    clusterId: String,
    parentClusterId: String?,
    updatedAt: Long
): StoredPhotoCluster {
    val bounds = mergedBounds(this)
    val latitude = map { point -> point.latitude }.average()
    val longitude = map { point -> point.longitude }.average()
    val coverPhotoId = maxByOrNull { point -> point.takenAt ?: 0L }?.photoId
    val diameterKm = clusterDiameterKm()
    val priorityScore = ln(1.0 + size) + when {
        diameterKm >= 20.0 -> size * 3.0
        diameterKm >= 5.0 -> size * 1.5
        diameterKm >= 1.0 -> size * 0.5
        else -> 0.0
    }
    return StoredPhotoCluster(
        clusterId = clusterId,
        level = level,
        h3Index = h3Index,
        parentClusterId = parentClusterId,
        latitude = latitude,
        longitude = longitude,
        photoCount = size,
        priorityScore = priorityScore,
        minLatitude = bounds.south,
        maxLatitude = bounds.north,
        minLongitude = bounds.west,
        maxLongitude = bounds.east,
        coverPhotoId = coverPhotoId,
        updatedAt = updatedAt
    )
}

private fun List<ClusterPoint>.mostCommonParent(parentByPhotoId: Map<Long, String>): String? {
    return mapNotNull { point -> parentByPhotoId[point.photoId] }
        .groupingBy { parentId -> parentId }
        .eachCount()
        .maxByOrNull { (_, count) -> count }
        ?.key
}

private fun List<ClusterPoint>.clusterDiameterKm(): Double {
    var diameter = 0.0
    for (firstIndex in indices) {
        for (secondIndex in firstIndex + 1 until size) {
            diameter = max(diameter, this[firstIndex].distanceKmTo(this[secondIndex]))
        }
    }
    return diameter
}

private fun ClusterPoint.distanceKmTo(other: ClusterPoint): Double {
    val firstLatitudeRad = Math.toRadians(latitude)
    val secondLatitudeRad = Math.toRadians(other.latitude)
    val deltaLatitudeRad = Math.toRadians(other.latitude - latitude)
    val deltaLongitudeRad = Math.toRadians(other.longitude - longitude)
    val halfChord = sin(deltaLatitudeRad / 2.0) * sin(deltaLatitudeRad / 2.0) +
        cos(firstLatitudeRad) * cos(secondLatitudeRad) *
        sin(deltaLongitudeRad / 2.0) * sin(deltaLongitudeRad / 2.0)
    val normalizedHalfChord = halfChord.coerceIn(0.0, 1.0)
    val angularDistance = 2.0 * atan2(
        sqrt(normalizedHalfChord),
        sqrt(1.0 - normalizedHalfChord)
    )
    return EarthRadiusKm * angularDistance
}

private class UnionFind(size: Int) {
    private val parents = IntArray(size) { index -> index }

    fun find(index: Int): Int {
        val parent = parents[index]
        if (parent == index) {
            return index
        }
        val root = find(parent)
        parents[index] = root
        return root
    }

    fun union(firstIndex: Int, secondIndex: Int) {
        val firstRoot = find(firstIndex)
        val secondRoot = find(secondIndex)
        if (firstRoot != secondRoot) {
            parents[secondRoot] = firstRoot
        }
    }

    fun canMerge(
        firstIndex: Int,
        secondIndex: Int,
        points: List<ClusterPoint>,
        maxDiameterKm: Double
    ): Boolean {
        val firstRoot = find(firstIndex)
        val secondRoot = find(secondIndex)
        if (firstRoot == secondRoot) {
            return true
        }
        val firstMembers = points.indices.filter { index -> find(index) == firstRoot }
        val secondMembers = points.indices.filter { index -> find(index) == secondRoot }
        return firstMembers.all { firstMember ->
            secondMembers.all { secondMember ->
                points[firstMember].distanceKmTo(points[secondMember]) <= maxDiameterKm
            }
        }
    }
}

private const val EarthRadiusKm = 6371.0
