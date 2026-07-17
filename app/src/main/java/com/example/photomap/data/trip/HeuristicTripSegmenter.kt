package com.example.photomap.data.trip

import com.example.photomap.core.util.LocationValidator
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.BaseRegion
import com.example.photomap.domain.trip.DetectedTrip
import com.example.photomap.domain.trip.DetectedTripDestination
import com.example.photomap.domain.trip.TripSegmentationResult
import com.example.photomap.domain.trip.TripType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

class HeuristicTripSegmenter : TripSegmenter {
    override suspend fun segmentTrips(
        photos: Collection<DevicePhoto>
    ): TripSegmentationResult {
        val locatedPhotos = photos.mapNotNull { photo -> photo.toLocatedPhoto() }
            .sortedWith(compareBy<LocatedPhoto> { photo -> photo.takenAtMillis }.thenBy { photo -> photo.mediaId })
        if (locatedPhotos.isEmpty()) {
            return TripSegmentationResult(
                trips = emptyList(),
                baseRegions = emptyList(),
                skippedPhotos = photos.size
            )
        }

        val sessions = buildPhotoSessions(locatedPhotos)
        val baseRegions = detectBaseRegions(sessions)
        if (sessions.isEmpty() || baseRegions.isEmpty()) {
            return TripSegmentationResult(
                trips = emptyList(),
                baseRegions = baseRegions,
                skippedPhotos = photos.size - locatedPhotos.size
            )
        }

        val trips = detectTrips(
            sessions = sessions,
            baseRegions = baseRegions
        )
        return TripSegmentationResult(
            trips = trips,
            baseRegions = baseRegions,
            skippedPhotos = photos.size - locatedPhotos.size
        )
    }

    private suspend fun buildPhotoSessions(photos: List<LocatedPhoto>): List<PhotoSession> {
        val sessions = mutableListOf<PhotoSession>()
        var current = mutableListOf<LocatedPhoto>()
        photos.forEachIndexed { index, photo ->
            if (index % CancellationCheckInterval == 0) {
                currentCoroutineContext().ensureActive()
            }
            val previous = current.lastOrNull()
            val startsNewSession = previous != null && (
                photo.takenAtMillis - previous.takenAtMillis > SessionGapMillis ||
                    previous.distanceKmTo(photo) > SessionDistanceKm
                )
            if (startsNewSession && current.isNotEmpty()) {
                sessions += current.toPhotoSession()
                current = mutableListOf()
            }
            current += photo
        }
        if (current.isNotEmpty()) {
            sessions += current.toPhotoSession()
        }
        return sessions
    }

    private fun detectBaseRegions(sessions: List<PhotoSession>): List<BaseRegion> {
        val regions = mutableListOf<MutableBaseRegion>()
        sessions.forEach { session ->
            val target = regions
                .filter { region -> region.distanceKmTo(session.latitude, session.longitude) <= BaseRegionRadiusKm }
                .minByOrNull { region -> region.distanceKmTo(session.latitude, session.longitude) }
            if (target == null) {
                regions += MutableBaseRegion(session)
            } else {
                target.add(session)
            }
        }

        val scoredRegions = regions
            .mapIndexed { index, region -> region.toBaseRegion(index) }
            .sortedWith(compareByDescending<BaseRegion> { region -> region.score }.thenBy { region -> region.id })
        val stableRegions = scoredRegions.filter { region -> region.uniqueVisitedDays >= MinBaseVisitedDays }
        return (stableRegions.ifEmpty { scoredRegions.take(1) }).take(MaxBaseRegions)
    }

    private suspend fun detectTrips(
        sessions: List<PhotoSession>,
        baseRegions: List<BaseRegion>
    ): List<DetectedTrip> {
        val trips = mutableListOf<DetectedTrip>()
        var index = 0
        while (index < sessions.size) {
            currentCoroutineContext().ensureActive()
            if (!sessions[index].isAwayFrom(baseRegions)) {
                index += 1
                continue
            }

            val startIndex = index
            var endIndex = index
            index += 1
            while (index < sessions.size) {
                val next = sessions[index]
                if (!next.isAwayFrom(baseRegions)) {
                    break
                }
                val gapDays = (next.day - sessions[endIndex].day - 1).coerceAtLeast(0)
                val sameAwayRegion = sessions[endIndex].distanceKmTo(next) <= SameAwayRegionAfterGapKm
                if (gapDays > MaxUnknownGapDays && !sameAwayRegion) {
                    break
                }
                endIndex = index
                index += 1
            }

            val segmentSessions = sessions.subList(startIndex, endIndex + 1)
            buildTripCandidate(
                index = trips.size,
                segmentSessions = segmentSessions,
                previousSession = sessions.getOrNull(startIndex - 1),
                nextSession = sessions.getOrNull(endIndex + 1),
                baseRegions = baseRegions
            )?.let { trip -> trips += trip }
        }
        return trips.sortedByDescending { trip -> trip.startDay }
    }

