package com.example.photomap.ui.home

import com.example.photomap.domain.model.DevicePhoto
import java.util.Locale

internal enum class GeoSource(val idPrefix: String) {
    OSM("osm"),
    LocalCatalog("local")
}

internal enum class GeoObjectType {
    Relation,
    Way,
    Node
}

internal enum class GeoMatchType {
    GeometryCovers,
    Proximity
}

internal enum class GeoRelationType {
    AdministrativeParent,
    Contains,
    LocatedIn,
    Overlaps,
    PartOf
}

internal enum class PlaceFacet {
    Country,
    Region,
    City,
    District,
    Neighbourhood,
    NaturalArea,
    Landmark,
    Venue,
    Transport,
    Unknown,
    Technical
}

internal data class GeoBoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    fun covers(latitude: Double, longitude: Double): Boolean {
        return latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
    }
}

internal data class GeoGeometry(
    val boundingBox: GeoBoundingBox
) {
    fun covers(latitude: Double, longitude: Double): Boolean {
        return boundingBox.covers(latitude = latitude, longitude = longitude)
    }
}

internal data class RawGeoObject(
    val source: GeoSource,
    val sourceObjectId: String,
    val objectType: GeoObjectType,
    val name: String?,
    val category: String?,
    val type: String?,
    val placeRank: Int? = null,
    val addressRank: Int? = null,
    val adminLevel: Int? = null,
    val displayName: String? = null,
    val geometry: GeoGeometry?,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val importance: Double,
    val hasMetadataReference: Boolean = false
) {
    val geoObjectId: String
        get() = "${source.idPrefix}:$sourceObjectId"

    fun covers(latitude: Double, longitude: Double): Boolean {
        return geometry?.covers(latitude = latitude, longitude = longitude) ?: false
    }
}

internal data class GeoObject(
    val id: String,
    val source: GeoSource,
    val sourceObjectId: String,
    val name: String,
    val facet: PlaceFacet,
    val geometry: GeoGeometry?,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val importance: Double,
    val qualityScore: Double
)

internal data class GeoObjectRelation(
    val parentId: String,
    val childId: String,
    val relationType: GeoRelationType
)

internal data class PhotoGeoObjectLink(
    val photoId: Long,
    val geoObjectId: String,
    val matchType: GeoMatchType,
    val confidence: Double
)

internal data class UserPlace(
    val geoObjectId: String,
    val photoCount: Int,
    val sessionCount: Int,
    val visitCount: Int,
    val activeDayCount: Int,
    val activeMonthCount: Int,
    val tripCount: Int,
    val firstVisitedAt: Long,
    val lastVisitedAt: Long,
    val score: Double,
    val coverPhotoId: Long?
)

internal data class NameQuality(
    val isValid: Boolean,
    val score: Double,
    val reasons: List<String>
)

internal enum class RawGeoObjectStatus {
    Accepted,
    RejectedTechnical,
    RejectedName,
    RejectedType,
    Unknown
}

internal data class RawGeoObjectEvaluation(
    val status: RawGeoObjectStatus,
    val facet: PlaceFacet,
    val nameQuality: NameQuality,
    val qualityScore: Double,
    val reasons: List<String>
)

internal data class PlaceFilterConfig(
    val acceptedPlaceTypes: Set<String>,
    val acceptedCategories: Set<String>,
    val rejectedCategories: Set<String>,
    val lowPriorityTypes: Set<String>,
    val minimumQuality: Double
)

internal data class HomeGeoObjectMatch(
    val id: String,
    val source: GeoSource,
    val sourceObjectId: String,
    val name: String,
    val facet: PlaceFacet,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val importance: Double,
    val qualityScore: Double,
    val matchType: GeoMatchType,
    val confidence: Double
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
    }
}

internal fun DevicePhoto.homeGeoObjectMatches(): List<HomeGeoObjectMatch> {
    val latitude = latitude ?: return emptyList()
    val longitude = longitude ?: return emptyList()
    return SeedRawGeoObjects
        .asSequence()
        .filter { rawObject -> rawObject.covers(latitude = latitude, longitude = longitude) }
        .mapNotNull { rawObject -> rawObject.toAcceptedHomeGeoObjectMatch() }
        .distinctBy { match -> match.id }
        .toList()
}

