package com.example.photomap.data.cluster

import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.domain.model.DevicePhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoClusterBuilderTest {
    @Test
    fun photosWithoutCoordinatesDoNotCreateClusters() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = null, longitude = 60.0),
                testPhoto(mediaId = 2L, latitude = 56.0, longitude = null)
            ),
            settings = PhotoClusterSettings(),
            updatedAt = 123L
        )

        assertTrue(built.clusters.isEmpty())
        assertTrue(built.links.isEmpty())
    }

    @Test
    fun singleGeotaggedPhotoKeepsOneLinkAtEveryStoredLevel() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(testPhoto(mediaId = 42L)),
            settings = PhotoClusterSettings(minPoints = 10),
            updatedAt = 123L
        )

        assertEquals(StoredClusterLevels.size, built.clusters.size)
        StoredClusterLevels.forEach { level ->
            assertEquals(1, built.links.count { link -> link.level == level && link.photoId == 42L })
            val cluster = built.clusters.single { item -> item.level == level }
            assertEquals(1, cluster.photoCount)
            assertEquals(42L, cluster.coverPhotoId)
            assertEquals(123L, cluster.updatedAt)
        }
    }

    @Test
    fun linkCountAtEveryLevelEqualsLocatedPhotoCount() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = 56.8389, longitude = 60.6057),
                testPhoto(mediaId = 2L, latitude = 56.8400, longitude = 60.6060),
                testPhoto(mediaId = 3L, latitude = 55.7558, longitude = 37.6173),
                testPhoto(mediaId = 4L, latitude = null, longitude = null)
            ),
            settings = PhotoClusterSettings(),
            updatedAt = 123L
        )

        StoredClusterLevels.forEach { level ->
            val photoIds = built.links
                .filter { link -> link.level == level }
                .map { link -> link.photoId }
                .sorted()
            assertEquals(listOf(1L, 2L, 3L), photoIds)
        }
    }

    @Test
    fun denseNearbyPhotosMergeAtDetailedLevel() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = 56.83890, longitude = 60.60570, dateTaken = 100L),
                testPhoto(mediaId = 2L, latitude = 56.83891, longitude = 60.60571, dateTaken = 300L),
                testPhoto(mediaId = 3L, latitude = 56.83892, longitude = 60.60572, dateTaken = 200L)
            ),
            settings = PhotoClusterSettings(minPoints = 2),
            updatedAt = 123L
        )

        val detailedClusters = built.clusters.filter { cluster -> cluster.level == 9 }

        assertEquals(1, detailedClusters.size)
        assertEquals(3, detailedClusters.single().photoCount)
        assertEquals(2L, detailedClusters.single().coverPhotoId)
    }

    @Test
    fun distantPhotosStaySeparateAtDetailedLevel() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = 56.8389, longitude = 60.6057),
                testPhoto(mediaId = 2L, latitude = 55.7558, longitude = 37.6173)
            ),
            settings = PhotoClusterSettings(minPoints = 2),
            updatedAt = 123L
        )

        val detailedClusters = built.clusters.filter { cluster -> cluster.level == 9 }

        assertEquals(2, detailedClusters.size)
        assertEquals(listOf(1, 1), detailedClusters.map { cluster -> cluster.photoCount }.sorted())
    }

    @Test
    fun childLevelsKeepParentClusterIdWhenParentExists() = runBlocking {
        val built = PhotoClusterBuilder().build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = 56.83890, longitude = 60.60570),
                testPhoto(mediaId = 2L, latitude = 56.83891, longitude = 60.60571)
            ),
            settings = PhotoClusterSettings(),
            updatedAt = 123L
        )

        built.clusters
            .filter { cluster -> cluster.level > StoredClusterLevels.first() }
            .forEach { cluster -> assertNotNull(cluster.parentClusterId) }
    }

    @Test
    fun clusterLevelForZoomUsesExpectedBuckets() {
        assertEquals(4, clusterLevelForZoom(4.9))
        assertEquals(5, clusterLevelForZoom(5.0))
        assertEquals(6, clusterLevelForZoom(8.0))
        assertEquals(7, clusterLevelForZoom(11.0))
        assertEquals(9, clusterLevelForZoom(14.0))
        assertEquals(0, clusterLevelForZoom(17.0))
    }

    @Test
    fun expandedBoundsClampToWorldLimits() {
        val expanded = PhotoMapBounds(
            south = -89.0,
            west = -179.0,
            north = 89.0,
            east = 179.0
        ).expanded(factor = 1.0)

        assertEquals(-90.0, expanded.south, 0.0)
        assertEquals(-180.0, expanded.west, 0.0)
        assertEquals(90.0, expanded.north, 0.0)
        assertEquals(180.0, expanded.east, 0.0)
    }

    private fun testPhoto(
        mediaId: Long,
        latitude: Double? = 56.8389,
        longitude: Double? = 60.6057,
        dateTaken: Long? = 1_700_000_000_000L
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = mediaId,
            uri = "content://media/external/images/media/$mediaId",
            displayName = "photo-$mediaId.jpg",
            mimeType = "image/jpeg",
            dateAdded = null,
            dateModified = null,
            dateTaken = dateTaken,
            width = null,
            height = null,
            size = null,
            orientation = null,
            latitude = latitude,
            longitude = longitude
        )
    }
}
