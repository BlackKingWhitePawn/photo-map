package com.example.photomap.ui.permissions

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photomap.core.permissions.PhotoPermissionManager
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.MAX_PHOTO_MAX_VISIBLE_THUMBNAILS
import com.example.photomap.core.settings.MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX
import com.example.photomap.core.settings.MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.MIN_PHOTO_MAX_VISIBLE_THUMBNAILS
import com.example.photomap.core.settings.MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX
import com.example.photomap.core.settings.MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
import com.example.photomap.core.settings.PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP
import com.example.photomap.core.settings.PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP
import com.example.photomap.core.settings.PHOTO_CLUSTER_MARKER_SCALE_PERCENT
import com.example.photomap.core.settings.PHOTO_CLUSTER_MARKER_SCALE_PERCENT_STEP
import com.example.photomap.core.settings.PHOTO_CLUSTER_MAX_DISTANCE_KM
import com.example.photomap.core.settings.PHOTO_CLUSTER_MAX_DISTANCE_KM_STEP
import com.example.photomap.core.settings.PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.PHOTO_CLUSTER_RADIUS_STEP
import com.example.photomap.core.settings.PHOTO_MAX_VISIBLE_THUMBNAILS
import com.example.photomap.core.settings.PHOTO_MAX_VISIBLE_THUMBNAILS_STEP
import com.example.photomap.core.settings.PHOTO_THUMBNAIL_CELL_SIZE_PX
import com.example.photomap.core.settings.PHOTO_THUMBNAIL_CELL_SIZE_PX_STEP
import com.example.photomap.core.settings.PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
import com.example.photomap.core.settings.PHOTO_THUMBNAIL_PRELOAD_PADDING_PX_STEP
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.data.cluster.PhotoClusterStore
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.cluster.clusterLevelForZoom
import com.example.photomap.data.media.AndroidMediaStorePhotoReader
import com.example.photomap.data.media.DevicePhotoReader
import com.example.photomap.data.media.PhotoReadControl
import com.example.photomap.domain.model.DevicePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoAccessViewModel(application: Application) : AndroidViewModel(application) {
    private val photoReader: DevicePhotoReader = AndroidMediaStorePhotoReader(application)
    private val clusterStore = PhotoClusterStore(application)
    private val settings = application.getSharedPreferences(SettingsName, Context.MODE_PRIVATE)
    private val scanPauseState = MutableStateFlow(false)
    private var scanJob: Job? = null
    private var loadedClusterBounds: PhotoMapBounds? = null
    private var loadedClusterLevel: Int? = null
    private var lastViewportBounds: PhotoMapBounds? = null
    private var lastViewportZoom: Double? = null

    private val _uiState = MutableStateFlow(
        PhotoAccessUiState(
            permissionStatus = PhotoPermissionManager.checkStatus(application),
            clusterSettings = readClusterSettings()
        )
    )
    val uiState: StateFlow<PhotoAccessUiState> = _uiState

    init {
        refreshIndexedPhotos()
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
        if (scanJob?.isActive == true) {
            return
        }

        val status = PhotoPermissionManager.checkStatus(getApplication())
        if (!status.canReadImages) {
            _uiState.update { state ->
                state.copy(
                    permissionStatus = status,
                    isLoading = false,
                    isScanPaused = false,
                    loadingMessage = null,
                    errorMessage = "Нет доступа к фотографиям"
                )
            }
            return
        }

        scanPauseState.value = false
        scanJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    permissionStatus = status,
                    isLoading = true,
                    isScanPaused = false,
                    loadingMessage = if (readExifLocation) {
                        "Ищем GPS-координаты в EXIF. На большой галерее это может занять несколько минут."
                    } else {
                        "Читаем список фотографий из MediaStore"
                    },
                    scanProcessed = 0,
                    scanTotal = 0,
                    errorMessage = null
                )
            }

            try {
                val result = photoReader.readPhotos(
                    readExifLocation = readExifLocation,
                    scanControl = object : PhotoReadControl {
                        override suspend fun awaitIfPaused() {
                            scanPauseState.first { paused -> !paused }
                        }
                    },
                    onBatchIndexed = { batch -> mergeIndexedBatch(batch) }
                ) { progress ->
                    _uiState.update { state ->
                        state.copy(
                            scanProcessed = progress.processed,
                            scanTotal = progress.total,
                            indexedLocationScannedCount = progress.indexedLocationScanned,
                            indexedPhotoCount = progress.indexedTotal
                        )
                    }
                }
                val photos = result.photos.sortedByNewestFirst()
                val resultStats = result.indexStats

                _uiState.update { state ->
                    state.copy(
                        permissionStatus = PhotoPermissionManager.checkStatus(getApplication()),
                        photos = photos,
                        photosWithLocationCount = photos.count { photo -> photo.hasLocation },
                        indexedLocationScannedCount = resultStats.locationScannedCount,
                        indexedPhotoCount = resultStats.totalCount,
                        isLoading = false,
                        isScanPaused = false,
                        loadingMessage = null
                    )
                }
                rebuildClusters(photos)
            } catch (cancellationException: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isScanPaused = false,
                        loadingMessage = null
                    )
                }
            } catch (exception: RuntimeException) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isScanPaused = false,
                        loadingMessage = null,
                        errorMessage = "Не удалось завершить сканирование"
                    )
                }
            } finally {
                scanPauseState.value = false
                scanJob = null
            }
        }
    }

    fun scanPhotosWithExif() {
        scanPhotos(readExifLocation = true)
    }

    fun pauseCurrentAction() {
        if (!uiState.value.isLoading) {
            return
        }

        scanPauseState.value = true
        _uiState.update { state ->
            state.copy(isScanPaused = true)
        }
    }

    fun resumeCurrentAction() {
        scanPauseState.value = false
        _uiState.update { state ->
            state.copy(isScanPaused = false)
        }
    }

    fun cancelCurrentAction() {
        scanJob?.cancel()
        scanPauseState.value = false
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isScanPaused = false,
                loadingMessage = null,
                errorMessage = null
            )
        }
    }

    fun increaseClusterRadius() {
        updateClusterSettings {
            copy(radiusPx = radiusPx + PHOTO_CLUSTER_RADIUS_STEP)
        }
    }

    fun decreaseClusterRadius() {
        updateClusterSettings {
            copy(radiusPx = radiusPx - PHOTO_CLUSTER_RADIUS_STEP)
        }
    }

    fun increaseClusterMinPoints() {
        updateClusterSettings {
            copy(minPoints = minPoints + 1)
        }
    }

    fun decreaseClusterMinPoints() {
        updateClusterSettings {
            copy(minPoints = minPoints - 1)
        }
    }

    fun increaseClusterLeavesPageSize() {
        updateClusterSettings {
            copy(leavesPageSize = leavesPageSize + PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP)
        }
    }

    fun decreaseClusterLeavesPageSize() {
        updateClusterSettings {
            copy(leavesPageSize = leavesPageSize - PHOTO_CLUSTER_LEAVES_PAGE_SIZE_STEP)
        }
    }

    fun increaseClusterMaxDistance() {
        updateClusterSettings {
            copy(maxDistanceKm = maxDistanceKm + PHOTO_CLUSTER_MAX_DISTANCE_KM_STEP)
        }
    }

    fun decreaseClusterMaxDistance() {
        updateClusterSettings {
            copy(maxDistanceKm = maxDistanceKm - PHOTO_CLUSTER_MAX_DISTANCE_KM_STEP)
        }
    }

    fun increaseClusterDensityCoefficient() {
        updateClusterSettings {
            copy(densityCoefficientPercent = densityCoefficientPercent + PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP)
        }
    }

    fun decreaseClusterDensityCoefficient() {
        updateClusterSettings {
            copy(densityCoefficientPercent = densityCoefficientPercent - PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP)
        }
    }

    fun increaseClusterMarkerScale() {
        updateClusterSettings {
            copy(markerScalePercent = markerScalePercent + PHOTO_CLUSTER_MARKER_SCALE_PERCENT_STEP)
        }
    }

    fun decreaseClusterMarkerScale() {
        updateClusterSettings {
            copy(markerScalePercent = markerScalePercent - PHOTO_CLUSTER_MARKER_SCALE_PERCENT_STEP)
        }
    }

    fun increaseThumbnailCellSize() {
        updateClusterSettings {
            copy(thumbnailCellSizePx = thumbnailCellSizePx + PHOTO_THUMBNAIL_CELL_SIZE_PX_STEP)
        }
    }

    fun decreaseThumbnailCellSize() {
        updateClusterSettings {
            copy(thumbnailCellSizePx = thumbnailCellSizePx - PHOTO_THUMBNAIL_CELL_SIZE_PX_STEP)
        }
    }

    fun increaseMaxVisibleThumbnails() {
        updateClusterSettings {
            copy(maxVisibleThumbnails = maxVisibleThumbnails + PHOTO_MAX_VISIBLE_THUMBNAILS_STEP)
        }
    }

    fun decreaseMaxVisibleThumbnails() {
        updateClusterSettings {
            copy(maxVisibleThumbnails = maxVisibleThumbnails - PHOTO_MAX_VISIBLE_THUMBNAILS_STEP)
        }
    }

    fun increaseThumbnailPreloadPadding() {
        updateClusterSettings {
            copy(thumbnailPreloadPaddingPx = thumbnailPreloadPaddingPx + PHOTO_THUMBNAIL_PRELOAD_PADDING_PX_STEP)
        }
    }

    fun decreaseThumbnailPreloadPadding() {
        updateClusterSettings {
            copy(thumbnailPreloadPaddingPx = thumbnailPreloadPaddingPx - PHOTO_THUMBNAIL_PRELOAD_PADDING_PX_STEP)
        }
    }

    fun onMapViewportChanged(bounds: PhotoMapBounds, zoom: Double) {
        lastViewportBounds = bounds
        lastViewportZoom = zoom
        val requestedLevel = clusterLevelForZoom(zoom)
        val currentLoadedBounds = loadedClusterBounds
        if (loadedClusterLevel == requestedLevel && currentLoadedBounds?.contains(bounds) == true) {
            return
        }

        viewModelScope.launch {
            val content = clusterStore.loadVisibleMapContent(
                bounds = bounds,
                zoom = zoom,
                settings = uiState.value.clusterSettings
            )
            loadedClusterBounds = content.loadedBounds
            loadedClusterLevel = content.level
            _uiState.update { state ->
                state.copy(
                    visibleMapItems = content.items,
                    visibleMapLevel = content.level
                )
            }
        }
    }

    private fun refreshIndexedPhotos() {
        viewModelScope.launch {
            val photos = photoReader.readIndexedPhotos()
            val stats = photoReader.getIndexStats()
            _uiState.update { state ->
                state.copy(
                    photos = photos,
                    photosWithLocationCount = stats.locationCount,
                    indexedLocationScannedCount = stats.locationScannedCount,
                    indexedPhotoCount = stats.totalCount
                )
            }
            clusterStore.rebuildClustersIfOutdated(
                photos = photos,
                settings = uiState.value.clusterSettings
            )
        }
    }

    private fun mergeIndexedBatch(batch: List<DevicePhoto>) {
        if (batch.isEmpty()) {
            return
        }

        _uiState.update { state ->
            val photos = (state.photos + batch).distinctByNewestRevision()
            state.copy(
                photos = photos,
                photosWithLocationCount = photos.count { photo -> photo.hasLocation }
            )
        }
    }

    private fun rebuildClusters(photos: List<DevicePhoto> = uiState.value.photos) {
        loadedClusterBounds = null
        loadedClusterLevel = null
        viewModelScope.launch {
            clusterStore.rebuildClusters(
                photos = photos,
                settings = uiState.value.clusterSettings
            )
            val bounds = lastViewportBounds
            val zoom = lastViewportZoom
            if (bounds != null && zoom != null) {
                val content = clusterStore.loadVisibleMapContent(
                    bounds = bounds,
                    zoom = zoom,
                    settings = uiState.value.clusterSettings
                )
                loadedClusterBounds = content.loadedBounds
                loadedClusterLevel = content.level
                _uiState.update { state ->
                    state.copy(
                        visibleMapItems = content.items,
                        visibleMapLevel = content.level
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        visibleMapItems = emptyList(),
                        visibleMapLevel = 0
                    )
                }
            }
        }
    }

    private companion object {
        const val SettingsName = "photo_map_settings"
        const val ClusterRadiusKey = "cluster_radius_px"
        const val ClusterMinPointsKey = "cluster_min_points"
        const val ClusterLeavesPageSizeKey = "cluster_leaves_page_size"
        const val ClusterMaxDistanceKmKey = "cluster_max_distance_km"
        const val ClusterDensityCoefficientPercentKey = "cluster_density_coefficient_percent"
        const val ClusterMarkerScalePercentKey = "cluster_marker_scale_percent"
        const val ThumbnailCellSizePxKey = "thumbnail_cell_size_px"
        const val MaxVisibleThumbnailsKey = "max_visible_thumbnails"
        const val ThumbnailPreloadPaddingPxKey = "thumbnail_preload_padding_px"
    }

    private fun readClusterSettings(): PhotoClusterSettings {
        return PhotoClusterSettings(
            radiusPx = settings.getInt(ClusterRadiusKey, PHOTO_CLUSTER_RADIUS),
            minPoints = settings.getInt(ClusterMinPointsKey, PHOTO_CLUSTER_MIN_POINTS),
            leavesPageSize = settings.getInt(ClusterLeavesPageSizeKey, PHOTO_CLUSTER_LEAVES_PAGE_SIZE),
            maxDistanceKm = settings.getInt(ClusterMaxDistanceKmKey, PHOTO_CLUSTER_MAX_DISTANCE_KM),
            densityCoefficientPercent = settings.getInt(
                ClusterDensityCoefficientPercentKey,
                PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
            ),
            markerScalePercent = settings.getInt(
                ClusterMarkerScalePercentKey,
                PHOTO_CLUSTER_MARKER_SCALE_PERCENT
            ),
            thumbnailCellSizePx = settings.getInt(ThumbnailCellSizePxKey, PHOTO_THUMBNAIL_CELL_SIZE_PX),
            maxVisibleThumbnails = settings.getInt(MaxVisibleThumbnailsKey, PHOTO_MAX_VISIBLE_THUMBNAILS),
            thumbnailPreloadPaddingPx = settings.getInt(
                ThumbnailPreloadPaddingPxKey,
                PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
            )
        ).normalized()
    }

    private fun updateClusterSettings(transform: PhotoClusterSettings.() -> PhotoClusterSettings) {
        val nextSettings = uiState.value.clusterSettings.transform().normalized()
        settings.edit()
            .putInt(ClusterRadiusKey, nextSettings.radiusPx.coerceIn(MIN_PHOTO_CLUSTER_RADIUS, MAX_PHOTO_CLUSTER_RADIUS))
            .putInt(
                ClusterMinPointsKey,
                nextSettings.minPoints.coerceIn(MIN_PHOTO_CLUSTER_MIN_POINTS, MAX_PHOTO_CLUSTER_MIN_POINTS)
            )
            .putInt(
                ClusterLeavesPageSizeKey,
                nextSettings.leavesPageSize.coerceIn(
                    MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE,
                    MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
                )
            )
            .putInt(
                ClusterMaxDistanceKmKey,
                nextSettings.maxDistanceKm.coerceIn(
                    MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM,
                    MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM
                )
            )
            .putInt(
                ClusterDensityCoefficientPercentKey,
                nextSettings.densityCoefficientPercent.coerceIn(
                    MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT,
                    MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
                )
            )
            .putInt(
                ClusterMarkerScalePercentKey,
                nextSettings.markerScalePercent.coerceIn(
                    MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT,
                    MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
                )
            )
            .putInt(
                ThumbnailCellSizePxKey,
                nextSettings.thumbnailCellSizePx.coerceIn(
                    MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX,
                    MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX
                )
            )
            .putInt(
                MaxVisibleThumbnailsKey,
                nextSettings.maxVisibleThumbnails.coerceIn(
                    MIN_PHOTO_MAX_VISIBLE_THUMBNAILS,
                    MAX_PHOTO_MAX_VISIBLE_THUMBNAILS
                )
            )
            .putInt(
                ThumbnailPreloadPaddingPxKey,
                nextSettings.thumbnailPreloadPaddingPx.coerceIn(
                    MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX,
                    MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
                )
            )
            .apply()
        _uiState.update { state ->
            state.copy(clusterSettings = nextSettings)
        }
        rebuildClusters()
    }
}

private fun List<DevicePhoto>.distinctByNewestRevision(): List<DevicePhoto> {
    val photosById = LinkedHashMap<Long, DevicePhoto>()
    forEach { photo -> photosById[photo.mediaId] = photo }
    return photosById.values.toList().sortedByNewestFirst()
}

private fun List<DevicePhoto>.sortedByNewestFirst(): List<DevicePhoto> {
    return sortedWith(
        compareByDescending<DevicePhoto> { photo ->
            photo.dateTaken ?: photo.dateModified ?: photo.dateAdded ?: 0L
        }.thenByDescending { photo -> photo.mediaId }
    )
}
