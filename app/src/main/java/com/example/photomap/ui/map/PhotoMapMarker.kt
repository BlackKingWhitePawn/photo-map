package com.example.photomap.ui.map

import org.maplibre.android.geometry.LatLng

data class PhotoMapMarker(
    val id: String,
    val title: String,
    val snippet: String,
    val position: LatLng,
    val count: Int,
    val thumbnailUri: String?,
    val type: PhotoMapMarkerType
)

enum class PhotoMapMarkerType {
    Heat,
    Thumbnail
}
