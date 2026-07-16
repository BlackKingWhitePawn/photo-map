package com.example.photomap.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMapDebugInfoTest {
    @Test
    fun coordinateUsesFiveDecimalPlaces() {
        assertEquals("56.12346", formatDebugCoordinate(56.1234567))
    }

    @Test
    fun clusterDebugLineContainsNumberCountAndCoordinates() {
        assertEquals(
            "3. 17 фото: 56.12346, 60.98765",
            formatClusterDebugLine(
                index = 2,
                photoCount = 17,
                latitude = 56.1234567,
                longitude = 60.9876543
            )
        )
    }

    @Test
    fun debugPanelStateDescribesHiddenState() {
        assertEquals("Скрыта", mapDebugPanelStateText(false))
    }

    @Test
    fun markerTopLeftCentersMarkerOnScreenPoint() {
        assertEquals(80 to 180, markerTopLeftPx(screenX = 100f, screenY = 200f, markerSizePx = 40f))
    }
}
