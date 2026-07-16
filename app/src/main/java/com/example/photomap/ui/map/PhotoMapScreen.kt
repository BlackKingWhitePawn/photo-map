package com.example.photomap.ui.map

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.cluster.VisiblePhotoMapItem
import com.example.photomap.domain.model.DevicePhoto
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun PhotoMapScreen(
    photos: List<DevicePhoto>,
    mapItems: List<VisiblePhotoMapItem>,
    mapStyleUrl: String,
    clusterSettings: PhotoClusterSettings,
    showDebugPanel: Boolean,
    isScanning: Boolean,
    isScanPaused: Boolean,
    scanProcessed: Int,
    scanTotal: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onClusterDensityChanged: (Int) -> Unit,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    val normalizedClusterSettings = clusterSettings.normalized()
    val photosWithLocation = remember(photos) {
        photos.filter { photo -> photo.toMapPoint() != null }
    }
    var centerRequestKey by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MapTopBar(
                totalPhotos = photos.size,
                photosWithLocation = photosWithLocation.size,
                isScanning = isScanning,
                isScanPaused = isScanPaused,
                scanProcessed = scanProcessed,
                scanTotal = scanTotal,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onCenterMap = { centerRequestKey += 1 },
                onOpenSettings = onOpenSettings
            )

            if (photosWithLocation.isEmpty() && !isScanning) {
                MapMessage(
                    title = "Нет фотографий с геопозицией",
                    text = "На карте появятся фотографии, в которых сохранены GPS-координаты. Можно повторить сканирование или открыть настройки доступа.",
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    PhotoMapLibreMap(
                        photos = photosWithLocation,
                        mapItems = mapItems,
                        mapStyleUrl = mapStyleUrl,
                        clusterSettings = normalizedClusterSettings,
                        showDebugPanel = showDebugPanel,
                        centerRequestKey = centerRequestKey,
                        onClusterDensityChanged = onClusterDensityChanged,
                        onViewportChanged = onViewportChanged
                    )

                    if (photosWithLocation.isEmpty() && isScanning) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        ) {
                            Text(
                                modifier = Modifier.padding(16.dp),
                                text = "Индексирование уже идёт. Карта доступна, найденные фото появятся здесь по мере обработки.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapTopBar(
    totalPhotos: Int,
    photosWithLocation: Int,
    isScanning: Boolean,
    isScanPaused: Boolean,
    scanProcessed: Int,
    scanTotal: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onCenterMap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$photosWithLocation/$totalPhotos",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                        contentDescription = "GPS",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_preferences),
                            contentDescription = "Настройки"
                        )
                    }
                    IconButton(onClick = onCenterMap) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                            contentDescription = "Центрировать карту"
                        )
                    }
                }
            }
            if (isScanning) {
                Text(
                    text = scanStatusText(isScanPaused, scanProcessed, scanTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = if (isScanPaused) onResume else onPause) {
                        Text(text = if (isScanPaused) "Продолжить" else "Пауза")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text(text = "Отмена")
                    }
                }
            }
        }
    }
}

