package com.example.photomap.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.photomap.domain.model.DevicePhoto
import java.util.Date
import kotlin.math.floor
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon as MapIcon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor

@Composable
fun PhotoMapScreen(
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    thumbnailThreshold: Int,
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
    val photosWithLocation = remember(photos) {
        photos.filter { photo -> photo.latitude != null && photo.longitude != null }
    }
    var centerRequestKey by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MapTopBar(
                totalPhotos = photos.size,
                photosWithLocation = photosWithLocation.size,
                thumbnailThreshold = thumbnailThreshold,
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
                        thumbnailThreshold = thumbnailThreshold,
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
    thumbnailThreshold: Int,
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
                text = "Всего: $totalPhotos, с координатами: $photosWithLocation, порог миниатюр: $thumbnailThreshold",
                style = MaterialTheme.typography.bodyMedium
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
                    Text(text = "\u0426\u0435\u043d\u0442\u0440")
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
    thumbnailThreshold: Int,
    centerRequestKey: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var zoomBucket by remember { mutableStateOf(DefaultZoomBucket) }
    var hasFitCamera by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    var bottomGalleryPhotos by remember { mutableStateOf<List<DevicePhoto>>(emptyList()) }
    val heatIconFactory = remember(context) {
        HeatIconFactory(context.applicationContext)
    }
    val thumbnailIconFactory = remember(context) {
        ThumbnailIconFactory(context.applicationContext)
    }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.configureGestures()
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    style.removePoliticalBoundaryLayers()
                    isStyleReady = true
                }
                mapLibreMap.addOnCameraIdleListener {
                    zoomBucket = mapLibreMap.cameraPosition.zoom.toZoomBucket()
                    visibleBounds = mapLibreMap.projection.visibleRegion.latLngBounds
                }
            }
        }
    }
    val markers = remember(photos, zoomBucket, visibleBounds, thumbnailThreshold) {
        photos
            .toMapMarkers(
                zoomBucket = zoomBucket,
                thumbnailThreshold = thumbnailThreshold
            )
            .filterByBounds(visibleBounds)
    }
    val cameraPositions = remember(photos) {
        photos.mapNotNull { photo ->
            val latitude = photo.latitude
            val longitude = photo.longitude
            if (latitude != null && longitude != null) {
                LatLng(latitude, longitude)
            } else {
                null
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

    LaunchedEffect(map, isStyleReady, markers, cameraPositions, thumbnailThreshold, zoomBucket) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }

        mapView.post {
            val markerLookup = mapLibreMap.renderMarkers(
                markers = markers,
                cameraPositions = cameraPositions,
                fitCamera = !hasFitCamera,
                heatIconFactory = heatIconFactory,
                thumbnailIconFactory = thumbnailIconFactory,
                thumbnailThreshold = thumbnailThreshold,
                zoomBucket = zoomBucket,
                clientWidth = mapView.width,
                clientHeight = mapView.height
            )
            mapLibreMap.setOnMarkerClickListener { marker ->
                val photoMarker = markerLookup[marker.id] ?: return@setOnMarkerClickListener false
                if (photoMarker.type == PhotoMapMarkerType.SinglePhoto) {
                    bottomGalleryPhotos = photoMarker.photos
                } else {
                    bottomGalleryPhotos = emptyList()
                    mapLibreMap.fitMarkerPhotos(photoMarker)
                }
                true
            }
        }

        if (cameraPositions.isNotEmpty()) {
            hasFitCamera = true
        }
    }

    LaunchedEffect(centerRequestKey, map, isStyleReady, cameraPositions) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || centerRequestKey == 0) {
            return@LaunchedEffect
        }

        mapView.post {
            mapLibreMap.fitPositions(cameraPositions, MapBoundsPaddingPx)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )

        if (bottomGalleryPhotos.isNotEmpty()) {
            BottomPhotoGallery(
                photos = bottomGalleryPhotos,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun BottomPhotoGallery(
    photos: List<DevicePhoto>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            photos.take(BottomGalleryPreviewCount).forEach { photo ->
                BottomPhotoThumbnail(photo = photo)
            }

            val firstPhoto = photos.firstOrNull()
            if (firstPhoto != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = firstPhoto.displayName ?: "Фото ${firstPhoto.mediaId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = firstPhoto.dateTaken?.let { millis ->
                            DateFormat.getDateFormat(LocalContext.current).format(Date(millis))
                        } ?: "Дата съемки неизвестна",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPhotoThumbnail(photo: DevicePhoto) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = runCatching {
            context.contentResolver.loadThumbnail(
                Uri.parse(photo.uri),
                Size(BottomGalleryThumbnailPx, BottomGalleryThumbnailPx),
                null
            )
        }.getOrNull()
    }

    if (thumbnail != null) {
        Image(
            bitmap = requireNotNull(thumbnail).asImageBitmap(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(76.dp)
        )
    } else {
        Box(modifier = Modifier.size(76.dp))
    }
}

private fun MapLibreMap.renderMarkers(
    markers: List<PhotoMapMarker>,
    cameraPositions: List<LatLng>,
    fitCamera: Boolean,
    heatIconFactory: HeatIconFactory,
    thumbnailIconFactory: ThumbnailIconFactory,
    thumbnailThreshold: Int,
    zoomBucket: Int,
    clientWidth: Int,
    clientHeight: Int
): Map<Long, PhotoMapMarker> {
    clear()
    val markerLookup = mutableMapOf<Long, PhotoMapMarker>()
    val visibleMarkers = expandMarkersForClientWindow(
        markers = markers,
        zoomBucket = zoomBucket,
        clientWidth = clientWidth,
        clientHeight = clientHeight
    )

    visibleMarkers.forEach { marker ->
        val icon = when (marker.type) {
            PhotoMapMarkerType.Heat -> heatIconFactory.iconFor(
                count = marker.count,
                threshold = thumbnailThreshold
            )
            PhotoMapMarkerType.Thumbnail,
            PhotoMapMarkerType.SinglePhoto -> thumbnailIconFactory.iconFor(
                marker = marker,
                zoomBucket = zoomBucket
            )
        }
        val options = MarkerOptions()
            .position(marker.position)
            .title(marker.title)
            .snippet(marker.snippet)
            .icon(icon)

        val mapMarker = addMarker(options)
        markerLookup[mapMarker.id] = marker
    }

    if (fitCamera) {
        fitPositions(cameraPositions, MapBoundsPaddingPx)
    }

    return markerLookup
}

private fun MapLibreMap.expandMarkersForClientWindow(
    markers: List<PhotoMapMarker>,
    zoomBucket: Int,
    clientWidth: Int,
    clientHeight: Int
): List<PhotoMapMarker> {
    if (clientWidth <= 0 || clientHeight <= 0) {
        return markers
    }

    return markers.flatMap { marker ->
        when {
            marker.count == 1 -> listOf(marker)
            hasSpaceForSinglePhotos(marker, zoomBucket, clientWidth, clientHeight) -> {
                marker.photos.map { photo -> photo.toSinglePhotoMarker() }
            }
            isInsideClientWindow(marker.position, clientWidth, clientHeight, MarkerClientPaddingPx) -> {
                listOf(marker)
            }
            else -> {
                marker.photos
                    .filter { photo -> isInsideClientWindow(photo, clientWidth, clientHeight, MarkerClientPaddingPx) }
                    .map { photo -> photo.toSinglePhotoMarker() }
            }
        }
    }
}

private fun MapLibreMap.hasSpaceForSinglePhotos(
    marker: PhotoMapMarker,
    zoomBucket: Int,
    clientWidth: Int,
    clientHeight: Int
): Boolean {
    if (marker.count > MaxAutoSplitPhotos || marker.photoPositions.size <= 1) {
        return false
    }

    val size = zoomBucket.thumbnailIconSize()
    val markerPadding = (size.width / 2).coerceAtLeast(MarkerClientPaddingPx)
    val points = marker.photoPositions.map { position ->
        projection.toScreenLocation(position)
    }

    if (!points.all { point -> point.isInsideClientWindow(clientWidth, clientHeight, markerPadding) }) {
        return false
    }

    val minDistance = size.width * SinglePhotoSplitSpacingFactor
    val minDistanceSquared = minDistance * minDistance
    for (index in points.indices) {
        for (otherIndex in index + 1 until points.size) {
            val dx = (points[index].x - points[otherIndex].x).toDouble()
            val dy = (points[index].y - points[otherIndex].y).toDouble()
            if (dx * dx + dy * dy < minDistanceSquared) {
                return false
            }
        }
    }

    return true
}

private fun MapLibreMap.isInsideClientWindow(
    photo: DevicePhoto,
    clientWidth: Int,
    clientHeight: Int,
    padding: Int
): Boolean {
    val latitude = photo.latitude ?: return false
    val longitude = photo.longitude ?: return false
    return isInsideClientWindow(LatLng(latitude, longitude), clientWidth, clientHeight, padding)
}

private fun MapLibreMap.isInsideClientWindow(
    position: LatLng,
    clientWidth: Int,
    clientHeight: Int,
    padding: Int
): Boolean {
    return projection
        .toScreenLocation(position)
        .isInsideClientWindow(clientWidth, clientHeight, padding)
}

private fun android.graphics.PointF.isInsideClientWindow(
    clientWidth: Int,
    clientHeight: Int,
    padding: Int
): Boolean {
    return x >= padding &&
        y >= padding &&
        x <= clientWidth - padding &&
        y <= clientHeight - padding
}

private fun DevicePhoto.toSinglePhotoMarker(): PhotoMapMarker {
    val position = LatLng(requireNotNull(latitude), requireNotNull(longitude))
    return PhotoMapMarker(
        id = mediaId.toString(),
        title = displayName ?: "Photo $mediaId",
        snippet = uri,
        position = position,
        count = 1,
        thumbnailUri = uri,
        type = PhotoMapMarkerType.SinglePhoto,
        photos = listOf(this),
        photoPositions = listOf(position)
    )
}

private fun MapLibreMap.fitMarkerPhotos(marker: PhotoMapMarker) {
    fitPositions(
        positions = marker.photoPositions,
        padding = MarkerClickBoundsPaddingPx,
        singlePositionZoom = (cameraPosition.zoom + MarkerClickZoomStep).coerceAtMost(MaxClickZoom)
    )
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

private fun List<PhotoMapMarker>.filterByBounds(bounds: LatLngBounds?): List<PhotoMapMarker> {
    if (bounds == null) {
        return this
    }

    return filter { marker ->
        bounds.contains(marker.position) || marker.photoPositions.any { position -> bounds.contains(position) }
    }
}

private fun List<DevicePhoto>.toMapMarkers(
    zoomBucket: Int,
    thumbnailThreshold: Int
): List<PhotoMapMarker> {
    return groupBy { photo ->
        val latitude = requireNotNull(photo.latitude)
        val longitude = requireNotNull(photo.longitude)
        val cellSize = zoomBucket.cellSize()
        "${floor(latitude / cellSize).toInt()}:${floor(longitude / cellSize).toInt()}"
    }.map { (cellId, groupedPhotos) ->
        val count = groupedPhotos.size
        val sortedPhotos = groupedPhotos.sortedByDescending { photo ->
            photo.dateTaken ?: photo.dateModified ?: photo.dateAdded ?: 0L
        }
        val newestPhoto = sortedPhotos.first()
        val photoPositions = sortedPhotos.map { photo ->
            LatLng(requireNotNull(photo.latitude), requireNotNull(photo.longitude))
        }
        val position = if (count == 1) {
            photoPositions.first()
        } else {
            photoPositions.averagePosition()
        }
        val type = when {
            count == 1 -> PhotoMapMarkerType.SinglePhoto
            count >= thumbnailThreshold -> PhotoMapMarkerType.Thumbnail
            else -> PhotoMapMarkerType.Heat
        }

        PhotoMapMarker(
            id = if (count == 1) newestPhoto.mediaId.toString() else cellId,
            title = if (count == 1) {
                newestPhoto.displayName ?: "Фото ${newestPhoto.mediaId}"
            } else {
                "Фотографий: $count"
            },
            snippet = if (count == 1) {
                newestPhoto.uri
            } else {
                groupedPhotos.joinToString(limit = 3) { photo ->
                    photo.displayName ?: "Фото ${photo.mediaId}"
                }
            },
            position = position,
            count = count,
            thumbnailUri = newestPhoto.uri,
            type = type,
            photos = sortedPhotos,
            photoPositions = photoPositions
        )
    }
}

private fun List<LatLng>.averagePosition(): LatLng {
    return LatLng(
        (sumOf { position -> position.latitude } / size).coerceIn(-90.0, 90.0),
        (sumOf { position -> position.longitude } / size).coerceIn(-180.0, 180.0)
    )
}

private class HeatIconFactory(context: Context) {
    private val iconFactory = IconFactory.getInstance(context)
    private val cache = mutableMapOf<String, MapIcon>()

    fun iconFor(count: Int, threshold: Int): MapIcon {
        val bucket = when {
            count <= 1 -> 1
            count < threshold / 2 -> 2
            else -> 3
        }
        val key = "$bucket:$threshold"
        return cache.getOrPut(key) {
            iconFactory.fromBitmap(createHeatBitmap(bucket))
        }
    }

    private fun createHeatBitmap(bucket: Int): Bitmap {
        val width = HeatIconBaseWidthPx + bucket * 42
        val height = HeatIconBaseHeightPx + bucket * 28
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = width / 2f
        val centerY = height / 2f
        val rings = listOf(
            Ring(0.98f, Color.argb(42 + bucket * 14, 255, 193, 7)),
            Ring(0.66f, Color.argb(66 + bucket * 16, 255, 112, 67)),
            Ring(0.34f, Color.argb(102 + bucket * 20, 198, 40, 40))
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        rings.forEach { ring ->
            paint.color = ring.color
            val ringWidth = width * ring.radius
            val ringHeight = height * ring.radius
            canvas.drawOval(
                RectF(
                    centerX - ringWidth / 2f,
                    centerY - ringHeight / 2f,
                    centerX + ringWidth / 2f,
                    centerY + ringHeight / 2f
                ),
                paint
            )
        }

        return bitmap
    }
}

private class ThumbnailIconFactory(context: Context) {
    private val appContext = context.applicationContext
    private val iconFactory = IconFactory.getInstance(appContext)
    private val cache = mutableMapOf<String, MapIcon>()

    fun iconFor(marker: PhotoMapMarker, zoomBucket: Int): MapIcon {
        val size = zoomBucket.thumbnailIconSize()
        val key = "${marker.thumbnailUri}:${marker.count}:$size:${marker.type}"
        return cache.getOrPut(key) {
            iconFactory.fromBitmap(
                createThumbnailBitmap(
                    uriString = marker.thumbnailUri,
                    count = marker.count,
                    width = size.width,
                    height = size.height,
                    showBadge = marker.type == PhotoMapMarkerType.Thumbnail
                )
            )
        }
    }

    private fun createThumbnailBitmap(
        uriString: String?,
        count: Int,
        width: Int,
        height: Int,
        showBadge: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 104, 94)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        val bottomInset = if (showBadge) ThumbnailBadgeHeightPx else 4
        val thumbnailRect = RectF(4f, 4f, width - 4f, height - bottomInset.toFloat())

        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            16f,
            16f,
            backgroundPaint
        )
        drawThumbnail(canvas, thumbnailRect, uriString)
        canvas.drawRoundRect(
            RectF(2f, 2f, width - 2f, height - 2f),
            16f,
            16f,
            borderPaint
        )
        if (showBadge) {
            drawBadge(canvas, count, width, height)
        }

        return bitmap
    }

    private fun drawThumbnail(canvas: Canvas, rect: RectF, uriString: String?) {
        val thumbnail = uriString?.let { uri ->
            runCatching {
                appContext.contentResolver.loadThumbnail(
                    Uri.parse(uri),
                    Size(ThumbnailLoadSizePx, ThumbnailLoadSizePx),
                    null
                )
            }.getOrNull()
        }

        if (thumbnail == null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(225, 235, 232)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, 12f, 12f, paint)
            return
        }

        val path = Path().apply {
            addRoundRect(rect, 12f, 12f, Path.Direction.CW)
        }
        val saveCount = canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(
            thumbnail,
            null,
            Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            ),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
        canvas.restoreToCount(saveCount)
    }

    private fun drawBadge(canvas: Canvas, count: Int, width: Int, height: Int) {
        val label = "+${(count - 1).coerceAtLeast(0)} фото"
        val badgeRect = RectF(10f, height - 36f, width - 10f, height - 8f)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 104, 94)
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        canvas.drawRoundRect(badgeRect, 14f, 14f, badgePaint)

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        canvas.drawText(label, badgeRect.centerX(), badgeRect.centerY() - textBounds.exactCenterY(), textPaint)
    }
}

