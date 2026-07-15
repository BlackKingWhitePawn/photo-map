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
        refreshIndexStats()
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

    fun scanPhotos(readExifLocation: Boolean = false) {
        val status = PhotoPermissionManager.checkStatus(getApplication())
        if (!status.canReadImages) {
            _uiState.update { state ->
                state.copy(
                    permissionStatus = status,
                    isLoading = false,
                    loadingMessage = null,
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
                    loadingMessage = if (readExifLocation) {
                        "Ищем GPS-координаты в EXIF. На большой галерее это может занять несколько минут."
                    } else {
                        "Быстро читаем список фотографий из MediaStore"
                    },
                    scanProcessed = 0,
                    scanTotal = 0,
                    errorMessage = null
                )
            }

            val result = photoReader.readPhotos(readExifLocation = readExifLocation) { progress ->
                _uiState.update { state ->
                    state.copy(
                        scanProcessed = progress.processed,
                        scanTotal = progress.total,
                        indexedLocationScannedCount = progress.indexedLocationScanned,
                        indexedPhotoCount = progress.indexedTotal
                    )
                }
            }
            val resultStats = result.indexStats

            _uiState.update { state ->
                state.copy(
                    permissionStatus = PhotoPermissionManager.checkStatus(getApplication()),
                    photos = result.photos,
                    photosWithLocationCount = result.photos.count { photo -> photo.hasLocation },
                    indexedLocationScannedCount = resultStats.locationScannedCount,
                    indexedPhotoCount = resultStats.totalCount,
                    isLoading = false,
                    loadingMessage = null
                )
            }
        }
    }

    fun scanPhotosWithExif() {
        scanPhotos(readExifLocation = true)
    }

    private fun refreshIndexStats() {
        viewModelScope.launch {
            val stats = photoReader.getIndexStats()
            _uiState.update { state ->
                state.copy(
                    photosWithLocationCount = stats.locationCount,
                    indexedLocationScannedCount = stats.locationScannedCount,
                    indexedPhotoCount = stats.totalCount
                )
            }
        }
    }
}
