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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.photomap.domain.model.DevicePhoto
import com.example.photomap.domain.model.photoDateDayToMillis
import com.example.photomap.domain.model.photoDateMillis
import java.util.Date
import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

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
private val PhotoDateGridDefaultThumbnailSize = 74.dp
private val PhotoDateGridDefaultSpacing = 8.dp
private val PhotoDateGridDefaultDaySpacing = 18.dp
private const val PhotoDateGridDefaultMaxColumns = 6
