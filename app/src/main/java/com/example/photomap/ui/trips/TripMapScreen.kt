package com.example.photomap.ui.trips

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.heatmap.VisibleTripHeatCell
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.TripMapMarker
import com.example.photomap.ui.map.PhotoMapLayerController
import com.example.photomap.ui.map.TripHeatmapFeatureMapper
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun TripMapScreen(
    tripMarkers: List<TripMapMarker>,
    tripHeatCells: List<VisibleTripHeatCell>,
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    focusedTripId: Long?,
    initialCameraState: TripMapCameraState?,
    isSegmenting: Boolean,
    onBack: () -> Unit,
    onRefreshTrips: () -> Unit,
    onCameraStateChanged: (TripMapCameraState) -> Unit,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit,
    onFocusTrip: (Long) -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    val photosById = remember(photos) {
        photos.associateBy { photo -> photo.mediaId }
    }
    var isTripTimelineExpanded by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = TripDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TripMapBackground,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TripMapTopBar(
                    modifier = Modifier.zIndex(2f),
                    tripCount = tripMarkers.size,
                    isSegmenting = isSegmenting,
                    onBack = onBack,
                    onRefreshTrips = onRefreshTrips
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    TripMapLibreMap(
                        tripMarkers = tripMarkers,
                        tripHeatCells = tripHeatCells,
                        photosById = photosById,
                        mapStyleUrl = mapStyleUrl,
                        focusedTripId = focusedTripId,
                        initialCameraState = initialCameraState,
                        onCameraStateChanged = onCameraStateChanged,
                        onViewportChanged = onViewportChanged,
                        onOpenTrip = onOpenTrip
                    )
                    if (tripMarkers.isNotEmpty()) {
                        if (isTripTimelineExpanded) {
                            TripYearScrubber(
                                tripMarkers = tripMarkers,
                                focusedTripId = focusedTripId,
                                onFocusTrip = onFocusTrip,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 2.dp)
                            )
                        } else {
                            TripTimelineCollapsedButton(
                                onClick = { isTripTimelineExpanded = true },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp)
                            )
                        }
                    }
                    if (tripMarkers.isEmpty() && !isSegmenting) {
                        TripMapMessage(
                            title = "Поездки не найдены",
                            text = "Здесь появятся поездки, выделенные по фотографиям с датой и геопозицией.",
                            onRefreshTrips = onRefreshTrips
                        )
                    }
                    if (isSegmenting) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            color = Color(0xE6101420),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Пересчитываем поездки",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class TripMapCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

fun TripMapMarker.toFocusedTripMapCameraState(): TripMapCameraState {
    return TripMapCameraState(
        latitude = latitude,
        longitude = longitude,
        zoom = zoomForTripRadius(radiusKm)
    )
}

private fun zoomForTripRadius(radiusKm: Double?): Double {
    val radius = radiusKm ?: return FocusedTripDefaultZoom
    return when {
        radius <= 3.0 -> 12.0
        radius <= 10.0 -> 10.5
        radius <= 35.0 -> 9.0
        radius <= 90.0 -> 7.5
        else -> 6.0
    }
}

@Composable
fun TripDetailsScreen(
    tripMarker: TripMapMarker?,
    photos: List<DevicePhoto>,
    mapStyleUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tripPhotos = remember(photos) { photos.sortedChronologically() }
    val photosById = remember(tripPhotos) { tripPhotos.associateBy { photo -> photo.mediaId } }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var selectedPhotoId by remember { mutableStateOf<Long?>(null) }
    var routeCenterRequestKey by remember { mutableStateOf(0) }
    val routePoints = remember(tripPhotos) { tripPhotos.toTripRoutePoints() }
    val photoGridIndexById = remember(tripPhotos) {
        tripPhotos.withIndex().associate { indexedPhoto -> indexedPhoto.value.mediaId to indexedPhoto.index }
    }
    val dateTitle = remember(context, tripPhotos, tripMarker) {
        formatTripDateRange(context = context, photos = tripPhotos, marker = tripMarker)
    }
    val title = remember(tripMarker?.placeName, dateTitle) {
        tripMarker?.placeName?.takeIf { placeName -> placeName.isNotBlank() } ?: dateTitle
    }
    val subtitle = remember(title, dateTitle, tripPhotos.size, tripMarker?.activeDayCount) {
        formatTripDateSubtitle(
            title = title,
            dateTitle = dateTitle,
            photoCount = tripPhotos.size,
            activeDayCount = tripMarker?.activeDayCount
        )
    }
    val onRoutePhotoClick: (Long) -> Unit = { photoId ->
        selectedPhotoId = photoId
        photoGridIndexById[photoId]?.let { index ->
            coroutineScope.launch {
                gridState.animateScrollToItem(index)
            }
        }
    }

    MaterialTheme(colorScheme = TripDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TripMapBackground,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TripDetailsTopBar(
                    modifier = Modifier.zIndex(2f),
                    title = title,
                    subtitle = subtitle,
                    isTitleLoading = false,
                    onCenterRoute = { routeCenterRequestKey += 1 },
                    onBack = onBack
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    TripRouteMap(
                        routePoints = routePoints,
                        photosById = photosById,
                        mapStyleUrl = mapStyleUrl,
                        selectedPhotoId = selectedPhotoId,
                        centerRequestKey = routeCenterRequestKey,
                        onPhotoPointClick = onRoutePhotoClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    TripPhotoScrubber(
                        routePoints = routePoints,
                        selectedPhotoId = selectedPhotoId,
                        onFocusPhoto = onRoutePhotoClick,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                    )
                    if (routePoints.isEmpty()) {
                        TripDetailsEmptyRouteMessage()
                    }
                }
                TripPhotoGrid(
                    photos = tripPhotos,
                    gridState = gridState,
                    selectedPhotoId = selectedPhotoId,
                    onPhotoClick = { photo -> context.openPhotoInDefaultGallery(photo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TripGalleryHeight)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                )
            }
        }
    }
}

@Composable
private fun TripDetailsTopBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    isTitleLoading: Boolean,
    onCenterRoute: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF101420),
        contentColor = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isTitleLoading) {
                    TripTitleSkeleton()
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C3D8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = onCenterRoute) {
                Text(text = "Центр")
            }
        }
    }
}

