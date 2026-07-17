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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.photoDateDayToMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.roundToLong
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
    dateFilter: PhotoDateFilter,
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
    onDateFilterChanged: (Long, Long) -> Unit,
    onDateFilterReset: () -> Unit,
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

            if (photosWithLocation.isEmpty() && !isScanning && !dateFilter.isActive) {
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
                        dateFilter = dateFilter,
                        showDebugPanel = showDebugPanel,
                        centerRequestKey = centerRequestKey,
                        onClusterDensityChanged = onClusterDensityChanged,
                        onDateFilterChanged = onDateFilterChanged,
                        onDateFilterReset = onDateFilterReset,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onOpenSettings
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_manage),
                            contentDescription = "Настройки",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onCenterMap
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                            contentDescription = "Центрировать карту",
                            modifier = Modifier.size(22.dp)
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
    dateFilter: PhotoDateFilter,
    showDebugPanel: Boolean,
    centerRequestKey: Int,
    onClusterDensityChanged: (Int) -> Unit,
    onDateFilterChanged: (Long, Long) -> Unit,
    onDateFilterReset: () -> Unit,
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
                            forceShowClusterGallery = bottomGalleryState != null,
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
            photosById = photosById,
            width = mapView.width,
            height = mapView.height,
            settings = clusterSettings
        )
        mapRenderState = renderState
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        layerController.update(
            style = style,
            featureCollection = emptyPhotoFeatureCollection(),
            settings = clusterSettings,
            colors = layerColors
        )
        layerController.updateVisibleThumbnails(
            style = style,
            featureCollection = emptyPhotoFeatureCollection()
        )
        val refreshMessage =
            "Map render refresh: photos=${photosById.size}, clusters=${renderState.clusterCount}, " +
                "singles=${renderState.singleCount}, thumbnails=${renderState.thumbnailPhotoIds.size}, " +
                "clusterThumbs=${renderState.clusterThumbnailRequests.size}, " +
                "projected=${renderState.projectedPhotoCount}, baseRadius=${clusterSettings.radiusPx}, " +
                "effectiveRadius=${renderState.effectiveClusterRadiusPx.roundToInt()}, " +
                "zoom=${mapLibreMap.cameraPosition.zoom}, density=${clusterSettings.densityCoefficientPercent}%"
        Log.d(PhotoMapLogTag, refreshMessage)
        AppDiagnostics.record(PhotoMapLogTag, refreshMessage)
    }

    LaunchedEffect(
        map,
        isStyleReady,
        cameraMoveRevision
    ) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || mapView.width <= 0 || mapView.height <= 0 || mapRenderState.items.isEmpty()) {
            return@LaunchedEffect
        }

        mapRenderState = mapLibreMap.reprojectPhotoMapRenderState(
            renderState = mapRenderState,
            width = mapView.width,
            height = mapView.height
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
                    forceShowClusterGallery = bottomGalleryState != null,
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
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bottomGalleryState?.let { state ->
                BottomPhotoGallery(
                    state = state,
                    onOpenPhoto = { photo -> context.openPhotoInDefaultGallery(photo) },
                    onClose = { bottomGalleryState = null }
                )
            }
            MapFabControls(
                modifier = Modifier.fillMaxWidth(),
                clusterSettings = clusterSettings,
                dateFilter = dateFilter,
                onDensityChanged = onClusterDensityChanged,
                onDateFilterChanged = onDateFilterChanged,
                onDateFilterReset = onDateFilterReset
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
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
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
    onOpenPhoto: (DevicePhoto) -> Unit,
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
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = onClose
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "Закрыть",
                        modifier = Modifier.size(22.dp)
                    )
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

        }
    }
}

private enum class MapFabPanel {
    DateFilter,
    Density
}

@Composable
private fun MapFabControls(
    clusterSettings: PhotoClusterSettings,
    dateFilter: PhotoDateFilter,
    onDensityChanged: (Int) -> Unit,
    onDateFilterChanged: (Long, Long) -> Unit,
    onDateFilterReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPanel by remember { mutableStateOf<MapFabPanel?>(null) }
    val context = LocalContext.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = expandedPanel == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(
                    containerColor = if (dateFilter.isActive) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (dateFilter.isActive) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    onClick = { expandedPanel = MapFabPanel.DateFilter }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_recent_history),
                            contentDescription = "Фильтр по дате",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = compactDateFilterLabel(context, dateFilter),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                FloatingActionButton(onClick = { expandedPanel = MapFabPanel.Density }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                            contentDescription = "Плотность кластеров",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${clusterSettings.densityCoefficientPercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            visible = expandedPanel != null,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            when (expandedPanel) {
                MapFabPanel.DateFilter -> DateFilterRangeSlider(
                    modifier = Modifier.fillMaxWidth(),
                    dateFilter = dateFilter,
                    onDateFilterChanged = onDateFilterChanged,
                    onDateFilterReset = onDateFilterReset,
                    onClose = { expandedPanel = null }
                )

                MapFabPanel.Density -> ClusterDensitySlider(
                    modifier = Modifier.fillMaxWidth(),
                    clusterSettings = clusterSettings,
                    onDensityChanged = onDensityChanged,
                    onClose = { expandedPanel = null }
                )

                null -> Unit
            }
        }
    }
}

