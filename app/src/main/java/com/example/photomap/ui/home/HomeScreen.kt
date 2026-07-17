package com.example.photomap.ui.home

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.heatmap.VisibleTripHeatCell
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.model.photoDateMillis
import com.example.photomap.domain.trip.TripMapMarker
import com.example.photomap.ui.components.MiniPhotoGallery
import com.example.photomap.ui.components.MiniPhotoThumbnail
import com.example.photomap.ui.map.PhotoMapLayerController
import com.example.photomap.ui.map.TripHeatmapFeatureMapper
import com.example.photomap.ui.permissions.PhotoAccessUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

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
    mapStyleUrl: String,
    onOpenMap: () -> Unit,
    onOpenAllTrips: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenAllPlaces: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onHeatmapViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    val photosWithLocation = remember(state.photos) {
        state.photos.filter { photo -> photo.hasLocation }
    }
    val featuredTrips = remember(state.tripMarkers) {
        state.tripMarkers
            .sortedWith(compareByDescending<TripMapMarker> { marker -> marker.endDay }.thenByDescending { it.photoCount })
            .take(12)
    }
    val places = remember(state.photos) {
        buildHomePlaceModels(state.photos)
    }
    val onThisDay = remember(state.photos) {
        buildOnThisDayUiState(state.photos)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
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
                        positions = homeMapPositions(photosWithLocation, state.tripMarkers),
                        mapStyleUrl = mapStyleUrl,
                        isLoading = state.isLoading || state.isTripSegmentationRunning,
                        hasTrips = state.tripMarkers.isNotEmpty(),
                        onOpenMap = onOpenMap,
                        onViewportChanged = onHeatmapViewportChanged
                    )
                }
                item {
                    FeaturedTripsSection(
                        trips = featuredTrips,
                        photosById = state.photos.associateBy { photo -> photo.mediaId },
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
                        mapStyleUrl = mapStyleUrl,
                        onOpenMap = onOpenMap
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
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SimpleHomeTopBar(
                title = place?.title ?: "Место",
                actionText = "Назад",
                onAction = onBack
            )
            if (place == null) {
                HomeEmptyState(
                    title = "Место не найдено",
                    text = "Данные места могли измениться после пересчета индекса.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        PlaceDetailsHeader(place = place)
                    }
                    groupedPhotosByDay(place.photos).forEach { group ->
                        item(key = "day-${group.day}") {
                            Text(
                                text = formatDate(group.day),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        item(key = "photos-${group.day}") {
                            MiniPhotoGallery(photos = group.photos)
                        }
                    }
                }
            }
        }
    }
}

