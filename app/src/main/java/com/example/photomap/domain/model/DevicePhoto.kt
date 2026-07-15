package com.example.photomap.domain.model

data class DevicePhoto(
    val mediaId: Long,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val dateTaken: Long?,
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val orientation: Int?
)