    private fun buildTripCandidate(
        index: Int,
        segmentSessions: List<PhotoSession>,
        previousSession: PhotoSession?,
        nextSession: PhotoSession?,
        baseRegions: List<BaseRegion>
    ): DetectedTrip? {
        if (segmentSessions.isEmpty()) {
            return null
        }

        val startDay = segmentSessions.minOf { session -> session.day }
        val endDay = segmentSessions.maxOf { session -> session.day }
        val durationDays = (endDay - startDay + 1).coerceAtLeast(1)
        val activeDays = segmentSessions.flatMap { session -> session.activeDays }.distinct().size
        if (activeDays < MinTripActiveDays || durationDays > MaxTripDurationDays) {
            return null
        }

        val destinations = detectDestinations(segmentSessions)
        val largestDestinationShare = destinations.maxOfOrNull { destination ->
            destination.photoCount.toDouble() / segmentSessions.sumOf { session -> session.photos.size }.coerceAtLeast(1)
        } ?: 0.0
        val centerSession = segmentSessions.medoid()
        val distancesToCenter = segmentSessions.map { session -> centerSession.distanceKmTo(session) }
        val radiusKm = percentile(distancesToCenter, 0.80).coerceIn(MinTripRadiusKm, MaxTripRadiusKm)
        val distanceFromBase = baseRegions.minOf { base -> centerSession.distanceKmTo(base.latitude, base.longitude) }
        if (distanceFromBase < MinDistanceFromBaseKm) {
            return null
        }

        val insideDuring = if (largestDestinationShare >= SingleDestinationShare) {
            largestDestinationShare
        } else {
            segmentSessions.count { session -> session.nearestBaseDistanceKm(baseRegions) >= MinDistanceFromBaseKm }
                .toDouble() / segmentSessions.size.coerceAtLeast(1)
        }
        if (insideDuring < MinDuringShare) {
            return null
        }

        val outsideBefore = previousSession?.let { session ->
            if (session.distanceKmTo(centerSession) > radiusKm) 1.0 else 0.0
        } ?: MissingBoundaryContrast
        val outsideAfter = nextSession?.let { session ->
            if (session.distanceKmTo(centerSession) > radiusKm) 1.0 else 0.0
        } ?: MissingBoundaryContrast
        val boundaryContrast = minOf(outsideBefore, outsideAfter)
        val maxGapDays = segmentSessions.zipWithNext()
            .maxOfOrNull { (first, second) -> (second.day - first.day - 1).coerceAtLeast(0) }
            ?: 0L
        val temporalContinuity = (1.0 - maxGapDays.toDouble() / ContinuityGapPenaltyDays).coerceIn(0.0, 1.0)
        val activeDayCoverage = (activeDays.toDouble() / durationDays.toDouble()).coerceIn(0.0, 1.0)
        val distanceFromBaseScore = ((distanceFromBase - MinDistanceFromBaseKm) / DistanceScoreFullKm)
            .coerceIn(0.0, 1.0)
        val confidence = (
            0.35 * insideDuring +
                0.25 * boundaryContrast +
                0.20 * distanceFromBaseScore +
                0.10 * temporalContinuity +
                0.10 * activeDayCoverage
            ).coerceIn(0.0, 1.0)
        if (confidence < MinTripConfidence) {
            return null
        }

        val photoIds = segmentSessions.flatMap { session -> session.photos.map { photo -> photo.mediaId } }.distinct()
        val coverPhotoId = segmentSessions
            .flatMap { session -> session.photos }
            .maxByOrNull { photo -> photo.takenAtMillis }
            ?.mediaId
        val type = when {
            destinations.isEmpty() -> TripType.UNKNOWN
            largestDestinationShare >= SingleDestinationShare -> TripType.SINGLE_DESTINATION_TRIP
            destinations.size > 1 -> TripType.MULTI_DESTINATION_TRIP
            else -> TripType.UNKNOWN
        }
        return DetectedTrip(
            id = tripIdFor(startDay, endDay, index),
            startDay = startDay,
            endDay = endDay,
            centerLatitude = centerSession.latitude,
            centerLongitude = centerSession.longitude,
            radiusKm = radiusKm,
            photoCount = photoIds.size,
            activeDayCount = activeDays,
            confidence = confidence,
            type = type,
            coverPhotoId = coverPhotoId,
            photoIds = photoIds,
            destinations = destinations
        )
    }

