package com.example.photomap.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.photomap.BuildConfig
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.MAX_PHOTO_MAX_VISIBLE_THUMBNAILS
import com.example.photomap.core.settings.MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX
import com.example.photomap.core.settings.MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.MIN_PHOTO_MAX_VISIBLE_THUMBNAILS
import com.example.photomap.core.settings.MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX
import com.example.photomap.core.settings.MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX
import com.example.photomap.core.util.AppDiagnostics
import com.example.photomap.ui.map.mapDebugPanelStateText
import com.example.photomap.ui.permissions.PhotoAccessUiState

@Composable
fun PhotoMapSettingsScreen(
    state: PhotoAccessUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onScanWithExif: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDecreaseClusterRadius: () -> Unit,
    onIncreaseClusterRadius: () -> Unit,
    onDecreaseClusterMinPoints: () -> Unit,
    onIncreaseClusterMinPoints: () -> Unit,
    onDecreaseClusterLeavesPageSize: () -> Unit,
    onIncreaseClusterLeavesPageSize: () -> Unit,
    onDecreaseClusterMaxDistance: () -> Unit,
    onIncreaseClusterMaxDistance: () -> Unit,
    onDecreaseClusterDensityCoefficient: () -> Unit,
    onIncreaseClusterDensityCoefficient: () -> Unit,
    onDecreaseClusterMarkerScale: () -> Unit,
    onIncreaseClusterMarkerScale: () -> Unit,
    onDecreaseThumbnailCellSize: () -> Unit,
    onIncreaseThumbnailCellSize: () -> Unit,
    onDecreaseMaxVisibleThumbnails: () -> Unit,
    onIncreaseMaxVisibleThumbnails: () -> Unit,
    onDecreaseThumbnailPreloadPadding: () -> Unit,
    onIncreaseThumbnailPreloadPadding: () -> Unit,
    onSetMapDebugPanelVisible: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(text = "Назад")
                }
            }

            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingsCard(title = "Кластеризация карты") {
                val clusterSettings = state.clusterSettings
                SettingsStepper(
                    title = "Радиус кластера",
                    value = "${clusterSettings.radiusPx} px",
                    range = "$MIN_PHOTO_CLUSTER_RADIUS-$MAX_PHOTO_CLUSTER_RADIUS px",
                    onDecrease = onDecreaseClusterRadius,
                    onIncrease = onIncreaseClusterRadius
                )
                SettingsStepper(
                    title = "Минимум точек",
                    value = clusterSettings.minPoints.toString(),
                    range = "$MIN_PHOTO_CLUSTER_MIN_POINTS-$MAX_PHOTO_CLUSTER_MIN_POINTS",
                    onDecrease = onDecreaseClusterMinPoints,
                    onIncrease = onIncreaseClusterMinPoints
                )
                SettingsStepper(
                    title = "Страница списка",
                    value = "${clusterSettings.leavesPageSize} фото",
                    range = "$MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE-$MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE",
                    onDecrease = onDecreaseClusterLeavesPageSize,
                    onIncrease = onIncreaseClusterLeavesPageSize
                )
                SettingsStepper(
                    title = "Макс. дистанция в кластере",
                    value = "${clusterSettings.maxDistanceKm} км",
                    range = "$MIN_PHOTO_CLUSTER_MAX_DISTANCE_KM-$MAX_PHOTO_CLUSTER_MAX_DISTANCE_KM км",
                    onDecrease = onDecreaseClusterMaxDistance,
                    onIncrease = onIncreaseClusterMaxDistance
                )
                SettingsStepper(
                    title = "Коэффициент плотности",
                    value = "${clusterSettings.densityCoefficientPercent}%",
                    range = "$MIN_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT-$MAX_PHOTO_CLUSTER_DENSITY_COEFFICIENT_PERCENT%",
                    onDecrease = onDecreaseClusterDensityCoefficient,
                    onIncrease = onIncreaseClusterDensityCoefficient
                )
                SettingsStepper(
                    title = "Размер кластеров",
                    value = "${clusterSettings.markerScalePercent}%",
                    range = "$MIN_PHOTO_CLUSTER_MARKER_SCALE_PERCENT-$MAX_PHOTO_CLUSTER_MARKER_SCALE_PERCENT%",
                    onDecrease = onDecreaseClusterMarkerScale,
                    onIncrease = onIncreaseClusterMarkerScale
                )
                SettingsStepper(
                    title = "Сетка миниатюр",
                    value = "${clusterSettings.thumbnailCellSizePx} px",
                    range = "$MIN_PHOTO_THUMBNAIL_CELL_SIZE_PX-$MAX_PHOTO_THUMBNAIL_CELL_SIZE_PX px",
                    onDecrease = onDecreaseThumbnailCellSize,
                    onIncrease = onIncreaseThumbnailCellSize
                )
                SettingsStepper(
                    title = "Лимит миниатюр",
                    value = clusterSettings.maxVisibleThumbnails.toString(),
                    range = "$MIN_PHOTO_MAX_VISIBLE_THUMBNAILS-$MAX_PHOTO_MAX_VISIBLE_THUMBNAILS",
                    onDecrease = onDecreaseMaxVisibleThumbnails,
                    onIncrease = onIncreaseMaxVisibleThumbnails
                )
                SettingsStepper(
                    title = "Предзагрузка миниатюр",
                    value = "${clusterSettings.thumbnailPreloadPaddingPx} px",
                    range = "$MIN_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX-$MAX_PHOTO_THUMBNAIL_PRELOAD_PADDING_PX px",
                    onDecrease = onDecreaseThumbnailPreloadPadding,
                    onIncrease = onIncreaseThumbnailPreloadPadding
                )
            }

            SettingsCard(title = "Индексирование") {
                Text(
                    text = "В индексе: ${state.indexedPhotoCount}, с координатами: ${state.photosWithLocationCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.isLoading) {
                    Text(
                        text = scanStatusText(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onScan,
                    enabled = state.permissionStatus.canReadImages && !state.isLoading
                ) {
                    Text(text = "Обновить список")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onScanWithExif,
                    enabled = state.permissionStatus.canReadImages && !state.isLoading
                ) {
                    Text(text = "Искать GPS в EXIF")
                }
                if (state.isLoading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = if (state.isScanPaused) onResume else onPause) {
                            Text(text = if (state.isScanPaused) "Продолжить" else "Пауза")
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text(text = "Отмена")
                        }
                    }
                }
            }

            SettingsCard(title = "Debug") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Debug-панель карты",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = mapDebugPanelStateText(state.showMapDebugPanel),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.showMapDebugPanel,
                        onCheckedChange = onSetMapDebugPanelVisible
                    )
                }
            }

            SettingsCard(title = "Доступ") {
                Text(
                    text = if (state.permissionStatus.canReadImages) {
                        "Доступ к фотографиям предоставлен."
                    } else {
                        "Доступ к фотографиям не предоставлен."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.permissionStatus.canReadImages) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text(text = "Отозвать доступ")
                    }
                }
            }

            SettingsCard(title = "О приложении") {
                Text(
                    text = "Photo Map ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { context.shareDiagnostics(state) }
                ) {
                    Text(text = "Экспорт логов")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun SettingsStepper(
    title: String,
    value: String,
    range: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$title: $value",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Диапазон: $range",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecrease) {
                Text(text = "-")
            }
            Button(onClick = onIncrease) {
                Text(text = "+")
            }
        }
    }
}

