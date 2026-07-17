package com.example.photomap.domain.model

data class PhotoDateFilter(
    val minDay: Long? = null,
    val maxDay: Long? = null,
    val startDay: Long? = null,
    val endDay: Long? = null
) {
    val isAvailable: Boolean
        get() = minDay != null && maxDay != null

    val selectedStartDay: Long?
        get() = startDay ?: minDay

    val selectedEndDay: Long?
        get() = endDay ?: maxDay

    val isActive: Boolean
        get() = isAvailable &&
            selectedStartDay != null &&
            selectedEndDay != null &&
            (selectedStartDay != minDay || selectedEndDay != maxDay)

    fun withSelectedDays(start: Long, end: Long): PhotoDateFilter {
        val min = minDay ?: return this
        val max = maxDay ?: return this
        val normalizedStart = minOf(start, end).coerceIn(min, max)
        val normalizedEnd = maxOf(start, end).coerceIn(min, max)
        return copy(
            startDay = normalizedStart,
            endDay = normalizedEnd
        )
    }

    fun reset(): PhotoDateFilter {
        return copy(
            startDay = minDay,
            endDay = maxDay
        )
    }
}

fun buildPhotoDateFilter(
    photos: Collection<DevicePhoto>,
    current: PhotoDateFilter = PhotoDateFilter()
): PhotoDateFilter {
    val days = photos.mapNotNull { photo -> photo.photoDateDay() }
    if (days.isEmpty()) {
        return PhotoDateFilter()
    }

    val min = days.minOrNull() ?: return PhotoDateFilter()
    val max = days.maxOrNull() ?: return PhotoDateFilter()
    val next = PhotoDateFilter(minDay = min, maxDay = max)
    if (!current.isActive) {
        return next.reset()
    }

    val currentStart = current.selectedStartDay ?: min
    val currentEnd = current.selectedEndDay ?: max
    return next.withSelectedDays(currentStart, currentEnd)
}

fun DevicePhoto.matchesPhotoDateFilter(filter: PhotoDateFilter): Boolean {
    if (!filter.isActive) {
        return true
    }

    val day = photoDateDay() ?: return false
    val start = filter.selectedStartDay ?: return true
    val end = filter.selectedEndDay ?: return true
    return day in start..end
}

fun DevicePhoto.photoDateMillis(): Long? {
    return (dateTaken ?: dateModified ?: dateAdded)?.normalizePhotoDateMillis()
}

fun photoDateDayToMillis(day: Long): Long {
    return day * PhotoDateMillisPerDay
}

private fun DevicePhoto.photoDateDay(): Long? {
    return photoDateMillis()?.let { millis -> Math.floorDiv(millis, PhotoDateMillisPerDay) }
}

private fun Long.normalizePhotoDateMillis(): Long? {
    if (this <= 0L) {
        return null
    }

    return if (this < PhotoDateMillisThreshold) this * 1000L else this
}

private const val PhotoDateMillisPerDay = 24L * 60L * 60L * 1000L
private const val PhotoDateMillisThreshold = 100_000_000_000L