internal fun DevicePhoto.photoGeoObjectLinks(): List<PhotoGeoObjectLink> {
    return homeGeoObjectMatches().map { match ->
        PhotoGeoObjectLink(
            photoId = mediaId,
            geoObjectId = match.id,
            matchType = match.matchType,
            confidence = match.confidence
        )
    }
}

internal fun classifyPlace(
    category: String?,
    type: String?,
    adminLevel: Int?
): PlaceFacet {
    val normalizedCategory = category?.lowercase(Locale.US)
    val normalizedType = type?.lowercase(Locale.US)
    if ((normalizedCategory != null && normalizedCategory in defaultPlaceFilterConfig.rejectedCategories) ||
        (normalizedCategory == "boundary" && normalizedType != null && normalizedType in setOf("statistical", "census"))
    ) {
        return PlaceFacet.Technical
    }

    if (normalizedCategory == "place") {
        return when (normalizedType) {
            "city", "town", "village", "hamlet" -> PlaceFacet.City
            "suburb", "borough", "quarter" -> PlaceFacet.District
            "neighbourhood" -> PlaceFacet.Neighbourhood
            "locality", "isolated_dwelling", "block" -> PlaceFacet.Unknown
            else -> PlaceFacet.Unknown
        }
    }

    if (normalizedCategory == "boundary" && normalizedType == "administrative") {
        return classifyAdministrativeLevel(adminLevel)
    }

    if (normalizedCategory == "boundary" && normalizedType == "protected_area") {
        return PlaceFacet.NaturalArea
    }

    if (normalizedCategory == "leisure" && normalizedType != null && normalizedType in setOf("park", "garden")) {
        return PlaceFacet.NaturalArea
    }

    if (normalizedCategory == "tourism" &&
        normalizedType != null && normalizedType in setOf("attraction", "museum", "gallery", "viewpoint")
    ) {
        return PlaceFacet.Landmark
    }

    if (normalizedCategory == "natural" && normalizedType != null && normalizedType in setOf("peak", "beach", "water")) {
        return PlaceFacet.NaturalArea
    }

    if (normalizedCategory == "amenity" &&
        normalizedType != null && normalizedType in setOf("university", "theatre", "cinema", "library", "cafe", "restaurant")
    ) {
        return PlaceFacet.Venue
    }

    if (normalizedCategory == "railway" && normalizedType == "station") {
        return PlaceFacet.Transport
    }

    if (normalizedCategory == "aeroway" && normalizedType == "aerodrome") {
        return PlaceFacet.Transport
    }

    if (normalizedCategory == "historic") {
        return PlaceFacet.Landmark
    }

    return PlaceFacet.Unknown
}

internal fun evaluateName(
    name: String?,
    category: String?,
    type: String?
): NameQuality {
    val value = name
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?: return NameQuality(false, 0.0, listOf("missing_name"))

    if (value.length !in 2..100) {
        return NameQuality(false, 0.0, listOf("invalid_length"))
    }

    val letters = value.count { character -> character.isLetter() }
    val digits = value.count { character -> character.isDigit() }
    if (letters == 0) {
        return NameQuality(false, 0.0, listOf("no_letters"))
    }
    if (digits > letters * 2) {
        return NameQuality(false, 0.0, listOf("mostly_digits"))
    }

    val normalizedCategory = category?.lowercase(Locale.US)
    val technicalCategory = normalizedCategory != null && normalizedCategory in defaultPlaceFilterConfig.rejectedCategories
    val genericTechnicalName = Regex(
        pattern = """(?iu)^(городская\s+территория|территория|участок|зона|сектор|объект|area|sector|zone|plot)\s*[-№#A-ZА-Я0-9]+$"""
    ).matches(value)
    if (technicalCategory || genericTechnicalName) {
        return NameQuality(false, 0.0, listOf("technical_generated_name"))
    }

    val normalizedType = type?.lowercase(Locale.US)
    val lowPriorityType = normalizedType != null && normalizedType in defaultPlaceFilterConfig.lowPriorityTypes
    val score = if (lowPriorityType) 0.65 else 1.0
    return NameQuality(true, score, emptyList())
}