fun buildHomePlaceModels(photos: List<DevicePhoto>): List<HomePlaceCardUiModel> {
    return photos
        .asSequence()
        .filter { photo -> photo.latitude != null && photo.longitude != null }
        .groupBy { photo ->
            val latitudeCell = ((photo.latitude ?: 0.0) / PlaceCellSizeDegrees).roundToInt()
            val longitudeCell = ((photo.longitude ?: 0.0) / PlaceCellSizeDegrees).roundToInt()
            "p_${latitudeCell}_${longitudeCell}"
        }
        .mapNotNull { (id, groupedPhotos) ->
            val validPhotos = groupedPhotos
                .filter { photo -> photo.latitude != null && photo.longitude != null }
                .sortedWith(compareByDescending<DevicePhoto> { photo -> photo.photoDateMillis() ?: 0L }.thenBy { it.mediaId })
            if (validPhotos.isEmpty()) {
                return@mapNotNull null
            }
            val latitude = validPhotos.mapNotNull { photo -> photo.latitude }.average()
            val longitude = validPhotos.mapNotNull { photo -> photo.longitude }.average()
            val visitDays = validPhotos
                .mapNotNull { photo -> photo.photoDateMillis()?.let { millis -> Math.floorDiv(millis, MillisPerDay) } }
                .toSet()
                .size
                .coerceAtLeast(1)
            HomePlaceCardUiModel(
                id = id,
                title = formatPlaceCoordinateTitle(latitude, longitude),
                latitude = latitude,
                longitude = longitude,
                visitCount = visitDays,
                photoCount = validPhotos.size,
                latestAt = validPhotos.firstOrNull()?.photoDateMillis(),
                coverPhoto = validPhotos.firstOrNull(),
                photos = validPhotos
            )
        }
        .sortedWith(
            compareByDescending<HomePlaceCardUiModel> { place -> place.visitCount }
                .thenByDescending { place -> place.photoCount }
                .thenByDescending { place -> place.latestAt ?: 0L }
        )
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Моя карта",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$photosWithLocationCount фото на карте · $tripCount поездок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
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
    onOpenMap: () -> Unit,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HomeHeatmapMap(
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
                text = "Ваша география",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Поездки и посещенные места",
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
                Text("Открыть карту")
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
    onOpenAllTrips: () -> Unit,
    onOpenTrip: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Ваши поездки",
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
                    TripCard(
                        trip = trip,
                        coverPhoto = trip.coverPhotoId?.let { photoId -> photosById[photoId] },
                        modifier = Modifier
                            .fillParentMaxWidth(0.38f)
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
                            .fillParentMaxWidth(0.54f)
                            .height(height)
                            .clickable { onOpenPlace(place.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnThisDaySection(
    height: Dp,
    state: OnThisDayUiState,
    mapStyleUrl: String,
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
                HomeMemoryMap(
                    points = state.points,
                    mapStyleUrl = mapStyleUrl
                )
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
    modifier: Modifier = Modifier
) {
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
private fun HomeHeatmapMap(
    heatCells: List<VisibleTripHeatCell>,
    positions: List<LatLng>,
    mapStyleUrl: String,
    onViewportChanged: (PhotoMapBounds, Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layerController = remember { PhotoMapLayerController() }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    val latestHeatCells = rememberUpdatedState(heatCells)
    val latestPositions = rememberUpdatedState(positions)
    val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.uiSettings.setAllGesturesEnabled(false)
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    layerController.reset()
                    layerController.updateTripHeatmap(
                        style = style,
                        featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(latestHeatCells.value)
                    )
                    isStyleReady = true
                    post {
                        mapLibreMap.fitHomePositions(latestPositions.value)
                        mapLibreMap.dispatchHomeViewportChanged(latestOnViewportChanged.value)
                    }
                    postDelayed(
                        { mapLibreMap.dispatchHomeViewportChanged(latestOnViewportChanged.value) },
                        HomeMapViewportRefreshDelayMs
                    )
                }
            }
        }
    }

    HomeMapLifecycle(lifecycleOwner = lifecycleOwner, mapView = mapView)

    LaunchedEffect(map, isStyleReady, heatCells) {
        val mapLibreMap = map ?: return@LaunchedEffect
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        layerController.updateTripHeatmap(
            style = style,
            featureCollection = TripHeatmapFeatureMapper.toFeatureCollection(heatCells)
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

@Composable
private fun HomeMemoryMap(
    points: List<OnThisDayMapPoint>,
    mapStyleUrl: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var isStyleReady by remember { mutableStateOf(false) }
    val latestPoints = rememberUpdatedState(points)
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { mapLibreMap ->
                map = mapLibreMap
                mapLibreMap.uiSettings.setAllGesturesEnabled(false)
                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    style.recreateMemoryPointLayer(latestPoints.value)
                    isStyleReady = true
                    post { mapLibreMap.fitHomePositions(latestPoints.value.map { point -> LatLng(point.latitude, point.longitude) }) }
                }
            }
        }
    }

    HomeMapLifecycle(lifecycleOwner = lifecycleOwner, mapView = mapView)

    LaunchedEffect(map, isStyleReady, points) {
        val mapLibreMap = map ?: return@LaunchedEffect
        val style = mapLibreMap.getStyle() ?: return@LaunchedEffect
        style.recreateMemoryPointLayer(points)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

@Composable
private fun HomeMapLifecycle(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    mapView: MapView
) {
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
}

private fun org.maplibre.android.maps.Style.recreateMemoryPointLayer(points: List<OnThisDayMapPoint>) {
    runCatching { removeLayer(HomeMemoryLayerId) }
    runCatching { removeSource(HomeMemorySourceId) }
    val features = points.map { point ->
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
            addNumberProperty("year", point.year)
            addStringProperty("photo_id", point.photoId.toString())
        }
    }
    addSource(GeoJsonSource(HomeMemorySourceId, FeatureCollection.fromFeatures(features)))
    addLayer(
        CircleLayer(HomeMemoryLayerId, HomeMemorySourceId).withProperties(
            circleRadius(8f),
            circleColor("#FF5C7A"),
            circleOpacity(0.88f),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2f)
        )
    )
}

private fun MapLibreMap.fitHomePositions(positions: List<LatLng>) {
    runCatching {
        when (positions.size) {
            0 -> moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(55.751244, 37.618423), 3.6))
            1 -> moveCamera(CameraUpdateFactory.newLatLngZoom(positions.first(), 8.0))
            else -> {
                val bounds = LatLngBounds.Builder()
                positions.forEach { position -> bounds.include(position) }
                moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), HomeMapBoundsPaddingPx))
            }
        }
    }.onFailure { error ->
        Log.w(HomeLogTag, "Failed to fit home map", error)
    }
}

