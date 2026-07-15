package com.example.photomap.ui.map

import android.content.Context
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.photomap.R
import com.example.photomap.core.settings.PhotoClusterSettings
import com.example.photomap.domain.model.DevicePhoto
import java.util.Date
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun PhotoMapScreen(
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    clusterSettings: PhotoClusterSettings,
    isScanning: Boolean,
    isScanPaused: Boolean,
    scanProcessed: Int,
    scanTotal: Int,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit
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
                clusterSettings = normalizedClusterSettings,
                isScanning = isScanning,
                isScanPaused = isScanPaused,
                scanProcessed = scanProcessed,
                scanTotal = scanTotal,
                onBack = onBack,
                onScan = onScan,
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
                    onBack = onBack
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    PhotoMapLibreMap(
                        photos = photosWithLocation,
                        mapStyleUrl = mapStyleUrl,
                        clusterSettings = normalizedClusterSettings,
                        centerRequestKey = centerRequestKey
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
    clusterSettings: PhotoClusterSettings,
    isScanning: Boolean,
    isScanPaused: Boolean,
    scanProcessed: Int,
    scanTotal: Int,
    onBack: () -> Unit,
    onScan: () -> Unit,
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
            Text(
                text = "Карта",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Всего: $totalPhotos, с координатами: $photosWithLocation",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Кластеры: ${clusterSettings.radiusPx}px, минимум ${clusterSettings.minPoints}",
                style = MaterialTheme.typography.bodySmall
            )
            if (isScanning) {
                Text(
                    text = scanStatusText(isScanPaused, scanProcessed, scanTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(text = "Назад")
                }
                OutlinedButton(onClick = onCenterMap) {
                    Text(text = "Центр")
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_24),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "Настройки")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScan,
                    enabled = !isScanning
                ) {
                    Text(text = "Сканировать")
                }
                if (isScanning) {
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
    text: String,
    onBack: () -> Unit
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
                OutlinedButton(onClick = onBack) {
                    Text(text = "Вернуться")
                }
            }
        }
    }
}

