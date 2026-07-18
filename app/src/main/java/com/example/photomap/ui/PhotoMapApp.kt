package com.example.photomap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.photomap.BuildConfig
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.matchesPhotoDateFilter
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.TripMapMarker
import com.example.photomap.ui.home.AllPlacesScreen
import com.example.photomap.ui.home.HomePlaceCardUiModel
import com.example.photomap.ui.home.HomeScreen
import com.example.photomap.ui.home.PlaceDetailsScreen
import com.example.photomap.ui.home.buildHomePlaceModels
import com.example.photomap.ui.home.photosForHomePlace
import com.example.photomap.ui.map.PhotoMapScreen
import com.example.photomap.ui.permissions.PhotoAccessRoute
import com.example.photomap.ui.permissions.PhotoAccessViewModel
import com.example.photomap.ui.settings.PhotoMapSettingsScreen
import com.example.photomap.ui.trips.TripDetailsScreen
import com.example.photomap.ui.trips.TripMapCameraState
import com.example.photomap.ui.trips.TripMapScreen
import com.example.photomap.ui.trips.toFocusedTripMapCameraState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoMapApp(
    viewModel: PhotoAccessViewModel = viewModel()
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var focusedTripId by remember { mutableStateOf<Long?>(null) }
    var mapCameraState by remember { mutableStateOf<TripMapCameraState?>(null) }
    var mapFocusPhotoIds by remember { mutableStateOf<Set<Long>?>(null) }
    var mapFocusRequestKey by remember { mutableStateOf(0) }
    var mapDateFilterPanelRequestKey by remember { mutableStateOf(0) }
    var tripMapCameraState by remember { mutableStateOf<TripMapCameraState?>(null) }

    LaunchedEffect(state.permissionStatus.canReadImages, currentRoute) {
        if (!state.permissionStatus.canReadImages &&
            currentRoute != null &&
            currentRoute in Routes.PermissionRequiredRoutes
        ) {
            navController.navigate(Routes.Photos) {
                launchSingleTop = true
                popUpTo(Routes.Home) {
                    inclusive = false
                }
            }
        }
    }

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    fun navigateBackOrMap() {
        if (!navController.popBackStack()) {
            navController.navigate(Routes.Home) {
                launchSingleTop = true
            }
        }
    }

    fun focusTripOnTripMap(tripId: Long) {
        focusedTripId = tripId
        state.tripMarkers
            .firstOrNull { marker -> marker.tripId == tripId }
            ?.toFocusedTripMapCameraState()
            ?.let { cameraState -> tripMapCameraState = cameraState }
    }

    fun openTripsFromMap() {
        focusedTripId = null
        mapCameraState?.let { cameraState -> tripMapCameraState = cameraState }
        navigateSingleTop(Routes.Trips)
    }

    fun openPlaceOnMap(placePhotos: List<DevicePhoto>) {
        val photoIds = placePhotos
            .filter { photo -> photo.hasLocation }
            .map { photo -> photo.mediaId }
            .toSet()
        if (photoIds.isNotEmpty()) {
            mapFocusPhotoIds = photoIds
            mapFocusRequestKey += 1
        } else {
            mapFocusPhotoIds = null
        }
        if (state.dateFilter.isActive) {
            viewModel.resetMapDateFilter()
        }
        navigateSingleTop(Routes.Map)
    }

    fun openMapForDateFilterDay(day: Long) {
        mapFocusPhotoIds = null
        viewModel.setMapDateFilter(day, day)
        mapDateFilterPanelRequestKey += 1
        navigateSingleTop(Routes.Map)
    }

    fun openTripDetails(tripId: Long) {
        focusTripOnTripMap(tripId)
        navController.navigate(Routes.tripDetails(tripId))
    }

    val photos = state.photos
    val mapPhotos = remember(photos, state.dateFilter) {
        photos.filter { photo -> photo.matchesPhotoDateFilter(state.dateFilter) }
    }
    val homePlaces by produceState(
        initialValue = emptyList<HomePlaceCardUiModel>(),
        photos,
        state.tripMarkers,
        state.tripPhotoIdsByTripId
    ) {
        value = withContext(Dispatchers.Default) {
            buildHomePlaceModels(
                photos = photos,
                trips = state.tripMarkers,
                tripPhotoIdsByTripId = state.tripPhotoIdsByTripId
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Home
    ) {
        composable(Routes.Photos) {
            PhotoAccessRoute(
                viewModel = viewModel,
                onOpenMap = { navigateSingleTop(Routes.Home) },
                onOpenAppSettings = { navigateSingleTop(Routes.Settings) }
            )
        }

        composable(Routes.Home) {
            HomeScreen(
                state = state,
                places = homePlaces,
                onOpenMap = {
                    mapFocusPhotoIds = null
                    navigateSingleTop(Routes.Map)
                },
                onOpenMapForDay = { day -> openMapForDateFilterDay(day) },
                onOpenAllTrips = { openTripsFromMap() },
                onOpenTrip = { tripId -> openTripDetails(tripId) },
                onOpenAllPlaces = { navigateSingleTop(Routes.Places) },
                onOpenPlace = { placeId -> navController.navigate(Routes.placeDetails(placeId)) },
                onOpenSettings = { navigateSingleTop(Routes.Settings) },
                onRefresh = { viewModel.scanPhotos() }
            )
        }

        composable(Routes.Map) {
            PhotoMapScreen(
                photos = mapPhotos,
                mapItems = state.visibleMapItems,
                tripHeatCells = state.visibleTripHeatCells,
                mapStyleUrl = BuildConfig.MAP_STYLE_URL,
                clusterSettings = state.clusterSettings,
                dateFilter = state.dateFilter,
                showDebugPanel = state.showMapDebugPanel,
                isScanning = state.isLoading,
                isScanPaused = state.isScanPaused,
                scanProcessed = state.scanProcessed,
                scanTotal = state.scanTotal,
                onPause = viewModel::pauseCurrentAction,
                onResume = viewModel::resumeCurrentAction,
                onCancel = viewModel::cancelCurrentAction,
                onOpenSettings = { navigateSingleTop(Routes.Settings) },
                onDateFilterChanged = viewModel::setMapDateFilter,
                onDateFilterReset = viewModel::resetMapDateFilter,
                openDateFilterRequestKey = mapDateFilterPanelRequestKey,
                focusPhotoIds = mapFocusPhotoIds,
                focusRequestKey = mapFocusRequestKey,
                onCameraStateChanged = { cameraState -> mapCameraState = cameraState },
                onViewportChanged = viewModel::onMapViewportChanged
            )
        }

        composable(Routes.Places) {
            AllPlacesScreen(
                places = homePlaces,
                onBack = { navigateBackOrMap() },
                onOpenPlace = { placeId -> navController.navigate(Routes.placeDetails(placeId)) }
            )
        }

        composable(
            route = Routes.PlaceDetails,
            arguments = listOf(navArgument(Routes.PlaceIdArgument) { type = NavType.StringType })
        ) { entry ->
            val placeId = entry.arguments?.getString(Routes.PlaceIdArgument).orEmpty()
            val place = homePlaces.firstOrNull { homePlace -> homePlace.id == placeId }
            val placePhotos = remember(photos, state.tripMarkers, state.tripPhotoIdsByTripId, placeId) {
                photosForHomePlace(
                    photos = photos,
                    placeId = placeId,
                    trips = state.tripMarkers,
                    tripPhotoIdsByTripId = state.tripPhotoIdsByTripId
                )
            }
            PlaceDetailsScreen(
                place = place?.copy(photos = placePhotos),
                onOpenMap = { openPlaceOnMap(placePhotos) }
            )
        }

        composable(Routes.Settings) {
            PhotoMapSettingsScreen(
                state = state,
                onBack = { navigateBackOrMap() },
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

        composable(Routes.Trips) {
            TripMapScreen(
                tripMarkers = state.tripMarkers,
                tripHeatCells = state.visibleTripHeatCells,
                photos = state.photos,
                mapStyleUrl = BuildConfig.TRIP_MAP_STYLE_URL,
                focusedTripId = focusedTripId,
                initialCameraState = tripMapCameraState,
                isSegmenting = state.isTripSegmentationRunning,
                onBack = { navigateBackOrMap() },
                onRefreshTrips = { viewModel.rebuildTrips() },
                onCameraStateChanged = { cameraState -> tripMapCameraState = cameraState },
                onViewportChanged = viewModel::onTripHeatmapViewportChanged,
                onFocusTrip = { tripId -> focusTripOnTripMap(tripId) },
                onOpenTrip = { tripId -> openTripDetails(tripId) }
            )
        }

        composable(
            route = Routes.TripDetails,
            arguments = listOf(navArgument(Routes.TripIdArgument) { type = NavType.LongType })
        ) { entry ->
            val tripId = entry.arguments?.getLong(Routes.TripIdArgument) ?: 0L
            val tripMarker = state.tripMarkers.firstOrNull { marker -> marker.tripId == tripId }
            val tripPhotos = photosForTrip(
                photos = state.photos,
                orderedTripPhotoIds = state.tripPhotoIdsByTripId[tripId].orEmpty(),
                marker = tripMarker
            )
            TripDetailsScreen(
                tripMarker = tripMarker,
                photos = tripPhotos,
                mapStyleUrl = BuildConfig.TRIP_MAP_STYLE_URL,
                onBack = { navigateBackOrMap() }
            )
        }
    }
}

private object Routes {
    const val Home = "home"
    const val Photos = "photos"
    const val Map = "map"
    const val Settings = "settings"
    const val Trips = "trips"
    const val Places = "places"
    const val TripIdArgument = "tripId"
    const val TripDetails = "trip/{$TripIdArgument}"
    const val PlaceIdArgument = "placeId"
    const val PlaceDetails = "place/{$PlaceIdArgument}"

    val PermissionRequiredRoutes = setOf(Home, Map, Trips, TripDetails, Places, PlaceDetails)

    fun tripDetails(tripId: Long): String {
        return "trip/$tripId"
    }

    fun placeDetails(placeId: String): String {
        return "place/$placeId"
    }
}

private fun photosForTrip(
    photos: List<DevicePhoto>,
    orderedTripPhotoIds: List<Long>,
    marker: TripMapMarker?
): List<DevicePhoto> {
    if (orderedTripPhotoIds.isNotEmpty()) {
        val orderById = orderedTripPhotoIds
            .withIndex()
            .associate { indexedValue -> indexedValue.value to indexedValue.index }
        return photos
            .filter { photo -> photo.mediaId in orderById }
            .sortedBy { photo -> orderById[photo.mediaId] ?: Int.MAX_VALUE }
    }

    if (marker == null) {
        return emptyList()
    }

    return photos
        .filter { photo ->
            val day = photo.tripDateDay() ?: return@filter false
            photo.hasLocation && day in marker.startDay..marker.endDay
        }
        .sortedWith(
            compareBy<DevicePhoto> { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
                .thenBy { photo -> photo.mediaId }
        )
}

private fun DevicePhoto.tripDateDay(): Long? {
    return photoDateMillis()?.let { millis -> Math.floorDiv(millis, MillisPerDay) }
}

private const val MillisPerDay = 24L * 60L * 60L * 1000L