@Composable
private fun TripTitleSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(22.dp)
                .background(
                    color = Color(0xFF263044),
                    shape = RoundedCornerShape(5.dp)
                )
        )
        Box(
            modifier = Modifier
                .width(188.dp)
                .height(13.dp)
                .background(
                    color = Color(0xFF20283A),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun TripDetailsEmptyRouteMessage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xF0182030),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Нет точек маршрута",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Для линии нужны фотографии поездки с датой и координатами.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD4DBEA)
                )
            }
        }
    }
}

@Composable
private fun TripMapTopBar(
    modifier: Modifier = Modifier,
    tripCount: Int,
    isSegmenting: Boolean,
    onBack: () -> Unit,
    onRefreshTrips: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF101420),
        contentColor = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Поездки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Найдено: $tripCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C3D8)
                    )
                }
            }
            TextButton(
                enabled = !isSegmenting,
                onClick = onRefreshTrips
            ) {
                Text(text = "Обновить")
            }
        }
    }
}

@Composable
private fun TripMapMessage(
    title: String,
    text: String,
    onRefreshTrips: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xF0182030),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 6.dp
        ) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD4DBEA)
                )
                OutlinedButton(onClick = onRefreshTrips) {
                    Text(text = "Пересчитать")
                }
            }
        }
    }
}