    private fun detectDestinations(sessions: List<PhotoSession>): List<DetectedTripDestination> {
        val clusters = mutableListOf<MutableDestination>()
        sessions.forEach { session ->
            val target = clusters
                .filter { cluster -> cluster.distanceKmTo(session.latitude, session.longitude) <= DestinationRadiusKm }
                .minByOrNull { cluster -> cluster.distanceKmTo(session.latitude, session.longitude) }
            if (target == null) {
                clusters += MutableDestination(session)
            } else {
                target.add(session)
            }
        }
        return clusters
            .sortedWith(compareBy<MutableDestination> { destination -> destination.firstDay }.thenByDescending { it.photoCount })
            .mapIndexed { index, destination -> destination.toDetectedDestination(index) }
    }

    private fun List<LocatedPhoto>.toPhotoSession(): PhotoSession {
        val centerPhoto = medoid()
        return PhotoSession(
            photos = this,
            latitude = centerPhoto.latitude,
            longitude = centerPhoto.longitude,
            startMillis = minOf { photo -> photo.takenAtMillis },
            endMillis = maxOf { photo -> photo.takenAtMillis },
            day = minOf { photo -> photo.day },
            activeDays = map { photo -> photo.day }.distinct(),
            activeMonths = map { photo -> photo.monthKey }.distinct()
        )
    }

    private fun List<LocatedPhoto>.medoid(): LocatedPhoto {
        if (size <= 1) {
            return first()
        }
        if (size > MaxMedoidPoints) {
            return minByOrNull { photo -> abs(photo.takenAtMillis - (first().takenAtMillis + last().takenAtMillis) / 2L) }
                ?: first()
        }
        return minByOrNull { candidate ->
            sumOf { other -> candidate.distanceKmTo(other) }
        } ?: first()
    }

    private fun List<PhotoSession>.medoid(): PhotoSession {
        if (size <= 1) {
            return first()
        }
        return minByOrNull { candidate ->
            sumOf { other -> candidate.distanceKmTo(other) }
        } ?: first()
    }

    private fun DevicePhoto.toLocatedPhoto(): LocatedPhoto? {
        val coords = LocationValidator.normalize(latitude, longitude) ?: return null
        val millis = photoDateMillis() ?: return null
        return LocatedPhoto(
            mediaId = mediaId,
            latitude = coords.latitude,
            longitude = coords.longitude,
            takenAtMillis = millis,
            day = Math.floorDiv(millis, MillisPerDay),
            monthKey = monthKey(millis)
        )
    }

    private fun monthKey(millis: Long): Int {
        val calendar = Calendar.getInstance(UtcTimeZone)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    }