internal fun calculateGeoObjectQuality(rawObject: RawGeoObject): Double {
    val facet = classifyPlace(
        category = rawObject.category,
        type = rawObject.type,
        adminLevel = rawObject.adminLevel
    )
    var score = when (facet) {
        PlaceFacet.City -> 1.0
        PlaceFacet.District -> 0.9
        PlaceFacet.Neighbourhood -> 0.85
        PlaceFacet.NaturalArea -> 0.8
        PlaceFacet.Landmark -> 0.8
        PlaceFacet.Transport -> 0.7
        PlaceFacet.Venue -> 0.65
        PlaceFacet.Country,
        PlaceFacet.Region -> 0.75
        PlaceFacet.Unknown -> 0.25
        PlaceFacet.Technical -> 0.0
    }

    val nameQuality = evaluateName(
        name = rawObject.name,
        category = rawObject.category,
        type = rawObject.type
    )
    if (!nameQuality.isValid) {
        return 0.0
    }

    score *= nameQuality.score
    if (rawObject.geometry != null) {
        score += 0.05
    }
    if (rawObject.hasMetadataReference) {
        score += 0.1
    }

    return score.coerceIn(0.0, 1.0)
}

internal fun evaluateRawGeoObject(rawObject: RawGeoObject): RawGeoObjectEvaluation {
    val category = rawObject.category?.lowercase(Locale.US)
    val type = rawObject.type?.lowercase(Locale.US)
    val facet = classifyPlace(
        category = category,
        type = type,
        adminLevel = rawObject.adminLevel
    )
    val nameQuality = evaluateName(
        name = rawObject.name,
        category = category,
        type = type
    )
    val reasons = mutableListOf<String>()

    val rejectedCategory = category != null && category in defaultPlaceFilterConfig.rejectedCategories
    val rejectedBoundaryType = category == "boundary" && type != null && type in setOf("statistical", "census")
    if (rejectedCategory || rejectedBoundaryType || facet == PlaceFacet.Technical) {
        reasons += "technical_category"
        reasons += nameQuality.reasons
        return RawGeoObjectEvaluation(
            status = RawGeoObjectStatus.RejectedTechnical,
            facet = facet,
            nameQuality = nameQuality,
            qualityScore = 0.0,
            reasons = reasons.distinct()
        )
    }

    if (!nameQuality.isValid) {
        return RawGeoObjectEvaluation(
            status = RawGeoObjectStatus.RejectedName,
            facet = facet,
            nameQuality = nameQuality,
            qualityScore = 0.0,
            reasons = nameQuality.reasons
        )
    }

    if (category == null ||
        category !in defaultPlaceFilterConfig.acceptedCategories ||
        category == "place" && (type == null || type !in defaultPlaceFilterConfig.acceptedPlaceTypes) ||
        facet !in visiblePlaceFacets
    ) {
        reasons += "unsupported_place_type"
        return RawGeoObjectEvaluation(
            status = RawGeoObjectStatus.RejectedType,
            facet = facet,
            nameQuality = nameQuality,
            qualityScore = 0.0,
            reasons = reasons
        )
    }

    val qualityScore = calculateGeoObjectQuality(rawObject)
    if (qualityScore < defaultPlaceFilterConfig.minimumQuality) {
        reasons += "low_quality"
        return RawGeoObjectEvaluation(
            status = RawGeoObjectStatus.Unknown,
            facet = facet,
            nameQuality = nameQuality,
            qualityScore = qualityScore,
            reasons = reasons
        )
    }

    return RawGeoObjectEvaluation(
        status = RawGeoObjectStatus.Accepted,
        facet = facet,
        nameQuality = nameQuality,
        qualityScore = qualityScore,
        reasons = emptyList()
    )
}

internal fun shouldCreateUserPlace(rawObject: RawGeoObject): Boolean {
    return evaluateRawGeoObject(rawObject).status == RawGeoObjectStatus.Accepted
}

private fun RawGeoObject.toAcceptedHomeGeoObjectMatch(): HomeGeoObjectMatch? {
    val evaluation = evaluateRawGeoObject(this)
    if (evaluation.status != RawGeoObjectStatus.Accepted) {
        return null
    }
    val geometryBounds = geometry?.boundingBox ?: return null
    return HomeGeoObjectMatch(
        id = geoObjectId,
        source = source,
        sourceObjectId = sourceObjectId,
        name = name?.trim().orEmpty(),
        facet = evaluation.facet,
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        minLatitude = geometryBounds.minLatitude,
        maxLatitude = geometryBounds.maxLatitude,
        minLongitude = geometryBounds.minLongitude,
        maxLongitude = geometryBounds.maxLongitude,
        importance = importance * evaluation.qualityScore,
        qualityScore = evaluation.qualityScore,
        matchType = GeoMatchType.GeometryCovers,
        confidence = 1.0
    )
}