@Composable
private fun TripYearScrubber(
    tripMarkers: List<TripMapMarker>,
    focusedTripId: Long?,
    onFocusTrip: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrubberItems = remember(tripMarkers) {
        tripMarkers
            .sortedWith(compareByDescending<TripMapMarker> { marker -> marker.startDay }.thenBy { marker ->
                marker.tripId
            })
            .map { marker ->
                TripYearScrubberItem(
                    marker = marker,
                    label = marker.tripTimelineLabel()
                )
            }
    }
    if (scrubberItems.isEmpty()) {
        return
    }

    var scrubberSize by remember { mutableStateOf(IntSize.Zero) }
    var timelineOffsetY by remember { mutableStateOf(0f) }
    var isTimelineDragging by remember { mutableStateOf(false) }
    var lastTimelineFocusTripId by remember { mutableStateOf<Long?>(focusedTripId) }
    val density = LocalDensity.current
    val latestFocusedTripId = rememberUpdatedState(focusedTripId)
    val selectedIndex = scrubberItems.indexOfFirst { item -> item.marker.tripId == focusedTripId }
        .takeIf { index -> index >= 0 }
        ?: 0
    val timelineEdgePaddingPx = with(density) { TripYearTimelineEdgePadding.toPx() }
    val timelinePointSpacingPx = with(density) { TripYearTimelinePointSpacing.toPx() }
    val labelHalfHeightPx = with(density) { TripYearTimelineLabelHeight.toPx() / 2f }
    val timelineHeightPx = (
        timelineEdgePaddingPx * 2f +
            timelinePointSpacingPx * (scrubberItems.size - 1).coerceAtLeast(0)
        ).coerceAtLeast(scrubberSize.height.toFloat())
    val selectedPointY = timelineYForIndex(
        index = selectedIndex,
        edgePaddingPx = timelineEdgePaddingPx,
        pointSpacingPx = timelinePointSpacingPx
    )
    val viewportCenterY = scrubberSize.height.toFloat() / 2f
    val minTimelineOffsetY = viewportCenterY - timelineYForIndex(
        index = scrubberItems.lastIndex,
        edgePaddingPx = timelineEdgePaddingPx,
        pointSpacingPx = timelinePointSpacingPx
    )
    val maxTimelineOffsetY = viewportCenterY - timelineYForIndex(
        index = 0,
        edgePaddingPx = timelineEdgePaddingPx,
        pointSpacingPx = timelinePointSpacingPx
    )
    val centeredTimelineOffsetY = (viewportCenterY - selectedPointY)
        .coerceIn(minTimelineOffsetY, maxTimelineOffsetY)

    LaunchedEffect(
        selectedIndex,
        scrubberSize.height,
        timelineEdgePaddingPx,
        timelinePointSpacingPx,
        isTimelineDragging
    ) {
        lastTimelineFocusTripId = focusedTripId
        if (!isTimelineDragging && scrubberSize.height > 0) {
            timelineOffsetY = centeredTimelineOffsetY
        }
    }

    fun focusAt(offsetY: Float) {
        val timelineY = offsetY - timelineOffsetY
        val index = timelineIndexForOffset(
            offsetY = timelineY,
            edgePaddingPx = timelineEdgePaddingPx,
            pointSpacingPx = timelinePointSpacingPx,
            itemCount = scrubberItems.size
        )
        val targetPointY = timelineYForIndex(
            index = index,
            edgePaddingPx = timelineEdgePaddingPx,
            pointSpacingPx = timelinePointSpacingPx
        )
        timelineOffsetY = (viewportCenterY - targetPointY)
            .coerceIn(minTimelineOffsetY, maxTimelineOffsetY)
        val tripId = scrubberItems[index].marker.tripId
        lastTimelineFocusTripId = tripId
        onFocusTrip(tripId)
    }

    fun focusCenteredItem(offsetY: Float) {
        val index = timelineIndexForOffset(
            offsetY = viewportCenterY - offsetY,
            edgePaddingPx = timelineEdgePaddingPx,
            pointSpacingPx = timelinePointSpacingPx,
            itemCount = scrubberItems.size
        )
        val tripId = scrubberItems[index].marker.tripId
        if (tripId != latestFocusedTripId.value && tripId != lastTimelineFocusTripId) {
            lastTimelineFocusTripId = tripId
            onFocusTrip(tripId)
        }
    }

    Box(
        modifier = modifier
            .width(TripYearScrubberWidth)
            .height(TripYearScrubberViewportHeight)
            .clipToBounds()
            .onSizeChanged { size -> scrubberSize = size }
            .pointerInput(scrubberItems, scrubberSize) {
                detectTapGestures { offset -> focusAt(offset.y) }
            }
            .pointerInput(scrubberItems, scrubberSize) {
                detectDragGestures(
                    onDragStart = {
                        isTimelineDragging = true
                    },
                    onDragEnd = {
                        isTimelineDragging = false
                    },
                    onDragCancel = {
                        isTimelineDragging = false
                    },
                    onDrag = { _, dragAmount ->
                        val nextOffset = (timelineOffsetY + dragAmount.y)
                            .coerceIn(minTimelineOffsetY, maxTimelineOffsetY)
                        timelineOffsetY = nextOffset
                        focusCenteredItem(nextOffset)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .width(TripYearScrubberWidth)
                .height(with(density) { timelineHeightPx.toDp() })
                .offset { IntOffset(x = 0, y = timelineOffsetY.roundToInt()) },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width - TripYearTimelineTrackEndPadding.toPx()
                val top = timelineEdgePaddingPx
                val bottom = size.height - timelineEdgePaddingPx
                if (scrubberItems.size > 1) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(centerX, top),
                        end = Offset(centerX, bottom),
                        strokeWidth = TripYearScrubberTrackWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                scrubberItems.forEachIndexed { index, _ ->
                    val y = timelineYForIndex(
                        index = index,
                        edgePaddingPx = timelineEdgePaddingPx,
                        pointSpacingPx = timelinePointSpacingPx
                    )
                    val center = Offset(centerX, y)
                    if (index == selectedIndex) {
                        drawCircle(
                            color = TripTimelineActiveColor.copy(alpha = 0.18f),
                            radius = 10.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = TripTimelineActiveColor,
                            radius = 5.8.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f),
                            radius = 8.2.dp.toPx(),
                            center = center,
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = 3.1.dp.toPx(),
                            center = center
                        )
                    }
                }
            }
            scrubberItems.forEachIndexed { index, item ->
                val y = timelineYForIndex(
                    index = index,
                    edgePaddingPx = timelineEdgePaddingPx,
                    pointSpacingPx = timelinePointSpacingPx
                )
                Text(
                    modifier = Modifier
                        .width(TripYearTimelineLabelWidth)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (y - labelHalfHeightPx).roundToInt()
                            )
                        },
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == selectedIndex) {
                        TripTimelineActiveColor.copy(alpha = 0.95f)
                    } else {
                        Color.White.copy(alpha = 0.33f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TripTimelineCollapsedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(TripTimelineCollapsedButtonSize)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xD9182030),
        contentColor = Color.White,
        border = BorderStroke(1.dp, TripTimelineActiveColor.copy(alpha = 0.72f)),
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TripPhotoScrubber(
    routePoints: List<TripRoutePoint>,
    selectedPhotoId: Long?,
    onFocusPhoto: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrubberItems = remember(routePoints) {
        routePoints
            .sortedWith(compareBy<TripRoutePoint> { point -> point.timestampMillis }.thenBy { point -> point.photoId })
            .map { point ->
                TripPhotoScrubberItem(
                    point = point,
                    label = formatPhotoScrubberLabel(point.timestampMillis)
                )
            }
    }
    if (scrubberItems.isEmpty()) {
        return
    }

    var scrubberSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedIndex = scrubberItems.indexOfFirst { item -> item.point.photoId == selectedPhotoId }
        .takeIf { index -> index >= 0 }
        ?: 0
    val selectedItem = scrubberItems[selectedIndex]
    val thumbHeightPx = with(LocalDensity.current) { TripPhotoScrubberThumbHeight.toPx() }

    fun focusAt(offsetY: Float) {
        val height = scrubberSize.height.coerceAtLeast(1)
        val index = scrubberIndexForOffset(
            offsetY = offsetY,
            height = height,
            itemCount = scrubberItems.size
        )
        onFocusPhoto(scrubberItems[index].point.photoId)
    }

    Box(
        modifier = modifier
            .width(TripPhotoScrubberWidth)
            .height(TripPhotoScrubberHeight)
            .onSizeChanged { size -> scrubberSize = size }
            .pointerInput(scrubberItems) {
                detectTapGestures { offset -> focusAt(offset.y) }
            }
            .pointerInput(scrubberItems) {
                detectDragGestures(
                    onDragStart = { offset -> focusAt(offset.y) },
                    onDrag = { change, _ -> focusAt(change.position.y) }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val top = TripPhotoScrubberTrackPadding.toPx()
                val bottom = size.height - TripPhotoScrubberTrackPadding.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(centerX, top),
                    end = Offset(centerX, bottom),
                    strokeWidth = TripPhotoScrubberTrackWidth.toPx(),
                    cap = StrokeCap.Round
                )
                scrubberItems.forEachIndexed { index, _ ->
                    val y = scrubberYForIndex(index, scrubberItems.size, size.height)
                    drawCircle(
                        color = if (index == selectedIndex) {
                            Color.White.copy(alpha = 0.72f)
                        } else {
                            Color.White.copy(alpha = 0.25f)
                        },
                        radius = if (index == selectedIndex) 4.8.dp.toPx() else 2.8.dp.toPx(),
                        center = Offset(centerX, y)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (
                                scrubberYForIndex(
                                    index = selectedIndex,
                                    itemCount = scrubberItems.size,
                                    heightPx = scrubberSize.height.toFloat()
                                ) - thumbHeightPx / 2f
                                ).roundToInt()
                        )
                    },
                color = Color(0x995B6CFF),
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 0.dp
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    text = selectedItem.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TripMapLibreMap(
    tripMarkers: List<TripMapMarker>,
    tripHeatCells: List<VisibleTripHeatCell>,
    photosById: Map<Long, DevicePhoto>,
    mapStyleUrl: String,
    focusedTripId: Long?,
    initialCameraState: TripMapCameraState?,
    onCameraStateChanged: (TripMapCameraState) -> Unit,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val heatmapLayerController = remember { PhotoMapLayerController() }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var cameraRevision by remember { mutableStateOf(0) }
    var hasAppliedInitialCamera by remember { mutableStateOf(false) }
    var appliedFocusedTripId by remember { mutableStateOf<Long?>(null) }
    var projectedMarkers by remember { mutableStateOf(emptyList<ProjectedTripMarker>()) }
    val overlayTapBlockedUntilMs = remember { AtomicLong(0L) }
    val lastCameraMoveProjectionAtMs = remember { AtomicLong(0L) }
    val latestTripMarkers = rememberUpdatedState(tripMarkers)
    val latestFocusedTripId = rememberUpdatedState(focusedTripId)
    val latestInitialCameraState = rememberUpdatedState(initialCameraState)
    val latestOnCameraStateChanged = rememberUpdatedState(onCameraStateChanged)
    val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.uiSettings.setAllGesturesEnabled(true)
                mapLibreMap.addOnCameraMoveListener {
                    val now = SystemClock.uptimeMillis()
                    overlayTapBlockedUntilMs.set(now + MapGestureTapBlockMs)
                    if (now - lastCameraMoveProjectionAtMs.get() >= MapMoveReprojectionThrottleMs) {
                        lastCameraMoveProjectionAtMs.set(now)
                        cameraRevision += 1
                    }
                }
                mapLibreMap.addOnCameraIdleListener {
                    val now = SystemClock.uptimeMillis()
                    overlayTapBlockedUntilMs.set(now + MapGestureTapBlockMs)
                    lastCameraMoveProjectionAtMs.set(now)
                    cameraRevision += 1
                    latestOnCameraStateChanged.value(mapLibreMap.toTripMapCameraState())
                    mapLibreMap.dispatchTripHeatmapViewportChanged(latestOnViewportChanged.value)
                }
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    style.applyTripDarkStyle()
                    heatmapLayerController.reset()
                    heatmapLayerController.updateTripHeatmap(
                        style = style,
                        featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(tripHeatCells)
                    )
                    isStyleReady = true
                    cameraRevision += 1
                    post {
                        mapLibreMap.dispatchTripHeatmapViewportChanged(latestOnViewportChanged.value)
                    }
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

    LaunchedEffect(map, isStyleReady, tripMarkers, focusedTripId, initialCameraState) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        mapView.post {
            val shouldApplyFocusedTrip = focusedTripId != null && focusedTripId != appliedFocusedTripId
            if (!hasAppliedInitialCamera || shouldApplyFocusedTrip) {
                mapLibreMap.applyTripMapCamera(
                    cameraState = latestInitialCameraState.value,
                    focusedTripId = latestFocusedTripId.value,
                    markers = latestTripMarkers.value
                )
                hasAppliedInitialCamera = true
                appliedFocusedTripId = focusedTripId
                latestOnCameraStateChanged.value(mapLibreMap.toTripMapCameraState())
                mapLibreMap.dispatchTripHeatmapViewportChanged(latestOnViewportChanged.value)
            }
            projectedMarkers = mapLibreMap.projectTripMarkers(tripMarkers, mapView.width, mapView.height)
        }
    }

    LaunchedEffect(map, isStyleReady, tripHeatCells) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        heatmapLayerController.updateTripHeatmap(
            style = style,
            featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(tripHeatCells)
        )
    }

    LaunchedEffect(map, isStyleReady, cameraRevision, tripMarkers) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        projectedMarkers = mapLibreMap.projectTripMarkers(tripMarkers, mapView.width, mapView.height)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
        )
        TripMarkerOverlay(
            projectedMarkers = projectedMarkers,
            photosById = photosById,
            focusedTripId = focusedTripId,
            isTapBlocked = { SystemClock.uptimeMillis() < overlayTapBlockedUntilMs.get() },
            onOpenTrip = onOpenTrip
        )
    }
}

@Composable
private fun TripMarkerOverlay(
    projectedMarkers: List<ProjectedTripMarker>,
    photosById: Map<Long, DevicePhoto>,
    focusedTripId: Long?,
    isTapBlocked: () -> Boolean,
    onOpenTrip: (Long) -> Unit
) {
    val density = LocalDensity.current
    val markerSize = 74.dp
    val focusedMarkerSize = 84.dp
    Box(modifier = Modifier.fillMaxSize()) {
        projectedMarkers.forEach { projected ->
            key(projected.marker.tripId) {
                val isFocused = projected.marker.tripId == focusedTripId
                val itemMarkerSize = if (isFocused) focusedMarkerSize else markerSize
                val itemMarkerSizePx = with(density) { itemMarkerSize.toPx() }
                val coverPhoto = projected.marker.coverPhotoId?.let { photoId -> photosById[photoId] }
                TripClusterMarker(
                    marker = projected.marker,
                    coverPhoto = coverPhoto,
                    isFocused = isFocused,
                    modifier = Modifier
                        .offset {
                            markerOffset(
                                screenX = projected.screenX,
                                screenY = projected.screenY,
                                markerSizePx = itemMarkerSizePx,
                                viewportWidth = projected.viewportWidth,
                                viewportHeight = projected.viewportHeight
                            )
                        }
                        .size(itemMarkerSize)
                        .clickable {
                            if (!isTapBlocked()) {
                                onOpenTrip(projected.marker.tripId)
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun TripClusterMarker(
    marker: TripMapMarker,
    coverPhoto: DevicePhoto?,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, coverPhoto?.uri) {
        value = if (coverPhoto == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                context.loadTripPreviewBitmap(
                    uriString = coverPhoto.uri,
                    targetSizePx = TripThumbnailPx,
                    photoId = coverPhoto.mediaId
                )
            }
        }
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            color = if (isFocused) Color(0xFF2D3B66) else Color(0xFF243047),
            border = BorderStroke(
                width = if (isFocused) 3.dp else 2.dp,
                color = if (isFocused) TripTimelineActiveColor else Color(0xFF9DB7FF).copy(alpha = 0.64f)
            ),
            shadowElevation = if (isFocused) 14.dp else 8.dp
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = requireNotNull(thumbnail).asImageBitmap(),
                    contentDescription = marker.placeName ?: "Поездка",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Фото",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFBFD0FF),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd),
            shape = CircleShape,
            color = if (isFocused) TripTimelineActiveColor else Color(0xEE5B6CFF),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFF101420))
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                text = "+${marker.photoCount}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TripRouteMap(
    routePoints: List<TripRoutePoint>,
    photosById: Map<Long, DevicePhoto>,
    mapStyleUrl: String,
    selectedPhotoId: Long?,
    centerRequestKey: Int,
    onPhotoPointClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var cameraRevision by remember { mutableStateOf(0) }
    var projectedRoutePoints by remember { mutableStateOf(emptyList<ProjectedTripRoutePoint>()) }
    val lastCameraMoveProjectionAtMs = remember { AtomicLong(0L) }
    val latestRoutePoints = rememberUpdatedState(routePoints)

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.uiSettings.setAllGesturesEnabled(true)
                mapLibreMap.addOnCameraMoveListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastCameraMoveProjectionAtMs.get() >= MapMoveReprojectionThrottleMs) {
                        lastCameraMoveProjectionAtMs.set(now)
                        cameraRevision += 1
                    }
                }
                mapLibreMap.addOnCameraIdleListener {
                    lastCameraMoveProjectionAtMs.set(SystemClock.uptimeMillis())
                    cameraRevision += 1
                }
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    style.applyTripDarkStyle()
                    isStyleReady = true
                    cameraRevision += 1
                    post {
                        mapLibreMap.fitRoutePoints(latestRoutePoints.value)
                        cameraRevision += 1
                    }
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

    LaunchedEffect(map, isStyleReady, routePoints) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        mapView.post {
            mapLibreMap.fitRoutePoints(routePoints)
            projectedRoutePoints = mapLibreMap.projectRoutePoints(routePoints, mapView.width, mapView.height)
        }
    }

    LaunchedEffect(map, isStyleReady, cameraRevision, routePoints) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        projectedRoutePoints = mapLibreMap.projectRoutePoints(routePoints, mapView.width, mapView.height)
    }

    LaunchedEffect(map, isStyleReady, selectedPhotoId, routePoints) {
        val mapLibreMap = map ?: return@LaunchedEffect
        val photoId = selectedPhotoId ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        routePoints.firstOrNull { point -> point.photoId == photoId }?.let { point ->
            mapView.post {
                mapLibreMap.focusRoutePoint(point)
                projectedRoutePoints = mapLibreMap.projectRoutePoints(routePoints, mapView.width, mapView.height)
            }
        }
    }

    LaunchedEffect(map, isStyleReady, centerRequestKey, routePoints) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || centerRequestKey == 0) {
            return@LaunchedEffect
        }
        mapView.post {
            mapLibreMap.fitRoutePoints(routePoints)
            projectedRoutePoints = mapLibreMap.projectRoutePoints(routePoints, mapView.width, mapView.height)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
        TripRouteOverlay(projectedRoutePoints = projectedRoutePoints)
        TripRouteThumbnailOverlay(
            projectedRoutePoints = projectedRoutePoints,
            photosById = photosById,
            selectedPhotoId = selectedPhotoId,
            onPhotoPointClick = onPhotoPointClick
        )
    }
}

@Composable
private fun TripRouteOverlay(
    projectedRoutePoints: List<ProjectedTripRoutePoint>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (projectedRoutePoints.isEmpty()) {
            return@Canvas
        }

        val lineStrokeWidth = TripRouteLineWidth.toPx()
        val lineHaloWidth = TripRouteLineHaloWidth.toPx()
        val pointRadius = TripRoutePointRadius.toPx()
        val pointHaloRadius = TripRoutePointHaloRadius.toPx()

        val drawPoints = smoothRouteDrawPoints(projectedRoutePoints)
        if (drawPoints.size > 1) {
            for (index in 0 until drawPoints.lastIndex) {
                val from = drawPoints[index]
                val to = drawPoints[index + 1]
                drawLine(
                    color = Color.Black.copy(alpha = 0.58f),
                    start = from.offset,
                    end = to.offset,
                    strokeWidth = lineHaloWidth,
                    cap = StrokeCap.Round
                )
            }
            for (index in 0 until drawPoints.lastIndex) {
                val from = drawPoints[index]
                val to = drawPoints[index + 1]
                val progress = (from.progress + to.progress) / 2f
                drawLine(
                    color = tripRouteHeatColor(progress),
                    start = from.offset,
                    end = to.offset,
                    strokeWidth = lineStrokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        projectedRoutePoints.forEachIndexed { index, projected ->
            val isTerminalPoint = index == 0 || index == projectedRoutePoints.lastIndex
            val center = Offset(projected.screenX, projected.screenY)
            val routeColor = tripRouteHeatColor(projected.point.progress)
            drawCircle(
                color = Color.Black.copy(alpha = 0.74f),
                radius = if (isTerminalPoint) pointHaloRadius * 1.2f else pointHaloRadius,
                center = center
            )
            drawCircle(
                color = routeColor,
                radius = if (isTerminalPoint) pointRadius * 1.18f else pointRadius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = if (isTerminalPoint) pointRadius * 0.36f else pointRadius * 0.24f,
                center = center
            )
        }
    }
}

@Composable
private fun TripRouteThumbnailOverlay(
    projectedRoutePoints: List<ProjectedTripRoutePoint>,
    photosById: Map<Long, DevicePhoto>,
    selectedPhotoId: Long?,
    onPhotoPointClick: (Long) -> Unit
) {
    val visiblePoints = remember(projectedRoutePoints) {
        selectVisibleRouteThumbnailPoints(projectedRoutePoints)
    }
    val density = LocalDensity.current
    val markerSize = TripRouteThumbnailSize
    val markerSizePx = with(density) { markerSize.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        visiblePoints.forEach { projected ->
            val photo = photosById[projected.point.photoId] ?: return@forEach
            key(projected.point.photoId) {
                TripRoutePhotoMarker(
                    photo = photo,
                    progress = projected.point.progress,
                    selected = selectedPhotoId == projected.point.photoId,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (projected.screenX - markerSizePx / 2f).roundToInt(),
                                y = (projected.screenY - markerSizePx / 2f).roundToInt()
                            )
                        }
                        .size(markerSize)
                        .clickable { onPhotoPointClick(projected.point.photoId) }
                )
            }
        }
    }
}

@Composable
private fun TripRoutePhotoMarker(
    photo: DevicePhoto,
    progress: Float,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            context.loadTripPreviewBitmap(
                uriString = photo.uri,
                targetSizePx = TripRouteThumbnailPx,
                photoId = photo.mediaId
            )
        }
    }
    val borderColor = if (selected) Color.White else tripRouteHeatColor(progress)
    val borderWidth = if (selected) 3.dp else 2.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF20283A),
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (selected) 8.dp else 4.dp
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = requireNotNull(thumbnail).asImageBitmap(),
                contentDescription = photo.displayName ?: "Фото маршрута",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Фото",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBFD0FF),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TripPhotoGrid(
    photos: List<DevicePhoto>,
    gridState: LazyGridState,
    selectedPhotoId: Long?,
    onPhotoClick: (DevicePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0D111C),
        contentColor = Color.White,
        shadowElevation = 8.dp
    ) {
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "В поездке пока нет фотографий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C3D8)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TripGalleryCellMinSize),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = photos,
                    key = { photo -> photo.mediaId }
                ) { photo ->
                    TripPhotoGridItem(
                        photo = photo,
                        selected = selectedPhotoId == photo.mediaId,
                        onPhotoClick = onPhotoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TripPhotoGridItem(
    photo: DevicePhoto,
    selected: Boolean,
    onPhotoClick: (DevicePhoto) -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            context.loadTripPreviewBitmap(
                uriString = photo.uri,
                targetSizePx = TripGridThumbnailPx,
                photoId = photo.mediaId
            )
        }
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onPhotoClick(photo) },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF20283A),
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) Color.White else Color(0xFF33415F)
        )
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = requireNotNull(thumbnail).asImageBitmap(),
                contentDescription = photo.displayName ?: "Фото поездки",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Фото",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFBFD0FF),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun MapLibreMap.projectTripMarkers(
    markers: List<TripMapMarker>,
    width: Int,
    height: Int
): List<ProjectedTripMarker> {
    if (width <= 0 || height <= 0) {
        return emptyList()
    }
    return markers.mapNotNull { marker ->
        val point = projection.toScreenLocation(LatLng(marker.latitude, marker.longitude))
        ProjectedTripMarker(
            marker = marker,
            screenX = point.x,
            screenY = point.y,
            viewportWidth = width,
            viewportHeight = height
        )
    }
}

