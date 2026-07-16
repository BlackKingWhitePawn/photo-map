package com.example.photomap.ui.map

import com.example.photomap.domain.model.DevicePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.geojson.Point

class PhotoMapFeatureMapperTest {
    @Test
    fun mapsLatitudeAndLongitudeInGeoJsonOrder() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(photo(mediaId = 1L, latitude = 56.84, longitude = 60.61))
        )

        val point = collection.features().first().geometry() as Point

        assertEquals(60.61, point.longitude(), 0.0)
        assertEquals(56.84, point.latitude(), 0.0)
    }

    @Test
    fun skipsMissingInvalidNanAndInfiniteCoordinates() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(
                photo(mediaId = 1L, latitude = null, longitude = 60.0),
                photo(mediaId = 2L, latitude = 56.0, longitude = null),
                photo(mediaId = 3L, latitude = Double.NaN, longitude = 60.0),
                photo(mediaId = 4L, latitude = 56.0, longitude = Double.POSITIVE_INFINITY),
                photo(mediaId = 5L, latitude = -91.0, longitude = 60.0),
                photo(mediaId = 6L, latitude = 91.0, longitude = 60.0),
                photo(mediaId = 7L, latitude = 56.0, longitude = -181.0),
                photo(mediaId = 8L, latitude = 56.0, longitude = 181.0)
            )
        )

        assertEquals(0, collection.features().size)
    }

    @Test
    fun keepsZeroZeroCoordinate() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(photo(mediaId = 1L, latitude = 0.0, longitude = 0.0))
        )

        val point = collection.features().first().geometry() as Point

        assertEquals(0.0, point.longitude(), 0.0)
        assertEquals(0.0, point.latitude(), 0.0)
    }

    @Test
    fun keepsMultiplePhotosWithSameCoordinates() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(
                photo(mediaId = 1L, latitude = 56.84, longitude = 60.61),
                photo(mediaId = 2L, latitude = 56.84, longitude = 60.61)
            )
        )

        assertEquals(2, collection.features().size)
    }

    @Test
    fun removesDuplicateStablePhotoIdOnly() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(
                photo(mediaId = 1L, latitude = 56.84, longitude = 60.61),
                photo(mediaId = 1L, latitude = 57.0, longitude = 61.0)
            )
        )

        val point = collection.features().first().geometry() as Point

        assertEquals(1, collection.features().size)
        assertEquals(60.61, point.longitude(), 0.0)
        assertEquals(56.84, point.latitude(), 0.0)
    }

    @Test
    fun emptyListCreatesEmptyFeatureCollection() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(emptyList())

        assertEquals(0, collection.features().size)
    }

    @Test
    fun nullTakenAtDoesNotAddTakenAtProperty() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(photo(mediaId = 1L, latitude = 56.84, longitude = 60.61, dateTaken = null))
        )

        assertNull(collection.features().first().getProperty(TAKEN_AT_PROPERTY))
    }

    @Test
    fun keepsContentUriWithNonStandardCharacters() {
        val uri = "content://media/external/images/media/1?name=тест фото #1"
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(photo(mediaId = 1L, uri = uri, latitude = 56.84, longitude = 60.61))
        )

        assertEquals(uri, collection.features().first().getStringProperty(CONTENT_URI_PROPERTY))
    }

    @Test
    fun addsStableThumbnailImageKey() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(photo(mediaId = 42L, latitude = 56.84, longitude = 60.61))
        )

        assertEquals(
            "photo-thumb-42",
            collection.features().first().getStringProperty(PHOTO_THUMBNAIL_KEY_PROPERTY)
        )
    }

    @Test
    fun keepsPhotosNearAntimeridian() {
        val collection = PhotoMapFeatureMapper.toFeatureCollection(
            listOf(
                photo(mediaId = 1L, latitude = 10.0, longitude = 180.0),
                photo(mediaId = 2L, latitude = 10.0, longitude = -180.0)
            )
        )

        assertEquals(2, collection.features().size)
    }

    private fun photo(
        mediaId: Long,
        uri: String = "content://media/external/images/media/$mediaId",
        latitude: Double?,
        longitude: Double?,
        dateTaken: Long? = 1_700_000_000_000L
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = mediaId,
            uri = uri,
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