private fun scanStatusText(isPaused: Boolean, processed: Int, total: Int): String {
    val prefix = if (isPaused) "Пауза" else "Сканирование"
    return if (total > 0) {
        "$prefix: $processed из $total"
    } else {
        prefix
    }
}

private fun Double.toZoomBucket(): Int {
    return when {
        this < 6.0 -> 1
        this < 10.0 -> 2
        this < 13.0 -> 3
        this < 15.0 -> 4
        this < 17.0 -> 5
        else -> 6
    }
}

private fun Int.cellSize(): Double {
    return when (this) {
        1 -> 5.0
        2 -> 1.0
        3 -> 0.25
        4 -> 0.05
        5 -> 0.01
        else -> 0.002
    }
}

private fun Int.thumbnailIconSize(): IconSize {
    return when (this) {
        1 -> IconSize(width = 88, height = 80)
        2 -> IconSize(width = 104, height = 92)
        3 -> IconSize(width = 122, height = 108)
        4 -> IconSize(width = 140, height = 124)
        5 -> IconSize(width = 158, height = 140)
        else -> IconSize(width = 176, height = 156)
    }
}

private data class Ring(
    val radius: Float,
    val color: Int
)

private data class IconSize(
    val width: Int,
    val height: Int
)

private const val DefaultZoomBucket = 1
private const val MapBoundsPaddingPx = 120
private const val MarkerClickBoundsPaddingPx = 180
private const val MarkerClickZoomStep = 2.0
private const val InitialSinglePhotoZoom = 14.0
private const val MaxClickZoom = 18.0
private const val MarkerClientPaddingPx = 24
private const val MaxAutoSplitPhotos = 24
private const val SinglePhotoSplitSpacingFactor = 0.9
private const val HeatIconBaseWidthPx = 190
private const val HeatIconBaseHeightPx = 120
private const val ThumbnailBadgeHeightPx = 40
private const val ThumbnailLoadSizePx = 96
private const val BottomGalleryPreviewCount = 4
private const val BottomGalleryThumbnailPx = 160

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