private fun scanStatusText(state: PhotoAccessUiState): String {
    val prefix = if (state.isScanPaused) "Пауза" else "Сканирование"
    return if (state.scanTotal > 0) {
        "$prefix: ${state.scanProcessed} из ${state.scanTotal}"
    } else {
        prefix
    }
}

private fun android.content.Context.shareDiagnostics(state: PhotoAccessUiState) {
    runCatching {
        val uri = AppDiagnostics.createReport(
            context = this,
            header = state.diagnosticHeader()
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Photo Map diagnostics")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Экспорт логов"))
    }.onFailure {
        Toast.makeText(this, "Не удалось экспортировать логи", Toast.LENGTH_SHORT).show()
    }
}

private fun PhotoAccessUiState.diagnosticHeader(): String {
    val settings = clusterSettings
    return buildString {
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Permission: $permissionStatus")
        appendLine("Photos in memory: ${photos.size}")
        appendLine("Indexed photos: $indexedPhotoCount")
        appendLine("Indexed with location: $photosWithLocationCount")
        appendLine("Indexed location scanned: $indexedLocationScannedCount")
        appendLine("Scanning: $isLoading")
        appendLine("Scan paused: $isScanPaused")
        appendLine("Scan progress: $scanProcessed/$scanTotal")
        appendLine("Cluster radius px: ${settings.radiusPx}")
        appendLine("Cluster min points: ${settings.minPoints}")
        appendLine("Cluster max distance km: ${settings.maxDistanceKm}")
        appendLine("Cluster density coefficient percent: ${settings.densityCoefficientPercent}")
        appendLine("Cluster marker scale percent: ${settings.markerScalePercent}")
        appendLine("Thumbnail cell size px: ${settings.thumbnailCellSizePx}")
        appendLine("Max visible thumbnails: ${settings.maxVisibleThumbnails}")
        appendLine("Thumbnail preload padding px: ${settings.thumbnailPreloadPaddingPx}")
        appendLine("Map debug panel: $showMapDebugPanel")
    }
}