@Composable
private fun MapMessage(
    title: String,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PhotoMapLibreMap(
    photos: List<DevicePhoto>,
    mapItems: List<VisiblePhotoMapItem>,
    mapStyleUrl: String,
    clusterSettings: PhotoClusterSettings,
    showDebugPanel: Boolean,
    centerRequestKey: Int,
    onClusterDensityChanged: (Int) -> Unit,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val layerColors = PhotoMapLayerColors(
        clusterSmall = colorScheme.primary.toArgb(),
        clusterMedium = colorScheme.tertiary.toArgb(),
        clusterLarge = colorScheme.secondary.toArgb(),
        clusterHuge = colorScheme.error.toArgb(),
        clusterText = colorScheme.onPrimary.toArgb(),
        clusterTextHalo = colorScheme.surface.toArgb(),
        photo = colorScheme.primary.toArgb(),
        photoStroke = colorScheme.surface.toArgb()
    )
    val layerController = remember { PhotoMapLayerController() }
    val thumbnailImageRegistry = remember { StyleImageRegistry(MaxCachedMapThumbnailImages) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var styleGeneration by remember { mutableStateOf(0) }
    var thumbnailRefreshKey by remember { mutableStateOf(0) }
    var cameraMoveRevision by remember { mutableStateOf(0) }
    var hasFitCamera by remember { mutableStateOf(false) }
    var bottomGalleryState by remember { mutableStateOf<BottomGalleryState?>(null) }
    var mapRenderState by remember { mutableStateOf<PhotoMapRenderState>(PhotoMapRenderState.Empty) }
    val photosById = remember(photos) {
        photos.associateBy { photo -> photo.mediaId }
    }
    val latestPhotosById = rememberUpdatedState(photosById)
    val latestClusterSettings = rememberUpdatedState(clusterSettings)
    val latestMapRenderState = rememberUpdatedState(mapRenderState)
    val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)
    val cameraPositions = remember(photos) {
        photos.mapNotNull { photo ->
            photo.toMapPoint()?.let { point -> LatLng(point.latitude, point.longitude) }
        }
    }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                Log.d(PhotoMapLogTag, "Map is ready")
                AppDiagnostics.record(PhotoMapLogTag, "Map is ready")
                map = mapLibreMap
                mapLibreMap.configureGestures()
                mapLibreMap.addOnCameraMoveListener {
                    cameraMoveRevision += 1
                }
                mapLibreMap.addOnCameraIdleListener {
                    thumbnailRefreshKey += 1
                    mapLibreMap.dispatchViewportChanged(latestOnViewportChanged.value)
                }
                mapLibreMap.addOnMapClickListener { point ->
                    runCatching {
                        mapLibreMap.handlePhotoMapClick(
                            point = point,
                            renderState = latestMapRenderState.value,
                            photosById = latestPhotosById.value,
                            clusterSettings = latestClusterSettings.value,
                            onShowPhotos = { state -> bottomGalleryState = state }
                        )
                    }.onFailure { error ->
                        Log.e(PhotoMapLogTag, "Map click handling failed", error)
                        AppDiagnostics.record(PhotoMapLogTag, "Map click handling failed", error)
                    }.getOrDefault(false)
                }
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    Log.d(PhotoMapLogTag, "Map style loaded")
                    AppDiagnostics.record(PhotoMapLogTag, "Map style loaded")
                    runCatching {
                        style.removePoliticalBoundaryLayers()
                    }.onFailure { error ->
                        Log.w(PhotoMapLogTag, "Failed to remove boundary layers", error)
                        AppDiagnostics.record(PhotoMapLogTag, "Failed to remove boundary layers", error)
                    }
                    layerController.reset()
                    thumbnailImageRegistry.clear()
                    isStyleReady = true
                    styleGeneration += 1
                    thumbnailRefreshKey += 1
                    mapLibreMap.dispatchViewportChanged(latestOnViewportChanged.value)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(
        map,
        isStyleReady,
        styleGeneration,
        thumbnailRefreshKey,
        mapItems,
        photosById,
        clusterSettings,
        layerColors
    ) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || mapView.width <= 0 || mapView.height <= 0) {
            return@LaunchedEffect
        }

        val renderState = mapLibreMap.buildPhotoMapRenderState(
            mapItems = mapItems,
            width = mapView.width,
            height = mapView.height,
            settings = clusterSettings
        )
        mapRenderState = renderState
        val visibleThumbnailFeatureCollection = withContext(Dispatchers.Default) {
            PhotoMapFeatureMapper.toFeatureCollection(
                renderState.thumbnailPhotoIds.mapNotNull { photoId -> photosById[photoId] }
            )
        }
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        layerController.update(
            style = style,
            featureCollection = renderState.clusterFeatureCollection,
            settings = clusterSettings,
            colors = layerColors
        )
        layerController.updateVisibleThumbnails(
            style = style,
            featureCollection = visibleThumbnailFeatureCollection
        )
        val missingClusterThumbnailRequests = renderState.clusterThumbnailRequests
            .filter { request -> !thumbnailImageRegistry.contains(request.imageKey) }
            .take(MaxClusterThumbnailImagesPerPass)
        val missingPhotoIds = renderState.thumbnailPhotoIds
            .filter { photoId -> !thumbnailImageRegistry.contains(photoThumbnailImageKey(photoId)) }
            .take(MaxThumbnailImagesPerPass)
        val refreshMessage =
            "Map render refresh: photos=${photosById.size}, clusters=${renderState.clusterCount}, " +
                "singles=${renderState.singleCount}, thumbnails=${renderState.thumbnailPhotoIds.size}, " +
                "missing=${missingPhotoIds.size}, clusterThumbs=${renderState.clusterThumbnailRequests.size}, " +
                "missingClusterThumbs=${missingClusterThumbnailRequests.size}, cached=${thumbnailImageRegistry.size}, " +
                "projected=${renderState.projectedPhotoCount}, baseRadius=${clusterSettings.radiusPx}, " +
                "effectiveRadius=${renderState.effectiveClusterRadiusPx.roundToInt()}, " +
                "zoom=${mapLibreMap.cameraPosition.zoom}, density=${clusterSettings.densityCoefficientPercent}%"
        Log.d(PhotoMapLogTag, refreshMessage)
        AppDiagnostics.record(PhotoMapLogTag, refreshMessage)
        if (missingPhotoIds.isEmpty() && missingClusterThumbnailRequests.isEmpty()) {
            return@LaunchedEffect
        }

        val clusterThumbnailImages = withContext(Dispatchers.IO) {
            missingClusterThumbnailRequests.mapNotNull { request ->
                val photo = photosById[request.representativePhotoId] ?: return@mapNotNull null
                val bitmap = context.createClusterMapThumbnailBitmap(
                    photoId = photo.mediaId,
                    uriString = photo.uri,
                    count = request.photoCount,
                    borderColor = layerColors.photoStroke,
                    backgroundColor = layerColors.photo
                ) ?: return@mapNotNull null
                request.imageKey to bitmap
            }
        }
        val thumbnailImages = withContext(Dispatchers.IO) {
            missingPhotoIds.mapNotNull { photoId ->
                val photo = photosById[photoId] ?: return@mapNotNull null
                val bitmap = context.createFixedMapThumbnailBitmap(
                    photoId = photoId,
                    uriString = photo.uri,
                    borderColor = layerColors.photoStroke,
                    backgroundColor = layerColors.photo
                ) ?: return@mapNotNull null
                photoThumbnailImageKey(photoId) to bitmap
            }
        }
        var addedCount = 0
        (clusterThumbnailImages + thumbnailImages).forEach { (key, bitmap) ->
            val added = runCatching {
                style.addImage(key, bitmap)
                thumbnailImageRegistry.markAdded(style, key)
            }.onFailure { error ->
                Log.w(PhotoMapLogTag, "Failed to add thumbnail image to style: key=$key", error)
                AppDiagnostics.record(PhotoMapLogTag, "Failed to add thumbnail image to style: key=$key", error)
            }
            if (added.isSuccess) {
                addedCount += 1
            }
        }
        val registeredMessage =
            "Thumbnail images registered: added=$addedCount, decoded=${thumbnailImages.size}, " +
                "clusterDecoded=${clusterThumbnailImages.size}, cached=${thumbnailImageRegistry.size}"
        Log.d(PhotoMapLogTag, registeredMessage)
        AppDiagnostics.record(PhotoMapLogTag, registeredMessage)
        if (addedCount > 0) {
            runCatching {
                layerController.update(
                    style = style,
                    featureCollection = renderState.clusterFeatureCollection,
                    settings = clusterSettings,
                    colors = layerColors
                )
                layerController.updateVisibleThumbnails(
                    style = style,
                    featureCollection = visibleThumbnailFeatureCollection
                )
            }.onFailure { error ->
                Log.w(PhotoMapLogTag, "Failed to refresh thumbnails after image registration", error)
                AppDiagnostics.record(PhotoMapLogTag, "Failed to refresh thumbnails after image registration", error)
            }
        }
    }

    LaunchedEffect(
        map,
        isStyleReady,
        styleGeneration,
        cameraMoveRevision,
        mapItems,
        clusterSettings
    ) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || mapView.width <= 0 || mapView.height <= 0) {
            return@LaunchedEffect
        }

        mapRenderState = mapLibreMap.buildPhotoMapRenderState(
            mapItems = mapItems,
            width = mapView.width,
            height = mapView.height,
            settings = clusterSettings
        )
    }

    LaunchedEffect(map, isStyleReady, cameraPositions) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || hasFitCamera || cameraPositions.isEmpty()) {
            return@LaunchedEffect
        }

        mapView.post {
            runCatching {
                mapLibreMap.fitPositions(cameraPositions, MapBoundsPaddingPx)
                hasFitCamera = true
            }.onFailure { error ->
                Log.e(PhotoMapLogTag, "Initial camera fit failed", error)
            }
        }
    }

    LaunchedEffect(centerRequestKey, map, isStyleReady, cameraPositions) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || centerRequestKey == 0) {
            return@LaunchedEffect
        }

        mapView.post {
            runCatching {
                mapLibreMap.fitPositions(cameraPositions, MapBoundsPaddingPx)
            }.onFailure { error ->
                Log.e(PhotoMapLogTag, "Manual camera fit failed", error)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )

        MapMarkerOverlay(
            renderState = mapRenderState,
            photosById = photosById,
            markerScalePercent = clusterSettings.markerScalePercent,
            onMarkerTap = { item ->
                map?.handlePhotoMapRenderItemTap(
                    renderItem = item,
                    photosById = latestPhotosById.value,
                    clusterSettings = latestClusterSettings.value,
                    onShowPhotos = { state -> bottomGalleryState = state }
                ) ?: false
            }
        )

        if (showDebugPanel) {
            ClusterDebugPanel(
                renderState = mapRenderState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bottomGalleryState?.let { state ->
                BottomPhotoGallery(
                    state = state,
                    onLoadMore = {
                        val nextState = state.loadNextPage(
                            photosById = latestPhotosById.value,
                            pageSize = latestClusterSettings.value.leavesPageSize
                        )
                        if (nextState != null) {
                            bottomGalleryState = nextState
                        }
                    },
                    onOpenPhoto = { photo -> context.openPhotoInDefaultGallery(photo) },
                    onZoomToTarget = { target ->
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(target.latitude, target.longitude),
                                target.zoom
                            )
                        )
                    },
                    onClose = { bottomGalleryState = null }
                )
            }
            ClusterDensitySlider(
                clusterSettings = clusterSettings,
                onDensityChanged = onClusterDensityChanged
            )
        }
    }
}