private fun MapLibreMap.projectRoutePoints(
    points: List<TripRoutePoint>,
    width: Int,
    height: Int
): List<ProjectedTripRoutePoint> {
    if (width <= 0 || height <= 0) {
        return emptyList()
    }
    return points.map { point ->
        val screenPoint = projection.toScreenLocation(LatLng(point.latitude, point.longitude))
        ProjectedTripRoutePoint(
            point = point,
            screenX = screenPoint.x,
            screenY = screenPoint.y
        )
    }
}

private fun MapLibreMap.fitTripMarkers(markers: List<TripMapMarker>) {
    if (markers.isEmpty()) {
        return
    }
    runCatching {
        if (markers.size == 1) {
            val marker = markers.first()
            animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(marker.latitude, marker.longitude),
                    SingleTripZoom
                )
            )
            return
        }
        val builder = LatLngBounds.Builder()
        markers.forEach { marker -> builder.include(LatLng(marker.latitude, marker.longitude)) }
        animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), TripMapBoundsPaddingPx))
    }.onFailure { error ->
        Log.w(TripMapLogTag, "Failed to fit trip map markers", error)
    }
}

private fun MapLibreMap.applyTripMapCamera(
    cameraState: TripMapCameraState?,
    focusedTripId: Long?,
    markers: List<TripMapMarker>
) {
    val focusedMarker = focusedTripId?.let { tripId ->
        markers.firstOrNull { marker -> marker.tripId == tripId }
    }
    val targetCameraState = focusedMarker?.toFocusedTripMapCameraState() ?: cameraState
    if (targetCameraState != null) {
        animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(targetCameraState.latitude, targetCameraState.longitude),
                targetCameraState.zoom
            )
        )
        return
    }

    fitTripMarkers(markers)
}