private fun classifyAdministrativeLevel(adminLevel: Int?): PlaceFacet {
    return when (adminLevel) {
        2 -> PlaceFacet.Country
        4 -> PlaceFacet.Region
        6 -> PlaceFacet.District
        8 -> PlaceFacet.City
        9 -> PlaceFacet.District
        10 -> PlaceFacet.Neighbourhood
        else -> PlaceFacet.Unknown
    }
}

private val visiblePlaceFacets = setOf(
    PlaceFacet.City,
    PlaceFacet.District,
    PlaceFacet.Neighbourhood,
    PlaceFacet.NaturalArea,
    PlaceFacet.Landmark,
    PlaceFacet.Venue,
    PlaceFacet.Transport
)

internal val defaultPlaceFilterConfig = PlaceFilterConfig(
    acceptedPlaceTypes = setOf(
        "city",
        "town",
        "village",
        "hamlet",
        "suburb",
        "borough",
        "quarter",
        "neighbourhood",
        "park",
        "garden",
        "museum",
        "attraction",
        "viewpoint",
        "station",
        "aerodrome"
    ),
    acceptedCategories = setOf(
        "place",
        "boundary",
        "leisure",
        "tourism",
        "amenity",
        "natural",
        "historic",
        "railway",
        "aeroway"
    ),
    rejectedCategories = setOf(
        "landuse",
        "building",
        "highway",
        "addr",
        "postal_code",
        "parking",
        "power",
        "utility",
        "route"
    ),
    lowPriorityTypes = setOf(
        "locality",
        "isolated_dwelling",
        "block"
    ),
    minimumQuality = 0.60
)

internal val SeedGeoObjectRelations = listOf(
    GeoObjectRelation(
        parentId = "osm:relation:79379",
        childId = "local:yekaterinburg:verkh-isetsky-district",
        relationType = GeoRelationType.AdministrativeParent
    )
)

internal fun geoObjectsAreRelated(firstId: String, secondId: String): Boolean {
    if (firstId == secondId) {
        return true
    }
    val pending = mutableListOf(firstId)
    val visited = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        val current = pending.removeAt(pending.lastIndex)
        if (!visited.add(current)) {
            continue
        }
        GeoObjectRelationEdges[current].orEmpty().forEach { next ->
            if (next == secondId) {
                return true
            }
            if (next !in visited) {
                pending += next
            }
        }
    }
    return false
}

private val GeoObjectRelationEdges: Map<String, Set<String>> = run {
    val edges = mutableMapOf<String, MutableSet<String>>()
    SeedGeoObjectRelations.forEach { relation ->
        edges.getOrPut(relation.parentId) { mutableSetOf() }.add(relation.childId)
        edges.getOrPut(relation.childId) { mutableSetOf() }.add(relation.parentId)
    }
    edges.mapValues { entry -> entry.value.toSet() }
}

private val SeedRawGeoObjects = listOf(
    RawGeoObject(
        source = GeoSource.OSM,
        sourceObjectId = "relation:79379",
        objectType = GeoObjectType.Relation,
        name = "Екатеринбург",
        category = "place",
        type = "city",
        adminLevel = 8,
        displayName = "Екатеринбург, городской округ Екатеринбург, Свердловская область, Россия",
        geometry = GeoGeometry(
            GeoBoundingBox(
                minLatitude = 56.65,
                maxLatitude = 57.05,
                minLongitude = 60.25,
                maxLongitude = 60.90
            )
        ),
        centerLatitude = 56.8389,
        centerLongitude = 60.6057,
        importance = 1.22,
        hasMetadataReference = true
    ),
    RawGeoObject(
        source = GeoSource.LocalCatalog,
        sourceObjectId = "yekaterinburg:verkh-isetsky-district",
        objectType = GeoObjectType.Relation,
        name = "Верх-Исетский район",
        category = "boundary",
        type = "administrative",
        adminLevel = 9,
        displayName = "Верх-Исетский район, Екатеринбург, Свердловская область, Россия",
        geometry = GeoGeometry(
            GeoBoundingBox(
                minLatitude = 56.76,
                maxLatitude = 56.93,
                minLongitude = 60.36,
                maxLongitude = 60.60
            )
        ),
        centerLatitude = 56.84,
        centerLongitude = 60.52,
        importance = 1.02,
        hasMetadataReference = true
    )
)