@Composable
private fun MapMarkerOverlay(
    renderState: PhotoMapRenderState,
    photosById: Map<Long, DevicePhoto>,
    markerScalePercent: Int,
    onMarkerTap: (PhotoMapRenderItem) -> Boolean
) {
    val density = LocalDensity.current
    val clusterSize = (44f * markerScalePercent.coerceIn(80, 200) / 100f).dp
    val photoSize = 46.dp
    val clusterSizePx = with(density) { clusterSize.toPx() }
    val photoSizePx = with(density) { photoSize.toPx() }
    val thumbnailIds = remember(renderState) {
        renderState.thumbnailPhotoIds.toSet()
    }
    val clusterItems = remember(renderState) {
        renderState.items.filter { item ->
            item.isCluster && item.isCenterInsideViewport(renderState.viewportWidth, renderState.viewportHeight)
        }
    }
    val photoItems = remember(renderState, thumbnailIds) {
        renderState.items.filter { item ->
            !item.isCluster &&
                item.isCenterInsideViewport(renderState.viewportWidth, renderState.viewportHeight) &&
                item.representativePhotoId?.let { photoId -> photoId in thumbnailIds } == true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        clusterItems.forEach { item ->
            key(item.id) {
                val representativePhoto = item.representativePhotoId?.let { photoId -> photosById[photoId] }
                MapClusterMarker(
                    item = item,
                    photo = representativePhoto,
                    sizePx = clusterSizePx,
                    onClick = { onMarkerTap(item) },
                    modifier = Modifier
                        .offset {
                            markerOffset(
                                screenX = item.screenX,
                                screenY = item.screenY,
                                markerSizePx = clusterSizePx,
                                viewportWidth = renderState.viewportWidth,
                                viewportHeight = renderState.viewportHeight
                            )
                        }
                        .size(clusterSize)
                )
            }
        }
        photoItems.forEach { item ->
            key(item.id) {
                val photo = item.representativePhotoId?.let { photoId -> photosById[photoId] }
                if (photo != null) {
                    MapPhotoMarker(
                        photo = photo,
                        onClick = { onMarkerTap(item) },
                        modifier = Modifier
                            .offset {
                            markerOffset(
                                screenX = item.screenX,
                                screenY = item.screenY,
                                markerSizePx = photoSizePx,
                                viewportWidth = renderState.viewportWidth,
                                viewportHeight = renderState.viewportHeight
                            )
                        }
                        .size(photoSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapClusterMarker(
    item: PhotoMapRenderItem,
    photo: DevicePhoto?,
    sizePx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo?.uri) {
        value = if (photo == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                context.loadPhotoPreviewBitmap(
                    uriString = photo.uri,
                    targetSizePx = MapOverlayThumbnailPx,
                    photoId = photo.mediaId
                )
            }
        }
    }
    Box(modifier = modifier.clickable(onClick = onClick)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            border = BorderStroke(2.dp, colorScheme.surface),
            shadowElevation = 4.dp
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = requireNotNull(thumbnail).asImageBitmap(),
                    contentDescription = photo?.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        ClusterCountBadge(
            label = item.photoCount.clusterCountLabel(),
            sizePx = sizePx,
            modifier = if (thumbnail == null) {
                Modifier.align(Alignment.Center)
            } else {
                Modifier.align(Alignment.BottomEnd)
            }
        )
    }
}

@Composable
private fun ClusterCountBadge(
    label: String,
    sizePx: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            text = label,
            style = if (sizePx >= 52f) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MapPhotoMarker(
    photo: DevicePhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            context.loadPhotoPreviewBitmap(
                uriString = photo.uri,
                targetSizePx = MapOverlayThumbnailPx,
                photoId = photo.mediaId
            )
        }
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        shadowElevation = 4.dp
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = requireNotNull(thumbnail).asImageBitmap(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ClusterDebugPanel(
    renderState: PhotoMapRenderState,
    modifier: Modifier = Modifier
) {
    val clusters = remember(renderState) {
        renderState.items.filter { item -> item.isCluster }
    }
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .heightIn(max = 220.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Debug: кластеров ${clusters.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            clusters.forEachIndexed { index, item ->
                Text(
                    text = formatClusterDebugLine(
                        index = index,
                        photoCount = item.photoCount,
                        latitude = item.latitude,
                        longitude = item.longitude
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun BottomPhotoGallery(
    state: BottomGalleryState,
    onLoadMore: () -> Unit,
    onOpenPhoto: (DevicePhoto) -> Unit,
    onZoomToTarget: (BottomGalleryZoomTarget) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.zoomTarget?.let { target ->
                        IconButton(onClick = { onZoomToTarget(target) }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_zoom),
                                contentDescription = "Приблизить"
                            )
                        }
                    }
                    OutlinedButton(onClick = onClose) {
                        Text(text = "Закрыть")
                    }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.photos,
                    key = { photo -> photo.mediaId }
                ) { photo ->
                    BottomPhotoThumbnail(
                        photo = photo,
                        onOpenPhoto = onOpenPhoto
                    )
                }
            }

            val firstPhoto = state.photos.firstOrNull()
            if (firstPhoto != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = firstPhoto.displayName ?: "Фото ${firstPhoto.mediaId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = firstPhoto.dateTaken?.let { millis ->
                            DateFormat.getDateFormat(LocalContext.current).format(Date(millis))
                        } ?: "Дата съёмки неизвестна",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (state.canLoadMore) {
                OutlinedButton(onClick = onLoadMore) {
                    Text(text = "Показать ещё")
                }
            }
        }
    }
}

@Composable
private fun ClusterDensitySlider(
    clusterSettings: PhotoClusterSettings,
    onDensityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember {
        mutableStateOf(clusterSettings.densityCoefficientPercent.toFloat())
    }
    var lastSentValue by remember {
        mutableStateOf(clusterSettings.densityCoefficientPercent)
    }
    LaunchedEffect(clusterSettings.densityCoefficientPercent) {
        sliderValue = clusterSettings.densityCoefficientPercent.toFloat()
        lastSentValue = clusterSettings.densityCoefficientPercent
    }
    val steppedValue = sliderValue.roundToDensityStep()
    val densityRangeStart = MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT.toFloat()
    val densityRangeEnd = MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT.toFloat()
    val densityRange = densityRangeStart..densityRangeEnd
    val sliderSteps = (
        (MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT - MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT) /
            PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP
        ).coerceAtLeast(1) - 1

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Плотность",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$steppedValue%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = sliderValue.coerceIn(densityRangeStart, densityRangeEnd),
                onValueChange = { value ->
                    sliderValue = value
                    val nextValue = value.roundToDensityStep()
                    if (nextValue != lastSentValue) {
                        lastSentValue = nextValue
                        onDensityChanged(nextValue)
                    }
                },
                onValueChangeFinished = {
                    val nextValue = sliderValue.roundToDensityStep()
                    if (nextValue != lastSentValue) {
                        lastSentValue = nextValue
                        onDensityChanged(nextValue)
                    }
                },
                valueRange = densityRange,
                steps = sliderSteps
            )
        }
    }
}

@Composable
private fun BottomPhotoThumbnail(
    photo: DevicePhoto,
    onOpenPhoto: (DevicePhoto) -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            context.loadPhotoPreviewBitmap(
                uriString = photo.uri,
                targetSizePx = BottomGalleryThumbnailPx,
                photoId = photo.mediaId
            )
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = requireNotNull(thumbnail).asImageBitmap(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(76.dp)
                .clickable { onOpenPhoto(photo) }
        )
    } else {
        Surface(
            modifier = Modifier
                .size(76.dp)
                .clickable { onOpenPhoto(photo) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Фото",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Context.loadPhotoPreviewBitmap(
    uriString: String,
    targetSizePx: Int,
    photoId: Long? = null
): Bitmap? {
    val uri = Uri.parse(uriString)
    return runCatching {
        contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "ContentResolver.loadThumbnail failed: photoId=$photoId", error)
    }.getOrNull() ?: runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val scale = (targetSizePx.toFloat() / maxOf(width, height).toFloat()).coerceAtMost(1f)
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1)
            )
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "ImageDecoder thumbnail fallback failed: photoId=$photoId", error)
    }.getOrNull()
}

private fun MapLibreMap.handlePhotoMapClick(
    point: LatLng,
    renderState: PhotoMapRenderState,
    photosById: Map<Long, DevicePhoto>,
    clusterSettings: PhotoClusterSettings,
    onShowPhotos: (BottomGalleryState) -> Unit
): Boolean {
    val screenPoint = projection.toScreenLocation(point)
    val renderItem = renderState.findHit(
        screenX = screenPoint.x,
        screenY = screenPoint.y,
        settings = clusterSettings
    )
    if (renderItem != null) {
        Log.d(
            PhotoMapLogTag,
            "Photo projection hit-test: photoIds=${renderItem.tappablePhotoIds.distinct().size}, " +
                "renderHit=${renderItem.id}, isCluster=${renderItem.isCluster}"
        )
        AppDiagnostics.record(
            PhotoMapLogTag,
            "Photo projection hit-test: photoIds=${renderItem.tappablePhotoIds.distinct().size}, " +
                "renderHit=${renderItem.id}, isCluster=${renderItem.isCluster}"
        )
        return handlePhotoMapRenderItemTap(
            renderItem = renderItem,
            photosById = photosById,
            clusterSettings = clusterSettings,
            onShowPhotos = onShowPhotos
        )
    }

    val photoIds = photoIdsNearTap(
        point = point,
        photos = photosById.values,
        hitSlopPx = MapTapHitSlopPx,
        maxResults = clusterSettings.leavesPageSize
    )
    Log.d(
        PhotoMapLogTag,
        "Photo projection hit-test: photoIds=${photoIds.distinct().size}, " +
            "renderHit=null, isCluster=null"
    )
    AppDiagnostics.record(
        PhotoMapLogTag,
        "Photo projection hit-test: photoIds=${photoIds.distinct().size}, " +
            "renderHit=null, isCluster=null"
    )

    return handlePhotoMapPhotoIdsTap(
        photoIds = photoIds,
        photosById = photosById,
        clusterSettings = clusterSettings,
        onShowPhotos = onShowPhotos
    )
}

private fun MapLibreMap.handlePhotoMapRenderItemTap(
    renderItem: PhotoMapRenderItem,
    photosById: Map<Long, DevicePhoto>,
    clusterSettings: PhotoClusterSettings,
    onShowPhotos: (BottomGalleryState) -> Unit
): Boolean {
    if (renderItem.isCluster) {
        val nextZoom = (cameraPosition.zoom + ClusterTapZoomStep).coerceAtMost(ClusterTapMaxZoom)
        if (renderItem.photoCount >= LargeClusterGalleryThreshold) {
            bottomGalleryStateForRenderItem(
                renderItem = renderItem,
                photosById = photosById,
                pageSize = clusterSettings.leavesPageSize,
                zoomTarget = BottomGalleryZoomTarget(
                    latitude = renderItem.latitude,
                    longitude = renderItem.longitude,
                    zoom = nextZoom
                )
            )?.let(onShowPhotos)
        }
        animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(renderItem.latitude, renderItem.longitude),
                nextZoom
            )
        )
        AppDiagnostics.record(
            PhotoMapLogTag,
            "Zoom to custom cluster: id=${renderItem.id}, photos=${renderItem.photoCount}, zoom=$nextZoom"
        )
        return true
    }

    return handlePhotoMapPhotoIdsTap(
        photoIds = renderItem.tappablePhotoIds,
        photosById = photosById,
        clusterSettings = clusterSettings,
        onShowPhotos = onShowPhotos
    )
}

