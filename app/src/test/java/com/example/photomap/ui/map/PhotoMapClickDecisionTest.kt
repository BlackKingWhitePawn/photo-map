package com.example.photomap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMapClickDecisionTest {
    @Test
    fun clusterExpandsWhenExpansionZoomIsUseful() {
        val action = PhotoMapClickDecision.forCluster(
            currentZoom = 12.0,
            expansionZoom = 14.0,
            maxZoom = 20.0,
            pageSize = 100
        )

        assertEquals(PhotoMapTapAction.ZoomToCluster(targetZoom = 14.0), action)
    }

    @Test
    fun clusterShowsPhotosWhenExpansionZoomDoesNotIncrease() {
        val action = PhotoMapClickDecision.forCluster(
            currentZoom = 18.0,
            expansionZoom = 18.0,
            maxZoom = 20.0,
            pageSize = 100
        )

        assertEquals(PhotoMapTapAction.ShowClusterPhotos(pageSize = 100), action)
    }

    @Test
    fun clusterShowsPhotosWhenExpansionZoomExceedsMaxZoom() {
        val action = PhotoMapClickDecision.forCluster(
            currentZoom = 20.0,
            expansionZoom = 21.0,
            maxZoom = 20.0,
            pageSize = 100
        )

        assertEquals(PhotoMapTapAction.ShowClusterPhotos(pageSize = 100), action)
    }

    @Test
    fun singlePointOpensPhoto() {
        val action = PhotoMapClickDecision.forUnclustered(listOf(7L))

        assertEquals(PhotoMapTapAction.OpenPhoto(photoId = 7L), action)
    }

    @Test
    fun overlappingPointsShowPhotos() {
        val action = PhotoMapClickDecision.forUnclustered(listOf(7L, 8L, 8L))

        assertEquals(PhotoMapTapAction.ShowPhotos(photoIds = listOf(7L, 8L)), action)
    }

    @Test
    fun emptyTapDoesNothing() {
        val action = PhotoMapClickDecision.forUnclustered(emptyList())

        assertEquals(PhotoMapTapAction.NoAction, action)
    }
}
