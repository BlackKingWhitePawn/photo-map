package com.example.photomap.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.model.photoDateMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun MiniPhotoGallery(
    photos: List<DevicePhoto>,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = 86.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    maxItems: Int = Int.MAX_VALUE,
    onPhotoClick: ((DevicePhoto) -> Unit)? = null
) {
    val context = LocalContext.current
    val visiblePhotos = photos.take(maxItems)
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement
    ) {
        items(visiblePhotos, key = { photo -> photo.mediaId }) { photo ->
            MiniPhotoThumbnail(
                photo = photo,
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (onPhotoClick != null) {
                            onPhotoClick(photo)
                        } else {
                            context.openMiniGalleryPhoto(photo)
                        }
                    }
            )
        }
    }
}

@Composable
fun MiniPhotoThumbnail(
    photo: DevicePhoto?,
    modifier: Modifier = Modifier,
    targetSizePx: Int = MiniPhotoThumbnailPx,
    placeholderText: String = "Фото"
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo?.uri, targetSizePx) {
        value = if (photo == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                context.loadMiniGalleryThumbnail(
                    uriString = photo.uri,
                    targetSizePx = targetSizePx,
                    photoId = photo.mediaId
                )
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = requireNotNull(thumbnail).asImageBitmap(),
            contentDescription = photo?.displayName ?: placeholderText,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MiniGalleryTimeScrubber(
    photos: List<DevicePhoto>,
    selectedPhotoId: Long?,
    onPhotoSelected: (DevicePhoto) -> Unit,
    modifier: Modifier = Modifier,
    allPhotoIds: List<Long> = emptyList(),
    totalCount: Int = if (allPhotoIds.isNotEmpty()) allPhotoIds.size else photos.size,
    onScrubbingChanged: (Boolean) -> Unit = {}
) {
    val selectionPhotoIds = remember(photos, allPhotoIds) {
        allPhotoIds.takeIf { ids -> ids.isNotEmpty() } ?: photos.map { photo -> photo.mediaId }
    }
    val timeline = remember(photos, selectionPhotoIds, totalCount) {
        buildMiniGalleryTimeline(
            photos = photos,
            allPhotoIds = selectionPhotoIds,
            totalCount = totalCount
        )
    } ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    var handleFraction by remember(timeline) {
        mutableStateOf(timeline.fractionForPhotoId(selectedPhotoId))
    }
    var isDragging by remember { mutableStateOf(false) }
    var showExpandedLabel by remember { mutableStateOf(false) }
    var lastDragSelectionAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(timeline, selectedPhotoId, isDragging) {
        if (!isDragging) {
            handleFraction = timeline.fractionForPhotoId(selectedPhotoId)
        }
    }
    LaunchedEffect(showExpandedLabel, isDragging, selectedPhotoId) {
        if (showExpandedLabel && !isDragging) {
            delay(MiniGalleryTimeScrubberLabelHideDelayMs)
            showExpandedLabel = false
        }
    }

    val labelPhoto = remember(timeline, handleFraction, selectedPhotoId, isDragging) {
        if (isDragging) {
            timeline.photoForFraction(handleFraction)
        } else {
            timeline.photoForId(selectedPhotoId) ?: timeline.photoForFraction(handleFraction)
        }
    }
    val compactLabel = remember(context, labelPhoto, timeline) {
        labelPhoto?.let { photo ->
            formatMiniGalleryScrubberDate(
                context = context,
                photo = photo,
                timeline = timeline,
                detailed = false
            )
        }.orEmpty()
    }
    val expandedLabel = remember(context, labelPhoto, timeline) {
        labelPhoto?.let { photo ->
            formatMiniGalleryScrubberDate(
                context = context,
                photo = photo,
                timeline = timeline,
                detailed = true
            )
        } ?: compactLabel
    }

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    ) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        if (maxHeightPx <= 0f) {
            return@BoxWithConstraints
        }
        val handleHeightPx = with(density) { MiniGalleryTimeScrubberHandleHeight.toPx() }
        val verticalPaddingPx = with(density) { MiniGalleryTimeScrubberVerticalPadding.toPx() }
        val trackHeightPx = (maxHeightPx - handleHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
        val handleTopPx = verticalPaddingPx + trackHeightPx * handleFraction.coerceIn(0f, 1f)
        val handleOffsetXPx = with(density) { MiniGalleryTimeScrubberHandleOutset.toPx().roundToInt() }
        val bubbleOffsetXPx = with(density) {
            -(
                MiniGalleryTimeScrubberHandleWidth +
                    MiniGalleryTimeScrubberExpandedLabelWidth +
                    6.dp
                ).toPx().roundToInt()
        }

        fun selectFraction(
            fraction: Float,
            force: Boolean
        ) {
            val nextFraction = fraction.coerceIn(0f, 1f)
            handleFraction = nextFraction
            val photo = timeline.photoForFraction(nextFraction) ?: return
            val now = android.os.SystemClock.uptimeMillis()
            if (force || now - lastDragSelectionAtMs >= MiniGalleryTimeScrubberDragFrameMs) {
                lastDragSelectionAtMs = now
                onPhotoSelected(photo)
            }
        }

        AnimatedVisibility(
            visible = showExpandedLabel || isDragging,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = bubbleOffsetXPx,
                        y = handleTopPx.roundToInt()
                    )
                },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.size(
                    width = MiniGalleryTimeScrubberExpandedLabelWidth,
                    height = MiniGalleryTimeScrubberExpandedLabelHeight
                ),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = expandedLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = handleOffsetXPx,
                        y = handleTopPx.roundToInt()
                    )
                }
                .size(
                    width = MiniGalleryTimeScrubberHandleWidth,
                    height = MiniGalleryTimeScrubberHandleHeight
                )
                .pointerInput(timeline, trackHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            showExpandedLabel = true
                            lastDragSelectionAtMs = 0L
                            onScrubbingChanged(true)
                        },
                        onDragEnd = {
                            selectFraction(handleFraction, force = true)
                            isDragging = false
                            onScrubbingChanged(false)
                        },
                        onDragCancel = {
                            selectFraction(handleFraction, force = true)
                            isDragging = false
                            onScrubbingChanged(false)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            selectFraction(
                                fraction = handleFraction + dragAmount.y / trackHeightPx,
                                force = false
                            )
                        }
                    )
                },
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
                bottomStart = 28.dp
            ),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(start = 10.dp, end = 36.dp, top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = compactLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class PhotoDateGridGroup(
    val day: Long,
    val photos: List<DevicePhoto>
)

@Composable
fun PhotoDateGridGallery(
    photos: List<DevicePhoto>,
    modifier: Modifier = Modifier,
    selectedPhotoId: Long? = null,
    dayTextColor: Color = MaterialTheme.colorScheme.onSurface,
    thumbnailSize: Dp = PhotoDateGridDefaultThumbnailSize,
    spacing: Dp = PhotoDateGridDefaultSpacing,
    maxColumns: Int = PhotoDateGridDefaultMaxColumns,
    maxItemsPerDay: Int = Int.MAX_VALUE,
    newestFirst: Boolean = true,
    onPhotoClick: ((DevicePhoto) -> Unit)? = null
) {
    val groups = remember(photos, newestFirst) {
        photoDateGridGroups(photos, newestFirst = newestFirst)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PhotoDateGridDefaultDaySpacing)
    ) {
        groups.forEach { group ->
            key(group.day) {
                PhotoDateGridDay(
                    group = group,
                    selectedPhotoId = selectedPhotoId,
                    dayTextColor = dayTextColor,
                    thumbnailSize = thumbnailSize,
                    spacing = spacing,
                    maxColumns = maxColumns,
                    maxItems = maxItemsPerDay,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
fun PhotoDateGridDay(
    group: PhotoDateGridGroup,
    modifier: Modifier = Modifier,
    selectedPhotoId: Long? = null,
    dayTextColor: Color = MaterialTheme.colorScheme.onSurface,
    thumbnailSize: Dp = PhotoDateGridDefaultThumbnailSize,
    spacing: Dp = PhotoDateGridDefaultSpacing,
    maxColumns: Int = PhotoDateGridDefaultMaxColumns,
    maxItems: Int = Int.MAX_VALUE,
    onPhotoClick: ((DevicePhoto) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val visiblePhotos = remember(group.photos, selectedPhotoId, maxItems) {
        group.photos
            .prioritizeSelectedPhoto(selectedPhotoId)
            .take(maxItems)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = formatPhotoDateGridDay(group.day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = dayTextColor
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = with(density) {
                val availablePx = maxWidth.toPx()
                val cellPx = thumbnailSize.toPx()
                val spacingPx = spacing.toPx()
                max(
                    2,
                    ((availablePx + spacingPx) / (cellPx + spacingPx)).toInt()
                ).coerceAtMost(maxColumns.coerceAtLeast(1))
            }
            val rows = remember(visiblePhotos, columns) {
                visiblePhotos.chunked(columns)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                rows.forEach { rowPhotos ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        rowPhotos.forEach { photo ->
                            key(photo.mediaId) {
                                MiniPhotoThumbnail(
                                    photo = photo,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (onPhotoClick != null) {
                                                onPhotoClick(photo)
                                            } else {
                                                context.openMiniGalleryPhoto(photo)
                                            }
                                        }
                                )
                            }
                        }
                        repeat(columns - rowPhotos.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

fun photoDateGridGroups(
    photos: List<DevicePhoto>,
    newestFirst: Boolean = true
): List<PhotoDateGridGroup> {
    val groups = photos
        .mapNotNull { photo ->
            val day = photo.photoDateMillis()?.let { millis -> Math.floorDiv(millis, MillisPerDay) }
                ?: return@mapNotNull null
            day to photo
        }
        .groupBy({ pair -> pair.first }, { pair -> pair.second })
        .map { (day, groupPhotos) ->
            PhotoDateGridGroup(
                day = day,
                photos = groupPhotos.sortedWith(
                    compareBy<DevicePhoto> { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
                        .thenBy { photo -> photo.mediaId }
                )
            )
        }
    return if (newestFirst) {
        groups.sortedByDescending { group -> group.day }
    } else {
        groups.sortedBy { group -> group.day }
    }
}

private fun List<DevicePhoto>.prioritizeSelectedPhoto(selectedPhotoId: Long?): List<DevicePhoto> {
    if (selectedPhotoId == null || none { photo -> photo.mediaId == selectedPhotoId }) {
        return this
    }
    return sortedWith(
        compareByDescending<DevicePhoto> { photo -> photo.mediaId == selectedPhotoId }
            .thenBy { photo -> photo.photoDateMillis() ?: Long.MAX_VALUE }
            .thenBy { photo -> photo.mediaId }
    )
}

private fun formatPhotoDateGridDay(day: Long): String {
    return DateFormat.format("d MMMM yyyy", Date(photoDateDayToMillis(day))).toString()
}

private fun buildMiniGalleryTimeline(
    photos: List<DevicePhoto>,
    allPhotoIds: List<Long>,
    totalCount: Int
): MiniGalleryTimeline? {
    if (totalCount <= MiniGalleryTimeScrubberMinimumPhotos ||
        allPhotoIds.size <= MiniGalleryTimeScrubberMinimumPhotos ||
        photos.size != allPhotoIds.size
    ) {
        return null
    }
    val photosById = photos.associateBy { photo -> photo.mediaId }
    val orderedPhotos = allPhotoIds.mapNotNull { photoId -> photosById[photoId] }
    if (orderedPhotos.size != allPhotoIds.size) {
        return null
    }
    val datedEntries = orderedPhotos
        .mapIndexedNotNull { index, photo ->
            photo.photoDateMillis()?.let { millis ->
                MiniGalleryTimelineEntry(
                    index = index,
                    photo = photo,
                    millis = millis
                )
            }
        }
        .sortedWith(
            compareBy<MiniGalleryTimelineEntry> { entry -> entry.millis }
                .thenBy { entry -> entry.index }
        )
    val startMillis = orderedPhotos.firstNotNullOfOrNull { photo -> photo.photoDateMillis() }
    val endMillis = orderedPhotos.asReversed().firstNotNullOfOrNull { photo -> photo.photoDateMillis() }
    return MiniGalleryTimeline(
        photos = orderedPhotos,
        indexByPhotoId = orderedPhotos.withIndex().associate { (index, photo) -> photo.mediaId to index },
        datedEntries = datedEntries,
        startMillis = startMillis,
        endMillis = endMillis
    )
}

private fun formatMiniGalleryScrubberDate(
    context: Context,
    photo: DevicePhoto,
    timeline: MiniGalleryTimeline,
    detailed: Boolean
): String {
    val millis = photo.photoDateMillis()
    if (millis == null) {
        val index = timeline.indexForPhotoId(photo.mediaId)?.plus(1) ?: 1
        return "\u0424\u043e\u0442\u043e $index/${timeline.totalCount}"
    }
    val pattern = if (detailed || timeline.spanMillis <= MiniGalleryTimeScrubberDetailedDateSpanMs) {
        MiniGalleryTimeScrubberFullDatePattern
    } else {
        MiniGalleryTimeScrubberMonthDatePattern
    }
    return SimpleDateFormat(pattern, context.miniGalleryLocale()).apply {
        timeZone = MiniGalleryTimeZone
    }.format(Date(millis))
}

private fun Context.miniGalleryLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.size() > 0) {
        locales.get(0)
    } else {
        Locale.getDefault()
    }
}

private data class MiniGalleryTimeline(
    val photos: List<DevicePhoto>,
    val indexByPhotoId: Map<Long, Int>,
    val datedEntries: List<MiniGalleryTimelineEntry>,
    val startMillis: Long?,
    val endMillis: Long?
) {
    val totalCount: Int
        get() = photos.size

    val spanMillis: Long
        get() {
            val start = startMillis ?: return 0L
            val end = endMillis ?: return 0L
            return abs(end - start)
        }

    fun photoForId(photoId: Long?): DevicePhoto? {
        val index = indexForPhotoId(photoId) ?: return null
        return photos.getOrNull(index)
    }

    fun indexForPhotoId(photoId: Long?): Int? {
        return photoId?.let { id -> indexByPhotoId[id] }
    }

    fun fractionForPhotoId(photoId: Long?): Float {
        val index = indexForPhotoId(photoId) ?: 0
        return indexFraction(index)
    }

    fun photoForFraction(fraction: Float): DevicePhoto? {
        if (photos.isEmpty()) {
            return null
        }
        val boundedFraction = fraction.coerceIn(0f, 1f)
        val start = startMillis
        val end = endMillis
        if (start != null && end != null && start != end && datedEntries.size >= 2) {
            val targetMillis = start + ((end - start) * boundedFraction).roundToLong()
            return nearestPhotoForMillis(targetMillis) ?: photoForIndexFraction(boundedFraction)
        }
        return photoForIndexFraction(boundedFraction)
    }

    private fun nearestPhotoForMillis(targetMillis: Long): DevicePhoto? {
        val exactIndex = datedEntries.binarySearchBy(targetMillis) { entry -> entry.millis }
        if (exactIndex >= 0) {
            return datedEntries[exactIndex].photo
        }
        val nextIndex = -exactIndex - 1
        val candidates = listOfNotNull(
            datedEntries.getOrNull(nextIndex - 1),
            datedEntries.getOrNull(nextIndex)
        )
        return candidates
            .minWithOrNull(
                compareBy<MiniGalleryTimelineEntry> { entry -> abs(entry.millis - targetMillis) }
                    .thenBy { entry -> abs(indexFraction(entry.index) - fractionForMillis(targetMillis)) }
            )
            ?.photo
    }

    private fun fractionForMillis(millis: Long): Float {
        val start = startMillis ?: return 0f
        val end = endMillis ?: return 0f
        if (start == end) {
            return 0f
        }
        return ((millis - start).toDouble() / (end - start).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun photoForIndexFraction(fraction: Float): DevicePhoto? {
        val index = (fraction.coerceIn(0f, 1f) * photos.lastIndex).roundToInt()
            .coerceIn(0, photos.lastIndex)
        return photos.getOrNull(index)
    }

    private fun indexFraction(index: Int): Float {
        return if (photos.size <= 1) {
            0f
        } else {
            index.coerceIn(0, photos.lastIndex).toFloat() / photos.lastIndex.toFloat()
        }
    }
}

private data class MiniGalleryTimelineEntry(
    val index: Int,
    val photo: DevicePhoto,
    val millis: Long
)

fun Context.openMiniGalleryPhoto(photo: DevicePhoto) {
    val activity = this as? Activity ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(photo.uri), photo.mimeType ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
        }
        Log.w(MiniPhotoGalleryLogTag, "Failed to open photo: photoId=${photo.mediaId}", error)
    }
}

private fun Context.loadMiniGalleryThumbnail(
    uriString: String,
    targetSizePx: Int,
    photoId: Long? = null
): Bitmap? {
    val uri = Uri.parse(uriString)
    return runCatching {
        contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
    }.onFailure { error ->
        Log.w(MiniPhotoGalleryLogTag, "ContentResolver.loadThumbnail failed: photoId=$photoId", error)
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
        Log.w(MiniPhotoGalleryLogTag, "ImageDecoder thumbnail fallback failed: photoId=$photoId", error)
    }.getOrNull()
}

private const val MiniPhotoThumbnailPx = 260
private const val MiniPhotoGalleryLogTag = "PhotoMapMiniGallery"
private const val MillisPerDay = 24L * 60L * 60L * 1000L
private const val MiniGalleryTimeScrubberMinimumPhotos = 30
private const val MiniGalleryTimeScrubberDragFrameMs = 32L
private const val MiniGalleryTimeScrubberLabelHideDelayMs = 800L
private const val MiniGalleryTimeScrubberDetailedDateSpanMs = 92L * MillisPerDay
private const val MiniGalleryTimeScrubberMonthDatePattern = "MMMM yyyy"
private const val MiniGalleryTimeScrubberFullDatePattern = "d MMMM yyyy"
private val PhotoDateGridDefaultThumbnailSize = 74.dp
private val PhotoDateGridDefaultSpacing = 8.dp
private val PhotoDateGridDefaultDaySpacing = 18.dp
private const val PhotoDateGridDefaultMaxColumns = 6
private val MiniGalleryTimeScrubberHandleWidth = 112.dp
private val MiniGalleryTimeScrubberHandleHeight = 54.dp
private val MiniGalleryTimeScrubberHandleOutset = 34.dp
private val MiniGalleryTimeScrubberVerticalPadding = 12.dp
private val MiniGalleryTimeScrubberExpandedLabelWidth = 132.dp
private val MiniGalleryTimeScrubberExpandedLabelHeight = 46.dp
private val MiniGalleryTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