private fun handlePhotoMapPhotoIdsTap(
    photoIds: List<Long>,
    photosById: Map<Long, DevicePhoto>,
    clusterSettings: PhotoClusterSettings,
    onShowPhotos: (BottomGalleryState) -> Unit
): Boolean {
    return when (val action = PhotoMapClickDecision.forUnclustered(photoIds)) {
        PhotoMapTapAction.NoAction,
        is PhotoMapTapAction.ZoomToCluster,
        is PhotoMapTapAction.ShowClusterPhotos -> false

        is PhotoMapTapAction.OpenPhoto -> {
            val photo = photosById[action.photoId] ?: return false
            onShowPhotos(
                BottomGalleryState(
                    photos = listOf(photo),
                    totalCount = 1,
                    allPhotoIds = listOf(photo.mediaId),
                    zoomTarget = null
                )
            )
            true
        }

        is PhotoMapTapAction.ShowPhotos -> {
            val state = bottomGalleryStateForPhotoIds(
                photoIds = action.photoIds,
                photosById = photosById,
                pageSize = clusterSettings.leavesPageSize,
                zoomTarget = null
            )
            if (state != null) {
                onShowPhotos(state)
                true
            } else {
                false
            }
        }
    }
}

private fun MapLibreMap.photoIdsNearTap(
    point: LatLng,
    photos: Collection<DevicePhoto>,
    hitSlopPx: Float,
    maxResults: Int
): List<Long> {
    val screenPoint = projection.toScreenLocation(point)
    val hitSlopSquared = hitSlopPx * hitSlopPx
    return runCatching {
        photos.asSequence()
            .mapNotNull { photo ->
                val photoPoint = photo.toMapPoint() ?: return@mapNotNull null
                val projected = projection.toScreenLocation(LatLng(photoPoint.latitude, photoPoint.longitude))
                val dx = projected.x - screenPoint.x
                val dy = projected.y - screenPoint.y
                val distance = dx * dx + dy * dy
                if (distance > hitSlopSquared) {
                    return@mapNotNull null
                }

                VisiblePhotoCandidate(
                    photoId = photo.mediaId,
                    distanceFromCenter = distance
                )
            }
            .sortedBy { candidate -> candidate.distanceFromCenter }
            .take(maxResults.coerceAtLeast(1))
            .map { candidate -> candidate.photoId }
            .toList()
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to calculate photo tap candidates", error)
    }.getOrDefault(emptyList())
}

private fun markerOffset(
    screenX: Float,
    screenY: Float,
    markerSizePx: Float,
    viewportWidth: Int,
    viewportHeight: Int
): IntOffset {
    val (x, y) = markerTopLeftPx(
        screenX = screenX,
        screenY = screenY,
        markerSizePx = markerSizePx
    )
    if (viewportWidth <= 0 || viewportHeight <= 0) {
        return IntOffset(x, y)
    }

    val markerSize = markerSizePx.roundToInt().coerceAtLeast(1)
    return IntOffset(
        x = x.coerceIn(0, max(0, viewportWidth - markerSize)),
        y = y.coerceIn(0, max(0, viewportHeight - markerSize))
    )
}

private fun BottomGalleryState.loadNextPage(
    photosById: Map<Long, DevicePhoto>,
    pageSize: Int
): BottomGalleryState? {
    if (!canLoadMore) {
        return null
    }

    val nextPhotos = allPhotoIds
        .drop(photos.size)
        .take(pageSize.coerceAtLeast(1))
        .mapNotNull { photoId -> photosById[photoId] }
    if (nextPhotos.isEmpty()) {
        return copy(totalCount = photos.size)
    }

    return copy(photos = (photos + nextPhotos).distinctBy { photo -> photo.mediaId })
}

