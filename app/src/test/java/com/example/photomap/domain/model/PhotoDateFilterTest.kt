package com.example.photomap.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDateFilterTest {
    @Test
    fun photoDateMillisPrefersTakenDateAndNormalizesSeconds() {
        val photo = testPhoto(
            dateTaken = 1_700_000_000L,
            dateModified = 1_800_000_000_000L,
            dateAdded = 1_900_000_000_000L
        )

        assertEquals(1_700_000_000_000L, photo.photoDateMillis())
    }

    @Test
    fun photoDateMillisFallsBackToModifiedThenAdded() {
        assertEquals(
            1_800_000_000_000L,
            testPhoto(dateTaken = null, dateModified = 1_800_000_000_000L, dateAdded = 1L).photoDateMillis()
        )
        assertEquals(
            1_900_000_000_000L,
            testPhoto(dateTaken = null, dateModified = null, dateAdded = 1_900_000_000_000L).photoDateMillis()
        )
    }

    @Test
    fun photoDateMillisRejectsNonPositiveDates() {
        assertNull(testPhoto(dateTaken = 0L, dateModified = null, dateAdded = null).photoDateMillis())
        assertNull(testPhoto(dateTaken = -1L, dateModified = null, dateAdded = null).photoDateMillis())
    }

    @Test
    fun buildPhotoDateFilterReturnsUnavailableForEmptyInput() {
        val filter = buildPhotoDateFilter(emptyList())

        assertFalse(filter.isAvailable)
        assertNull(filter.selectedStartDay)
        assertNull(filter.selectedEndDay)
    }

    @Test
    fun buildPhotoDateFilterUsesMinAndMaxPhotoDays() {
        val filter = buildPhotoDateFilter(
            listOf(
                testPhoto(mediaId = 1L, dateTaken = photoDateDayToMillis(BaseDay + 2)),
                testPhoto(mediaId = 2L, dateTaken = photoDateDayToMillis(BaseDay)),
                testPhoto(mediaId = 3L, dateTaken = photoDateDayToMillis(BaseDay + 5))
            )
        )

        assertEquals(BaseDay, filter.minDay)
        assertEquals(BaseDay + 5, filter.maxDay)
        assertEquals(BaseDay, filter.selectedStartDay)
        assertEquals(BaseDay + 5, filter.selectedEndDay)
        assertFalse(filter.isActive)
    }

    @Test
    fun withSelectedDaysNormalizesReversedAndClampedRange() {
        val filter = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
            .withSelectedDays(start = BaseDay + 50, end = BaseDay - 5)

        assertEquals(BaseDay, filter.selectedStartDay)
        assertEquals(BaseDay + 10, filter.selectedEndDay)
    }

    @Test
    fun matchesPhotoDateFilterIncludesRangeBoundaries() {
        val filter = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
            .withSelectedDays(start = BaseDay + 2, end = BaseDay + 4)

        assertTrue(testPhoto(dateTaken = photoDateDayToMillis(BaseDay + 2)).matchesPhotoDateFilter(filter))
        assertTrue(testPhoto(dateTaken = photoDateDayToMillis(BaseDay + 4)).matchesPhotoDateFilter(filter))
        assertFalse(testPhoto(dateTaken = photoDateDayToMillis(BaseDay + 1)).matchesPhotoDateFilter(filter))
        assertFalse(testPhoto(dateTaken = photoDateDayToMillis(BaseDay + 5)).matchesPhotoDateFilter(filter))
    }

    @Test
    fun inactiveFilterMatchesPhotosWithoutDate() {
        assertTrue(testPhoto(dateTaken = null, dateModified = null, dateAdded = null).matchesPhotoDateFilter(PhotoDateFilter()))
    }

    @Test
    fun activeFilterRejectsPhotosWithoutDate() {
        val filter = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
            .withSelectedDays(start = BaseDay + 2, end = BaseDay + 4)

        assertFalse(testPhoto(dateTaken = null, dateModified = null, dateAdded = null).matchesPhotoDateFilter(filter))
    }

    @Test
    fun activeSelectionSurvivesNewBoundsWithClamping() {
        val current = PhotoDateFilter(minDay = BaseDay, maxDay = BaseDay + 10)
            .withSelectedDays(start = BaseDay + 2, end = BaseDay + 8)
        val next = buildPhotoDateFilter(
            photos = listOf(
                testPhoto(mediaId = 1L, dateTaken = photoDateDayToMillis(BaseDay + 5)),
                testPhoto(mediaId = 2L, dateTaken = photoDateDayToMillis(BaseDay + 15))
            ),
            current = current
        )

        assertEquals(BaseDay + 5, next.selectedStartDay)
        assertEquals(BaseDay + 8, next.selectedEndDay)
        assertTrue(next.isActive)
    }

    private fun testPhoto(
        mediaId: Long = 1L,
        dateTaken: Long? = 1_700_000_000_000L,
        dateModified: Long? = null,
        dateAdded: Long? = null
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = mediaId,
            uri = "content://media/external/images/media/$mediaId",
            displayName = "photo-$mediaId.jpg",
            mimeType = "image/jpeg",
            dateAdded = dateAdded,
            dateModified = dateModified,
            dateTaken = dateTaken,
            width = null,
            height = null,
            size = null,
            orientation = null,
            latitude = 56.8389,
            longitude = 60.6057
        )
    }

    private companion object {
        const val BaseDay = 20_000L
    }
}