private fun MapLibreMap.toTripMapCameraState(): TripMapCameraState {
    val target = cameraPosition.target
    return TripMapCameraState(
        latitude = target!!.latitude,
        longitude = target!!.longitude,
        zoom = cameraPosition.zoom
    )
}

private fun MapLibreMap.dispatchTripHeatmapViewportChanged(
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
        Log.w(TripMapLogTag, "Failed to dispatch trip heatmap viewport", error)
    }
}

private fun scrubberIndexForOffset(
    offsetY: Float,
    height: Int,
    itemCount: Int
): Int {
    if (itemCount <= 1) {
        return 0
    }
    val heightPx = height.toFloat().coerceAtLeast(1f)
    val edgePadding = scrubberEdgePadding(heightPx)
    val trackHeight = (heightPx - edgePadding * 2f).coerceAtLeast(1f)
    val fraction = ((offsetY - edgePadding) / trackHeight).coerceIn(0f, 1f)
    return (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
}

private fun timelineIndexForOffset(
    offsetY: Float,
    edgePaddingPx: Float,
    pointSpacingPx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 1) {
        return 0
    }
    val normalizedOffset = offsetY - edgePaddingPx
    return (normalizedOffset / pointSpacingPx)
        .roundToInt()
        .coerceIn(0, itemCount - 1)
}