private fun bottomGalleryStateForPhotoIds(
    photoIds: List<Long>,
    photosById: Map<Long, DevicePhoto>,
    pageSize: Int,
    zoomTarget: BottomGalleryZoomTarget?
): BottomGalleryState? {
    val availablePhotoIds = photoIds.distinct().filter { photoId -> photosById.containsKey(photoId) }
    if (availablePhotoIds.isEmpty()) {
        return null
    }

    return BottomGalleryState(
        photos = availablePhotoIds
            .take(pageSize.coerceAtLeast(1))
            .mapNotNull { photoId -> photosById[photoId] },
        totalCount = availablePhotoIds.size,
        allPhotoIds = availablePhotoIds,
        zoomTarget = zoomTarget
    )
}

private fun bottomGalleryStateForRenderItem(
    renderItem: PhotoMapRenderItem,
    photosById: Map<Long, DevicePhoto>,
    pageSize: Int,
    zoomTarget: BottomGalleryZoomTarget?
): BottomGalleryState? {
    return bottomGalleryStateForPhotoIds(
        photoIds = renderItem.galleryPhotoIds(photosById),
        photosById = photosById,
        pageSize = pageSize,
        zoomTarget = zoomTarget
    )
}

private fun PhotoMapRenderItem.galleryPhotoIds(
    photosById: Map<Long, DevicePhoto>
): List<Long> {
    if (photoIds.isNotEmpty()) {
        return tappablePhotoIds
    }

    return photosById.values.asSequence()
        .filter { photo -> photo.hasLocation }
        .filter { photo ->
            val latitude = photo.latitude ?: return@filter false
            val longitude = photo.longitude ?: return@filter false
            latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
        }
        .map { photo -> photo.mediaId }
        .toList()
}

private fun MapLibreMap.buildPhotoMapRenderState(
    mapItems: List<VisiblePhotoMapItem>,
    width: Int,
    height: Int,
    settings: PhotoClusterSettings
): PhotoMapRenderState {
    if (width <= 0 || height <= 0 || mapItems.isEmpty()) {
        return PhotoMapRenderState.Empty
    }

    val centerX = width / 2f
    val centerY = height / 2f
    return runCatching {
        val padding = settings.thumbnailPreloadPaddingPx.toFloat()
        val minX = -padding
        val minY = -padding
        val maxX = width + padding
        val maxY = height + padding
        val projectedItems = mapItems.asSequence()
            .mapNotNull { item ->
                val screenPoint = projection.toScreenLocation(LatLng(item.latitude, item.longitude))
                if (screenPoint.x !in minX..maxX || screenPoint.y !in minY..maxY) {
                    return@mapNotNull null
                }

                val dx = screenPoint.x - centerX
                val dy = screenPoint.y - centerY
                PhotoMapRenderItem(
                    id = item.id,
                    level = item.level,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    photoCount = item.photoCount,
                    priorityScore = item.priorityScore,
                    minLatitude = item.minLatitude,
                    maxLatitude = item.maxLatitude,
                    minLongitude = item.minLongitude,
                    maxLongitude = item.maxLongitude,
                    coverPhotoId = item.coverPhotoId,
                    screenX = screenPoint.x,
                    screenY = screenPoint.y,
                    photoIds = item.photoIds,
                    distanceFromCenter = dx * dx + dy * dy
                )
            }
            .sortedBy { candidate -> candidate.distanceFromCenter }
            .toList()
        val effectiveClusterRadiusPx = effectiveClusterRadiusPx(
            width = width,
            height = height,
            zoom = cameraPosition.zoom,
            settings = settings
        )
        val clusterFeatures = projectedItems
            .filter { item -> item.isCluster }
            .map { item -> item.toClusterFeature() }
        val clusterThumbnailRequests = projectedItems
            .filter { item -> item.isCluster }
            .mapNotNull { item ->
                val representativePhotoId = item.representativePhotoId ?: return@mapNotNull null
                ClusterThumbnailRequest(
                    imageKey = photoClusterThumbnailImageKey(item.id),
                    representativePhotoId = representativePhotoId,
                    photoCount = item.photoCount
                )
            }
        val thumbnailPhotoIds = visibleThumbnailPhotoIds(
            items = projectedItems,
            width = width,
            height = height,
            settings = settings
        )

        PhotoMapRenderState(
            items = projectedItems,
            thumbnailPhotoIds = thumbnailPhotoIds,
            clusterThumbnailRequests = clusterThumbnailRequests,
            clusterFeatureCollection = FeatureCollection.fromFeatures(clusterFeatures),
            projectedPhotoCount = projectedItems.size,
            viewportWidth = width,
            viewportHeight = height,
            effectiveClusterRadiusPx = effectiveClusterRadiusPx
        )
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to build photo map render state", error)
    }.getOrDefault(PhotoMapRenderState.Empty)
}

private fun clusterProjectedPhotos(
    projectedPhotos: List<ProjectedMapPhoto>,
    radiusPx: Float,
    minPoints: Int,
    maxDistanceKm: Double
): List<PhotoMapRenderItem> {
    if (projectedPhotos.isEmpty()) {
        return emptyList()
    }

    val clusters = mutableListOf<MutableMapPhotoCluster>()
    val clustersByCell = mutableMapOf<ScreenGridCell, MutableList<MutableMapPhotoCluster>>()
    val cellSize = radiusPx.coerceAtLeast(1f)
    projectedPhotos.forEach { projectedPhoto ->
        val cell = projectedPhoto.screenCell(cellSize)
        val targetCluster = cell.neighbors()
            .asSequence()
            .flatMap { neighbor -> clustersByCell[neighbor].orEmpty().asSequence() }
            .distinct()
            .filter { cluster ->
                cluster.screenDistanceSquaredTo(projectedPhoto) <= radiusPx * radiusPx &&
                    cluster.canAccept(projectedPhoto, maxDistanceKm)
            }
            .minByOrNull { cluster -> cluster.screenDistanceSquaredTo(projectedPhoto) }

        if (targetCluster == null) {
            val cluster = MutableMapPhotoCluster(projectedPhoto, cell)
            clusters += cluster
            clustersByCell.getOrPut(cell) { mutableListOf() }.add(cluster)
        } else {
            val previousCell = targetCluster.cell
            targetCluster.add(projectedPhoto)
            val nextCell = targetCluster.screenCell(cellSize)
            if (nextCell != previousCell) {
                clustersByCell[previousCell]?.remove(targetCluster)
                clustersByCell.getOrPut(nextCell) { mutableListOf() }.add(targetCluster)
                targetCluster.cell = nextCell
            }
        }
    }

    return clusters.flatMapIndexed { index, cluster ->
        if (cluster.size >= minPoints) {
            listOf(cluster.toRenderItem(index))
        } else {
            cluster.toSingleItems()
        }
    }
}

private fun effectiveClusterRadiusPx(
    width: Int,
    height: Int,
    zoom: Double,
    settings: PhotoClusterSettings
): Float {
    val baseRadius = settings.radiusPx.toFloat()
    val viewportMinSide = minOf(width, height).toFloat().coerceAtLeast(baseRadius)
    val viewportMaxSide = maxOf(width, height).toFloat().coerceAtLeast(baseRadius)
    val viewportRadius = when {
        zoom <= CityWideClusterZoom -> viewportMinSide * 0.68f
        zoom <= DistrictClusterZoom -> viewportMinSide * 0.50f
        zoom <= NeighborhoodClusterZoom -> viewportMinSide * 0.32f
        else -> baseRadius
    }
    return max(baseRadius, viewportRadius)
        .coerceAtMost(viewportMaxSide)
}

