package com.example.photomap.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationValidatorTest {
    @Test
    fun normalizeRejectsMissingCoordinates() {
        assertNull(LocationValidator.normalize(latitude = null, longitude = 60.0))
        assertNull(LocationValidator.normalize(latitude = 56.0, longitude = null))
    }

    @Test
    fun normalizeRejectsOutOfRangeCoordinates() {
        assertNull(LocationValidator.normalize(latitude = -90.1, longitude = 60.0))
        assertNull(LocationValidator.normalize(latitude = 90.1, longitude = 60.0))
        assertNull(LocationValidator.normalize(latitude = 56.0, longitude = -180.1))
        assertNull(LocationValidator.normalize(latitude = 56.0, longitude = 180.1))
    }

    @Test
    fun normalizeRejectsNonFiniteCoordinates() {
        assertNull(LocationValidator.normalize(latitude = Double.NaN, longitude = 60.0))
        assertNull(LocationValidator.normalize(latitude = 56.0, longitude = Double.POSITIVE_INFINITY))
    }

    @Test
    fun normalizeRejectsZeroZeroCoordinates() {
        assertNull(LocationValidator.normalize(latitude = 0.0, longitude = 0.0))
    }

    @Test
    fun normalizeKeepsBoundaryCoordinatesExceptZeroZero() {
        assertEquals(
            GeoCoordinates(latitude = -90.0, longitude = -180.0),
            LocationValidator.normalize(latitude = -90.0, longitude = -180.0)
        )
        assertEquals(
            GeoCoordinates(latitude = 90.0, longitude = 180.0),
            LocationValidator.normalize(latitude = 90.0, longitude = 180.0)
        )
    }
}