private fun timelineYForIndex(
    index: Int,
    edgePaddingPx: Float,
    pointSpacingPx: Float
): Float {
    return edgePaddingPx + pointSpacingPx * index.toFloat()
}

private fun scrubberYForIndex(
    index: Int,
    itemCount: Int,
    heightPx: Float
): Float {
    if (itemCount <= 1) {
        return heightPx / 2f
    }
    val fraction = index.toFloat() / (itemCount - 1).toFloat()
    val edgePadding = scrubberEdgePadding(heightPx)
    return edgePadding + (heightPx - edgePadding * 2f) * fraction
}

private fun scrubberEdgePadding(heightPx: Float): Float {
    return (heightPx * TripYearScrubberEdgePaddingFraction).coerceAtMost(heightPx / 3f)
}

private fun TripMapMarker.tripTimelineLabel(): String {
    placeName?.trim()?.takeIf { value -> value.isNotEmpty() }?.let { placeName ->
        return placeName
    }
    val start = photoDateDayToMillis(startDay)
    val end = photoDateDayToMillis(endDay)
    val startText = DateFormat.format("dd.MM.yy", start).toString()
    val endText = DateFormat.format("dd.MM.yy", end).toString()
    val dateText = if (startText == endText) {
        startText
    } else {
        "$startText - $endText"
    }
    return "Поездка $dateText"
}

private fun formatPhotoScrubberLabel(timestampMillis: Long): String {
    return DateFormat.format("dd.MM", timestampMillis).toString()
}

private fun MapLibreMap.fitRoutePoints(points: List<TripRoutePoint>) {
    if (points.isEmpty()) {
        return
    }
    runCatching {
        val first = points.first()
        val hasMultipleCoordinates = points.any { point ->
            point.latitude != first.latitude || point.longitude != first.longitude
        }
        if (!hasMultipleCoordinates) {
            animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude, first.longitude),
                    SingleRoutePointZoom
                )
            )
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { point -> builder.include(LatLng(point.latitude, point.longitude)) }
        animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), TripRouteBoundsPaddingPx))
    }.onFailure { error ->
        Log.w(TripMapLogTag, "Failed to fit trip route", error)
    }
}