private fun MapLibreMap.dispatchHomeViewportChanged(
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
        Log.w(HomeLogTag, "Failed to dispatch home viewport", error)
    }
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
    val points = matchingPhotos.map { (photo, year) ->
        OnThisDayMapPoint(
            photoId = photo.mediaId,
            latitude = requireNotNull(photo.latitude),
            longitude = requireNotNull(photo.longitude),
            year = year,
            thumbnailUri = photo.uri
        )
    }
    val subtitle = SimpleDateFormat("d MMMM", Locale("ru")).format(now.time) + " в разные годы"
    return OnThisDayUiState(
        day = day,
        month = month + 1,
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
    val tripPositions = trips.map { marker -> LatLng(marker.latitude, marker.longitude) }
    if (tripPositions.isNotEmpty()) {
        return tripPositions
    }
    return photos.mapNotNull { photo ->
        val latitude = photo.latitude ?: return@mapNotNull null
        val longitude = photo.longitude ?: return@mapNotNull null
        LatLng(latitude, longitude)
    }
}

private fun groupedPhotosByDay(photos: List<DevicePhoto>): List<PlacePhotoDayGroup> {
    return photos
        .mapNotNull { photo ->
            val day = photo.photoDateMillis()?.let { millis -> Math.floorDiv(millis, MillisPerDay) }
                ?: return@mapNotNull null
            day to photo
        }
        .groupBy({ pair -> pair.first }, { pair -> pair.second })
        .map { (day, groupPhotos) ->
            PlacePhotoDayGroup(
                day = day,
                photos = groupPhotos.sortedWith(
                    compareBy<DevicePhoto> { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
                        .thenBy { photo -> photo.mediaId }
                )
            )
        }
        .sortedByDescending { group -> group.day }
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

private fun formatDate(day: Long): String {
    return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(photoDateDayToMillis(day)))
}

private fun formatMillisDate(millis: Long): String {
    return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
}

private fun formatPlaceCoordinateTitle(latitude: Double, longitude: Double): String {
    return "%.4f, %.4f".format(Locale.US, latitude, longitude)
}

private data class OnThisDayUiState(
    val day: Int,
    val month: Int,
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

private data class PlacePhotoDayGroup(
    val day: Long,
    val photos: List<DevicePhoto>
)

private const val PlaceCellSizeDegrees = 0.02
private const val MillisPerDay = 24L * 60L * 60L * 1000L
private const val HomeMapBoundsPaddingPx = 110
private const val HomeMapViewportRefreshDelayMs = 350L
private const val HomeMemorySourceId = "home-memory-source"
private const val HomeMemoryLayerId = "home-memory-layer"
private const val HomeLogTag = "PhotoMapHome"
