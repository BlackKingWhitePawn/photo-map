package com.example.photomap.ui.map

import com.example.photomap.data.heatmap.VisibleTripHeatCell
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Point

class TripHeatmapFeatureMapperTest {
    @Test
    fun emptyCellsCreateEmptyFeatureCollection() {
        val collection = TripHeatmapFeatureMapper.toFeatureCollection(emptyList())

        assertEquals(0, collection.features().size)
    }

    @Test
    fun invalidCellCoordinatesAreSkipped() {
        val collection = TripHeatmapFeatureMapper.toFeatureCollection(
            listOf(
                cell(h3Index = "invalid-latitude", latitude = 91.0, longitude = 60.0),
                cell(h3Index = "invalid-longitude", latitude = 56.0, longitude = 181.0),
                cell(h3Index = "nan", latitude = Double.NaN, longitude = 60.0)
            )
        )

        assertEquals(0, collection.features().size)
    }

    @Test
    fun mapsCellPropertiesToFeature() {
        val collection = TripHeatmapFeatureMapper.toFeatureCollection(
            listOf(
                cell(
                    h3Index = "8928308280fffff",
                    latitude = 56.8389,
                    longitude = 60.6057,
                    normalizedIntensity = 0.4
                )
            )
        )

        val feature = collection.features().single()
        val point = feature.geometry() as Point

        assertEquals(60.6057, point.longitude(), 0.0)
        assertEquals(56.8389, point.latitude(), 0.0)
        assertEquals(0.4, feature.getNumberProperty(TRIP_HEATMAP_WEIGHT_PROPERTY).toDouble(), 0.0)
        assertEquals("8928308280fffff", feature.getStringProperty(TRIP_HEATMAP_H3_INDEX_PROPERTY))
    }

    @Test
    fun normalizedIntensityIsClampedToZeroOneRange() {
        val collection = TripHeatmapFeatureMapper.toFeatureCollection(
            listOf(
                cell(h3Index = "low", normalizedIntensity = -1.0),
                cell(h3Index = "high", normalizedIntensity = 2.0)
            )
        )

        val weights = collection.features()
            .map { feature -> feature.getNumberProperty(TRIP_HEATMAP_WEIGHT_PROPERTY).toDouble() }

        assertEquals(listOf(0.0, 1.0), weights)
    }

    private fun cell(
        h3Index: String,
        latitude: Double = 56.8389,
        longitude: Double = 60.6057,
        normalizedIntensity: Double = 0.5
    ): VisibleTripHeatCell {
        return VisibleTripHeatCell(
            h3Index = h3Index,
            resolution = 6,
            centerLatitude = latitude,
            centerLongitude = longitude,
            tripCount = 2,
            activeDays = 3,
            daysSpent = 4,
            sessionCount = 5,
            latestTripAt = 1_700_000_000_000L,
            intensity = 3.14,
            normalizedIntensity = normalizedIntensity
        )
    }
}