private fun MapLibreMap.focusRoutePoint(point: TripRoutePoint) {
    runCatching {
        val zoom = max(cameraPosition.zoom, FocusedTripPhotoZoom)
        animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                zoom
            )
        )
    }.onFailure { error ->
        Log.w(TripMapLogTag, "Failed to focus trip photo point: photoId=${point.photoId}", error)
    }
}

private fun Style.applyTripDarkStyle() {
    TripDarkFillLayerIds.forEach { layerId ->
        getLayerAs<FillLayer>(layerId)?.setProperties(fillColor(TripMapLandFill))
    }
}

private fun markerOffset(
    screenX: Float,
    screenY: Float,
    markerSizePx: Float,
    viewportWidth: Int,
    viewportHeight: Int
): IntOffset {
    val half = markerSizePx / 2f
    val x = (screenX - half)
        .coerceIn(-markerSizePx, viewportWidth.toFloat())
        .roundToInt()
    val y = (screenY - half)
        .coerceIn(-markerSizePx, viewportHeight.toFloat())
        .roundToInt()
    return IntOffset(x, y)
}

private fun List<DevicePhoto>.sortedChronologically(): List<DevicePhoto> {
    return sortedWith(
        compareBy<DevicePhoto> { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
            .thenBy { photo -> photo.mediaId }
    )
}

private fun List<DevicePhoto>.toTripRoutePoints(): List<TripRoutePoint> {
    val sortedPoints = mapNotNull { photo ->
        val latitude = photo.latitude ?: return@mapNotNull null
        val longitude = photo.longitude ?: return@mapNotNull null
        val timestampMillis = photo.photoDateMillis() ?: return@mapNotNull null
        TripRoutePoint(
            photoId = photo.mediaId,
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            progress = 0f
        )
    }.sortedWith(
        compareBy<TripRoutePoint> { point -> point.timestampMillis }
            .thenBy { point -> point.photoId }
    )

    val lastIndex = sortedPoints.lastIndex
    if (lastIndex <= 0) {
        return sortedPoints
    }
    return sortedPoints.mapIndexed { index, point ->
        point.copy(progress = index.toFloat() / lastIndex.toFloat())
    }
}

private fun smoothRouteDrawPoints(points: List<ProjectedTripRoutePoint>): List<RouteDrawPoint> {
    if (points.size <= 2) {
        return points.map { point ->
            RouteDrawPoint(
                offset = Offset(point.screenX, point.screenY),
                progress = point.point.progress
            )
        }
    }

    val drawPoints = mutableListOf<RouteDrawPoint>()
    drawPoints += points.first().toRouteDrawPoint()
    for (index in 1 until points.lastIndex) {
        val previous = points[index - 1]
        val current = points[index]
        val next = points[index + 1]
        val start = if (index == 1) {
            previous.toRouteDrawPoint()
        } else {
            midpoint(previous, current)
        }
        val end = midpoint(current, next)
        repeat(TripRouteSmoothingSteps) { step ->
            val t = (step + 1).toFloat() / TripRouteSmoothingSteps.toFloat()
            drawPoints += quadraticRoutePoint(
                start = start,
                control = current.toRouteDrawPoint(),
                end = end,
                t = t
            )
        }
    }
    drawPoints += points.last().toRouteDrawPoint()
    return drawPoints
}

private fun ProjectedTripRoutePoint.toRouteDrawPoint(): RouteDrawPoint {
    return RouteDrawPoint(
        offset = Offset(screenX, screenY),
        progress = point.progress
    )
}

private fun midpoint(
    first: ProjectedTripRoutePoint,
    second: ProjectedTripRoutePoint
): RouteDrawPoint {
    return RouteDrawPoint(
        offset = Offset(
            x = (first.screenX + second.screenX) / 2f,
            y = (first.screenY + second.screenY) / 2f
        ),
        progress = (first.point.progress + second.point.progress) / 2f
    )
}

private fun quadraticRoutePoint(
    start: RouteDrawPoint,
    control: RouteDrawPoint,
    end: RouteDrawPoint,
    t: Float
): RouteDrawPoint {
    val inverse = 1f - t
    val x = inverse * inverse * start.offset.x +
        2f * inverse * t * control.offset.x +
        t * t * end.offset.x
    val y = inverse * inverse * start.offset.y +
        2f * inverse * t * control.offset.y +
        t * t * end.offset.y
    return RouteDrawPoint(
        offset = Offset(x, y),
        progress = start.progress + (end.progress - start.progress) * t
    )
}

private fun selectVisibleRouteThumbnailPoints(
    points: List<ProjectedTripRoutePoint>
): List<ProjectedTripRoutePoint> {
    if (points.size <= TripRouteMaxThumbnailMarkers) {
        return points
    }

    val selected = mutableListOf(points.first())
    points.drop(1).dropLast(1).forEach { candidate ->
        if (selected.size >= TripRouteMaxThumbnailMarkers - 1) {
            return@forEach
        }
        val hasEnoughSpace = selected.all { selectedPoint ->
            selectedPoint.screenDistancePx(candidate) >= TripRouteThumbnailMinDistancePx
        }
        if (hasEnoughSpace) {
            selected += candidate
        }
    }
    if (selected.none { point -> point.point.photoId == points.last().point.photoId }) {
        selected += points.last()
    }
    return selected
        .distinctBy { point -> point.point.photoId }
        .sortedWith(compareBy<ProjectedTripRoutePoint> { point -> point.point.timestampMillis }.thenBy { point ->
            point.point.photoId
        })
}

private fun ProjectedTripRoutePoint.screenDistancePx(other: ProjectedTripRoutePoint): Float {
    val dx = screenX - other.screenX
    val dy = screenY - other.screenY
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

private fun tripRouteHeatColor(progress: Float): Color {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return if (clampedProgress <= 0.5f) {
        interpolateColor(
            start = TripRouteStartColor,
            end = TripRouteMiddleColor,
            fraction = clampedProgress / 0.5f
        )
    } else {
        interpolateColor(
            start = TripRouteMiddleColor,
            end = TripRouteEndColor,
            fraction = (clampedProgress - 0.5f) / 0.5f
        )
    }
}

private fun interpolateColor(
    start: Color,
    end: Color,
    fraction: Float
): Color {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * clampedFraction,
        green = start.green + (end.green - start.green) * clampedFraction,
        blue = start.blue + (end.blue - start.blue) * clampedFraction,
        alpha = start.alpha + (end.alpha - start.alpha) * clampedFraction
    )
}

private fun formatTripDateRange(
    context: Context,
    photos: List<DevicePhoto>,
    marker: TripMapMarker?
): String {
    val photoDates = photos.mapNotNull { photo -> photo.photoDateMillis() }
    val startMillis = photoDates.minOrNull() ?: marker?.startDay?.let(::photoDateDayToMillis)
    val endMillis = photoDates.maxOrNull() ?: marker?.endDay?.let(::photoDateDayToMillis)
    if (startMillis == null || endMillis == null) {
        return "Поездка"
    }

    val formatter = DateFormat.getMediumDateFormat(context)
    val startText = formatter.format(Date(startMillis))
    val endText = formatter.format(Date(endMillis))
    return if (startText == endText) startText else "$startText - $endText"
}

private fun formatTripDateSubtitle(
    title: String,
    dateTitle: String,
    photoCount: Int,
    activeDayCount: Int?
): String {
    val stats = buildString {
        append("Фото: ")
        append(photoCount)
        if (activeDayCount != null) {
            append(" · дней: ")
            append(activeDayCount)
        }
    }
    return if (title == dateTitle) stats else "$dateTitle · $stats"
}

private fun Context.openPhotoInDefaultGallery(photo: DevicePhoto) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(photo.uri), photo.mimeType ?: "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (this@openPhotoInDefaultGallery !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Log.w(TripMapLogTag, "No gallery app found: photoId=${photo.mediaId}", exception)
        Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
    } catch (exception: RuntimeException) {
        Log.w(TripMapLogTag, "Failed to open photo in gallery: photoId=${photo.mediaId}", exception)
        Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.loadTripPreviewBitmap(
    uriString: String,
    targetSizePx: Int,
    photoId: Long? = null
): Bitmap? {
    val uri = Uri.parse(uriString)
    return runCatching {
        contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
    }.onFailure { error ->
        Log.w(TripMapLogTag, "Trip thumbnail loadThumbnail failed: photoId=$photoId", error)
    }.getOrNull() ?: runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val scale = (targetSizePx.toFloat() / max(width, height).toFloat()).coerceAtMost(1f)
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1)
            )
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
    }.onFailure { error ->
        Log.w(TripMapLogTag, "Trip thumbnail ImageDecoder failed: photoId=$photoId", error)
    }.getOrNull()
}