private fun visibleThumbnailPhotoIds(
    items: List<PhotoMapRenderItem>,
    width: Int,
    height: Int,
    settings: PhotoClusterSettings
): List<Long> {
    val cellSize = settings.thumbnailCellSizePx.coerceAtLeast(1).toFloat()
    val centerX = width / 2f
    val centerY = height / 2f
    val occupiedCells = LinkedHashMap<ScreenGridCell, PhotoMapRenderItem>()
    items.asSequence()
        .filter { item -> !item.isCluster }
        .sortedBy { item ->
            val dx = item.screenX - centerX
            val dy = item.screenY - centerY
            dx * dx + dy * dy
        }
        .forEach { item ->
            occupiedCells.putIfAbsent(item.screenCell(cellSize), item)
        }

    return occupiedCells.values
        .asSequence()
        .take(settings.maxVisibleThumbnails.coerceAtLeast(1))
        .mapNotNull { item -> item.representativePhotoId }
        .toList()
}

private fun Context.openPhotoInDefaultGallery(photo: DevicePhoto) {
    val uri = Uri.parse(photo.uri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, photo.mimeType ?: "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (this@openPhotoInDefaultGallery !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Log.w(PhotoMapLogTag, "No external gallery app found: photoId=${photo.mediaId}", exception)
        Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
    } catch (exception: RuntimeException) {
        Log.w(PhotoMapLogTag, "Failed to open photo externally: photoId=${photo.mediaId}", exception)
        Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.createFixedMapThumbnailBitmap(
    photoId: Long,
    uriString: String,
    borderColor: Int,
    backgroundColor: Int
): Bitmap? {
    return runCatching {
        createFixedMapThumbnailBitmapOrThrow(
            photoId = photoId,
            uriString = uriString,
            borderColor = borderColor,
            backgroundColor = backgroundColor
        )
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to create map thumbnail bitmap: photoId=$photoId", error)
    }.getOrNull()
}

private fun Context.createFixedMapThumbnailBitmapOrThrow(
    photoId: Long,
    uriString: String,
    borderColor: Int,
    backgroundColor: Int
): Bitmap {
    val width = mapThumbnailWidthPx()
    val height = mapThumbnailHeightPx()
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val outerRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val imageInset = MapThumbnailBorderPx
    val imageRect = RectF(
        imageInset,
        imageInset,
        width - imageInset,
        height - imageInset
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = MapThumbnailBorderPx
    }
    val image = loadPhotoPreviewBitmap(
        uriString = uriString,
        targetSizePx = max(width, height),
        photoId = photoId
    )?.toSoftwareBitmapForCanvas(photoId)

    canvas.drawRoundRect(outerRect, MapThumbnailCornerRadiusPx, MapThumbnailCornerRadiusPx, backgroundPaint)
    if (image != null) {
        val path = Path().apply {
            addRoundRect(imageRect, MapThumbnailInnerCornerRadiusPx, MapThumbnailInnerCornerRadiusPx, Path.Direction.CW)
        }
        val saveCount = canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(image, image.centerCropSourceRect(imageRect), imageRect, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(saveCount)
    } else {
        Log.w(PhotoMapLogTag, "Map thumbnail uses placeholder because bitmap is null: photoId=$photoId")
    }
    canvas.drawRoundRect(
        RectF(
            MapThumbnailBorderPx / 2f,
            MapThumbnailBorderPx / 2f,
            width - MapThumbnailBorderPx / 2f,
            height - MapThumbnailBorderPx / 2f
        ),
        MapThumbnailCornerRadiusPx,
        MapThumbnailCornerRadiusPx,
        borderPaint
    )

    return result
}

private fun Context.createClusterMapThumbnailBitmap(
    photoId: Long,
    uriString: String,
    count: Int,
    borderColor: Int,
    backgroundColor: Int
): Bitmap? {
    return runCatching {
        createClusterMapThumbnailBitmapOrThrow(
            photoId = photoId,
            uriString = uriString,
            count = count,
            borderColor = borderColor,
            backgroundColor = backgroundColor
        )
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to create cluster thumbnail bitmap: photoId=$photoId count=$count", error)
        AppDiagnostics.record(PhotoMapLogTag, "Failed to create cluster thumbnail bitmap: photoId=$photoId count=$count", error)
    }.getOrNull()
}

private fun Context.createClusterMapThumbnailBitmapOrThrow(
    photoId: Long,
    uriString: String,
    count: Int,
    borderColor: Int,
    backgroundColor: Int
): Bitmap {
    val width = mapThumbnailWidthPx()
    val height = mapThumbnailHeightPx()
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val outerRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val imageInset = MapThumbnailBorderPx
    val imageRect = RectF(
        imageInset,
        imageInset,
        width - imageInset,
        height - imageInset
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = MapThumbnailBorderPx
    }
    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
    }
    val image = loadPhotoPreviewBitmap(
        uriString = uriString,
        targetSizePx = max(width, height),
        photoId = photoId
    )?.toSoftwareBitmapForCanvas(photoId)

    canvas.drawRoundRect(outerRect, MapThumbnailCornerRadiusPx, MapThumbnailCornerRadiusPx, backgroundPaint)
    val path = Path().apply {
        addRoundRect(imageRect, MapThumbnailInnerCornerRadiusPx, MapThumbnailInnerCornerRadiusPx, Path.Direction.CW)
    }
    val saveCount = canvas.save()
    canvas.clipPath(path)
    if (image != null) {
        val tiny = Bitmap.createBitmap(
            ClusterThumbnailBlurSamplePx,
            ClusterThumbnailBlurSamplePx,
            Bitmap.Config.ARGB_8888
        )
        val tinyRect = RectF(
            0f,
            0f,
            ClusterThumbnailBlurSamplePx.toFloat(),
            ClusterThumbnailBlurSamplePx.toFloat()
        )
        val tinyCanvas = Canvas(tiny)
        tinyCanvas.drawBitmap(
            image,
            image.centerCropSourceRect(tinyRect),
            tinyRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                isDither = true
            }
        )
        canvas.drawBitmap(
            tiny,
            Rect(0, 0, ClusterThumbnailBlurSamplePx, ClusterThumbnailBlurSamplePx),
            imageRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                isDither = true
            }
        )
        tiny.recycle()
    } else {
        Log.w(PhotoMapLogTag, "Cluster thumbnail uses placeholder because bitmap is null: photoId=$photoId")
        AppDiagnostics.record(PhotoMapLogTag, "Cluster thumbnail uses placeholder because bitmap is null: photoId=$photoId")
    }
    canvas.drawRoundRect(imageRect, MapThumbnailInnerCornerRadiusPx, MapThumbnailInnerCornerRadiusPx, overlayPaint)
    canvas.restoreToCount(saveCount)
    canvas.drawRoundRect(
        RectF(
            MapThumbnailBorderPx / 2f,
            MapThumbnailBorderPx / 2f,
            width - MapThumbnailBorderPx / 2f,
            height - MapThumbnailBorderPx / 2f
        ),
        MapThumbnailCornerRadiusPx,
        MapThumbnailCornerRadiusPx,
        borderPaint
    )

    return result
}

private data class VisiblePhotoCandidate(
    val photoId: Long,
    val distanceFromCenter: Float
)