    private fun tripIdFor(startDay: Long, endDay: Long, index: Int): Long {
        val value = startDay * 1_000_003L + endDay * 9_176L + index
        return if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)
    }

    private data class LocatedPhoto(
        val mediaId: Long,
        val latitude: Double,
        val longitude: Double,
        val takenAtMillis: Long,
        val day: Long,
        val monthKey: Int
    ) {
        fun distanceKmTo(other: LocatedPhoto): Double {
            return haversineKm(latitude, longitude, other.latitude, other.longitude)
        }
    }

    private data class PhotoSession(
        val photos: List<LocatedPhoto>,
        val latitude: Double,
        val longitude: Double,
        val startMillis: Long,
        val endMillis: Long,
        val day: Long,
        val activeDays: List<Long>,
        val activeMonths: List<Int>
    ) {
        fun distanceKmTo(other: PhotoSession): Double {
            return haversineKm(latitude, longitude, other.latitude, other.longitude)
        }

        fun distanceKmTo(latitude: Double, longitude: Double): Double {
            return haversineKm(this.latitude, this.longitude, latitude, longitude)
        }

        fun nearestBaseDistanceKm(baseRegions: List<BaseRegion>): Double {
            return baseRegions.minOf { base -> distanceKmTo(base.latitude, base.longitude) }
        }

        fun isAwayFrom(baseRegions: List<BaseRegion>): Boolean {
            return nearestBaseDistanceKm(baseRegions) >= MinDistanceFromBaseKm
        }
    }

    private class MutableBaseRegion(firstSession: PhotoSession) {
        private val sessions = mutableListOf(firstSession)
        private var latitude = firstSession.latitude
        private var longitude = firstSession.longitude

        fun add(session: PhotoSession) {
            sessions += session
            val total = sessions.size.toDouble()
            latitude = sessions.sumOf { item -> item.latitude } / total
            longitude = sessions.sumOf { item -> item.longitude } / total
        }

        fun distanceKmTo(latitude: Double, longitude: Double): Double {
            return haversineKm(this.latitude, this.longitude, latitude, longitude)
        }

        fun toBaseRegion(index: Int): BaseRegion {
            val uniqueDays = sessions.flatMap { session -> session.activeDays }.distinct().size
            val activeMonths = sessions.flatMap { session -> session.activeMonths }.distinct().size
            val score = uniqueDays + activeMonths * BaseMonthWeight
            return BaseRegion(
                id = "base-$index",
                latitude = latitude,
                longitude = longitude,
                uniqueVisitedDays = uniqueDays,
                activeMonths = activeMonths,
                score = score
            )
        }
    }

    private class MutableDestination(firstSession: PhotoSession) {
        private val sessions = mutableListOf(firstSession)
        private var latitude = firstSession.latitude
        private var longitude = firstSession.longitude

        val firstDay: Long
            get() = sessions.minOf { session -> session.day }

        val photoCount: Int
            get() = sessions.sumOf { session -> session.photos.size }

        fun add(session: PhotoSession) {
            sessions += session
            val total = sessions.sumOf { item -> item.photos.size }.toDouble().coerceAtLeast(1.0)
            latitude = sessions.sumOf { item -> item.latitude * item.photos.size } / total
            longitude = sessions.sumOf { item -> item.longitude * item.photos.size } / total
        }

        fun distanceKmTo(latitude: Double, longitude: Double): Double {
            return haversineKm(this.latitude, this.longitude, latitude, longitude)
        }

        fun toDetectedDestination(index: Int): DetectedTripDestination {
            val centerSession = sessions.minByOrNull { candidate ->
                sessions.sumOf { session -> candidate.distanceKmTo(session) }
            } ?: sessions.first()
            val radiusKm = percentile(
                sessions.map { session -> centerSession.distanceKmTo(session) },
                0.80
            ).coerceIn(MinTripRadiusKm, MaxTripRadiusKm)
            return DetectedTripDestination(
                centerLatitude = centerSession.latitude,
                centerLongitude = centerSession.longitude,
                radiusKm = radiusKm,
                photoCount = photoCount,
                firstDay = sessions.minOf { session -> session.day },
                lastDay = sessions.maxOf { session -> session.day },
                sortOrder = index
            )
        }
    }

    private companion object {
        const val SessionGapMillis = 3L * 60L * 60L * 1000L
        const val SessionDistanceKm = 20.0
        const val BaseRegionRadiusKm = 50.0
        const val DestinationRadiusKm = 50.0
        const val SameAwayRegionAfterGapKm = 80.0
        const val MinDistanceFromBaseKm = 50.0
        const val MinDuringShare = 0.65
        const val SingleDestinationShare = 0.65
        const val MissingBoundaryContrast = 0.55
        const val MinTripRadiusKm = 2.0
        const val MaxTripRadiusKm = 50.0
        const val DistanceScoreFullKm = 250.0
        const val ContinuityGapPenaltyDays = 7.0
        const val MinTripConfidence = 0.35
        const val BaseMonthWeight = 3.0
        const val MinBaseVisitedDays = 2
        const val MaxBaseRegions = 3
        const val MaxUnknownGapDays = 2L
        const val MaxTripDurationDays = 60L
        const val MinTripActiveDays = 1
        const val MaxMedoidPoints = 128
        const val MillisPerDay = 24L * 60L * 60L * 1000L
        const val EarthRadiusKm = 6371.0
        const val CancellationCheckInterval = 128
        val UtcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun percentile(values: List<Double>, percentile: Double): Double {
    if (values.isEmpty()) {
        return 0.0
    }
    val sorted = values.sorted()
    val rawIndex = ((sorted.size - 1) * percentile).roundToLong().toInt()
    return sorted[rawIndex.coerceIn(sorted.indices)]
}

private fun haversineKm(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double
): Double {
    val firstLatitudeRad = Math.toRadians(firstLatitude)
    val secondLatitudeRad = Math.toRadians(secondLatitude)
    val deltaLatitudeRad = Math.toRadians(secondLatitude - firstLatitude)
    val deltaLongitudeRad = Math.toRadians(secondLongitude - firstLongitude)
    val halfChord = sin(deltaLatitudeRad / 2.0) * sin(deltaLatitudeRad / 2.0) +
        cos(firstLatitudeRad) * cos(secondLatitudeRad) *
        sin(deltaLongitudeRad / 2.0) * sin(deltaLongitudeRad / 2.0)
    val normalizedHalfChord = halfChord.coerceIn(0.0, 1.0)
    val angularDistance = 2.0 * atan2(
        sqrt(normalizedHalfChord),
        sqrt(1.0 - normalizedHalfChord)
    )
    return 6371.0 * angularDistance
}
