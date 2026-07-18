package com.example.photomap.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import com.example.photomap.R
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.heatmap.VisibleTripHeatCell
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.TripMapMarker
import com.example.photomap.ui.components.MiniGalleryTimeScrubber
import com.example.photomap.ui.components.MiniPhotoThumbnail
import com.example.photomap.ui.components.PhotoDateGridDay
import com.example.photomap.ui.components.openMiniGalleryPhoto
import com.example.photomap.ui.components.photoDateGridGroups
import com.example.photomap.ui.map.PhotoMapLayerController
import com.example.photomap.ui.map.TripHeatmapFeatureMapper
import com.example.photomap.ui.permissions.PhotoAccessUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

data class HomeSectionHeights(
    val heatmapHeight: Dp,
    val onThisDayHeight: Dp,
    val placesHeight: Dp
)

fun calculateHomeSectionHeights(availableHeight: Dp): HomeSectionHeights {
    return HomeSectionHeights(
        heatmapHeight = (availableHeight * 0.32f).coerceIn(240.dp, 360.dp),
        onThisDayHeight = (availableHeight * 0.42f).coerceIn(300.dp, 420.dp),
        placesHeight = (availableHeight * 0.18f).coerceIn(160.dp, 220.dp)
    )
}

data class HomePlaceCardUiModel(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
    val photoCount: Int,
    val latestAt: Long?,
    val coverPhoto: DevicePhoto?,
    val photos: List<DevicePhoto>
)

@Composable
fun HomeScreen(
    state: PhotoAccessUiState,
    places: List<HomePlaceCardUiModel>,
    mapStyleUrl: String,
    onOpenMap: () -> Unit,
    onOpenMapForDay: (Long) -> Unit,
    onOpenAllTrips: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenAllPlaces: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onHeatmapViewportChanged: (PhotoMapBounds, Double) -> Unit,
    onRefresh: () -> Unit
) {
    val photos = state.photos
    val photosWithLocation = remember(state.photos) {
        state.photos.filter { photo -> photo.hasLocation }
    }
    val photosById = remember(photos) {
        photos.associateBy { photo -> photo.mediaId }
    }
    val featuredTrips = remember(state.tripMarkers) {
        state.tripMarkers
            .sortedWith(compareByDescending<TripMapMarker> { marker -> marker.endDay }.thenByDescending { it.photoCount })
            .take(12)
    }
    val onThisDay by produceState(initialValue = emptyOnThisDayUiState(), photos) {
        value = withContext(Dispatchers.Default) {
            buildOnThisDayUiState(photos)
        }
    }
    val homePositions = remember(photosWithLocation, state.tripMarkers) {
        homeMapPositions(photosWithLocation, state.tripMarkers)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBarIconActions(
            photosWithLocationCount = photosWithLocation.size,
            tripCount = state.tripMarkers.size,
            isLoading = state.isLoading || state.isTripSegmentationRunning,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val heights = calculateHomeSectionHeights(maxHeight)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    HomeHeatmapSection(
                        height = heights.heatmapHeight,
                        heatCells = state.visibleTripHeatCells,
                        positions = homePositions,
                        mapStyleUrl = mapStyleUrl,
                        isLoading = state.isLoading || state.isTripSegmentationRunning,
                        hasTrips = state.tripMarkers.isNotEmpty(),
                        onViewportChanged = onHeatmapViewportChanged,
                        onOpenMap = onOpenMap
                    )
                }
                item {
                    FeaturedTripsSection(
                        trips = featuredTrips,
                        photosById = photosById,
                        tripPhotoIdsByTripId = state.tripPhotoIdsByTripId,
                        onOpenAllTrips = onOpenAllTrips,
                        onOpenTrip = onOpenTrip
                    )
                }
                item {
                    PopularPlacesSection(
                        height = heights.placesHeight,
                        places = places.take(8),
                        onOpenAllPlaces = onOpenAllPlaces,
                        onOpenPlace = onOpenPlace
                    )
                }
                item {
                    OnThisDaySection(
                        height = heights.onThisDayHeight,
                        state = onThisDay,
                        onOpenMap = { onOpenMapForDay(onThisDay.targetDateFilterDay) }
                    )
                }
            }
        }
    }
}

