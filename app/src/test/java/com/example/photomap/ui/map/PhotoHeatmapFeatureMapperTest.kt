package com.example.photomap.ui.map

import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.photoDateDayToMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoHeatmapFeatureMapperTest {
    @Test
    fun emptyInputReturnsEmptyStateWithRequestId() = runBlocking {
        val state = PhotoHeatmapFeatureMapper.build(
            photos = emptyList(),
            request = request(requestId = 99L)
        )

        assertEquals(99L, state.requestId)
        assertEquals(0, state.visiblePhotoCount)
        assertTrue(state.cells.isEmpty())
        assertEquals(0, state.featureCollection.features().size)
    }

    @Test
    fun invalidViewportSizeReturnsEmptyStateWithRequestId() = runBlocking {
        val state = PhotoHeatmapFeatureMapper.build(
            photos = listOf(testPhoto(mediaId = 1L)),
            request = request(requestId = 7L, viewportWidthPx = 0)
        )

        assertEquals(7L, state.requestId)
        assertEquals(0, state.visiblePhotoCount)
        assertTrue(state.cells.isEmpty())
    }

    @Test
    fun singleGeotaggedPhotoRemainsVisibleAtEveryHeatmapZoomLevel() = runBlocking {
        listOf(1.0, 6.0, 10.0, 14.0, 18.0).forEach { zoom ->
            val state = PhotoHeatmapFeatureMapper.build(
                photos = listOf(testPhoto(mediaId = 1L)),
                request = request(zoom = zoom)
            )

            assertEquals(1, state.visiblePhotoCount)
            assertEquals(1, state.cells.sumOf { cell -> cell.photoCount })
            assertEquals(1, state.featureCollection.features().size)
        }
    }

    @Test
    fun zeroZeroPhotoIsExcludedFromHeatmap() = runBlocking {
        val state = PhotoHeatmapFeatureMapper.build(
            photos = listOf(testPhoto(mediaId = 1L, latitude = 0.0, longitude = 0.0)),
            request = request(bounds = PhotoMapBounds(south = -1.0, west = -1.0, north = 1.0, east = 1.0))
        )

        assertEquals(0, state.visiblePhotoCount)
        assertTrue(state.cells.isEmpty())
    }

    @Test
    fun visiblePhotoCountIgnoresPhotosOutsideViewport() = runBlocking {
        val state = PhotoHeatmapFeatureMapper.build(
            photos = listOf(
                testPhoto(mediaId = 1L, latitude = 56.8389, longitude = 60.6057),
                testPhoto(mediaId = 2L, latitude = 55.7558, longitude = 37.6173)
            ),
            request = request()
        )

        assertEquals(1, state.visiblePhotoCount)
        assertEquals(1, state.cells.sumOf { cell -> cell.photoCount })
    }

    @Test
    fun activeDateFilterExcludesPhotosOutsideSelectedDays() = runBlocking {
        val filter = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
            .withSelectedDays(start = BaseDay + 2, end = BaseDay + 2)
        val state = PhotoHeatmapFeatureMapper.build(
            photos = listOf(
                testPhoto(mediaId = 1L, dateTaken = photoDateDayToMillis(BaseDay + 2)),
                testPhoto(mediaId = 2L, dateTaken = photoDateDayToMillis(BaseDay + 3))
            ),
            request = request(dateFilter = filter)
        )

        assertEquals(1, state.visiblePhotoCount)
        assertEquals(1, state.cells.sumOf { cell -> cell.photoCount })
    }

    @Test
    fun heatmapWeightsStayInExpectedRange() = runBlocking {
        val photos = (1L..5L).map { id ->
            testPhoto(
                mediaId = id,
                latitude = 56.8389 + id * 0.01,
                longitude = 60.6057 + id * 0.01
            )
        }
        val state = PhotoHeatmapFeatureMapper.build(
            photos = photos,
            request = request()
        )

        assertTrue(state.cells.isNotEmpty())
        state.cells.forEach { cell ->
            assertTrue(cell.weight in 0.08..1.0)
        }
    }

    @Test
    fun selectedPhotoMarkerSkipsInvalidPhotoCoordinates() {
        val collection = SelectedPhotoMarkerFeatureMapper.toFeatureCollection(
            testPhoto(mediaId = 1L, latitude = Double.NaN, longitude = 60.0)
        )

        assertEquals(0, collection.features().size)
    }

    @Test
    fun selectedPhotoMarkerUsesGeoJsonLongitudeLatitudeOrder() {
        val collection = SelectedPhotoMarkerFeatureMapper.toFeatureCollection(
            photoId = 1L,
            latitude = 56.8389,
            longitude = 60.6057
        )
        val point = collection.features().single().geometry() as org.maplibre.geojson.Point

        assertEquals(60.6057, point.longitude(), 0.0)
        assertEquals(56.8389, point.latitude(), 0.0)
    }

    private fun request(
        requestId: Long = 1L,
        zoom: Double = 12.0,
        bounds: PhotoMapBounds = PhotoMapBounds(
            south = 56.0,
            west = 60.0,
            north = 57.0,
            east = 61.0
        ),
        viewportWidthPx: Int = 1080,
        viewportHeightPx: Int = 1920,
        dateFilter: PhotoDateFilter = PhotoDateFilter()
    ): PhotoHeatmapViewportRequest {
        return PhotoHeatmapViewportRequest(
            requestId = requestId,
            zoom = zoom,
            bounds = bounds,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            density = 1.0f,
            dateFilter = dateFilter
        )
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

    private companion object {
        const val BaseDay = 20_000L
    }
}