private data class PhotoMapRenderState(
    val items: List<PhotoMapRenderItem>,
    val thumbnailPhotoIds: List<Long>,
    val clusterThumbnailRequests: List<ClusterThumbnailRequest>,
    val clusterFeatureCollection: FeatureCollection,
    val projectedPhotoCount: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val effectiveClusterRadiusPx: Float
) {
    val clusterCount: Int
        get() = items.count { item -> item.isCluster }

    val singleCount: Int
        get() = items.count { item -> !item.isCluster }

    fun findHit(
        screenX: Float,
        screenY: Float,
        settings: PhotoClusterSettings
    ): PhotoMapRenderItem? {
        return items
            .asSequence()
            .mapNotNull { item ->
                val dx = item.screenX - screenX
                val dy = item.screenY - screenY
                val distanceSquared = dx * dx + dy * dy
                val hitRadius = if (item.isCluster) {
                    (24f * settings.markerScalePercent.coerceIn(80, 200) / 100f) + 18f
                } else {
                    MapTapHitSlopPx
                }
                if (distanceSquared <= hitRadius * hitRadius) {
                    item to distanceSquared
                } else {
                    null
                }
            }
            .minByOrNull { (_, distanceSquared) -> distanceSquared }
            ?.first
    }

    companion object {
        val Empty = PhotoMapRenderState(
            items = emptyList(),
            thumbnailPhotoIds = emptyList(),
            clusterThumbnailRequests = emptyList(),
            clusterFeatureCollection = emptyPhotoFeatureCollection(),
            projectedPhotoCount = 0,
            viewportWidth = 0,
            viewportHeight = 0,
            effectiveClusterRadiusPx = 0f
        )
    }
}

private data class ClusterThumbnailRequest(
    val imageKey: String,
    val representativePhotoId: Long,
    val photoCount: Int
)

private data class PhotoMapRenderItem(
    val id: String,
    val level: Int,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val priorityScore: Double,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val coverPhotoId: Long?,
    val screenX: Float,
    val screenY: Float,
    val photoIds: List<Long>,
    val distanceFromCenter: Float = 0f
) {
    val isCluster: Boolean
        get() = isPhotoMapCluster(photoCount)

    val representativePhotoId: Long?
        get() = selectRepresentativePhotoId(
            coverPhotoId = coverPhotoId,
            photoIds = photoIds
        )

    val tappablePhotoIds: List<Long>
        get() = selectTappablePhotoIds(
            coverPhotoId = coverPhotoId,
            photoIds = photoIds
        )

    fun toClusterFeature(): Feature {
        return Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            addStringProperty(PHOTO_CLUSTER_ID_PROPERTY, id)
            addNumberProperty(PHOTO_CLUSTER_COUNT_PROPERTY, photoCount)
            addStringProperty(PHOTO_CLUSTER_COUNT_ABBREVIATED_PROPERTY, photoCount.clusterCountLabel())
            addStringProperty(PHOTO_CLUSTER_THUMBNAIL_KEY_PROPERTY, photoClusterThumbnailImageKey(id))
        }
    }
}

private data class ProjectedMapPhoto(
    val photo: DevicePhoto,
    val point: PhotoMapPoint,
    val screenX: Float,
    val screenY: Float,
    val distanceFromCenter: Float
) {
    fun screenCell(cellSize: Float): ScreenGridCell {
        return ScreenGridCell(
            x = floor(screenX / cellSize).toInt(),
            y = floor(screenY / cellSize).toInt()
        )
    }
}

private data class ScreenGridCell(
    val x: Int,
    val y: Int
) {
    fun neighbors(): Sequence<ScreenGridCell> = sequence {
        for (dx in -1..1) {
            for (dy in -1..1) {
                yield(ScreenGridCell(x + dx, y + dy))
            }
        }
    }
}

private class MutableMapPhotoCluster(
    firstPhoto: ProjectedMapPhoto,
    var cell: ScreenGridCell
) {
    private val photos = mutableListOf(firstPhoto)
    var screenX: Float = firstPhoto.screenX
        private set
    var screenY: Float = firstPhoto.screenY
        private set
    private var latitude: Double = firstPhoto.point.latitude
    private var longitude: Double = firstPhoto.point.longitude

    val size: Int
        get() = photos.size

    fun add(projectedPhoto: ProjectedMapPhoto) {
        photos += projectedPhoto
        screenX = photos.map { photo -> photo.screenX }.average().toFloat()
        screenY = photos.map { photo -> photo.screenY }.average().toFloat()
        latitude = photos.map { photo -> photo.point.latitude }.average()
        longitude = photos.map { photo -> photo.point.longitude }.average()
    }

    fun canAccept(projectedPhoto: ProjectedMapPhoto, maxDistanceKm: Double): Boolean {
        return photos.all { photo ->
            haversineDistanceKm(
                firstLatitude = photo.point.latitude,
                firstLongitude = photo.point.longitude,
                secondLatitude = projectedPhoto.point.latitude,
                secondLongitude = projectedPhoto.point.longitude
            ) <= maxDistanceKm
        }
    }

    fun screenDistanceSquaredTo(projectedPhoto: ProjectedMapPhoto): Float {
        val dx = projectedPhoto.screenX - screenX
        val dy = projectedPhoto.screenY - screenY
        return dx * dx + dy * dy
    }

    fun screenCell(cellSize: Float): ScreenGridCell {
        return ScreenGridCell(
            x = floor(screenX / cellSize).toInt(),
            y = floor(screenY / cellSize).toInt()
        )
    }

    fun toRenderItem(index: Int): PhotoMapRenderItem {
        val photoIds = photos.map { photo -> photo.photo.mediaId }
        return PhotoMapRenderItem(
            id = "cluster-${index}-${photoIds.firstOrNull() ?: 0L}",
            level = 9,
            latitude = latitude,
            longitude = longitude,
            photoCount = photoIds.size,
            priorityScore = photoIds.size.toDouble(),
            minLatitude = photos.minOf { photo -> photo.point.latitude },
            maxLatitude = photos.maxOf { photo -> photo.point.latitude },
            minLongitude = photos.minOf { photo -> photo.point.longitude },
            maxLongitude = photos.maxOf { photo -> photo.point.longitude },
            coverPhotoId = photoIds.firstOrNull(),
            screenX = screenX,
            screenY = screenY,
            photoIds = photoIds
        )
    }

    fun toSingleItems(): List<PhotoMapRenderItem> {
        return photos.map { photo ->
            PhotoMapRenderItem(
                id = "photo-${photo.photo.mediaId}",
                level = 0,
                latitude = photo.point.latitude,
                longitude = photo.point.longitude,
                photoCount = 1,
                priorityScore = 1.0,
                minLatitude = photo.point.latitude,
                maxLatitude = photo.point.latitude,
                minLongitude = photo.point.longitude,
                maxLongitude = photo.point.longitude,
                coverPhotoId = photo.photo.mediaId,
                screenX = photo.screenX,
                screenY = photo.screenY,
                photoIds = listOf(photo.photo.mediaId)
            )
        }
    }
}

private fun PhotoMapRenderItem.screenCell(cellSize: Float): ScreenGridCell {
    return ScreenGridCell(
        x = floor(screenX / cellSize).toInt(),
        y = floor(screenY / cellSize).toInt()
    )
}

private fun PhotoMapRenderItem.isCenterInsideViewport(
    viewportWidth: Int,
    viewportHeight: Int
): Boolean {
    if (viewportWidth <= 0 || viewportHeight <= 0) {
        return true
    }

    return screenX in 0f..viewportWidth.toFloat() &&
        screenY in 0f..viewportHeight.toFloat()
}

private fun haversineDistanceKm(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double
): Double {
    val firstLatitudeRad = Math.toRadians(firstLatitude)
    val secondLatitudeRad = Math.toRadians(secondLatitude)
    val deltaLatitudeRad = Math.toRadians(secondLatitude - firstLatitude)
    val deltaLongitudeRad = Math.toRadians(secondLongitude - firstLongitude)
    val halfChord = sin(deltaLatitudeRad / 2.0) * sin(deltaLatitudeRad / 2.0) +
        cos(firstLatitudeRad) * cos(secondLatitudeRad) *
        sin(deltaLongitudeRad / 2.0) * sin(deltaLongitudeRad / 2.0)
    val normalizedHalfChord = halfChord.coerceIn(0.0, 1.0)
    val angularDistance = 2.0 * atan2(
        sqrt(normalizedHalfChord),
        sqrt(1.0 - normalizedHalfChord)
    )
    return EarthRadiusKm * angularDistance
}

