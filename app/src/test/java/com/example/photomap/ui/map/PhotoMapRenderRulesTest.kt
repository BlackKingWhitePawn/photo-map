package com.example.photomap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMapRenderRulesTest {
    @Test
    fun singleStoredClusterRendersAsPhoto() {
        assertFalse(isPhotoMapCluster(photoCount = 1))
    }

    @Test
    fun multiPhotoStoredClusterRendersAsCluster() {
        assertTrue(isPhotoMapCluster(photoCount = 2))
    }

    @Test
    fun representativeFallsBackToFirstPhotoId() {
        assertEquals(
            42L,
            selectRepresentativePhotoId(
                coverPhotoId = null,
                photoIds = listOf(42L, 43L)
            )
        )
    }

    @Test
    fun representativeUsesCoverPhotoForStoredSingle() {
        assertEquals(
            7L,
            selectRepresentativePhotoId(
                coverPhotoId = 7L,
                photoIds = emptyList()
            )
        )
    }

    @Test
    fun tappableIdsUseCoverPhotoForStoredSingle() {
        assertEquals(
            listOf(7L),
            selectTappablePhotoIds(
                coverPhotoId = 7L,
                photoIds = emptyList()
            )
        )
    }

    @Test
    fun tappableIdsKeepExplicitClusterLeaves() {
        assertEquals(
            listOf(7L, 8L),
            selectTappablePhotoIds(
                coverPhotoId = 9L,
                photoIds = listOf(7L, 8L)
            )
        )
    }
}
