package com.example.photomap.ui.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photomap.core.permissions.PhotoPermissionManager
import com.example.photomap.data.media.AndroidMediaStorePhotoReader
import com.example.photomap.data.media.DevicePhotoReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoAccessViewModel(application: Application) : AndroidViewModel(application) {
    private val photoReader: DevicePhotoReader = AndroidMediaStorePhotoReader(application)

    private val _uiState = MutableStateFlow(
        PhotoAccessUiState(
            permissionStatus = PhotoPermissionManager.checkStatus(application)
        )
    )
    val uiState: StateFlow<PhotoAccessUiState> = _uiState

    init {
        refreshPermissions(scanWhenAllowed = true)
    }

    fun onPermissionsRequested() {
        _uiState.update { state ->
            state.copy(hasRequestedPermissions = true)
        }
    }

    fun onPermissionResult() {
        refreshPermissions(scanWhenAllowed = true)
    }

    fun refreshPermissions(scanWhenAllowed: Boolean) {
        val status = PhotoPermissionManager.checkStatus(getApplication())
        _uiState.update { state ->
            state.copy(
                permissionStatus = status,
                errorMessage = null
            )
        }

        if (scanWhenAllowed && status.canReadImages) {
            scanPhotos()
        }
    }

    fun scanPhotos() {
        val status = PhotoPermissionManager.checkStatus(getApplication())
        if (!status.canReadImages) {
            _uiState.update { state ->
                state.copy(
                    permissionStatus = status,
                    isLoading = false,
                    errorMessage = "Нет доступа к фотографиям"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    permissionStatus = status,
                    isLoading = true,
                    errorMessage = null
                )
            }

            val photos = photoReader.readPhotos()

            _uiState.update { state ->
                state.copy(
                    permissionStatus = PhotoPermissionManager.checkStatus(getApplication()),
                    photos = photos,
                    isLoading = false
                )
            }
        }
    }
}
