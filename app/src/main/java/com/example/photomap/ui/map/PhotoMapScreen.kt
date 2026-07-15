package com.example.photomap.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.photomap.domain.model.DevicePhoto
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor

@Composable
fun PhotoMapScreen(
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    onBack: () -> Unit,
    onScan: () -> Unit
) {
    val photosWithLocation = remember(photos) {
        photos.filter { photo -> photo.latitude != null && photo.longitude != null }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MapTopBar(
                totalPhotos = photos.size,
                photosWithLocation = photosWithLocation.size,
                onBack = onBack,
                onScan = onScan
            )

            if (photosWithLocation.isEmpty()) {
                MapMessage(
                    title = "Нет фотографий с геопозицией",
                    text = "На карте появятся фотографии, в которых сохранены GPS-координаты. Сейчас можно повторить сканирование или вернуться к списку фотографий.",
                    onBack = onBack
                )
            } else {
                PhotoMapLibreMap(
                    photos = photosWithLocation,
                    mapStyleUrl = mapStyleUrl
                )
            }
        }
    }
}

@Composable
private fun MapTopBar(
    totalPhotos: Int,
    photosWithLocation: Int,
    onBack: () -> Unit,
    onScan: () -> Unit
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
                text = "Всего фотографий: $totalPhotos, с координатами: $photosWithLocation",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(text = "Назад")
                }
                Button(onClick = onScan) {
                    Text(text = "Сканировать")
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
    mapStyleUrl: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var zoomBucket by remember { mutableStateOf(DefaultZoomBucket) }
    var hasFitCamera by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    val clusterIconFactory = remember(context) {
        ClusterIconFactory(context.applicationContext)
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
    val markers = remember(photos, zoomBucket, visibleBounds) {
        photos
            .filterByBounds(visibleBounds)
            .toMapMarkers(zoomBucket)
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

    LaunchedEffect(map, isStyleReady, markers, cameraPositions) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }

        mapView.post {
            mapLibreMap.renderMarkers(
                markers = markers,
                cameraPositions = cameraPositions,
                fitCamera = !hasFitCamera,
                clusterIconFactory = clusterIconFactory
            )
        }

        if (markers.isNotEmpty()) {
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
    clusterIconFactory: ClusterIconFactory
) {
    clear()

    markers.forEach { marker ->
        val options = MarkerOptions()
            .position(marker.position)
            .title(marker.title)
            .snippet(marker.snippet)

        if (marker.count > 1) {
            options.icon(clusterIconFactory.iconFor(marker.count))
        }

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

private fun List<DevicePhoto>.toMapMarkers(zoomBucket: Int): List<PhotoMapMarker> {
    return groupBy { photo ->
        val latitude = requireNotNull(photo.latitude)
        val longitude = requireNotNull(photo.longitude)
        val cellSize = zoomBucket.cellSize()
        "${(latitude / cellSize).toInt()}:${(longitude / cellSize).toInt()}"
    }.map { (cellId, photos) ->
        if (photos.size == 1) {
            val photo = photos.first()
            PhotoMapMarker(
                id = photo.mediaId.toString(),
                title = photo.displayName ?: "Фото ${photo.mediaId}",
                snippet = photo.uri,
                position = LatLng(requireNotNull(photo.latitude), requireNotNull(photo.longitude)),
                count = 1
            )
        } else {
            val latitude = photos.map { requireNotNull(it.latitude) }.average()
            val longitude = photos.map { requireNotNull(it.longitude) }.average()
            PhotoMapMarker(
                id = cellId,
                title = "Фотографий: ${photos.size}",
                snippet = photos.joinToString(limit = 3) { it.displayName ?: "Фото ${it.mediaId}" },
                position = LatLng(latitude, longitude),
                count = photos.size
            )
        }
    }
}

private class ClusterIconFactory(context: Context) {
    private val iconFactory = IconFactory.getInstance(context)
    private val cache = mutableMapOf<String, Icon>()

    fun iconFor(count: Int): Icon {
        val label = count.toClusterLabel()
        return cache.getOrPut(label) {
            iconFactory.fromBitmap(createClusterBitmap(label))
        }
    }

    private fun createClusterBitmap(label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ClusterIconSizePx, ClusterIconSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 104, 94)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (label.length <= 3) 28f else 23f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val center = ClusterIconSizePx / 2f
        canvas.drawCircle(center, center, center - 5f, circlePaint)
        canvas.drawCircle(center, center, center - 7f, strokePaint)

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        canvas.drawText(label, center, center - textBounds.exactCenterY(), textPaint)
        return bitmap
    }
}

private fun Int.toClusterLabel(): String {
    return when {
        this > 9999 -> "9999+"
        this > 999 -> "${this / 1000}k"
        else -> toString()
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

private const val DefaultZoomBucket = 1
private const val MapBoundsPaddingPx = 120
private const val ClusterIconSizePx = 88

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
