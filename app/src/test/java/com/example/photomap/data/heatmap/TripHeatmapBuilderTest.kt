package com.example.photomap.data.heatmap

import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.trip.BaseRegion
import com.example.photomap.domain.trip.DetectedTrip
import com.example.photomap.domain.trip.DetectedTripDestination
import com.example.photomap.domain.trip.TripSegmentationResult
import com.example.photomap.domain.trip.TripType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripHeatmapBuilderTest {
    @Test
    fun emptyTripListBuildsEmptyHeatmapWithDataVersion() = runBlocking {
        val heatmap = TripHeatmapBuilder().build(
            photos = emptyList(),
            segmentationResult = TripSegmentationResult(
                trips = emptyList(),
                baseRegions = emptyList(),
                skippedPhotos = 0
            ),
            updatedAt = 123L
        )

        assertTrue(heatmap.cells.isEmpty())
        assertTrue(heatmap.contributions.isEmpty())
        assertEquals(123L, heatmap.dataVersion)
    }

    @Test
    fun tripWithoutDestinationsBuildsEmptyHeatmap() = runBlocking {
        val heatmap = TripHeatmapBuilder().build(
            photos = listOf(testPhoto(mediaId = 1L)),
            segmentationResult = TripSegmentationResult(
                trips = listOf(testTrip(destinations = emptyList())),
                baseRegions = emptyList(),
                skippedPhotos = 0
            ),
            updatedAt = 123L
        )

        assertTrue(heatmap.cells.isEmpty())
        assertTrue(heatmap.contributions.isEmpty())
    }

    @Test
    fun baseRegionDestinationsAreExcludedFromHeatmap() = runBlocking {
        val heatmap = TripHeatmapBuilder().build(
            photos = listOf(testPhoto(mediaId = 1L)),
            segmentationResult = TripSegmentationResult(
                trips = listOf(
                    testTrip(
                        destinations = listOf(
            DetectedTripDestination(
                centerLatitude = HomeLatitude,
                centerLongitude = HomeLongitude,
                radiusKm = 2.0,
                photoCount = 1,
                firstDay = BaseDay + 3,
                lastDay = BaseDay + 3,
                sortOrder = 0
            )
                        )
                    )
                ),
                baseRegions = listOf(
                    BaseRegion(
                        id = "home",
                        latitude = HomeLatitude,
                        longitude = HomeLongitude,
                        uniqueVisitedDays = 10,
                        activeMonths = 2,
                        score = 16.0
                    )
                ),
                skippedPhotos = 0
            ),
            updatedAt = 123L
        )

        assertTrue(heatmap.cells.isEmpty())
        assertTrue(heatmap.contributions.isEmpty())
    }

    @Test
    fun tripHeatIntensityIsZeroForEmptyStats() {
        assertEquals(
            0.0,
            tripHeatIntensity(tripCount = 0, daysSpent = 0, activeDays = 0, sessionCount = 0),
            0.0
        )
    }

    @Test
    fun tripHeatIntensityIsMonotonicForEachInput() {
        val base = tripHeatIntensity(tripCount = 1, daysSpent = 1, activeDays = 1, sessionCount = 1)

        assertTrue(tripHeatIntensity(tripCount = 2, daysSpent = 1, activeDays = 1, sessionCount = 1) > base)
        assertTrue(tripHeatIntensity(tripCount = 1, daysSpent = 2, activeDays = 1, sessionCount = 1) > base)
        assertTrue(tripHeatIntensity(tripCount = 1, daysSpent = 1, activeDays = 2, sessionCount = 1) > base)
        assertTrue(tripHeatIntensity(tripCount = 1, daysSpent = 1, activeDays = 1, sessionCount = 2) > base)
    }

    @Test
    fun tripHeatIntensityTreatsNegativeStatsAsZero() {
        assertEquals(
            0.0,
            tripHeatIntensity(tripCount = -1, daysSpent = -1, activeDays = -1, sessionCount = -1),
            0.0
        )
    }

    private fun testTrip(
        destinations: List<DetectedTripDestination>
    ): DetectedTrip {
        return DetectedTrip(
            id = 100L,
            startDay = BaseDay + 3,
            endDay = BaseDay + 3,
            centerLatitude = KazanLatitude,
            centerLongitude = KazanLongitude,
            radiusKm = 2.0,
            photoCount = 1,
            activeDayCount = 1,
            confidence = 1.0,
            type = TripType.SINGLE_DESTINATION_TRIP,
            coverPhotoId = 1L,
            photoIds = listOf(1L),
            destinations = destinations
        )
    }

    private fun testPhoto(
        mediaId: Long
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = mediaId,
            uri = "content://media/external/images/media/$mediaId",
            displayName = "photo-$mediaId.jpg",
            mimeType = "image/jpeg",
            dateAdded = null,
            dateModified = null,
            dateTaken = (BaseDay + 3) * MillisPerDay,
            width = null,
            height = null,
            size = null,
            orientation = null,
            latitude = KazanLatitude,
            longitude = KazanLongitude
        )
    }

    private companion object {
        const val BaseDay = 20_000L
        const val MillisPerDay = 24L * 60L * 60L * 1000L
        const val HomeLatitude = 56.8389
        const val HomeLongitude = 60.6057
        const val KazanLatitude = 55.7887
        const val KazanLongitude = 49.1221
    }
}
