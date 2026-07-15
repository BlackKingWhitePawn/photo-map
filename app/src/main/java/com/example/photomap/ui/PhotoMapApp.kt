package com.example.photomap.ui

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

@Composable
fun PhotoMapApp(
    viewModel: PhotoAccessViewModel = viewModel()
) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Photos) }
    val state by viewModel.uiState.collectAsState()

    when (currentScreen) {
        AppScreen.Photos -> PhotoAccessRoute(
            viewModel = viewModel,
            onOpenMap = { currentScreen = AppScreen.Map }
        )

        AppScreen.Map -> PhotoMapScreen(
            photos = state.photos,
            mapStyleUrl = BuildConfig.MAP_STYLE_URL,
            onBack = { currentScreen = AppScreen.Photos },
            onScan = viewModel::scanPhotos
        )
    }
}

private enum class AppScreen {
    Photos,
    Map
}
