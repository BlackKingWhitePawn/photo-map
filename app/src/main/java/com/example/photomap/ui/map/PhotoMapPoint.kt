package com.example.photomap.ui.map

data class PhotoMapPoint(
    val photoId: Long,
    val contentUri: String,
    val latitude: Double,
    val longitude: Double,
    val takenAt: Long?
)
