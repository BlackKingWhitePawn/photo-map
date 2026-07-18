package com.example.photomap.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.photomap.data.cluster.PhotoClusterLink
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.cluster.StoredPhotoCluster
import com.example.photomap.data.heatmap.HEATMAP_ALGORITHM_VERSION
import com.example.photomap.data.heatmap.TripHeatCellEntity
import com.example.photomap.data.heatmap.TripHeatContributionEntity
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.trip.DetectedTrip
import com.example.photomap.domain.trip.DetectedTripDestination
import com.example.photomap.domain.trip.TripType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoIndexDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: PhotoIndexDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DatabaseName)
        database = PhotoIndexDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DatabaseName)
    }

    @Test
    fun upsertPhotosIsIdempotentAndUpdatesExistingRow() {
        database.upsertPhotos(
            listOf(indexedPhoto(mediaId = 1L, dateModified = 100L, size = 1_000L, displayName = "old.jpg"))
        )
        database.upsertPhotos(
            listOf(indexedPhoto(mediaId = 1L, dateModified = 200L, size = 2_000L, displayName = "new.jpg"))
        )

        val photos = database.getAllPhotosById()

        assertEquals(setOf(1L), photos.keys)
        assertEquals(200L, photos.getValue(1L).dateModified)
        assertEquals(2_000L, photos.getValue(1L).size)
        assertEquals("new.jpg", photos.getValue(1L).displayName)
    }

    @Test
    fun getPhotosInBoundsFiltersByViewportAndDate() {
        database.upsertPhotos(
            listOf(
                indexedPhoto(
                    mediaId = 1L,
                    latitude = 56.8389,
                    longitude = 60.6057,
                    dateTaken = photoDateDayToMillis(BaseDay + 2)
                ),
                indexedPhoto(
                    mediaId = 2L,
                    latitude = 56.8389,
                    longitude = 60.6057,
                    dateTaken = photoDateDayToMillis(BaseDay + 5)
                ),
                indexedPhoto(
                    mediaId = 3L,
                    latitude = 55.7558,
                    longitude = 37.6173,
                    dateTaken = photoDateDayToMillis(BaseDay + 2)
                )
            )
        )

        val photos = database.getPhotosInBounds(
            bounds = PhotoMapBounds(south = 56.0, west = 60.0, north = 57.0, east = 61.0),
            dateFilter = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
                .withSelectedDays(start = BaseDay + 2, end = BaseDay + 2)
        )

        assertEquals(listOf(1L), photos.map { photo -> photo.mediaId })
    }

    @Test
    fun deleteMissingPhotosRemovesPhotoAndDependentLinks() {
        database.upsertPhotos(
            listOf(
                indexedPhoto(mediaId = 1L),
                indexedPhoto(mediaId = 2L)
            )
        )
        database.replaceClusters(
            clusters = listOf(storedCluster(clusterId = "cluster-1", coverPhotoId = 1L)),
            links = listOf(PhotoClusterLink(photoId = 1L, clusterId = "cluster-1", level = 9))
        )
        database.replaceTrips(
            trips = listOf(detectedTrip(id = 100L, photoIds = listOf(1L)))
        )

        database.deleteMissingPhotos(existingMediaIds = setOf(2L))

        assertEquals(setOf(2L), database.getAllPhotosById().keys)
        assertTrue(database.getClusterPhotoIds("cluster-1").isEmpty())
        assertTrue(database.getTripPhotoIds(100L).isEmpty())
    }

    @Test
    fun replaceTripHeatmapReplacesPreviousCellsAndMetaVersion() {
        database.replaceTripHeatmap(
            cells = listOf(tripHeatCell(h3Index = "old", intensity = 1.0)),
            contributions = listOf(tripHeatContribution(tripId = 1L, h3Index = "old")),
            dataVersion = 111L
        )
        database.replaceTripHeatmap(
            cells = listOf(tripHeatCell(h3Index = "new", intensity = 2.0)),
            contributions = listOf(tripHeatContribution(tripId = 2L, h3Index = "new")),
            dataVersion = 222L
        )

        val cells = database.getVisibleTripHeatCells(
            resolution = 6,
            bounds = PhotoMapBounds(south = 56.0, west = 60.0, north = 57.0, east = 61.0)
        )

        assertEquals(222L, database.getTripHeatDataVersion())
        assertEquals(listOf("new"), cells.map { cell -> cell.h3Index })
    }

    @Test
    fun getStatsCountsIndexedAndLocatedPhotos() {
        database.upsertPhotos(
            listOf(
                indexedPhoto(mediaId = 1L, latitude = 56.8389, longitude = 60.6057, locationScanned = true),
                indexedPhoto(mediaId = 2L, latitude = null, longitude = null, locationScanned = true),
                indexedPhoto(mediaId = 3L, latitude = null, longitude = null, locationScanned = false)
            )
        )

        val stats = database.getStats()

        assertEquals(3, stats.totalCount)
        assertEquals(2, stats.locationScannedCount)
        assertEquals(1, stats.locationCount)
    }

    private fun indexedPhoto(
        mediaId: Long,
        latitude: Double? = 56.8389,
        longitude: Double? = 60.6057,
        dateTaken: Long? = photoDateDayToMillis(BaseDay + 2),
        dateModified: Long? = 100L,
        size: Long? = 1_000L,
        displayName: String? = "photo-$mediaId.jpg",
        locationScanned: Boolean = latitude != null && longitude != null
    ): IndexedPhoto {
        return IndexedPhoto(
            mediaId = mediaId,
            uri = "content://media/external/images/media/$mediaId",
            displayName = displayName,
            mimeType = "image/jpeg",
            dateAdded = null,
            dateModified = dateModified,
            dateTaken = dateTaken,
            width = null,
            height = null,
            size = size,
            orientation = null,
            latitude = latitude,
            longitude = longitude,
            locationScanned = locationScanned,
            indexedAt = 123L
        )
    }

    private fun storedCluster(
        clusterId: String,
        coverPhotoId: Long?
    ): StoredPhotoCluster {
        return StoredPhotoCluster(
            clusterId = clusterId,
            level = 9,
            h3Index = "cell",
            parentClusterId = null,
            latitude = 56.8389,
            longitude = 60.6057,
            photoCount = 1,
            priorityScore = 1.0,
            minLatitude = 56.8389,
            maxLatitude = 56.8389,
            minLongitude = 60.6057,
            maxLongitude = 60.6057,
            coverPhotoId = coverPhotoId,
            updatedAt = 123L
        )
    }

    private fun detectedTrip(
        id: Long,
        photoIds: List<Long>
    ): DetectedTrip {
        return DetectedTrip(
            id = id,
            startDay = BaseDay + 2,
            endDay = BaseDay + 2,
            centerLatitude = 56.8389,
            centerLongitude = 60.6057,
            radiusKm = 2.0,
            photoCount = photoIds.size,
            activeDayCount = 1,
            confidence = 1.0,
            type = TripType.SINGLE_DESTINATION_TRIP,
            coverPhotoId = photoIds.firstOrNull(),
            photoIds = photoIds,
            destinations = listOf(
                DetectedTripDestination(
                    centerLatitude = 56.8389,
                    centerLongitude = 60.6057,
                    radiusKm = 2.0,
                    photoCount = photoIds.size,
                    firstDay = BaseDay + 2,
                    lastDay = BaseDay + 2,
                    sortOrder = 0
                )
            )
        )
    }

    private fun tripHeatCell(
        h3Index: String,
        intensity: Double
    ): TripHeatCellEntity {
        return TripHeatCellEntity(
            h3Index = h3Index,
            resolution = 6,
            centerLatitude = 56.8389,
            centerLongitude = 60.6057,
            tripCount = 1,
            activeDays = 1,
            daysSpent = 1,
            sessionCount = 1,
            latestTripAt = 123L,
            intensity = intensity,
            algorithmVersion = HEATMAP_ALGORITHM_VERSION,
            updatedAt = 123L
        )
    }

    private fun tripHeatContribution(
        tripId: Long,
        h3Index: String
    ): TripHeatContributionEntity {
        return TripHeatContributionEntity(
            tripId = tripId,
            h3Index = h3Index,
            resolution = 6,
            activeDays = 1,
            daysSpent = 1,
            sessionCount = 1,
            weight = 1.0
        )
    }

    private companion object {
        const val DatabaseName = "photo_index.db"
        const val BaseDay = 20_000L
    }
}