@Composable
private fun PhotoMapLibreMap(
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    clusterSettings: PhotoClusterSettings,
    centerRequestKey: Int
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
    var hasFitCamera by remember { mutableStateOf(false) }
    var bottomGalleryState by remember { mutableStateOf<BottomGalleryState?>(null) }
    val photosById = remember(photos) {
        photos.associateBy { photo -> photo.mediaId }
    }
    val latestPhotosById = rememberUpdatedState(photosById)
    val latestClusterSettings = rememberUpdatedState(clusterSettings)
    val featureCollection by produceState(
        initialValue = emptyPhotoFeatureCollection(),
        photos
    ) {
        value = withContext(Dispatchers.Default) {
            val collection = PhotoMapFeatureMapper.toFeatureCollection(photos)
            Log.d(PhotoMapLogTag, "GeoJSON built: photos=${photos.size}, features=${collection.features()?.size}")
            collection
        }
    }
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
                map = mapLibreMap
                mapLibreMap.configureGestures()
                mapLibreMap.addOnCameraIdleListener {
                    thumbnailRefreshKey += 1
                }
                mapLibreMap.addOnMapClickListener { point ->
                    runCatching {
                        mapLibreMap.handlePhotoMapClick(
                            point = point,
                            photosById = latestPhotosById.value,
                            clusterSettings = latestClusterSettings.value,
                            onShowPhotos = { state -> bottomGalleryState = state }
                        )
                    }.onFailure { error ->
                        Log.e(PhotoMapLogTag, "Map click handling failed", error)
                    }.getOrDefault(false)
                }
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    Log.d(PhotoMapLogTag, "Map style loaded")
                    runCatching {
                        style.removePoliticalBoundaryLayers()
                    }.onFailure { error ->
                        Log.w(PhotoMapLogTag, "Failed to remove boundary layers", error)
                    }
                    layerController.reset()
                    thumbnailImageRegistry.clear()
                    isStyleReady = true
                    styleGeneration += 1
                    thumbnailRefreshKey += 1
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
        featureCollection,
        clusterSettings,
        layerColors
    ) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }

        mapView.post {
            val style = mapLibreMap.getStyle() ?: return@post
            runCatching {
                layerController.update(
                    style = style,
                    featureCollection = featureCollection,
                    settings = clusterSettings,
                    colors = layerColors,
                    mapMaxZoom = mapLibreMap.getMaxZoomLevel()
                )
            }.onSuccess {
                Log.d(
                    PhotoMapLogTag,
                    "Map layers updated: features=${featureCollection.features()?.size}, " +
                        "radius=${clusterSettings.radiusPx}, minPoints=${clusterSettings.minPoints}, " +
                        "mapMaxZoom=${mapLibreMap.getMaxZoomLevel()}"
                )
                thumbnailRefreshKey += 1
            }.onFailure { error ->
                Log.e(PhotoMapLogTag, "Failed to update MapLibre photo layers", error)
            }
        }
    }

    LaunchedEffect(
        map,
        isStyleReady,
        styleGeneration,
        thumbnailRefreshKey,
        photosById,
        layerColors
    ) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || mapView.width <= 0 || mapView.height <= 0) {
            return@LaunchedEffect
        }

        val visiblePhotoIds = mapLibreMap.visibleThumbnailCandidatePhotoIds(
            photos = photosById.values,
            width = mapView.width,
            height = mapView.height
        )
        val visibleThumbnailFeatureCollection = withContext(Dispatchers.Default) {
            PhotoMapFeatureMapper.toFeatureCollection(
                visiblePhotoIds.mapNotNull { photoId -> photosById[photoId] }
            )
        }
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        layerController.updateVisibleThumbnails(
            style = style,
            featureCollection = visibleThumbnailFeatureCollection
        )
        val missingPhotoIds = visiblePhotoIds
            .filter { photoId -> !thumbnailImageRegistry.contains(photoThumbnailImageKey(photoId)) }
            .take(MaxThumbnailImagesPerPass)
        Log.d(
            PhotoMapLogTag,
            "Thumbnail refresh: visibleCandidates=${visiblePhotoIds.size}, " +
                "missing=${missingPhotoIds.size}, cached=${thumbnailImageRegistry.size}"
        )
        if (missingPhotoIds.isEmpty()) {
            return@LaunchedEffect
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
        thumbnailImages.forEach { (key, bitmap) ->
            val added = runCatching {
                style.addImage(key, bitmap)
                thumbnailImageRegistry.markAdded(style, key)
            }.onFailure { error ->
                Log.w(PhotoMapLogTag, "Failed to add thumbnail image to style: key=$key", error)
            }
            if (added.isSuccess) {
                addedCount += 1
            }
        }
        Log.d(
            PhotoMapLogTag,
            "Thumbnail images registered: added=$addedCount, decoded=${thumbnailImages.size}, cached=${thumbnailImageRegistry.size}"
        )
        if (addedCount > 0) {
            runCatching {
                layerController.updateVisibleThumbnails(
                    style = style,
                    featureCollection = visibleThumbnailFeatureCollection
                )
            }.onFailure { error ->
                Log.w(PhotoMapLogTag, "Failed to refresh thumbnails after image registration", error)
            }
        }
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

        bottomGalleryState?.let { state ->
            BottomPhotoGallery(
                state = state,
                onLoadMore = {
                    val mapLibreMap = map ?: return@BottomPhotoGallery
                    val nextState = mapLibreMap.loadNextClusterPage(
                        state = state,
                        photosById = latestPhotosById.value,
                        pageSize = latestClusterSettings.value.leavesPageSize
                    )
                    if (nextState != null) {
                        bottomGalleryState = nextState
                    }
                },
                onClose = { bottomGalleryState = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun BottomPhotoGallery(
    state: BottomGalleryState,
    onLoadMore: () -> Unit,
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
                OutlinedButton(onClick = onClose) {
                    Text(text = "Закрыть")
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.photos,
                    key = { photo -> photo.mediaId }
                ) { photo ->
                    BottomPhotoThumbnail(photo = photo)
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
private fun BottomPhotoThumbnail(photo: DevicePhoto) {
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
            modifier = Modifier.size(76.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(76.dp),
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
    photosById: Map<Long, DevicePhoto>,
    clusterSettings: PhotoClusterSettings,
    onShowPhotos: (BottomGalleryState) -> Unit
): Boolean {
    val photoIds = photoIdsNearTap(
        point = point,
        photos = photosById.values,
        hitSlopPx = MapTapHitSlopPx,
        maxResults = clusterSettings.leavesPageSize
    )
    Log.d(PhotoMapLogTag, "Photo projection hit-test: photoIds=${photoIds.distinct().size}")

    return when (val action = PhotoMapClickDecision.forUnclustered(photoIds)) {
        PhotoMapTapAction.NoAction,
        is PhotoMapTapAction.ZoomToCluster,
        is PhotoMapTapAction.ShowClusterPhotos -> false

        is PhotoMapTapAction.OpenPhoto -> {
            val photo = photosById[action.photoId] ?: return false
            onShowPhotos(BottomGalleryState(photos = listOf(photo)))
            true
        }

        is PhotoMapTapAction.ShowPhotos -> {
            val photos = action.photoIds.mapNotNull { photoId -> photosById[photoId] }
            if (photos.isNotEmpty()) {
                onShowPhotos(BottomGalleryState(photos = photos))
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

private fun MapLibreMap.loadNextClusterPage(
    state: BottomGalleryState,
    photosById: Map<Long, DevicePhoto>,
    pageSize: Int
): BottomGalleryState? {
    val clusterFeature = state.clusterFeature ?: return null
    if (!state.canLoadMore) {
        return null
    }

    val photos = loadClusterPhotos(
        clusterFeature = clusterFeature,
        photosById = photosById,
        pageSize = pageSize,
        offset = state.photos.size.toLong()
    )
    if (photos.isEmpty()) {
        return state.copy(totalCount = state.photos.size)
    }

    return state.copy(
        photos = (state.photos + photos).distinctBy { photo -> photo.mediaId }
    )
}

private fun MapLibreMap.loadClusterPhotos(
    clusterFeature: Feature,
    photosById: Map<Long, DevicePhoto>,
    pageSize: Int,
    offset: Long
): List<DevicePhoto> {
    val source = getStyle()?.getSourceAs<GeoJsonSource>(PHOTO_MAP_SOURCE_ID)
    if (source == null) {
        Log.w(PhotoMapLogTag, "Cluster leaves requested before source is available")
        return emptyList()
    }
    val leaves = runCatching {
        source.getClusterLeaves(clusterFeature, pageSize.toLong(), offset).features()
    }.onFailure { error ->
        Log.e(PhotoMapLogTag, "Failed to load cluster leaves: pageSize=$pageSize, offset=$offset", error)
    }.getOrNull().orEmpty()

    return leaves.mapNotNull { feature ->
        feature.photoId()?.let { photoId -> photosById[photoId] }
    }
}

private fun Feature.clusterLatLngOr(fallback: LatLng): LatLng {
    val point = geometry() as? Point ?: return fallback
    return LatLng(point.latitude(), point.longitude())
}

private fun Feature.pointCount(): Int? {
    return getNumberProperty("point_count")?.toInt()
}

private fun MapLibreMap.visibleThumbnailCandidatePhotoIds(
    photos: Collection<DevicePhoto>,
    width: Int,
    height: Int
): List<Long> {
    if (width <= 0 || height <= 0 || photos.isEmpty()) {
        return emptyList()
    }

    val minX = -MapThumbnailPreloadPaddingPx
    val minY = -MapThumbnailPreloadPaddingPx
    val maxX = width + MapThumbnailPreloadPaddingPx
    val maxY = height + MapThumbnailPreloadPaddingPx
    val centerX = width / 2f
    val centerY = height / 2f
    return runCatching {
        photos.asSequence()
            .mapNotNull { photo ->
                val point = photo.toMapPoint() ?: return@mapNotNull null
                val screenPoint = projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                if (screenPoint.x !in minX..maxX || screenPoint.y !in minY..maxY) {
                    return@mapNotNull null
                }

                val dx = screenPoint.x - centerX
                val dy = screenPoint.y - centerY
                VisiblePhotoCandidate(
                    photoId = photo.mediaId,
                    distanceFromCenter = dx * dx + dy * dy
                )
            }
            .sortedBy { candidate -> candidate.distanceFromCenter }
            .take(MaxThumbnailCandidatePhotoIds)
            .map { candidate -> candidate.photoId }
            .toList()
    }.onFailure { error ->
        Log.w(PhotoMapLogTag, "Failed to calculate visible thumbnail candidates", error)
    }.getOrDefault(emptyList())
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

private data class VisiblePhotoCandidate(
    val photoId: Long,
    val distanceFromCenter: Float
)

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

private fun emptyPhotoFeatureCollection(): FeatureCollection {
    return FeatureCollection.fromFeatures(emptyList<Feature>())
}

private data class BottomGalleryState(
    val photos: List<DevicePhoto>,
    val totalCount: Int? = null,
    val clusterFeature: Feature? = null
) {
    val canLoadMore: Boolean
        get() = clusterFeature != null && totalCount != null && photos.size < totalCount

    val title: String
        get() = when {
            totalCount != null && canLoadMore -> "Фотографий: ${photos.size} из $totalCount"
            totalCount != null -> "Фотографий: $totalCount"
            photos.size == 1 -> "Фотография"
            else -> "Фотографий: ${photos.size}"
        }
}

private const val MapBoundsPaddingPx = 120
private const val InitialSinglePhotoZoom = 14.0
private const val MapTapHitSlopPx = 84f
private const val BottomGalleryThumbnailPx = 160
private const val MapThumbnailWidthDp = 56f
private const val MapThumbnailHeightDp = 56f
private const val MapThumbnailBorderPx = 4f
private const val MapThumbnailCornerRadiusPx = 14f
private const val MapThumbnailInnerCornerRadiusPx = 10f
private const val MapThumbnailPreloadPaddingPx = 96f
private const val MaxCachedMapThumbnailImages = 360
private const val MaxThumbnailCandidatePhotoIds = 240
private const val MaxThumbnailImagesPerPass = 80
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