@Composable
fun AllPlacesScreen(
    places: List<HomePlaceCardUiModel>,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SimpleHomeTopBar(
                title = "Все места",
                actionText = "Назад",
                onAction = onBack
            )
            if (places.isEmpty()) {
                HomeEmptyState(
                    title = "Места пока не найдены",
                    text = "Добавьте фотографии с геопозицией, чтобы увидеть часто посещаемые места.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(places, key = { place -> place.id }) { place ->
                        PlaceCard(
                            place = place,
                            modifier = Modifier
                                .height(180.dp)
                                .clickable { onOpenPlace(place.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDetailsScreen(
    place: HomePlaceCardUiModel?,
    onOpenMap: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SimpleHomeTopBar(
                title = place?.title ?: "Место",
                actionText = "Открыть на карте",
                onAction = onOpenMap
            )
            if (place == null) {
                HomeEmptyState(
                    title = "Место не найдено",
                    text = "Данные места могли измениться после пересчета индекса.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                val context = LocalContext.current
                val photoGroups = remember(place.photos) {
                    photoDateGridGroups(place.photos)
                }
                val groupIndexByPhotoId = remember(photoGroups) {
                    photoGroups.flatMapIndexed { index, group ->
                        group.photos.map { photo -> photo.mediaId to index }
                    }.toMap()
                }
                val galleryListState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                var selectedPhotoId by remember(place.photos) {
                    mutableStateOf(place.photos.firstOrNull()?.mediaId)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = galleryListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            PlaceDetailsHeader(place = place)
                        }
                        items(photoGroups, key = { group -> group.day }) { group ->
                            PhotoDateGridDay(
                                group = group,
                                selectedPhotoId = selectedPhotoId,
                                onPhotoClick = { photo ->
                                    selectedPhotoId = photo.mediaId
                                    context.openMiniGalleryPhoto(photo)
                                }
                            )
                        }
                    }
                    MiniGalleryTimeScrubber(
                        photos = place.photos,
                        selectedPhotoId = selectedPhotoId,
                        onPhotoSelected = { photo ->
                            selectedPhotoId = photo.mediaId
                            groupIndexByPhotoId[photo.mediaId]?.let { groupIndex ->
                                coroutineScope.launch {
                                    galleryListState.scrollToItem(groupIndex + 1)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

fun buildHomePlaceModels(
    photos: List<DevicePhoto>,
    trips: List<TripMapMarker> = emptyList(),
    tripPhotoIdsByTripId: Map<Long, List<Long>> = emptyMap(),
    maxPlaces: Int = MaxHomePlaceModels,
    maxPreviewPhotosPerPlace: Int = MaxHomePlacePreviewPhotos
): List<HomePlaceCardUiModel> {
    val scoredPlaces = buildSeedHomePlaceCandidates(
        photos = photos,
        maxPreviewPhotosPerPlace = maxPreviewPhotosPerPlace
    ) + buildTripHomePlaceCandidates(
        photos = photos,
        trips = trips,
        tripPhotoIdsByTripId = tripPhotoIdsByTripId,
        maxPreviewPhotosPerPlace = maxPreviewPhotosPerPlace
    )

    return scoredPlaces
        .sortedWith(
            compareByDescending<HomeScoredPlace> { scoredPlace -> scoredPlace.score }
                .thenByDescending { scoredPlace -> scoredPlace.place.latestAt ?: 0L }
                .thenBy { scoredPlace -> scoredPlace.place.title }
        )
        .hierarchyAware(maxPlaces)
        .map { scoredPlace -> scoredPlace.place }
        .take(maxPlaces)
}

private fun buildSeedHomePlaceCandidates(
    photos: List<DevicePhoto>,
    maxPreviewPhotosPerPlace: Int
): List<HomeScoredPlace> {
    val photosByGeoObject = linkedMapOf<String, MutableList<DevicePhoto>>()
    val geoObjectsById = linkedMapOf<String, HomeGeoObjectMatch>()
    photos.forEach { photo ->
        photo.homeGeoObjectMatches().forEach { geoObject ->
            geoObjectsById.putIfAbsent(geoObject.id, geoObject)
            photosByGeoObject.getOrPut(geoObject.id) { mutableListOf() } += photo
        }
    }

    return photosByGeoObject.mapNotNull { (id, groupedPhotos) ->
        val geoObject = geoObjectsById[id] ?: return@mapNotNull null
        val validPhotos = groupedPhotos
            .distinctBy { photo -> photo.mediaId }
            .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
        if (validPhotos.isEmpty()) {
            return@mapNotNull null
        }

        val activeDays = validPhotos.activeDayCount()
        val sessionCount = validPhotos.placeSessionCount(geoObject.facet)
        val activeMonths = validPhotos.activeMonthCount()
        val tripCount = validPhotos.tripLikeVisitCount()
        val score = placeScore(
            photoCount = validPhotos.size,
            sessionCount = sessionCount,
            activeDays = activeDays,
            activeMonths = activeMonths,
            tripCount = tripCount,
            importance = geoObject.importance
        )
        HomeScoredPlace(
            place = HomePlaceCardUiModel(
                id = id,
                title = geoObject.name,
                latitude = geoObject.centerLatitude,
                longitude = geoObject.centerLongitude,
                visitCount = sessionCount.coerceAtLeast(activeDays),
                photoCount = validPhotos.size,
                latestAt = validPhotos.firstOrNull()?.photoDateMillis(),
                coverPhoto = validPhotos.firstOrNull(),
                photos = validPhotos.take(maxPreviewPhotosPerPlace)
            ),
            photoIds = validPhotos.map { photo -> photo.mediaId }.toSet(),
            score = score,
            facet = geoObject.facet
        )
    }
}

private fun buildTripHomePlaceCandidates(
    photos: List<DevicePhoto>,
    trips: List<TripMapMarker>,
    tripPhotoIdsByTripId: Map<Long, List<Long>>,
    maxPreviewPhotosPerPlace: Int
): List<HomeScoredPlace> {
    if (photos.isEmpty() || trips.isEmpty()) {
        return emptyList()
    }

    val photosById = photos.associateBy { photo -> photo.mediaId }
    val groups = linkedMapOf<String, MutableTripHomePlace>()
    trips
        .sortedWith(compareByDescending<TripMapMarker> { marker -> marker.endDay }.thenByDescending { it.photoCount })
        .forEach { marker ->
            val tripPhotos = photosForTripPlaceMarker(
                photos = photos,
                photosById = photosById,
                marker = marker,
                orderedPhotoIds = tripPhotoIdsByTripId[marker.tripId]
            )
            if (tripPhotos.isEmpty()) {
                return@forEach
            }

            val placeId = marker.tripPlaceId()
            groups
                .getOrPut(placeId) {
                    MutableTripHomePlace(
                        id = placeId,
                        title = marker.tripPlaceTitle()
                    )
                }
                .add(marker = marker, photos = tripPhotos)
        }

    return groups.values.mapNotNull { group ->
        group.toScoredPlace(maxPreviewPhotosPerPlace)
    }
}

fun photosForHomePlace(
    photos: List<DevicePhoto>,
    placeId: String,
    trips: List<TripMapMarker> = emptyList(),
    tripPhotoIdsByTripId: Map<Long, List<Long>> = emptyMap()
): List<DevicePhoto> {
    if (placeId.isTripHomePlaceId()) {
        return photosForTripHomePlace(
            photos = photos,
            placeId = placeId,
            trips = trips,
            tripPhotoIdsByTripId = tripPhotoIdsByTripId
        )
    }

    return photos
        .asSequence()
        .filter { photo -> photo.homeGeoObjectMatches().any { geoObject -> geoObject.id == placeId } }
        .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
        .toList()
}

private fun photosForTripHomePlace(
    photos: List<DevicePhoto>,
    placeId: String,
    trips: List<TripMapMarker>,
    tripPhotoIdsByTripId: Map<Long, List<Long>>
): List<DevicePhoto> {
    if (photos.isEmpty() || trips.isEmpty()) {
        return emptyList()
    }

    val photosById = photos.associateBy { photo -> photo.mediaId }
    return trips
        .asSequence()
        .filter { marker -> marker.tripPlaceId() == placeId }
        .flatMap { marker ->
            photosForTripPlaceMarker(
                photos = photos,
                photosById = photosById,
                marker = marker,
                orderedPhotoIds = tripPhotoIdsByTripId[marker.tripId]
            ).asSequence()
        }
        .distinctBy { photo -> photo.mediaId }
        .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
        .toList()
}

private fun photosForTripPlaceMarker(
    photos: List<DevicePhoto>,
    photosById: Map<Long, DevicePhoto>,
    marker: TripMapMarker,
    orderedPhotoIds: List<Long>?
): List<DevicePhoto> {
    val linkedPhotos = orderedPhotoIds
        .orEmpty()
        .mapNotNull { photoId -> photosById[photoId] }
    if (linkedPhotos.isNotEmpty()) {
        return linkedPhotos
            .distinctBy { photo -> photo.mediaId }
            .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
    }

    val radiusKm = (marker.radiusKm ?: TripPlaceFallbackRadiusKm)
        .coerceIn(MinTripPlaceFallbackRadiusKm, MaxTripPlaceFallbackRadiusKm) + TripPlaceFallbackPaddingKm
    return photos
        .asSequence()
        .filter { photo -> photo.hasLocation }
        .filter { photo ->
            val day = photo.homePhotoDay() ?: return@filter false
            day in marker.startDay..marker.endDay
        }
        .filter { photo ->
            haversineKm(
                firstLatitude = requireNotNull(photo.latitude),
                firstLongitude = requireNotNull(photo.longitude),
                secondLatitude = marker.latitude,
                secondLongitude = marker.longitude
            ) <= radiusKm
        }
        .distinctBy { photo -> photo.mediaId }
        .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
        .toList()
}

@Composable
private fun HomeTopBarIconActions(
    photosWithLocationCount: Int,
    tripCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Моя карта",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$photosWithLocationCount фото на карте · $tripCount поездок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                TextButton(onClick = onRefresh) {
                    Text(
                        text = "Обновить",
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_gear),
                        contentDescription = "Настройки"
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBarCompact(
    photosWithLocationCount: Int,
    tripCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Моя карта",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$photosWithLocationCount фото на карте · $tripCount поездок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                TextButton(onClick = onRefresh) {
                    Text(
                        text = "Обновить",
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text(
                        text = "Настройки",
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    photosWithLocationCount: Int,
    tripCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Моя карта",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$photosWithLocationCount фото на карте · $tripCount поездок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                TextButton(onClick = onRefresh) {
                    Text("Обновить")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Настройки")
                }
            }
        }
    }
}

@Composable
private fun SimpleHomeTopBar(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun HomeHeatmapSection(
    height: Dp,
    heatCells: List<VisibleTripHeatCell>,
    positions: List<LatLng>,
    mapStyleUrl: String,
    isLoading: Boolean,
    hasTrips: Boolean,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit,
    onOpenMap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HomeHeatmapPreview(
            heatCells = heatCells,
            positions = positions,
            mapStyleUrl = mapStyleUrl,
            onViewportChanged = onViewportChanged
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.58f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.36f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "География галереи",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Поездки и часто посещаемые места",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onOpenMap) {
                Text("Посмотреть на карте")
            }
            if (isLoading) {
                Surface(
                    color = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        text = "Обновляем данные",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else if (!hasTrips) {
                Surface(
                    color = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        text = "Поездки пока не найдены",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedTripsSection(
    trips: List<TripMapMarker>,
    photosById: Map<Long, DevicePhoto>,
    tripPhotoIdsByTripId: Map<Long, List<Long>>,
    onOpenAllTrips: () -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Поездки",
            action = "Все поездки",
            onAction = onOpenAllTrips
        )
        if (trips.isEmpty()) {
            HomeInlineEmptyState(
                title = "Поездки пока не найдены",
                text = "Добавьте фотографии с датой и геопозицией."
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trips, key = { trip -> trip.tripId }) { trip ->
                    val firstTripPhoto = firstPhotoForTrip(
                        trip = trip,
                        photosById = photosById,
                        tripPhotoIdsByTripId = tripPhotoIdsByTripId
                    )
                    val coverPhoto = trip.coverPhotoId?.let { photoId -> photosById[photoId] } ?: firstTripPhoto
                    TripCard(
                        trip = trip,
                        coverPhoto = coverPhoto,
                        shareBackgroundPhoto = firstTripPhoto ?: coverPhoto,
                        modifier = Modifier
                            .fillParentMaxWidth(HomeCarouselCardWidthFraction)
                            .height(172.dp)
                            .clickable { onOpenTrip(trip.tripId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PopularPlacesSection(
    height: Dp,
    places: List<HomePlaceCardUiModel>,
    onOpenAllPlaces: () -> Unit,
    onOpenPlace: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Популярные места",
            action = "Все места",
            onAction = onOpenAllPlaces
        )
        if (places.isEmpty()) {
            HomeInlineEmptyState(
                title = "Места пока не найдены",
                text = "На главной появятся точки с повторными посещениями."
            )
        } else {
            LazyRow(
                modifier = Modifier.height(height),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(places, key = { place -> place.id }) { place ->
                    PlaceCard(
                        place = place,
                        modifier = Modifier
                            .fillParentMaxWidth(HomeCarouselCardWidthFraction)
                            .height(height)
                            .clickable { onOpenPlace(place.id) }
                    )
                }
            }
        }
    }
}

private fun firstPhotoForTrip(
    trip: TripMapMarker,
    photosById: Map<Long, DevicePhoto>,
    tripPhotoIdsByTripId: Map<Long, List<Long>>
): DevicePhoto? {
    return tripPhotoIdsByTripId[trip.tripId]
        .orEmpty()
        .mapNotNull { photoId -> photosById[photoId] }
        .minWithOrNull(
            compareBy<DevicePhoto> { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
                .thenBy { photo -> photo.mediaId }
        )
}

@Composable
private fun OnThisDaySection(
    height: Dp,
    state: OnThisDayUiState,
    onOpenMap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Этот день в прошлом",
            action = "Карта",
            onAction = onOpenMap
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(height)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (state.points.isNotEmpty()) {
                HomeMemoryPreview(points = state.points)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.46f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.46f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    text = if (state.isEmpty) "Воспоминаний пока нет" else "${state.photoCount} фото · ${state.years.joinToString()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.previewPhotos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.previewPhotos.take(4).forEach { photo ->
                        MiniPhotoThumbnail(
                            photo = photo,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onAction) {
            Text(action)
        }
    }
}

@Composable
private fun TripCard(
    trip: TripMapMarker,
    coverPhoto: DevicePhoto?,
    shareBackgroundPhoto: DevicePhoto?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiniPhotoThumbnail(
                photo = coverPhoto,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clickable { context.shareTripAnalyticsImage(trip, shareBackgroundPhoto) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_share),
                        contentDescription = "Поделиться поездкой",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = trip.placeName?.takeIf { value -> value.isNotBlank() } ?: "Поездка",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTripDateRange(trip.startDay, trip.endDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${trip.photoCount} фото · ${trip.activeDayCount} дн.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(
    place: HomePlaceCardUiModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiniPhotoThumbnail(
                photo = place.coverPhoto,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = place.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${place.visitCount} посещений · ${place.photoCount} фото",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f)
                )
                place.latestAt?.let { latest ->
                    Text(
                        text = "Последний раз ${formatMillisDate(latest)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailsHeader(place: HomePlaceCardUiModel) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniPhotoThumbnail(
                photo = place.coverPhoto,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = place.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${place.visitCount} посещений",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${place.photoCount} фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                place.latestAt?.let { latest ->
                    Text(
                        text = "Последний раз ${formatMillisDate(latest)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeInlineEmptyState(
    title: String,
    text: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HomeHeatmapPreview(
    heatCells: List<VisibleTripHeatCell>,
    positions: List<LatLng>,
    mapStyleUrl: String,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layerController = remember { PhotoMapLayerController() }
    val latestHeatCells = rememberUpdatedState(heatCells)
    val latestPositions = rememberUpdatedState(positions)
    val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    var hasFitCamera by remember(positions) { mutableStateOf(false) }
    val mapView = remember(mapStyleUrl) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.configureHomeHeatmapPreview()
                mapLibreMap.addOnCameraIdleListener {
                    mapLibreMap.dispatchHomeHeatmapViewportChanged(latestOnViewportChanged.value)
                }
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    style.removeHomeMapPoliticalLayers()
                    layerController.reset()
                    layerController.updateTripHeatmap(
                        style = style,
                        featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(latestHeatCells.value)
                    )
                    isStyleReady = true
                    post {
                        mapLibreMap.fitHomeHeatmapPreview(latestPositions.value)
                        mapLibreMap.dispatchHomeHeatmapViewportChanged(latestOnViewportChanged.value)
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    mapView.post {
                        map?.dispatchHomeHeatmapViewportChanged(latestOnViewportChanged.value)
                    }
                }
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

    LaunchedEffect(map, isStyleReady, heatCells) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady) {
            return@LaunchedEffect
        }
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        layerController.updateTripHeatmap(
            style = style,
            featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(heatCells)
        )
    }

    LaunchedEffect(map, isStyleReady, positions) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (!isStyleReady || hasFitCamera) {
            return@LaunchedEffect
        }
        mapView.post {
            mapLibreMap.fitHomeHeatmapPreview(positions)
            hasFitCamera = true
            mapLibreMap.dispatchHomeHeatmapViewportChanged(latestOnViewportChanged.value)
            mapView.postDelayed(
                {
                    mapLibreMap.dispatchHomeHeatmapViewportChanged(latestOnViewportChanged.value)
                },
                HomeHeatmapViewportRefreshDelayMs
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7D948C))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )
    }
}

private fun MapLibreMap.configureHomeHeatmapPreview() {
    uiSettings.setAllGesturesEnabled(false)
    uiSettings.setCompassEnabled(false)
    uiSettings.setLogoEnabled(false)
    uiSettings.setAttributionEnabled(false)
}

private fun Style.removeHomeMapPoliticalLayers() {
    layers
        .map { layer -> layer.id }
        .filter { layerId -> layerId.isHomeMapPoliticalLayerId() }
        .forEach { layerId -> removeLayer(layerId) }
}

private fun String.isHomeMapPoliticalLayerId(): Boolean {
    val normalized = lowercase(Locale.US)
    val isBoundaryLayer = normalized in HomeExactPoliticalLayerIds ||
        HomePoliticalBoundaryTokens.any { token -> normalized.contains(token) }
    val isCountryLabelLayer = HomeCountryLabelTokens.any { token -> normalized.contains(token) } &&
        HomeLabelLayerTokens.any { token -> normalized.contains(token) }
    return isBoundaryLayer || isCountryLabelLayer
}

private fun MapLibreMap.fitHomeHeatmapPreview(positions: List<LatLng>) {
    runCatching {
        when (positions.size) {
            0 -> moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(HomeHeatmapFallbackLatitude, HomeHeatmapFallbackLongitude),
                    HomeHeatmapFallbackZoom
                )
            )
            1 -> moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    positions.first(),
                    HomeHeatmapSinglePositionZoom
                )
            )
            else -> {
                val bounds = LatLngBounds.Builder()
                positions.forEach { position -> bounds.include(position) }
                moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), HomeHeatmapBoundsPaddingPx))
            }
        }
    }.onFailure { error ->
        Log.w(HomeMapLogTag, "Failed to fit home heatmap preview", error)
    }
}

private fun MapLibreMap.dispatchHomeHeatmapViewportChanged(
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    runCatching {
        val visibleRegion = projection.visibleRegion
        val points = listOfNotNull(
            visibleRegion.nearLeft,
            visibleRegion.nearRight,
            visibleRegion.farLeft,
            visibleRegion.farRight
        )
        if (points.isEmpty()) {
            return@runCatching
        }
        onViewportChanged(
            PhotoMapBounds(
                south = points.minOf { point -> point.latitude },
                west = points.minOf { point -> point.longitude },
                north = points.maxOf { point -> point.latitude },
                east = points.maxOf { point -> point.longitude }
            ),
            cameraPosition.zoom
        )
    }.onFailure { error ->
        Log.w(HomeMapLogTag, "Failed to dispatch home heatmap viewport", error)
    }
}

@Composable
private fun HomeMemoryPreview(points: List<OnThisDayMapPoint>) {
    val previewPoints = remember(points) {
        points
            .asSequence()
            .take(MaxHomePreviewMemoryPoints)
            .map { point ->
                HomePreviewPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    weight = 0.74f
                )
            }
            .toList()
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF586C74))
    ) {
        drawRect(Color(0xFF596F73))
        drawCircle(
            color = Color(0xFF86A798).copy(alpha = 0.34f),
            radius = maxOf(size.width, size.height) * 0.44f,
            center = Offset(size.width * 0.64f, size.height * 0.42f)
        )
        previewPoints.forEach { point ->
            val center = point.toPreviewOffset(width = size.width, height = size.height)
            drawCircle(
                color = Color(0xFFFF5C7A).copy(alpha = 0.72f),
                radius = 9f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.86f),
                radius = 3.5f,
                center = center
            )
        }
    }
}

private fun List<DevicePhoto>.activeDayCount(): Int {
    return mapNotNull { photo ->
        photo.homePhotoDay()
    }.toSet().size.coerceAtLeast(1)
}

private fun List<DevicePhoto>.activeMonthCount(): Int {
    return mapNotNull { photo ->
        val millis = photo.photoDateMillis() ?: return@mapNotNull null
        Calendar.getInstance().apply { timeInMillis = millis }.let { calendar ->
            calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
        }
    }.toSet().size.coerceAtLeast(1)
}

private fun List<DevicePhoto>.placeSessionCount(facet: PlaceFacet): Int {
    val timestamps = mapNotNull { photo -> photo.photoDateMillis() }.sorted()
    if (timestamps.isEmpty()) {
        return activeDayCount()
    }

    val gapMs = when (facet) {
        PlaceFacet.City -> 36L * 60L * 60L * 1000L
        PlaceFacet.District,
        PlaceFacet.Neighbourhood -> 24L * 60L * 60L * 1000L
        PlaceFacet.NaturalArea,
        PlaceFacet.Landmark,
        PlaceFacet.Transport,
        PlaceFacet.Venue -> 4L * 60L * 60L * 1000L
        PlaceFacet.Country,
        PlaceFacet.Region,
        PlaceFacet.Unknown,
        PlaceFacet.Technical -> 36L * 60L * 60L * 1000L
    }
    var sessions = 1
    var previous = timestamps.first()
    timestamps.drop(1).forEach { timestamp ->
        if (timestamp - previous > gapMs) {
            sessions += 1
        }
        previous = timestamp
    }
    return sessions
}

private fun List<DevicePhoto>.tripLikeVisitCount(): Int {
    val days = mapNotNull { photo ->
        photo.homePhotoDay()
    }.distinct().sorted()
    if (days.isEmpty()) {
        return 1
    }

    var visits = 1
    var previous = days.first()
    days.drop(1).forEach { day ->
        if (day - previous > TripLikeVisitGapDays) {
            visits += 1
        }
        previous = day
    }
    return visits
}

private fun DevicePhoto.homePhotoDay(): Long? {
    return photoDateMillis()?.let { millis -> Math.floorDiv(millis, MillisPerDay) }
}

private fun placeScore(
    photoCount: Int,
    sessionCount: Int,
    activeDays: Int,
    activeMonths: Int,
    tripCount: Int,
    importance: Double
): Double {
    return importance *
        (
            3.0 * ln(1.0 + sessionCount) +
                2.0 * ln(1.0 + activeDays) +
                1.5 * ln(1.0 + activeMonths) +
                1.0 * ln(1.0 + tripCount) +
                0.3 * ln(1.0 + photoCount)
            )
}

private fun List<HomeScoredPlace>.hierarchyAware(maxPlaces: Int = MaxHomePlaceModels): List<HomeScoredPlace> {
    val remaining = toMutableList()
    val selected = mutableListOf<HomeScoredPlace>()
    while (remaining.isNotEmpty() && selected.size < maxPlaces) {
        val next = remaining.maxBy { candidate -> candidate.displayScoreAfter(selected) }
        selected += next
        remaining -= next
    }
    return selected
}

private fun HomeScoredPlace.displayScoreAfter(selected: List<HomeScoredPlace>): Double {
    return selected.fold(score) { currentScore, selectedPlace ->
        val overlap = photoIds.overlapWith(selectedPlace.photoIds)
        val related = geoObjectsAreRelated(place.id, selectedPlace.place.id)
        val penalty = when {
            related && overlap > 0.85 -> 0.88
            related && overlap > 0.65 -> 0.94
            overlap > 0.85 -> 0.42
            overlap > 0.65 -> 0.68
            facet == selectedPlace.facet && overlap > 0.40 -> 0.82
            else -> 1.0
        }
        currentScore * penalty
    }
}

private fun Set<Long>.overlapWith(other: Set<Long>): Double {
    val baseSize = minOf(size, other.size)
    if (baseSize == 0) {
        return 0.0
    }
    return count { id -> id in other }.toDouble() / baseSize.toDouble()
}

private fun TripMapMarker.tripPlaceId(): String {
    val title = placeName?.cleanPlaceTitle()?.takeIf { value -> value.isNotEmpty() }
    return if (title != null) {
        TripPlaceNameIdPrefix + title.lowercase(Locale.US).stablePlaceHash()
    } else {
        TripPlaceTripIdPrefix + tripId
    }
}

private fun TripMapMarker.tripPlaceTitle(): String {
    return placeName
        ?.cleanPlaceTitle()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: formatTripCoordinates(latitude = latitude, longitude = longitude)
}

private fun String.cleanPlaceTitle(): String {
    return trim().replace(WhitespaceRegex, " ")
}

private fun String.stablePlaceHash(): String {
    val hash = hashCode().toLong()
    val positiveHash = if (hash < 0) -hash else hash
    return positiveHash.toString(Character.MAX_RADIX)
}

private fun String.isTripHomePlaceId(): Boolean {
    return startsWith(TripPlaceNameIdPrefix) || startsWith(TripPlaceTripIdPrefix)
}

private fun formatTripCoordinates(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.3f, %.3f", latitude, longitude)
}

private fun Context.shareTripAnalyticsImage(
    trip: TripMapMarker,
    backgroundPhoto: DevicePhoto?
) {
    runCatching {
        val bitmap = createTripAnalyticsShareBitmap(trip, backgroundPhoto)
        val directory = File(cacheDir, "trip-share").apply { mkdirs() }
        val file = File(directory, "trip-${trip.tripId}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться поездкой"))
    }.onFailure {
        Toast.makeText(this, "Не удалось подготовить изображение поездки", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.createTripAnalyticsShareBitmap(
    trip: TripMapMarker,
    backgroundPhoto: DevicePhoto?
): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                0xFF111827.toInt(),
                0xFF172033.toInt(),
                0xFF2A1F4F.toInt()
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

    backgroundPhoto
        ?.let { photo ->
            loadTripShareBackgroundBitmap(
                photo = photo,
                targetWidthPx = width,
                targetHeightPx = height
            )
        }
        ?.let { image ->
            val target = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(
                image,
                image.centerCropSourceRect(target),
                target,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
        }

    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                0xD608101B.toInt(),
                0xAA101827.toInt(),
                0xE308101B.toInt()
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 64f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDDE5ECFF.toInt()
        textSize = 34f
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAFCBD5E1.toInt()
        textSize = 29f
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 42f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCFE5ECFF.toInt()
        textSize = 30f
    }

    val title = trip.placeName?.takeIf { value -> value.isNotBlank() } ?: "Поездка"
    canvas.drawText(title.fitToWidth(titlePaint, 920f), 80f, 135f, titlePaint)
    canvas.drawText(formatTripDateRange(trip.startDay, trip.endDay), 80f, 190f, subtitlePaint)

    val radiusText = trip.radiusKm?.let { radius ->
        String.format(Locale("ru"), "%.1f км", radius)
    } ?: "не рассчитан"
    val metricTop = 285f
    val cardWidth = 420f
    val cardHeight = 154f
    drawTripShareMetric(canvas, "Фотографии", "${trip.photoCount}", 80f, metricTop, cardWidth, cardHeight, labelPaint, valuePaint)
    drawTripShareMetric(canvas, "Активные дни", "${trip.activeDayCount}", 580f, metricTop, cardWidth, cardHeight, labelPaint, valuePaint)
    drawTripShareMetric(canvas, "Радиус", radiusText, 80f, metricTop + 190f, cardWidth, cardHeight, labelPaint, valuePaint)
    drawTripShareMetric(canvas, "Тип", formatTripTypeLabel(trip.type), 580f, metricTop + 190f, cardWidth, cardHeight, labelPaint, valuePaint)

    canvas.drawText("Координаты центра", 80f, 810f, labelPaint)
    canvas.drawText(formatTripCoordinates(trip.latitude, trip.longitude), 80f, 865f, valuePaint)

    val footerBox = RectF(80f, 1100f, 1000f, 1255f)
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(footerBox, 34f, 34f, footerPaint)
    canvas.drawText("Traverse", 120f, 1162f, valuePaint)
    canvas.drawText("Аналитика поездки, созданная локально на устройстве", 120f, 1215f, smallPaint)

    return bitmap
}

private fun drawTripShareMetric(
    canvas: AndroidCanvas,
    label: String,
    value: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    labelPaint: Paint,
    valuePaint: Paint
) {
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x24FFFFFF
        style = Paint.Style.FILL
    }
    val rect = RectF(left, top, left + width, top + height)
    canvas.drawRoundRect(rect, 30f, 30f, cardPaint)
    canvas.drawText(label, left + 34f, top + 52f, labelPaint)
    canvas.drawText(value.fitToWidth(valuePaint, width - 68f), left + 34f, top + 112f, valuePaint)
}

private fun Context.loadTripShareBackgroundBitmap(
    photo: DevicePhoto,
    targetWidthPx: Int,
    targetHeightPx: Int
): Bitmap? {
    val uri = Uri.parse(photo.uri)
    return runCatching {
        contentResolver.loadThumbnail(uri, Size(targetWidthPx, targetHeightPx), null)
    }.onFailure { error ->
        Log.w(HomeMapLogTag, "Trip share loadThumbnail failed: photoId=${photo.mediaId}", error)
    }.getOrNull() ?: runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val targetScale = maxOf(
                targetWidthPx.toFloat() / width.toFloat(),
                targetHeightPx.toFloat() / height.toFloat()
            )
            decoder.setTargetSize(
                (width * targetScale).toInt().coerceAtLeast(1),
                (height * targetScale).toInt().coerceAtLeast(1)
            )
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
    }.onFailure { error ->
        Log.w(HomeMapLogTag, "Trip share ImageDecoder failed: photoId=${photo.mediaId}", error)
    }.getOrNull()
}

private fun Bitmap.centerCropSourceRect(targetRect: RectF): Rect {
    val targetAspect = targetRect.width() / targetRect.height()
    val sourceAspect = width.toFloat() / height.toFloat()
    return if (sourceAspect > targetAspect) {
        val cropWidth = (height * targetAspect).toInt().coerceAtLeast(1)
        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        Rect(left, 0, (left + cropWidth).coerceAtMost(width), height)
    } else {
        val cropHeight = (width / targetAspect).toInt().coerceAtLeast(1)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        Rect(0, top, width, (top + cropHeight).coerceAtMost(height))
    }
}

private fun String.fitToWidth(paint: Paint, maxWidth: Float): String {
    if (paint.measureText(this) <= maxWidth) {
        return this
    }
    var result = this
    while (result.length > 1 && paint.measureText("$result...") > maxWidth) {
        result = result.dropLast(1)
    }
    return "$result..."
}

private fun formatTripTypeLabel(type: com.example.photomap.domain.trip.TripType): String {
    return when (type) {
        com.example.photomap.domain.trip.TripType.SINGLE_DESTINATION_TRIP -> "Одна локация"
        com.example.photomap.domain.trip.TripType.MULTI_DESTINATION_TRIP -> "Несколько локаций"
        com.example.photomap.domain.trip.TripType.UNKNOWN -> "Не определён"
    }
}

private fun haversineKm(
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

private fun emptyOnThisDayUiState(): OnThisDayUiState {
    val now = Calendar.getInstance()
    val subtitle = SimpleDateFormat("d MMMM", Locale("ru")).format(now.time) + " в разные годы"
    return OnThisDayUiState(
        day = now.get(Calendar.DAY_OF_MONTH),
        month = now.get(Calendar.MONTH) + 1,
        targetDateFilterDay = previousYearDateFilterDay(now),
        years = emptyList(),
        photoCount = 0,
        points = emptyList(),
        previewPhotos = emptyList(),
        subtitle = subtitle,
        isEmpty = true
    )
}

private fun buildOnThisDayUiState(photos: List<DevicePhoto>): OnThisDayUiState {
    val now = Calendar.getInstance()
    val day = now.get(Calendar.DAY_OF_MONTH)
    val month = now.get(Calendar.MONTH)
    val currentYear = now.get(Calendar.YEAR)
    val matchingPhotos = photos
        .mapNotNull { photo ->
            val millis = photo.photoDateMillis() ?: return@mapNotNull null
            val calendar = Calendar.getInstance().apply { timeInMillis = millis }
            if (calendar.get(Calendar.DAY_OF_MONTH) == day &&
                calendar.get(Calendar.MONTH) == month &&
                calendar.get(Calendar.YEAR) < currentYear &&
                photo.latitude != null &&
                photo.longitude != null
            ) {
                photo to calendar.get(Calendar.YEAR)
            } else {
                null
            }
        }
        .sortedByDescending { pair -> pair.second }
    val years = matchingPhotos.map { pair -> pair.second }.distinct()
    val points = matchingPhotos
        .asSequence()
        .take(MaxOnThisDayMapPoints)
        .map { (photo, year) ->
            OnThisDayMapPoint(
                photoId = photo.mediaId,
                latitude = requireNotNull(photo.latitude),
                longitude = requireNotNull(photo.longitude),
                year = year,
                thumbnailUri = photo.uri
            )
        }
        .toList()
    val targetDateFilterDay = matchingPhotos
        .firstOrNull()
        ?.first
        ?.photoDateMillis()
        ?.let(::homeDateFilterDayFromMillis)
        ?: previousYearDateFilterDay(now)
    val subtitle = SimpleDateFormat("d MMMM", Locale("ru")).format(now.time) + " в разные годы"
    return OnThisDayUiState(
        day = day,
        month = month + 1,
        targetDateFilterDay = targetDateFilterDay,
        years = years,
        photoCount = matchingPhotos.size,
        points = points,
        previewPhotos = matchingPhotos.map { pair -> pair.first }.take(8),
        subtitle = subtitle,
        isEmpty = matchingPhotos.isEmpty()
    )
}

private fun homeMapPositions(
    photos: List<DevicePhoto>,
    trips: List<TripMapMarker>
): List<LatLng> {
    val tripPositions = trips
        .asSequence()
        .map { marker -> LatLng(marker.latitude, marker.longitude) }
        .take(MaxHomeMapPositions)
        .toList()
    if (tripPositions.isNotEmpty()) {
        return tripPositions
    }
    return photos
        .asSequence()
        .mapNotNull { photo ->
            val latitude = photo.latitude ?: return@mapNotNull null
            val longitude = photo.longitude ?: return@mapNotNull null
            LatLng(latitude, longitude)
        }
        .take(MaxHomeMapPositions)
        .toList()
}

private fun formatTripDateRange(startDay: Long, endDay: Long): String {
    val pattern = if (startDay == endDay) "d MMMM yyyy" else "d MMM - d MMM yyyy"
    val formatter = SimpleDateFormat(pattern, Locale("ru"))
    return if (startDay == endDay) {
        formatter.format(Date(photoDateDayToMillis(startDay)))
    } else {
        val start = SimpleDateFormat("d MMM", Locale("ru")).format(Date(photoDateDayToMillis(startDay)))
        val end = SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(photoDateDayToMillis(endDay)))
        "$start - $end"
    }
}

private fun formatMillisDate(millis: Long): String {
    return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
}

private fun homeDateFilterDayFromMillis(millis: Long): Long {
    return Math.floorDiv(millis, MillisPerDay)
}

private fun previousYearDateFilterDay(now: Calendar): Long {
    val previousYear = (now.clone() as Calendar).apply {
        add(Calendar.YEAR, -1)
    }
    return homeDateFilterDayFromMillis(previousYear.timeInMillis)
}

private data class OnThisDayUiState(
    val day: Int,
    val month: Int,
    val targetDateFilterDay: Long,
    val years: List<Int>,
    val photoCount: Int,
    val points: List<OnThisDayMapPoint>,
    val previewPhotos: List<DevicePhoto>,
    val subtitle: String,
    val isEmpty: Boolean
)

private data class OnThisDayMapPoint(
    val photoId: Long,
    val latitude: Double,
    val longitude: Double,
    val year: Int,
    val thumbnailUri: String
)

private data class HomeScoredPlace(
    val place: HomePlaceCardUiModel,
    val photoIds: Set<Long>,
    val score: Double,
    val facet: PlaceFacet
)

private class MutableTripHomePlace(
    val id: String,
    val title: String
) {
    private val markers = mutableListOf<TripMapMarker>()
    private val photosById = linkedMapOf<Long, DevicePhoto>()

    fun add(marker: TripMapMarker, photos: List<DevicePhoto>) {
        markers += marker
        photos.forEach { photo -> photosById[photo.mediaId] = photo }
    }

    fun toScoredPlace(maxPreviewPhotosPerPlace: Int): HomeScoredPlace? {
        val validPhotos = photosById.values
            .distinctBy { photo -> photo.mediaId }
            .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
        if (validPhotos.isEmpty() || markers.isEmpty()) {
            return null
        }

        val totalWeight = markers
            .sumOf { marker -> marker.photoCount.coerceAtLeast(1) }
            .coerceAtLeast(1)
        val latitude = markers.sumOf { marker ->
            marker.latitude * marker.photoCount.coerceAtLeast(1)
        } / totalWeight.toDouble()
        val longitude = markers.sumOf { marker ->
            marker.longitude * marker.photoCount.coerceAtLeast(1)
        } / totalWeight.toDouble()
        val activeDays = validPhotos.activeDayCount()
        val activeMonths = validPhotos.activeMonthCount()
        val tripCount = markers.size.coerceAtLeast(validPhotos.tripLikeVisitCount())
        val sessionCount = validPhotos.placeSessionCount(PlaceFacet.City).coerceAtLeast(tripCount)
        val averageConfidence = markers.map { marker -> marker.confidence }.average()
        val importance = (
            0.95 +
                averageConfidence.coerceIn(0.0, 1.0) * 0.3 +
                ln(1.0 + tripCount) * 0.08
            ).coerceIn(0.75, 1.35)
        val score = placeScore(
            photoCount = validPhotos.size,
            sessionCount = sessionCount,
            activeDays = activeDays,
            activeMonths = activeMonths,
            tripCount = tripCount,
            importance = importance
        )

        return HomeScoredPlace(
            place = HomePlaceCardUiModel(
                id = id,
                title = title,
                latitude = latitude,
                longitude = longitude,
                visitCount = sessionCount.coerceAtLeast(activeDays),
                photoCount = validPhotos.size,
                latestAt = validPhotos.firstOrNull()?.photoDateMillis(),
                coverPhoto = validPhotos.firstOrNull(),
                photos = validPhotos.take(maxPreviewPhotosPerPlace)
            ),
            photoIds = validPhotos.map { photo -> photo.mediaId }.toSet(),
            score = score,
            facet = PlaceFacet.City
        )
    }
}

private data class HomePreviewPoint(
    val latitude: Double,
    val longitude: Double,
    val weight: Float
)

private fun HomePreviewPoint.toPreviewOffset(width: Float, height: Float): Offset {
    val x = (((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0) * width).toFloat()
    val y = (((90.0 - latitude) / 180.0).coerceIn(0.0, 1.0) * height).toFloat()
    return Offset(x, y)
}

private const val MaxHomePlaceModels = 200
private const val MaxHomePlacePreviewPhotos = 80
private const val MaxHomeMapPositions = 800
private const val MaxOnThisDayMapPoints = 300
private const val MaxHomePreviewMemoryPoints = 80
private const val HomeHeatmapBoundsPaddingPx = 90
private const val HomeHeatmapViewportRefreshDelayMs = 320L
private const val HomeHeatmapSinglePositionZoom = 5.5
private const val HomeHeatmapFallbackLatitude = 20.0
private const val HomeHeatmapFallbackLongitude = 0.0
private const val HomeHeatmapFallbackZoom = 0.8
private const val HomeCarouselCardWidthFraction = 0.54f
private const val TripLikeVisitGapDays = 3L
private const val MillisPerDay = 24L * 60L * 60L * 1000L
private const val EarthRadiusKm = 6371.0
private const val TripPlaceFallbackRadiusKm = 35.0
private const val MinTripPlaceFallbackRadiusKm = 15.0
private const val MaxTripPlaceFallbackRadiusKm = 80.0
private const val TripPlaceFallbackPaddingKm = 10.0
private const val TripPlaceNameIdPrefix = "trip-place-name-"
private const val TripPlaceTripIdPrefix = "trip-place-trip-"
private const val HomeMapLogTag = "PhotoMapHome"
private val WhitespaceRegex = Regex("\\s+")
private val HomeExactPoliticalLayerIds = setOf(
    "countries-boundary",
    "geolines",
    "geolines-label"
)
private val HomePoliticalBoundaryTokens = listOf(
    "boundary",
    "border",
    "admin-",
    "admin_"
)
private val HomeCountryLabelTokens = listOf(
    "country",
    "countries"
)
private val HomeLabelLayerTokens = listOf(
    "label",
    "name",
    "place"
)
