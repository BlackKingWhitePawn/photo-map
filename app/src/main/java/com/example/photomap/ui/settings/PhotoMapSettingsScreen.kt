package com.example.photomap.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.photomap.BuildConfig
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MAX_PHOTO_CLUSTER_RADIUS
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_LEAVES_PAGE_SIZE
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_MIN_POINTS
import com.example.photomap.core.settings.MIN_PHOTO_CLUSTER_RADIUS
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
    onIncreaseClusterLeavesPageSize: () -> Unit
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
