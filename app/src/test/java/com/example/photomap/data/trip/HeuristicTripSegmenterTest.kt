package com.example.photomap.data.trip

import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.trip.TripType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicTripSegmenterTest {
    @Test
    fun photosWithoutDateOrLocationAreSkipped() = runBlocking {
        val result = HeuristicTripSegmenter().segmentTrips(
            listOf(
                testPhoto(mediaId = 1L, day = 1L, latitude = null, longitude = null),
                testPhoto(mediaId = 2L, day = null, latitude = HomeLatitude, longitude = HomeLongitude)
            )
        )

        assertTrue(result.trips.isEmpty())
        assertTrue(result.baseRegions.isEmpty())
        assertEquals(2, result.skippedPhotos)
    }

    @Test
    fun homePhotosDoNotCreateTrip() = runBlocking {
        val result = HeuristicTripSegmenter().segmentTrips(
            listOf(
                testPhoto(mediaId = 1L, day = 1L),
                testPhoto(mediaId = 2L, day = 2L),
                testPhoto(mediaId = 3L, day = 3L)
            )
        )

        assertTrue(result.trips.isEmpty())
        assertEquals(1, result.baseRegions.size)
    }

    @Test
    fun oneRemoteAwaySegmentCreatesSingleDestinationTrip() = runBlocking {
        val result = HeuristicTripSegmenter().segmentTrips(
            listOf(
                testPhoto(mediaId = 1L, day = 1L),
                testPhoto(mediaId = 2L, day = 2L),
                testPhoto(mediaId = 3L, day = 3L, latitude = KazanLatitude, longitude = KazanLongitude),
                testPhoto(mediaId = 4L, day = 4L)
            )
        )

        val trip = result.trips.single()
        assertEquals(BaseDay + 3, trip.startDay)
        assertEquals(BaseDay + 3, trip.endDay)
        assertEquals(TripType.SINGLE_DESTINATION_TRIP, trip.type)
        assertEquals(listOf(3L), trip.photoIds)
        assertEquals(1, trip.activeDayCount)
        assertTrue(trip.confidence >= 0.35)
    }

    @Test
    fun multipleAwayDestinationsOnSameDayCreateRouteTrip() = runBlocking {
        val result = HeuristicTripSegmenter().segmentTrips(
            listOf(
                testPhoto(mediaId = 1L, day = 1L),
                testPhoto(mediaId = 2L, day = 2L),
                testPhoto(
                    mediaId = 3L,
                    day = 3L,
                    hour = 8,
                    latitude = KazanLatitude,
                    longitude = KazanLongitude
                ),
                testPhoto(
                    mediaId = 4L,
                    day = 3L,
                    hour = 20,
                    latitude = MoscowLatitude,
                    longitude = MoscowLongitude
                ),
                testPhoto(mediaId = 5L, day = 4L)
            )
        )

        val trip = result.trips.single()
        assertEquals(TripType.MULTI_DESTINATION_TRIP, trip.type)
        assertEquals(listOf(3L, 4L), trip.photoIds)
        assertEquals(2, trip.destinations.size)
    }

    @Test
    fun repeatedSegmentationKeepsStableTripIdForSameInput() = runBlocking {
        val photos = listOf(
            testPhoto(mediaId = 1L, day = 1L),
            testPhoto(mediaId = 2L, day = 2L),
            testPhoto(mediaId = 3L, day = 3L, latitude = KazanLatitude, longitude = KazanLongitude),
            testPhoto(mediaId = 4L, day = 4L)
        )
        val segmenter = HeuristicTripSegmenter()

        val first = segmenter.segmentTrips(photos)
        val second = segmenter.segmentTrips(photos.reversed())

        assertEquals(first.trips.map { trip -> trip.id }, second.trips.map { trip -> trip.id })
    }

    @Test
    fun nearBasePhotosAreNotClassifiedAsAwayTrip() = runBlocking {
        val result = HeuristicTripSegmenter().segmentTrips(
            listOf(
                testPhoto(mediaId = 1L, day = 1L),
                testPhoto(mediaId = 2L, day = 2L),
                testPhoto(mediaId = 3L, day = 3L, latitude = HomeLatitude + 0.02, longitude = HomeLongitude + 0.02),
                testPhoto(mediaId = 4L, day = 4L)
            )
        )

        assertTrue(result.trips.isEmpty())
    }

    private fun testPhoto(
        mediaId: Long,
        day: Long?,
        hour: Int = 12,
        latitude: Double? = HomeLatitude,
        longitude: Double? = HomeLongitude
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = mediaId,
            uri = "content://media/external/images/media/$mediaId",
            displayName = "photo-$mediaId.jpg",
            mimeType = "image/jpeg",
            dateAdded = null,
            dateModified = null,
            dateTaken = day?.let { value -> (BaseDay + value) * MillisPerDay + hour * MillisPerHour },
            width = null,
            height = null,
            size = null,
            orientation = null,
            latitude = latitude,
            longitude = longitude
        )
    }

    private companion object {
        const val BaseDay = 20_000L
        const val MillisPerHour = 60L * 60L * 1000L
        const val MillisPerDay = 24L * MillisPerHour
        const val HomeLatitude = 56.8389
        const val HomeLongitude = 60.6057
        const val KazanLatitude = 55.7887
        const val KazanLongitude = 49.1221
        const val MoscowLatitude = 55.7558
        const val MoscowLongitude = 37.6173
    }
}
