package com.example.photomap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photomap.BuildConfig
import com.example.photomap.ui.map.PhotoMapScreen
import com.example.photomap.ui.permissions.PhotoAccessRoute
import com.example.photomap.ui.permissions.PhotoAccessViewModel
import com.example.photomap.ui.settings.PhotoMapSettingsScreen

@Composable
fun PhotoMapApp(
    viewModel: PhotoAccessViewModel = viewModel()
) {
    var screenStack by rememberSaveable { mutableStateOf(listOf(AppScreen.Map.name)) }
    val state by viewModel.uiState.collectAsState()
    val currentScreen = AppScreen.valueOf(screenStack.last())

    fun navigateTo(screen: AppScreen) {
        if (currentScreen != screen) {
            screenStack = screenStack + screen.name
        }
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        }
    }

    BackHandler(enabled = screenStack.size > 1) {
        navigateBack()
    }

    when (currentScreen) {
        AppScreen.Photos -> PhotoAccessRoute(
            viewModel = viewModel,
            onOpenMap = { navigateTo(AppScreen.Map) },
            onOpenAppSettings = { navigateTo(AppScreen.Settings) }
        )

        AppScreen.Map -> PhotoMapScreen(
            photos = state.photos,
            mapItems = state.visibleMapItems,
            mapStyleUrl = BuildConfig.MAP_STYLE_URL,
            clusterSettings = state.clusterSettings,
            showDebugPanel = state.showMapDebugPanel,
            isScanning = state.isLoading,
            isScanPaused = state.isScanPaused,
            scanProcessed = state.scanProcessed,
            scanTotal = state.scanTotal,
            onBack = { navigateBack() },
            onScan = { viewModel.scanPhotos() },
            onPause = viewModel::pauseCurrentAction,
            onResume = viewModel::resumeCurrentAction,
            onCancel = viewModel::cancelCurrentAction,
            onOpenSettings = { navigateTo(AppScreen.Settings) },
            onViewportChanged = viewModel::onMapViewportChanged
        )

        AppScreen.Settings -> PhotoMapSettingsScreen(
            state = state,
            onBack = { navigateBack() },
            onScan = { viewModel.scanPhotos() },
            onScanWithExif = viewModel::scanPhotosWithExif,
            onPause = viewModel::pauseCurrentAction,
            onResume = viewModel::resumeCurrentAction,
            onCancel = viewModel::cancelCurrentAction,
            onDecreaseClusterRadius = viewModel::decreaseClusterRadius,
            onIncreaseClusterRadius = viewModel::increaseClusterRadius,
            onDecreaseClusterMinPoints = viewModel::decreaseClusterMinPoints,
            onIncreaseClusterMinPoints = viewModel::increaseClusterMinPoints,
            onDecreaseClusterLeavesPageSize = viewModel::decreaseClusterLeavesPageSize,
            onIncreaseClusterLeavesPageSize = viewModel::increaseClusterLeavesPageSize,
            onDecreaseClusterMaxDistance = viewModel::decreaseClusterMaxDistance,
            onIncreaseClusterMaxDistance = viewModel::increaseClusterMaxDistance,
            onDecreaseClusterDensityCoefficient = viewModel::decreaseClusterDensityCoefficient,
            onIncreaseClusterDensityCoefficient = viewModel::increaseClusterDensityCoefficient,
            onDecreaseClusterMarkerScale = viewModel::decreaseClusterMarkerScale,
            onIncreaseClusterMarkerScale = viewModel::increaseClusterMarkerScale,
            onDecreaseThumbnailCellSize = viewModel::decreaseThumbnailCellSize,
            onIncreaseThumbnailCellSize = viewModel::increaseThumbnailCellSize,
            onDecreaseMaxVisibleThumbnails = viewModel::decreaseMaxVisibleThumbnails,
            onIncreaseMaxVisibleThumbnails = viewModel::increaseMaxVisibleThumbnails,
            onDecreaseThumbnailPreloadPadding = viewModel::decreaseThumbnailPreloadPadding,
            onIncreaseThumbnailPreloadPadding = viewModel::increaseThumbnailPreloadPadding,
            onSetMapDebugPanelVisible = viewModel::setMapDebugPanelVisible
        )
    }
}

private enum class AppScreen {
    Photos,
    Map,
    Settings
}
