package com.example.photomap.ui.permissions

import com.example.photomap.core.permissions.PhotoAccessLevel
import com.example.photomap.core.permissions.PhotoPermissionStatus
import com.example.photomap.domain.model.DevicePhoto

data class PhotoAccessUiState(
    val permissionStatus: PhotoPermissionStatus = PhotoPermissionStatus(
        accessLevel = PhotoAccessLevel.None,
        canReadImages = false,
        canReadOriginalLocation = false
    ),
    val photos: List<DevicePhoto> = emptyList(),
    val photosWithLocationCount: Int = 0,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val scanProcessed: Int = 0,
    val scanTotal: Int = 0,
    val hasRequestedPermissions: Boolean = false,
    val errorMessage: String? = null
)