@Composable
private fun DateFilterRangeSlider(
    dateFilter: PhotoDateFilter,
    onDateFilterChanged: (Long, Long) -> Unit,
    onDateFilterReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minDay = dateFilter.minDay
    val maxDay = dateFilter.maxDay
    Card(modifier = modifier) {
        if (minDay == null || maxDay == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Нет дат для фильтра",
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = onClose
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "Скрыть фильтр даты",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            return@Card
        }

        val context = LocalContext.current
        val initialStartDay = dateFilter.selectedStartDay ?: minDay
        val initialEndDay = dateFilter.selectedEndDay ?: maxDay
        var sliderRange by remember(dateFilter) {
            mutableStateOf(
                minOf(initialStartDay, initialEndDay).toFloat()..
                    maxOf(initialStartDay, initialEndDay).toFloat()
            )
        }
        var sliderWindow by remember(dateFilter) {
            mutableStateOf(
                buildDateSliderWindow(
                    minDay = minDay,
                    maxDay = maxDay,
                    selectedStartDay = minOf(initialStartDay, initialEndDay),
                    selectedEndDay = maxOf(initialStartDay, initialEndDay),
                    zoomed = dateFilter.isActive
                )
            )
        }
        var isDragging by remember { mutableStateOf(false) }
        var pickerTarget by remember { mutableStateOf<DateFilterPickerTarget?>(null) }
        val visibleStartDay = sliderWindow.startDay
        val visibleEndDay = sliderWindow.endDay
        val selectedStartDay = sliderRange.start.roundToLong().coerceIn(minDay, maxDay)
        val selectedEndDay = sliderRange.endInclusive.roundToLong().coerceIn(minDay, maxDay)
        val startLabel = formatDateFilterSliderDay(context, minOf(selectedStartDay, selectedEndDay))
        val endLabel = formatDateFilterSliderDay(context, maxOf(selectedStartDay, selectedEndDay))
        val dateTicks = remember(context, minDay, maxDay, visibleStartDay, visibleEndDay) {
            buildDateSliderTicks(
                context = context,
                minDay = minDay,
                maxDay = maxDay,
                visibleStartDay = visibleStartDay,
                visibleEndDay = visibleEndDay
            )
        }
        val snapDays = remember(dateTicks, minDay, maxDay) {
            (dateTicks.map { tick -> tick.day } + listOf(minDay, maxDay))
                .distinct()
                .sorted()
        }

        LaunchedEffect(pickerTarget) {
            val target = pickerTarget ?: return@LaunchedEffect
            val selectedDay = when (target) {
                DateFilterPickerTarget.Start -> minOf(selectedStartDay, selectedEndDay)
                DateFilterPickerTarget.End -> maxOf(selectedStartDay, selectedEndDay)
            }
            showDateFilterPicker(
                context = context,
                title = when (target) {
                    DateFilterPickerTarget.Start -> "Начальная дата"
                    DateFilterPickerTarget.End -> "Конечная дата"
                },
                selectedDay = selectedDay,
                minDay = minDay,
                maxDay = maxDay,
                onDateSelected = { pickedDay ->
                    val nextRange = when (target) {
                        DateFilterPickerTarget.Start -> normalizeDateSliderRange(
                            startDay = pickedDay,
                            endDay = selectedEndDay,
                            minDay = minDay,
                            maxDay = maxDay
                        )

                        DateFilterPickerTarget.End -> normalizeDateSliderRange(
                            startDay = selectedStartDay,
                            endDay = pickedDay,
                            minDay = minDay,
                            maxDay = maxDay
                        )
                    }
                    sliderRange = nextRange.toFloatRange()
                    sliderWindow = buildDateSliderWindow(
                        minDay = minDay,
                        maxDay = maxDay,
                        selectedStartDay = nextRange.startDay,
                        selectedEndDay = nextRange.endDay,
                        zoomed = true
                    )
                    onDateFilterChanged(nextRange.startDay, nextRange.endDay)
                }
            )
            pickerTarget = null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DateFilterDateLabel(
                        modifier = Modifier.weight(1f),
                        text = startLabel,
                        active = isDragging || dateFilter.isActive,
                        onClick = { pickerTarget = DateFilterPickerTarget.Start }
                    )
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DateFilterDateLabel(
                        modifier = Modifier.weight(1f),
                        text = endLabel,
                        active = isDragging || dateFilter.isActive,
                        onClick = { pickerTarget = DateFilterPickerTarget.End }
                    )
                }
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = onClose
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "Скрыть фильтр даты",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (minDay == maxDay) {
                Text(
                    text = "Все фото на карте в одну дату",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp, max = 68.dp)
                ) {
                    DateSliderTimelineTicks(
                        modifier = Modifier.fillMaxSize(),
                        minDay = minDay,
                        maxDay = maxDay,
                        visibleStartDay = visibleStartDay,
                        visibleEndDay = visibleEndDay,
                        ticks = dateTicks
                    )
                    RangeSlider(
                        modifier = Modifier.fillMaxWidth(),
                        value = sliderRange,
                        onValueChange = { range ->
                            isDragging = true
                            val start = range.start.roundToLong()
                                .coerceIn(visibleStartDay, visibleEndDay)
                                .snapToDateSliderTick(
                                    snapDays = snapDays,
                                    visibleStartDay = visibleStartDay,
                                    visibleEndDay = visibleEndDay,
                                    thresholdMultiplier = DateSliderDragSnapThresholdMultiplier
                                )
                            val end = range.endInclusive.roundToLong()
                                .coerceIn(visibleStartDay, visibleEndDay)
                                .snapToDateSliderTick(
                                    snapDays = snapDays,
                                    visibleStartDay = visibleStartDay,
                                    visibleEndDay = visibleEndDay,
                                    thresholdMultiplier = DateSliderDragSnapThresholdMultiplier
                                )
                            val nextRange = normalizeDateSliderRange(
                                startDay = start,
                                endDay = end,
                                minDay = minDay,
                                maxDay = maxDay
                            )
                            sliderRange = nextRange.toFloatRange()
                            sliderWindow = expandDateSliderWindowIfNeeded(
                                current = sliderWindow,
                                minDay = minDay,
                                maxDay = maxDay,
                                selectedStartDay = nextRange.startDay,
                                selectedEndDay = nextRange.endDay
                            )
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            val start = sliderRange.start.roundToLong()
                                .coerceIn(minDay, maxDay)
                                .snapToDateSliderTick(
                                    snapDays = snapDays,
                                    visibleStartDay = visibleStartDay,
                                    visibleEndDay = visibleEndDay,
                                    thresholdMultiplier = DateSliderReleaseSnapThresholdMultiplier
                                )
                            val end = sliderRange.endInclusive.roundToLong()
                                .coerceIn(minDay, maxDay)
                                .snapToDateSliderTick(
                                    snapDays = snapDays,
                                    visibleStartDay = visibleStartDay,
                                    visibleEndDay = visibleEndDay,
                                    thresholdMultiplier = DateSliderReleaseSnapThresholdMultiplier
                                )
                            val nextRange = normalizeDateSliderRange(
                                startDay = start,
                                endDay = end,
                                minDay = minDay,
                                maxDay = maxDay
                            )
                            sliderRange = nextRange.toFloatRange()
                            sliderWindow = buildDateSliderWindow(
                                minDay = minDay,
                                maxDay = maxDay,
                                selectedStartDay = nextRange.startDay,
                                selectedEndDay = nextRange.endDay,
                                zoomed = nextRange.startDay != minDay || nextRange.endDay != maxDay
                            )
                            onDateFilterChanged(nextRange.startDay, nextRange.endDay)
                        },
                        valueRange = visibleStartDay.toFloat()..visibleEndDay.toFloat()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDateFilterFabDay(context, visibleStartDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            sliderRange = minDay.toFloat()..maxDay.toFloat()
                            sliderWindow = DateSliderWindow(minDay, maxDay)
                            onDateFilterReset()
                        }
                    ) {
                        Text(text = "Сброс")
                    }
                    Text(
                        text = formatDateFilterFabDay(context, visibleEndDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DateFilterDateLabel(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DateSliderTimelineTicks(
    minDay: Long,
    maxDay: Long,
    visibleStartDay: Long,
    visibleEndDay: Long,
    ticks: List<DateSliderTick>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val majorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPaint = remember(density, labelColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
    }
    labelPaint.color = labelColor.toArgb()
    labelPaint.textSize = with(density) { 10.dp.toPx() }

    ComposeCanvas(modifier = modifier) {
        if (visibleEndDay > visibleStartDay && maxDay > minDay) {
            val horizontalPadding = DateSliderTickHorizontalPaddingDp.dp.toPx()
            val trackStart = horizontalPadding
            val trackEnd = size.width - horizontalPadding
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
            val trackY = 32.dp.toPx().coerceAtMost(size.height - 24.dp.toPx())
            val labelBaseline = size.height - 6.dp.toPx()
            val visibleSpan = (visibleEndDay - visibleStartDay).toFloat().coerceAtLeast(1f)

            drawLine(
                color = minorColor,
                start = Offset(trackStart, trackY),
                end = Offset(trackEnd, trackY),
                strokeWidth = 1.dp.toPx()
            )

            ticks.forEach { tick ->
                if (tick.day !in visibleStartDay..visibleEndDay) {
                    return@forEach
                }

                val fraction = ((tick.day - visibleStartDay).toFloat() / visibleSpan)
                    .coerceIn(0f, 1f)
                val x = trackStart + trackWidth * fraction
                val tickHeight = if (tick.isMajor) 16.dp.toPx() else 9.dp.toPx()
                drawLine(
                    color = if (tick.isMajor) majorColor else minorColor,
                    start = Offset(x, trackY - tickHeight),
                    end = Offset(x, trackY + 2.dp.toPx()),
                    strokeWidth = if (tick.isMajor) 2.dp.toPx() else 1.dp.toPx()
                )

                val label = tick.label ?: return@forEach
                val halfLabel = labelPaint.measureText(label) / 2f
                val labelX = x.coerceIn(trackStart + halfLabel, trackEnd - halfLabel)
                drawContext.canvas.nativeCanvas.drawText(label, labelX, labelBaseline, labelPaint)
            }
        }
    }
}

private enum class DateFilterPickerTarget {
    Start,
    End
}

private data class DateSliderWindow(
    val startDay: Long,
    val endDay: Long
)

private data class DateFilterDayRange(
    val startDay: Long,
    val endDay: Long
) {
    fun toFloatRange(): ClosedFloatingPointRange<Float> {
        return startDay.toFloat()..endDay.toFloat()
    }
}

private data class DateSliderTick(
    val day: Long,
    val label: String?,
    val isMajor: Boolean
)

private fun showDateFilterPicker(
    context: Context,
    title: String,
    selectedDay: Long,
    minDay: Long,
    maxDay: Long,
    onDateSelected: (Long) -> Unit
) {
    val locale = context.dateFilterLocale()
    val selectedCalendar = Calendar.getInstance(DateFilterUtcTimeZone, locale).apply {
        timeInMillis = photoDateDayToMillis(selectedDay.coerceIn(minDay, maxDay))
    }
    val dialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val pickedCalendar = Calendar.getInstance(DateFilterUtcTimeZone, locale).apply {
                clear()
                set(year, month, dayOfMonth, 0, 0, 0)
            }
            onDateSelected(
                millisToDateFilterDay(pickedCalendar.timeInMillis).coerceIn(minDay, maxDay)
            )
        },
        selectedCalendar.get(Calendar.YEAR),
        selectedCalendar.get(Calendar.MONTH),
        selectedCalendar.get(Calendar.DAY_OF_MONTH)
    )
    dialog.setTitle(title)
    dialog.datePicker.minDate = datePickerLocalMillisForDateFilterDay(minDay, locale)
    dialog.datePicker.maxDate = datePickerLocalMillisForDateFilterDay(maxDay, locale)
    dialog.show()
}

private fun buildDateSliderWindow(
    minDay: Long,
    maxDay: Long,
    selectedStartDay: Long,
    selectedEndDay: Long,
    zoomed: Boolean
): DateSliderWindow {
    if (!zoomed || maxDay <= minDay) {
        return DateSliderWindow(minDay, maxDay)
    }

    val fullSpan = maxDay - minDay
    val start = minOf(selectedStartDay, selectedEndDay).coerceIn(minDay, maxDay)
    val end = maxOf(selectedStartDay, selectedEndDay).coerceIn(minDay, maxDay)
    if (start == minDay && end == maxDay) {
        return DateSliderWindow(minDay, maxDay)
    }

    val selectedSpan = (end - start).coerceAtLeast(1L)
    val padding = maxOf(DateSliderMinFocusedPaddingDays, selectedSpan / 2L)
    var windowStart = (start - padding).coerceAtLeast(minDay)
    var windowEnd = (end + padding).coerceAtMost(maxDay)
    val minWindowSpan = minOf(fullSpan, maxOf(DateSliderMinFocusedWindowDays, selectedSpan))

    if (windowEnd - windowStart < minWindowSpan) {
        val center = (start + end) / 2L
        windowStart = (center - minWindowSpan / 2L).coerceAtLeast(minDay)
        windowEnd = (windowStart + minWindowSpan).coerceAtMost(maxDay)
        windowStart = (windowEnd - minWindowSpan).coerceAtLeast(minDay)
    }

    return DateSliderWindow(windowStart, windowEnd)
}

private fun expandDateSliderWindowIfNeeded(
    current: DateSliderWindow,
    minDay: Long,
    maxDay: Long,
    selectedStartDay: Long,
    selectedEndDay: Long
): DateSliderWindow {
    if (current.startDay <= minDay && current.endDay >= maxDay) {
        return current
    }

    val span = (current.endDay - current.startDay).coerceAtLeast(1L)
    val edgeThreshold = maxOf(1L, (span * DateSliderEdgeExpandThresholdMultiplier).roundToLong())
    val expandBy = maxOf(DateSliderMinFocusedWindowDays, span)
    var nextStart = current.startDay
    var nextEnd = current.endDay

    if (selectedStartDay <= current.startDay + edgeThreshold && current.startDay > minDay) {
        nextStart = (current.startDay - expandBy).coerceAtLeast(minDay)
    }
    if (selectedEndDay >= current.endDay - edgeThreshold && current.endDay < maxDay) {
        nextEnd = (current.endDay + expandBy).coerceAtMost(maxDay)
    }

    return DateSliderWindow(nextStart, nextEnd)
}

private fun normalizeDateSliderRange(
    startDay: Long,
    endDay: Long,
    minDay: Long,
    maxDay: Long
): DateFilterDayRange {
    val start = minOf(startDay, endDay).coerceIn(minDay, maxDay)
    val end = maxOf(startDay, endDay).coerceIn(minDay, maxDay)
    return DateFilterDayRange(start, end)
}

private fun Long.snapToDateSliderTick(
    snapDays: List<Long>,
    visibleStartDay: Long,
    visibleEndDay: Long,
    thresholdMultiplier: Double
): Long {
    if (snapDays.isEmpty() || visibleEndDay <= visibleStartDay) {
        return this
    }

    val threshold = maxOf(
        1L,
        ((visibleEndDay - visibleStartDay) * thresholdMultiplier).roundToLong()
    )
    val nearest = snapDays
        .asSequence()
        .filter { day -> day in visibleStartDay..visibleEndDay }
        .minByOrNull { day -> abs(day - this) }
        ?: return this
    return if (abs(nearest - this) <= threshold) nearest else this
}

private fun buildDateSliderTicks(
    context: Context,
    minDay: Long,
    maxDay: Long,
    visibleStartDay: Long,
    visibleEndDay: Long
): List<DateSliderTick> {
    val start = visibleStartDay.coerceIn(minDay, maxDay)
    val end = visibleEndDay.coerceIn(minDay, maxDay)
    if (end <= start) {
        return emptyList()
    }

    val span = end - start
    val locale = context.dateFilterLocale()
    val monthFormatter = SimpleDateFormat("MMM", locale).apply {
        timeZone = DateFilterUtcTimeZone
    }
    val yearFormatter = SimpleDateFormat("yyyy", locale).apply {
        timeZone = DateFilterUtcTimeZone
    }
    val calendar = Calendar.getInstance(DateFilterUtcTimeZone, locale).apply {
        timeInMillis = photoDateDayToMillis(start)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (millisToDateFilterDay(calendar.timeInMillis) < start) {
        calendar.add(Calendar.MONTH, 1)
    }

    val ticks = mutableListOf<DateSliderTick>()
    while (millisToDateFilterDay(calendar.timeInMillis) <= end) {
        val day = millisToDateFilterDay(calendar.timeInMillis)
        val month = calendar.get(Calendar.MONTH)
        val isYear = month == Calendar.JANUARY
        val includeTick = when {
            isYear -> true
            span <= DateSliderShowEveryMonthMaxDays -> true
            span <= DateSliderShowQuarterMaxDays -> month % 3 == 0
            span <= DateSliderShowHalfYearMaxDays -> month % 6 == 0
            else -> false
        }
        if (includeTick) {
            val label = when {
                isYear -> yearFormatter.format(calendar.time)
                span <= DateSliderMonthLabelMaxDays -> monthFormatter.format(calendar.time)
                span <= DateSliderQuarterLabelMaxDays && month % 3 == 0 -> {
                    monthFormatter.format(calendar.time)
                }
                else -> null
            }
            ticks += DateSliderTick(
                day = day,
                label = label,
                isMajor = isYear
            )
        }
        calendar.add(Calendar.MONTH, 1)
    }

    return ticks.distinctBy { tick -> tick.day }.sortedBy { tick -> tick.day }
}

private fun millisToDateFilterDay(millis: Long): Long {
    return Math.floorDiv(millis, DateFilterMillisPerDay)
}

private fun datePickerLocalMillisForDateFilterDay(day: Long, locale: Locale): Long {
    val utcCalendar = Calendar.getInstance(DateFilterUtcTimeZone, locale).apply {
        timeInMillis = photoDateDayToMillis(day)
    }
    return Calendar.getInstance(locale).apply {
        clear()
        set(
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH),
            utcCalendar.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0
        )
    }.timeInMillis
}

@Composable
private fun ClusterDensityFabControl(
    clusterSettings: PhotoClusterSettings,
    onDensityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FloatingActionButton(onClick = { isExpanded = true }) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                        contentDescription = "Плотность кластеров",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${clusterSettings.densityCoefficientPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            visible = isExpanded,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            ClusterDensitySlider(
                modifier = Modifier.fillMaxWidth(),
                clusterSettings = clusterSettings,
                onDensityChanged = onDensityChanged,
                onClose = { isExpanded = false }
            )
        }
    }
}

@Composable
private fun ClusterDensitySlider(
    clusterSettings: PhotoClusterSettings,
    onDensityChanged: (Int) -> Unit,
    onClose: () -> Unit,
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
    val densityRangeStart = MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT.toFloat()
    val densityRangeEnd = MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT.toFloat()
    val densityRange = densityRangeStart..densityRangeEnd
    val sliderSteps = (
        (MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT - MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT) /
            PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT_STEP
        ).coerceAtLeast(1) - 1

    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                modifier = Modifier.weight(1f),
                value = sliderValue.coerceIn(densityRangeStart, densityRangeEnd),
                onValueChange = { value ->
                    sliderValue = value
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
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onClose
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "Скрыть плотность",
                    modifier = Modifier.size(22.dp)
                )
            }
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
    forceShowClusterGallery: Boolean,
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
            forceShowClusterGallery = forceShowClusterGallery,
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
    forceShowClusterGallery: Boolean,
    onShowPhotos: (BottomGalleryState) -> Unit
): Boolean {
    if (renderItem.isCluster) {
        val nextZoom = (cameraPosition.zoom + ClusterTapZoomStep).coerceAtMost(ClusterTapMaxZoom)
        val galleryState = bottomGalleryStateForRenderItem(
            renderItem = renderItem,
            photosById = photosById,
            zoomTarget = BottomGalleryZoomTarget(
                latitude = renderItem.latitude,
                longitude = renderItem.longitude,
                zoom = nextZoom
            )
        )
        val isSingleGeoPointCluster = galleryState?.hasSingleGeoPoint() == true
        if (forceShowClusterGallery || renderItem.photoCount >= LargeClusterGalleryThreshold || isSingleGeoPointCluster) {
            galleryState?.let(onShowPhotos)
        }
        if (isSingleGeoPointCluster) {
            AppDiagnostics.record(
                PhotoMapLogTag,
                "Open same-location cluster gallery: id=${renderItem.id}, photos=${renderItem.photoCount}"
            )
            return true
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

private fun bottomGalleryStateForPhotoIds(
    photoIds: List<Long>,
    photosById: Map<Long, DevicePhoto>,
    zoomTarget: BottomGalleryZoomTarget?
): BottomGalleryState? {
    val availablePhotoIds = photoIds.distinct().filter { photoId -> photosById.containsKey(photoId) }
    if (availablePhotoIds.isEmpty()) {
        return null
    }

    return BottomGalleryState(
        photos = availablePhotoIds
            .mapNotNull { photoId -> photosById[photoId] },
        totalCount = availablePhotoIds.size,
        allPhotoIds = availablePhotoIds,
        zoomTarget = zoomTarget
    )
}

private fun BottomGalleryState.hasSingleGeoPoint(): Boolean {
    if (photos.size < 2) {
        return false
    }
    val points = photos.mapNotNull { photo -> photo.toMapPoint() }
    if (points.size != photos.size) {
        return false
    }
    val firstPoint = points.first()
    return points.drop(1).all { point ->
        haversineDistanceKm(
            firstLatitude = firstPoint.latitude,
            firstLongitude = firstPoint.longitude,
            secondLatitude = point.latitude,
            secondLongitude = point.longitude
        ) <= SameGeoPointClusterDistanceKm
    }
}

private fun bottomGalleryStateForRenderItem(
    renderItem: PhotoMapRenderItem,
    photosById: Map<Long, DevicePhoto>,
    zoomTarget: BottomGalleryZoomTarget?
): BottomGalleryState? {
    return bottomGalleryStateForPhotoIds(
        photoIds = renderItem.galleryPhotoIds(photosById),
        photosById = photosById,
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

private suspend fun MapLibreMap.buildPhotoMapRenderState(
    mapItems: List<VisiblePhotoMapItem>,
    photosById: Map<Long, DevicePhoto>,
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
        val effectiveClusterRadiusPx = effectiveClusterRadiusPx(
            width = width,
            height = height,
            zoom = cameraPosition.zoom,
            settings = settings
        )
        val projectedPhotos = projectVisibleMapPhotos(
            mapItems = mapItems,
            photosById = photosById,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY,
            centerX = centerX,
            centerY = centerY
        )
        val projectedFallbackItems = if (projectedPhotos.isEmpty()) {
            projectVisibleMapItems(
                mapItems = mapItems,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                centerX = centerX,
                centerY = centerY
            )
        } else {
            emptyList()
        }
        val zoom = cameraPosition.zoom

        withContext(Dispatchers.Default) {
            val projectedItems = if (projectedPhotos.isNotEmpty()) {
                clusterProjectedPhotos(
                    projectedPhotos = projectedPhotos,
                    radiusPx = effectiveClusterRadiusPx,
                    minPoints = settings.minPoints.coerceAtLeast(2),
                    maxDistanceKm = clusterMaxDistanceKm(
                        zoom = zoom,
                        settings = settings
                    )
                )
                    .map { item -> item.withDistanceFromCenter(centerX, centerY) }
                    .sortedBy { item -> item.distanceFromCenter }
            } else {
                projectedFallbackItems
            }
            val renderItems = compactDenseRenderItems(
                items = projectedItems,
                width = width,
                height = height,
                centerX = centerX,
                centerY = centerY
            )
            val clusterFeatures = renderItems
                .filter { item -> item.isCluster }
                .map { item -> item.toClusterFeature() }
            val clusterThumbnailRequests = renderItems
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
                items = renderItems,
                width = width,
                height = height,
                settings = settings
            )

            PhotoMapRenderState(
                items = renderItems,
                thumbnailPhotoIds = thumbnailPhotoIds,
                clusterThumbnailRequests = clusterThumbnailRequests,
                clusterFeatureCollection = FeatureCollection.fromFeatures(clusterFeatures),
                projectedPhotoCount = projectedItems.size,
                viewportWidth = width,
                viewportHeight = height,
                effectiveClusterRadiusPx = effectiveClusterRadiusPx
            )
        }
    }.onFailure { error ->
        if (error is CancellationException) {
            throw error
        }
        Log.w(PhotoMapLogTag, "Failed to build photo map render state", error)
    }.getOrDefault(PhotoMapRenderState.Empty)
}

private fun MapLibreMap.reprojectPhotoMapRenderState(
    renderState: PhotoMapRenderState,
    width: Int,
    height: Int
): PhotoMapRenderState {
    if (width <= 0 || height <= 0 || renderState.items.isEmpty()) {
        return renderState
    }
    val centerX = width / 2f
    val centerY = height / 2f
    val reprojectedItems = renderState.items.map { item ->
        val screenPoint = projection.toScreenLocation(LatLng(item.latitude, item.longitude))
        item.copy(
            screenX = screenPoint.x,
            screenY = screenPoint.y
        ).withDistanceFromCenter(centerX, centerY)
    }
    return renderState.copy(
        items = reprojectedItems,
        viewportWidth = width,
        viewportHeight = height
    )
}

private suspend fun compactDenseRenderItems(
    items: List<PhotoMapRenderItem>,
    width: Int,
    height: Int,
    centerX: Float,
    centerY: Float
): List<PhotoMapRenderItem> {
    if (width <= 0 || height <= 0 || items.size < DenseRenderMinClusterItems) {
        return items
    }

    val radiusPx = DenseRenderClusterRadiusPx.coerceAtLeast(1f)
    val clusters = mutableListOf<MutableRenderItemCluster>()
    val clustersByCell = mutableMapOf<ScreenGridCell, MutableList<MutableRenderItemCluster>>()
    val sortedItems = items.sortedBy { item -> item.distanceFromCenter }
    for (index in sortedItems.indices) {
        if (index % RenderCancellationCheckInterval == 0) {
            currentCoroutineContext().ensureActive()
        }
        val item = sortedItems[index]
        val cell = item.screenCell(radiusPx)
        val targetCluster = cell.neighbors()
            .asSequence()
            .flatMap { neighbor -> clustersByCell[neighbor].orEmpty().asSequence() }
            .distinct()
            .filter { cluster -> cluster.screenDistanceSquaredTo(item) <= radiusPx * radiusPx }
            .minByOrNull { cluster -> cluster.screenDistanceSquaredTo(item) }

        if (targetCluster == null) {
            val cluster = MutableRenderItemCluster(item, cell)
            clusters += cluster
            clustersByCell.getOrPut(cell) { mutableListOf() }.add(cluster)
        } else {
            val previousCell = targetCluster.cell
            targetCluster.add(item)
            val nextCell = targetCluster.screenCell(radiusPx)
            if (nextCell != previousCell) {
                clustersByCell[previousCell]?.remove(targetCluster)
                clustersByCell.getOrPut(nextCell) { mutableListOf() }.add(targetCluster)
                targetCluster.cell = nextCell
            }
        }
    }

    return clusters.flatMapIndexed { index, cluster ->
        if (cluster.shouldMerge) {
            listOf(cluster.toRenderItem(index = index, centerX = centerX, centerY = centerY))
        } else {
            cluster.items
        }
    }.sortedBy { item -> item.distanceFromCenter }
}

private class MutableRenderItemCluster(
    firstItem: PhotoMapRenderItem,
    var cell: ScreenGridCell
) {
    private val mutableItems = mutableListOf(firstItem)
    var screenX: Float = firstItem.screenX
        private set
    var screenY: Float = firstItem.screenY
        private set

    val items: List<PhotoMapRenderItem>
        get() = mutableItems

    val shouldMerge: Boolean
        get() = mutableItems.size >= DenseRenderMinItemsPerCluster ||
            mutableItems.count { item -> item.isCluster } >= DenseRenderMinClusterItems

    fun add(item: PhotoMapRenderItem) {
        mutableItems += item
        val totalWeight = mutableItems.sumOf { renderItem -> renderItem.photoCount.coerceAtLeast(1) }
            .toDouble()
            .coerceAtLeast(1.0)
        screenX = (
            mutableItems.sumOf { renderItem ->
                renderItem.screenX.toDouble() * renderItem.photoCount.coerceAtLeast(1)
            } / totalWeight
            ).toFloat()
        screenY = (
            mutableItems.sumOf { renderItem ->
                renderItem.screenY.toDouble() * renderItem.photoCount.coerceAtLeast(1)
            } / totalWeight
            ).toFloat()
    }

    fun screenDistanceSquaredTo(item: PhotoMapRenderItem): Float {
        val dx = item.screenX - screenX
        val dy = item.screenY - screenY
        return dx * dx + dy * dy
    }

    fun screenCell(cellSize: Float): ScreenGridCell {
        return ScreenGridCell(
            x = floor(screenX / cellSize).toInt(),
            y = floor(screenY / cellSize).toInt()
        )
    }

    fun toRenderItem(index: Int, centerX: Float, centerY: Float): PhotoMapRenderItem {
        return mutableItems.toDenseRenderCluster(index = index, centerX = centerX, centerY = centerY)
    }
}

private fun List<PhotoMapRenderItem>.toDenseRenderCluster(
    index: Int,
    centerX: Float,
    centerY: Float
): PhotoMapRenderItem {
    val canUseExactPhotoIds = all { item -> item.photoIds.isNotEmpty() || item.photoCount <= 1 }
    val mergedPhotoIds = if (canUseExactPhotoIds) {
        flatMap { item -> item.tappablePhotoIds }.distinct()
    } else {
        emptyList()
    }
    val mergedPhotoCount = mergedPhotoIds.size.takeIf { count -> count > 0 } ?: sumOf { item -> item.photoCount }
    val totalWeight = sumOf { item -> item.photoCount.coerceAtLeast(1) }.toDouble().coerceAtLeast(1.0)
    val weightedLatitude = sumOf { item -> item.latitude * item.photoCount.coerceAtLeast(1) } / totalWeight
    val weightedLongitude = sumOf { item -> item.longitude * item.photoCount.coerceAtLeast(1) } / totalWeight
    val weightedScreenX = (sumOf { item -> item.screenX.toDouble() * item.photoCount.coerceAtLeast(1) } / totalWeight).toFloat()
    val weightedScreenY = (sumOf { item -> item.screenY.toDouble() * item.photoCount.coerceAtLeast(1) } / totalWeight).toFloat()
    val representativeItem = minByOrNull { item -> item.distanceFromCenter } ?: first()

    return PhotoMapRenderItem(
        id = "dense-$index-${representativeItem.id}",
        level = maxOf { item -> item.level },
        latitude = weightedLatitude,
        longitude = weightedLongitude,
        photoCount = mergedPhotoCount,
        priorityScore = sumOf { item -> item.priorityScore },
        minLatitude = minOf { item -> item.minLatitude },
        maxLatitude = maxOf { item -> item.maxLatitude },
        minLongitude = minOf { item -> item.minLongitude },
        maxLongitude = maxOf { item -> item.maxLongitude },
        coverPhotoId = representativeItem.representativePhotoId,
        screenX = weightedScreenX,
        screenY = weightedScreenY,
        photoIds = mergedPhotoIds
    ).withDistanceFromCenter(centerX, centerY)
}

private fun MapLibreMap.projectVisibleMapPhotos(
    mapItems: List<VisiblePhotoMapItem>,
    photosById: Map<Long, DevicePhoto>,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
    centerX: Float,
    centerY: Float
): List<ProjectedMapPhoto> {
    val projectedByPhotoId = LinkedHashMap<Long, ProjectedMapPhoto>()
    mapItems.forEach { item ->
        val photoIds = when {
            item.photoIds.isNotEmpty() -> item.photoIds
            item.photoCount <= 1 -> item.coverPhotoId?.let(::listOf).orEmpty()
            else -> emptyList()
        }
        photoIds.forEach { photoId ->
            if (projectedByPhotoId.containsKey(photoId)) {
                return@forEach
            }
            val photo = photosById[photoId] ?: return@forEach
            val projectedPhoto = projectVisiblePhoto(
                photo = photo,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                centerX = centerX,
                centerY = centerY
            ) ?: return@forEach
            projectedByPhotoId[photoId] = projectedPhoto
        }
    }
    return projectedByPhotoId.values.sortedBy { photo -> photo.distanceFromCenter }
}

private fun MapLibreMap.projectVisiblePhoto(
    photo: DevicePhoto,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
    centerX: Float,
    centerY: Float
): ProjectedMapPhoto? {
    val photoPoint = photo.toMapPoint() ?: return null
    val screenPoint = projection.toScreenLocation(LatLng(photoPoint.latitude, photoPoint.longitude))
    if (screenPoint.x !in minX..maxX || screenPoint.y !in minY..maxY) {
        return null
    }

    val dx = screenPoint.x - centerX
    val dy = screenPoint.y - centerY
    return ProjectedMapPhoto(
        photo = photo,
        point = photoPoint,
        screenX = screenPoint.x,
        screenY = screenPoint.y,
        distanceFromCenter = dx * dx + dy * dy
    )
}

private fun MapLibreMap.projectVisibleMapItems(
    mapItems: List<VisiblePhotoMapItem>,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
    centerX: Float,
    centerY: Float
): List<PhotoMapRenderItem> {
    return mapItems.asSequence()
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
        .sortedBy { item -> item.distanceFromCenter }
        .toList()
}

private suspend fun clusterProjectedPhotos(
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
    for (index in projectedPhotos.indices) {
        if (index % RenderCancellationCheckInterval == 0) {
            currentCoroutineContext().ensureActive()
        }
        val projectedPhoto = projectedPhotos[index]
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

private fun PhotoMapRenderItem.withDistanceFromCenter(
    centerX: Float,
    centerY: Float
): PhotoMapRenderItem {
    val dx = screenX - centerX
    val dy = screenY - centerY
    return copy(distanceFromCenter = dx * dx + dy * dy)
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
    val densityScale = settings.densityCoefficientPercent.coerceIn(80, 320) / 100f
    return (max(baseRadius, viewportRadius) * densityScale)
        .coerceAtMost(viewportMaxSide)
}

private fun clusterMaxDistanceKm(
    zoom: Double,
    settings: PhotoClusterSettings
): Double {
    val densityScale = settings.densityCoefficientPercent.coerceIn(80, 320) / 100.0
    return (zoom.coerceAtLeast(0.1) * densityScale).coerceAtLeast(0.1)
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

private fun compactDateFilterLabel(context: Context, dateFilter: PhotoDateFilter): String {
    val startDay = dateFilter.selectedStartDay ?: return DateFabFallbackLabel
    val endDay = dateFilter.selectedEndDay ?: return DateFabFallbackLabel
    return "${formatDateFilterFabDay(context, startDay)} - ${formatDateFilterFabDay(context, endDay)}"
}

private fun formatDateFilterSliderDay(context: Context, day: Long): String {
    return SimpleDateFormat(DateFilterSliderPattern, context.dateFilterLocale()).apply {
        timeZone = DateFilterUtcTimeZone
    }
        .format(Date(photoDateDayToMillis(day)))
}

private fun formatDateFilterFabDay(context: Context, day: Long): String {
    return SimpleDateFormat(DateFilterFabPattern, context.dateFilterLocale()).apply {
        timeZone = DateFilterUtcTimeZone
    }
        .format(Date(photoDateDayToMillis(day)))
}

private fun Context.dateFilterLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.size() > 0) {
        locales.get(0)
    } else {
        Locale.getDefault()
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
private const val DenseRenderClusterRadiusPx = 118f
private const val DenseRenderMinItemsPerCluster = 3
private const val DenseRenderMinClusterItems = 2
private const val ClusterTapZoomStep = 2.0
private const val ClusterTapMaxZoom = 20.0
private const val LargeClusterGalleryThreshold = 50
private const val SameGeoPointClusterDistanceKm = 0.001
private const val ClusterThumbnailBlurSamplePx = 16
private const val RenderCancellationCheckInterval = 128
private const val DateFilterSliderPattern = "dd MMMM yyyy"
private const val DateFilterFabPattern = "dd.MM.yy"
private const val DateFilterMillisPerDay = 24L * 60L * 60L * 1000L
private const val DateSliderMinFocusedPaddingDays = 7L
private const val DateSliderMinFocusedWindowDays = 45L
private const val DateSliderTickHorizontalPaddingDp = 18f
private const val DateSliderEdgeExpandThresholdMultiplier = 0.12
private const val DateSliderDragSnapThresholdMultiplier = 0.018
private const val DateSliderReleaseSnapThresholdMultiplier = 0.026
private const val DateSliderMonthLabelMaxDays = 185L
private const val DateSliderQuarterLabelMaxDays = 730L
private const val DateSliderShowEveryMonthMaxDays = 730L
private const val DateSliderShowQuarterMaxDays = 1460L
private const val DateSliderShowHalfYearMaxDays = 2920L
private const val DateFabFallbackLabel = "\u0414\u0430\u0442\u0430"
private const val CityWideClusterZoom = 11.0
private const val DistrictClusterZoom = 12.5
private const val NeighborhoodClusterZoom = 14.0
private const val EarthRadiusKm = 6371.0
private const val PhotoMapLogTag = "PhotoMapMap"

private const val LandFillColor = "#E7ECE6"

private val DateFilterUtcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

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
