package com.example.photomap.ui.permissions

import com.example.photomap.core.permissions.PhotoAccessLevel
import com.example.photomap.core.permissions.PhotoPermissionStatus
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.data.cluster.VisiblePhotoMapItem
import com.example.photomap.data.heatmap.VisibleTripHeatCell
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.trip.TripMapMarker

data class PhotoAccessUiState(
    val permissionStatus: PhotoPermissionStatus = PhotoPermissionStatus(
        accessLevel = PhotoAccessLevel.None,
        canReadImages = false,
        canReadOriginalLocation = false
    ),
    val photos: List<DevicePhoto> = emptyList(),
    val photosWithLocationCount: Int = 0,
    val indexedLocationScannedCount: Int = 0,
    val indexedPhotoCount: Int = 0,
    val visibleMapItems: List<VisiblePhotoMapItem> = emptyList(),
    val visibleMapLevel: Int = 0,
    val visibleTripHeatCells: List<VisibleTripHeatCell> = emptyList(),
    val visibleTripHeatResolution: Int = 0,
    val tripHeatmapDataVersion: Long = 0L,
    val tripMarkers: List<TripMapMarker> = emptyList(),
    val tripPhotoIdsByTripId: Map<Long, List<Long>> = emptyMap(),
    val isTripSegmentationRunning: Boolean = false,
    val isLoading: Boolean = false,
    val isScanPaused: Boolean = false,
    val loadingMessage: String? = null,
    val scanProcessed: Int = 0,
    val scanTotal: Int = 0,
    val clusterSettings: PhotoClusterSettings = PhotoClusterSettings(),
    val dateFilter: PhotoDateFilter = PhotoDateFilter(),
    val showMapDebugPanel: Boolean = false,
    val hasRequestedPermissions: Boolean = false,
    val errorMessage: String? = null
)
