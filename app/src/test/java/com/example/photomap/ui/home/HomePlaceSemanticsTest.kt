package com.example.photomap.ui.home

import com.example.photomap.domain.model.DevicePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePlaceSemanticsTest {
    @Test
    fun classifyPlaceAcceptsCityTypes() {
        assertEquals(PlaceFacet.City, classifyPlace(category = "place", type = "city", adminLevel = null))
        assertEquals(PlaceFacet.City, classifyPlace(category = "place", type = "town", adminLevel = null))
        assertEquals(PlaceFacet.City, classifyPlace(category = "place", type = "village", adminLevel = null))
    }

    @Test
    fun classifyPlaceAcceptsAdministrativeDistrict() {
        assertEquals(
            PlaceFacet.District,
            classifyPlace(category = "boundary", type = "administrative", adminLevel = 9)
        )
    }

    @Test
    fun classifyPlaceAcceptsNamedNaturalAndLandmarkObjects() {
        assertEquals(PlaceFacet.NaturalArea, classifyPlace(category = "leisure", type = "park", adminLevel = null))
        assertEquals(PlaceFacet.Landmark, classifyPlace(category = "tourism", type = "museum", adminLevel = null))
        assertEquals(PlaceFacet.Transport, classifyPlace(category = "railway", type = "station", adminLevel = null))
    }

    @Test
    fun rejectedCategoriesAreTechnical() {
        listOf("landuse", "building", "highway", "power", "utility", "route").forEach { category ->
            assertEquals(
                PlaceFacet.Technical,
                classifyPlace(category = category, type = "residential", adminLevel = null)
            )
        }
    }

    @Test
    fun technicalGeneratedNameIsRejected() {
        val quality = evaluateName(
            name = "городская территория 200B",
            category = "landuse",
            type = "residential"
        )

        assertFalse(quality.isValid)
        assertEquals(listOf("technical_generated_name"), quality.reasons)
    }

    @Test
    fun missingNameIsRejectedBeforeCreatingUserPlace() {
        val evaluation = evaluateRawGeoObject(
            rawObject(name = null, category = "place", type = "city")
        )

        assertEquals(RawGeoObjectStatus.RejectedName, evaluation.status)
        assertEquals(listOf("missing_name"), evaluation.reasons)
    }

    @Test
    fun acceptedCityCanCreateUserPlace() {
        val rawObject = rawObject(
            name = "Test City",
            category = "place",
            type = "city",
            adminLevel = 8
        )

        assertTrue(shouldCreateUserPlace(rawObject))
        assertEquals(RawGeoObjectStatus.Accepted, evaluateRawGeoObject(rawObject).status)
    }

    @Test
    fun unsupportedCategoryDoesNotCreateUserPlace() {
        val evaluation = evaluateRawGeoObject(
            rawObject(name = "Test Shop", category = "shop", type = "bakery")
        )

        assertEquals(RawGeoObjectStatus.RejectedType, evaluation.status)
        assertFalse(shouldCreateUserPlace(rawObject(name = "Test Shop", category = "shop", type = "bakery")))
    }

    @Test
    fun geoObjectIdUsesSourceAndStableObjectId() {
        val rawObject = rawObject(sourceObjectId = "relation:123", category = "place", type = "city")

        assertEquals("osm:relation:123", rawObject.geoObjectId)
    }

    @Test
    fun photoInsideSeedBoundsCanMatchMultipleGeoObjects() {
        val matches = testPhoto(latitude = 56.84, longitude = 60.52).homeGeoObjectMatches()
        val ids = matches.map { match -> match.id }.sorted()

        assertEquals(
            listOf(
                "local:yekaterinburg:verkh-isetsky-district",
                "osm:relation:79379"
            ),
            ids
        )
    }

    @Test
    fun photoGeoObjectLinksUseSameMatches() {
        val links = testPhoto(latitude = 56.84, longitude = 60.52).photoGeoObjectLinks()

        assertEquals(
            listOf(
                "local:yekaterinburg:verkh-isetsky-district",
                "osm:relation:79379"
            ),
            links.map { link -> link.geoObjectId }.sorted()
        )
        assertTrue(links.all { link -> link.matchType == GeoMatchType.GeometryCovers })
        assertTrue(links.all { link -> link.confidence == 1.0 })
    }

    @Test
    fun seededCityAndDistrictAreRelatedThroughGraph() {
        assertTrue(
            geoObjectsAreRelated(
                firstId = "osm:relation:79379",
                secondId = "local:yekaterinburg:verkh-isetsky-district"
            )
        )
        assertFalse(
            geoObjectsAreRelated(
                firstId = "osm:relation:79379",
                secondId = "osm:relation:missing"
            )
        )
    }

    private fun rawObject(
        sourceObjectId: String = "relation:123",
        name: String? = "Test Place",
        category: String?,
        type: String?,
        adminLevel: Int? = null
    ): RawGeoObject {
        return RawGeoObject(
            source = GeoSource.OSM,
            sourceObjectId = sourceObjectId,
            objectType = GeoObjectType.Relation,
            name = name,
            category = category,
            type = type,
            placeRank = null,
            addressRank = null,
            adminLevel = adminLevel,
            displayName = null,
            geometry = GeoGeometry(
                GeoBoundingBox(
                    minLatitude = 56.0,
                    maxLatitude = 57.0,
                    minLongitude = 60.0,
                    maxLongitude = 61.0
                )
            ),
            centerLatitude = 56.5,
            centerLongitude = 60.5,
            importance = 1.0,
            hasMetadataReference = true
        )
    }

    private fun testPhoto(
        latitude: Double?,
        longitude: Double?
    ): DevicePhoto {
        return DevicePhoto(
            mediaId = 1L,
            uri = "content://media/external/images/media/1",
            displayName = "photo-1.jpg",
            mimeType = "image/jpeg",
            dateAdded = null,
            dateModified = null,
            dateTaken = 1_700_000_000_000L,
            width = null,
            height = null,
            size = null,
            orientation = null,
            latitude = latitude,
            longitude = longitude
        )
    }
}