private data class ProjectedTripMarker(
    val marker: TripMapMarker,
    val screenX: Float,
    val screenY: Float,
    val viewportWidth: Int,
    val viewportHeight: Int
)

private data class TripRoutePoint(
    val photoId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val progress: Float
)

private data class ProjectedTripRoutePoint(
    val point: TripRoutePoint,
    val screenX: Float,
    val screenY: Float
)

private data class TripYearScrubberItem(
    val marker: TripMapMarker,
    val label: String
)

private data class TripPhotoScrubberItem(
    val point: TripRoutePoint,
    val label: String
)

private data class RouteDrawPoint(
    val offset: Offset,
    val progress: Float
)

private val TripDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF06122D),
    primaryContainer = Color(0xFF25345C),
    onPrimaryContainer = Color(0xFFE4EAFF),
    surface = Color(0xFF101420),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF20283A),
    onSurfaceVariant = Color(0xFFD4DBEA),
    background = Color(0xFF070A10),
    onBackground = Color.White
)

private const val SingleTripZoom = 5.5
private const val FocusedTripDefaultZoom = 8.5
private const val SingleRoutePointZoom = 11.0
private const val FocusedTripPhotoZoom = 13.5
private const val TripMapBoundsPaddingPx = 140
private const val TripRouteBoundsPaddingPx = 120
private const val TripThumbnailPx = 160
private const val TripGridThumbnailPx = 220
private const val TripRouteThumbnailPx = 128
private const val TripRouteMaxThumbnailMarkers = 36
private const val TripRouteThumbnailMinDistancePx = 56f
private const val TripRouteSmoothingSteps = 10
private const val MapGestureTapBlockMs = 450L
private const val MapMoveReprojectionThrottleMs = 32L
private const val TripYearScrubberEdgePaddingFraction = 0.08f
private const val TripMapLogTag = "PhotoMapTrips"
private const val TripMapLandFill = "#151B28"

private val TripMapBackground = Color(0xFF070A10)
private val TripTimelineActiveColor = Color(0xFF9DB7FF)
private val TripTimelineCollapsedButtonSize = 48.dp
private val TripYearScrubberWidth = 154.dp
private val TripYearScrubberViewportHeight = 520.dp
private val TripYearTimelineEdgePadding = 16.dp
private val TripYearTimelinePointSpacing = 58.dp
private val TripYearTimelineTrackEndPadding = 8.dp
private val TripYearTimelineLabelWidth = 112.dp
private val TripYearTimelineLabelHeight = 22.dp
private val TripYearScrubberTrackWidth = 3.dp
private val TripPhotoScrubberWidth = 66.dp
private val TripPhotoScrubberHeight = 292.dp
private val TripPhotoScrubberThumbHeight = 31.dp
private val TripPhotoScrubberTrackPadding = 18.dp
private val TripPhotoScrubberTrackWidth = 3.dp
private val TripGalleryHeight = 248.dp
private val TripGalleryCellMinSize = 86.dp
private val TripRouteThumbnailSize = 42.dp
private val TripRouteLineWidth = 6.dp
private val TripRouteLineHaloWidth = 11.dp
private val TripRoutePointRadius = 5.dp
private val TripRoutePointHaloRadius = 8.dp
private val TripRouteStartColor = Color(0xFF2DD4BF)
private val TripRouteMiddleColor = Color(0xFFFFD166)
private val TripRouteEndColor = Color(0xFFFF5C7A)

private val TripDarkFillLayerIds = listOf(
    "countries-fill",
    "crimea-fill",
    "landcover",
    "landuse"
)
