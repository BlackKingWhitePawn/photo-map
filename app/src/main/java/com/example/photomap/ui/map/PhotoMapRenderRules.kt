package com.example.photomap.ui.map

internal fun isPhotoMapCluster(photoCount: Int): Boolean {
    return photoCount > 1
}

internal fun selectRepresentativePhotoId(
    coverPhotoId: Long?,
    photoIds: List<Long>
): Long? {
    return coverPhotoId ?: photoIds.firstOrNull()
}

internal fun selectTappablePhotoIds(
    coverPhotoId: Long?,
    photoIds: List<Long>
): List<Long> {
    return if (photoIds.isNotEmpty()) {
        photoIds
    } else {
        coverPhotoId?.let(::listOf).orEmpty()
    }
}