private fun Int.clusterCountLabel(): String {
    return when {
        this < 1000 -> "+$this"
        this < 1_000_000 -> "+${this / 1000}k"
        else -> "+${this / 1_000_000}m"
    }
}

private fun Bitmap.toSoftwareBitmapForCanvas(photoId: Long): Bitmap? {
    if (config != Bitmap.Config.HARDWARE) {
        return this
    }

    return runCatching {
        copy(Bitmap.Config.ARGB_8888, false)
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to copy hardware bitmap for map thumbnail: photoId=$photoId", error)
    }.getOrNull()
}

private fun Bitmap.centerCropSourceRect(targetRect: RectF): Rect {
    val targetAspect = targetRect.width() / targetRect.height()
    val sourceAspect = width.toFloat() / height.toFloat()
    return if (sourceAspect > targetAspect) {
        val cropWidth = (height * targetAspect).roundToInt().coerceAtLeast(1)
        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        Rect(left, 0, (left + cropWidth).coerceAtMost(width), height)
    } else {
        val cropHeight = (width / targetAspect).roundToInt().coerceAtLeast(1)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        Rect(0, top, width, (top + cropHeight).coerceAtMost(height))
    }
}

private fun Context.mapThumbnailWidthPx(): Int {
    return (MapThumbnailWidthDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
}

private fun Context.mapThumbnailHeightPx(): Int {
    return (MapThumbnailHeightDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
}

private class StyleImageRegistry(
    private val maxSize: Int
) {
    private val keys = LinkedHashSet<String>()

    val size: Int
        get() = keys.size

    fun contains(key: String): Boolean {
        return keys.contains(key)
    }

    fun markAdded(style: Style, key: String) {
        keys.remove(key)
        keys.add(key)
        while (keys.size > maxSize) {
            val oldestKey = keys.first()
            keys.remove(oldestKey)
            runCatching { style.removeImage(oldestKey) }
        }
    }

    fun clear() {
        keys.clear()
    }
}

private fun MapLibreMap.fitPositions(
    positions: List<LatLng>,
    padding: Int,
    singlePositionZoom: Double = InitialSinglePhotoZoom
) {
    when (positions.size) {
        0 -> Unit
        1 -> animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                positions.first(),
                singlePositionZoom
            )
        )
        else -> {
            val bounds = LatLngBounds.Builder()
            positions.forEach { position -> bounds.include(position) }
            animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding))
        }
    }
}

private fun MapLibreMap.dispatchViewportChanged(
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    runCatching {
        val visibleRegion = projection.visibleRegion
        val points = listOf(
            visibleRegion.nearLeft,
            visibleRegion.nearRight,
            visibleRegion.farLeft,
            visibleRegion.farRight
        )
        onViewportChanged(
            PhotoMapBounds(
                south = points.minOf { point -> point!!.latitude },
                west = points.minOf { point -> point!!.longitude },
                north = points.maxOf { point -> point!!.latitude },
                east = points.maxOf { point -> point!!.longitude }
            ),
            cameraPosition.zoom
        )
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to dispatch map viewport", error)
        AppDiagnostics.record(PhotoMapLogTag, "Failed to dispatch map viewport", error)
    }
}

private fun MapLibreMap.configureGestures() {
    uiSettings.setAllGesturesEnabled(true)
    uiSettings.setScrollGesturesEnabled(true)
    uiSettings.setHorizontalScrollGesturesEnabled(true)
    uiSettings.setZoomGesturesEnabled(true)
    uiSettings.setDoubleTapGesturesEnabled(true)
    uiSettings.setQuickZoomGesturesEnabled(true)
}

private fun Style.removePoliticalBoundaryLayers() {
    layers
        .map { layer -> layer.id }
        .filter { layerId -> layerId.isPoliticalBoundaryLayerId() }
        .forEach { layerId -> removeLayer(layerId) }

    LandFillLayerIds.forEach { layerId ->
        getLayerAs<FillLayer>(layerId)?.setProperties(fillColor(LandFillColor))
    }
}

private fun String.isPoliticalBoundaryLayerId(): Boolean {
    val normalized = lowercase()
    return normalized in ExactPoliticalBoundaryLayerIds ||
        BoundaryLayerPrefixes.any { prefix -> normalized.startsWith(prefix) } ||
        BoundaryLayerTokens.any { token -> normalized.contains(token) }
}

private fun scanStatusText(isPaused: Boolean, processed: Int, total: Int): String {
    val prefix = if (isPaused) "Пауза" else "Сканирование"
    return if (total > 0) {
        "$prefix: $processed из $total"
    } else {
        prefix
    }
}

private fun Float.roundToDensityStep(): Int {
    val step = PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP
    return ((this / step).roundToInt() * step)
        .coerceIn(
            MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT,
            MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
        )
}

private fun emptyPhotoFeatureCollection(): FeatureCollection {
    return FeatureCollection.fromFeatures(emptyList<Feature>())
}

private data class BottomGalleryState(
    val photos: List<DevicePhoto>,
    val totalCount: Int,
    val allPhotoIds: List<Long>,
    val zoomTarget: BottomGalleryZoomTarget?
) {
    val canLoadMore: Boolean
        get() = photos.size < totalCount

    val title: String
        get() = when {
            canLoadMore -> "Фотографий: ${photos.size} из $totalCount"
            totalCount > 1 -> "Фотографий: $totalCount"
            photos.size == 1 -> "Фотография"
            else -> "Фотографий: ${photos.size}"
        }
}

private data class BottomGalleryZoomTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

private const val MapBoundsPaddingPx = 120
private const val InitialSinglePhotoZoom = 14.0
private const val MapTapHitSlopPx = 84f
private const val BottomGalleryThumbnailPx = 160
private const val MapOverlayThumbnailPx = 96
private const val MapThumbnailWidthDp = 56f
private const val MapThumbnailHeightDp = 56f
private const val MapThumbnailBorderPx = 4f
private const val MapThumbnailCornerRadiusPx = 14f
private const val MapThumbnailInnerCornerRadiusPx = 10f
private const val MaxCachedMapThumbnailImages = 360
private const val MaxThumbnailImagesPerPass = 32
private const val MaxClusterThumbnailImagesPerPass = 24
private const val ClusterTapZoomStep = 2.0
private const val ClusterTapMaxZoom = 20.0
private const val LargeClusterGalleryThreshold = 50
private const val ClusterThumbnailBlurSamplePx = 16
private const val CityWideClusterZoom = 11.0
private const val DistrictClusterZoom = 12.5
private const val NeighborhoodClusterZoom = 14.0
private const val EarthRadiusKm = 6371.0
private const val PhotoMapLogTag = "PhotoMapMap"

private const val LandFillColor = "#E7ECE6"

private val ExactPoliticalBoundaryLayerIds = setOf(
    "countries-boundary",
    "geolines",
    "geolines-label"
)

private val BoundaryLayerPrefixes = listOf(
    "boundary_",
    "boundary-",
    "admin_",
    "admin-"
)

private val BoundaryLayerTokens = listOf(
    "country-boundary",
    "state-boundary",
    "maritime-boundary",
    "disputed-boundary"
)

private val LandFillLayerIds = listOf(
    "countries-fill",
    "crimea-fill"
)
