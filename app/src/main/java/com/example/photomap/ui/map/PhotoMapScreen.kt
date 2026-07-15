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
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.floor
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
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
                        thumbnailThreshold = thumbnailThreshold
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
    thumbnailThreshold: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var zoomBucket by remember { mutableStateOf(DefaultZoomBucket) }
    var hasFitCamera by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }
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
            .filterByBounds(visibleBounds)
            .toMapMarkers(
                zoomBucket = zoomBucket,
                thumbnailThreshold = thumbnailThreshold
            )
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

    LaunchedEffect(map, isStyleReady, markers, cameraPositions, thumbnailThreshold) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }

        mapView.post {
            mapLibreMap.renderMarkers(
                markers = markers,
                cameraPositions = cameraPositions,
                fitCamera = !hasFitCamera,
                heatIconFactory = heatIconFactory,
                thumbnailIconFactory = thumbnailIconFactory,
                thumbnailThreshold = thumbnailThreshold
            )
        }

        if (cameraPositions.isNotEmpty()) {
            hasFitCamera = true
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

private fun MapLibreMap.renderMarkers(
    markers: List<PhotoMapMarker>,
    cameraPositions: List<LatLng>,
    fitCamera: Boolean,
    heatIconFactory: HeatIconFactory,
    thumbnailIconFactory: ThumbnailIconFactory,
    thumbnailThreshold: Int
) {
    clear()

    markers.forEach { marker ->
        val icon = when (marker.type) {
            PhotoMapMarkerType.Heat -> heatIconFactory.iconFor(
                count = marker.count,
                threshold = thumbnailThreshold
            )
            PhotoMapMarkerType.Thumbnail -> thumbnailIconFactory.iconFor(marker)
        }
        val options = MarkerOptions()
            .position(marker.position)
            .title(marker.title)
            .snippet(marker.snippet)
            .icon(icon)

        addMarker(options)
    }

    if (fitCamera) {
        when (cameraPositions.size) {
            0 -> Unit
            1 -> animateCamera(CameraUpdateFactory.newLatLngZoom(cameraPositions.first(), 14.0))
            else -> {
                val bounds = LatLngBounds.Builder()
                cameraPositions.forEach { position -> bounds.include(position) }
                animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), MapBoundsPaddingPx))
            }
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

private fun List<DevicePhoto>.filterByBounds(bounds: LatLngBounds?): List<DevicePhoto> {
    if (bounds == null) {
        return this
    }

    return filter { photo ->
        val latitude = photo.latitude
        val longitude = photo.longitude
        latitude != null && longitude != null && bounds.contains(LatLng(latitude, longitude))
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
        val newestPhoto = groupedPhotos.maxByOrNull { photo ->
            photo.dateTaken ?: photo.dateModified ?: photo.dateAdded ?: 0L
        } ?: groupedPhotos.first()
        val latitude = groupedPhotos.map { photo -> requireNotNull(photo.latitude) }.average()
        val longitude = groupedPhotos.map { photo -> requireNotNull(photo.longitude) }.average()
        val type = if (count >= thumbnailThreshold) {
            PhotoMapMarkerType.Thumbnail
        } else {
            PhotoMapMarkerType.Heat
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
            position = LatLng(latitude, longitude),
            count = count,
            thumbnailUri = newestPhoto.uri,
            type = type
        )
    }
}

private class HeatIconFactory(context: Context) {
    private val iconFactory = IconFactory.getInstance(context)
    private val cache = mutableMapOf<String, Icon>()

    fun iconFor(count: Int, threshold: Int): Icon {
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
        val size = HeatIconSizePx + bucket * 12
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val maxRadius = center - 2f
        val rings = listOf(
            Ring(maxRadius, Color.argb(52 + bucket * 16, 255, 193, 7)),
            Ring(maxRadius * 0.66f, Color.argb(72 + bucket * 18, 255, 112, 67)),
            Ring(maxRadius * 0.34f, Color.argb(108 + bucket * 22, 198, 40, 40))
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        rings.forEach { ring ->
            paint.color = ring.color
            canvas.drawCircle(center, center, ring.radius, paint)
        }

        return bitmap
    }
}

private class ThumbnailIconFactory(context: Context) {
    private val appContext = context.applicationContext
    private val iconFactory = IconFactory.getInstance(appContext)
    private val cache = mutableMapOf<String, Icon>()

    fun iconFor(marker: PhotoMapMarker): Icon {
        val key = "${marker.thumbnailUri}:${marker.count}"
        return cache.getOrPut(key) {
            iconFactory.fromBitmap(createThumbnailBitmap(marker.thumbnailUri, marker.count))
        }
    }

    private fun createThumbnailBitmap(uriString: String?, count: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ThumbnailIconWidthPx, ThumbnailIconHeightPx, Bitmap.Config.ARGB_8888)
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
        val thumbnailRect = RectF(4f, 4f, ThumbnailIconWidthPx - 4f, ThumbnailIconHeightPx - 24f)

        canvas.drawRoundRect(
            RectF(0f, 0f, ThumbnailIconWidthPx.toFloat(), ThumbnailIconHeightPx.toFloat()),
            16f,
            16f,
            backgroundPaint
        )
        drawThumbnail(canvas, thumbnailRect, uriString)
        canvas.drawRoundRect(
            RectF(2f, 2f, ThumbnailIconWidthPx - 2f, ThumbnailIconHeightPx - 2f),
            16f,
            16f,
            borderPaint
        )
        drawBadge(canvas, count)

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

    private fun drawBadge(canvas: Canvas, count: Int) {
        val label = "+${(count - 1).coerceAtLeast(0)} фото"
        val badgeRect = RectF(10f, ThumbnailIconHeightPx - 36f, ThumbnailIconWidthPx - 10f, ThumbnailIconHeightPx - 8f)
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
        else -> 4
    }
}

private fun Int.cellSize(): Double {
    return when (this) {
        1 -> 5.0
        2 -> 1.0
        3 -> 0.25
        else -> 0.02
    }
}

private data class Ring(
    val radius: Float,
    val color: Int
)

private const val DefaultZoomBucket = 1
private const val MapBoundsPaddingPx = 120
private const val HeatIconSizePx = 76
private const val ThumbnailIconWidthPx = 116
private const val ThumbnailIconHeightPx = 104
private const val ThumbnailLoadSizePx = 96

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
