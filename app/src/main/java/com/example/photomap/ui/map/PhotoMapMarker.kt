package com.example.photomap.ui.map

import com.example.photomap.domain.model.DevicePhoto
import org.maplibre.android.geometry.LatLng

data class PhotoMapMarker(
    val id: String,
    val title: String,
    val snippet: String,
    val position: LatLng,
    val count: Int,
    val thumbnailUri: String?,
    val type: PhotoMapMarkerType,
    val photos: List<DevicePhoto>,
    val photoPositions: List<LatLng>
)

enum class PhotoMapMarkerType {
    Heat,
    Thumbnail,
    SinglePhoto
}
