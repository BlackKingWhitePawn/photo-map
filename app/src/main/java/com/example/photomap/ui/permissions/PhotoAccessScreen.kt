package com.example.photomap.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photomap.core.permissions.PhotoAccessLevel
import com.example.photomap.core.permissions.PhotoPermissionManager
import com.example.photomap.domain.model.DevicePhoto
import java.util.Date

@Composable
fun PhotoAccessRoute(
    viewModel: PhotoAccessViewModel = viewModel(),
    onOpenMap: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionResult()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(scanWhenAllowed = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PhotoAccessScreen(
        state = state,
        onRequestPermissions = {
            viewModel.onPermissionsRequested()
            permissionLauncher.launch(PhotoPermissionManager.permissionsToRequest())
        },
        onScan = { viewModel.scanPhotos() },
        onScanWithExif = viewModel::scanPhotosWithExif,
        onOpenMap = onOpenMap,
        onOpenSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        }
    )
}

@Composable
fun PhotoAccessScreen(
    state: PhotoAccessUiState,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onScanWithExif: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Photo Map",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            PermissionSummaryCard(
                state = state,
                onRequestPermissions = onRequestPermissions,
                onScan = onScan,
                onScanWithExif = onScanWithExif,
                onOpenMap = onOpenMap,
                onOpenSettings = onOpenSettings
            )

            if (state.isLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { state.scanProgressFraction() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = state.loadingText(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Найдено фотографий: ${state.photos.size}, с координатами: ${state.photosWithLocationCount}",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.indexedPhotoCount > 0) {
                Text(
                    text = "GPS-индекс: обработано ${state.indexedLocationScannedCount} из ${state.indexedPhotoCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.photos.isEmpty() && state.permissionStatus.canReadImages && !state.isLoading) {
                Text(
                    text = "Фотографии пока не найдены. Если доступ был выдан только к выбранным фото, выберите изображения в системном окне доступа.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.photos.take(50),
                    key = { photo -> photo.mediaId }
                ) { photo ->
                    PhotoRow(photo = photo)
                }
            }
        }
    }
}

@Composable
private fun PermissionSummaryCard(
    state: PhotoAccessUiState,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onScanWithExif: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Доступ к фотографиям",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = accessDescription(state.permissionStatus.accessLevel),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = if (state.permissionStatus.canReadOriginalLocation) {
                    "Доступ к исходным геоданным разрешён"
                } else {
                    "Доступ к исходным геоданным не выдан"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!state.permissionStatus.canReadImages) {
                Text(
                    text = "Приложению нужен доступ к фотографиям, чтобы найти снимки на устройстве и позже показать их на карте.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (state.hasRequestedPermissions) {
                    Text(
                        text = "Если доступ был отклонён навсегда, разрешение можно включить в системных настройках приложения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestPermissions
                ) {
                    Text(text = "Предоставить доступ")
                }

                OutlinedButton(
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

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenMap,
                    enabled = state.permissionStatus.canReadImages
                ) {
                    Text(text = "Открыть карту")
                }

                if (state.hasRequestedPermissions && !state.permissionStatus.canReadImages) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSettings
                    ) {
                        Text(text = "Открыть настройки")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(photo: DevicePhoto) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = photo.displayName ?: "Фото ${photo.mediaId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "ID: ${photo.mediaId}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = listOfNotNull(
                    photo.mimeType,
                    if (photo.hasLocation) "GPS" else null,
                    photo.dateTaken?.let { millis -> formatDate(context, millis) },
                    photo.size?.let { bytes -> "${bytes / 1024} КБ" }
                ).joinToString(separator = " · "),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = photo.uri,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun accessDescription(accessLevel: PhotoAccessLevel): String {
    return when (accessLevel) {
        PhotoAccessLevel.None -> "Доступ к фотографиям не выдан"
        PhotoAccessLevel.Limited -> "Доступ выдан только к выбранным фотографиям"
        PhotoAccessLevel.Full -> "Доступ ко всем фотографиям выдан"
    }
}

private fun PhotoAccessUiState.loadingText(): String {
    val message = loadingMessage ?: "Читаем фотографии из MediaStore"
    return if (scanTotal > 0) {
        "$message\nОбработано $scanProcessed из $scanTotal (${scanProgressPercent()}%)"
    } else {
        message
    }
}

private fun PhotoAccessUiState.scanProgressFraction(): Float {
    return if (scanTotal > 0) {
        (scanProcessed.toFloat() / scanTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}

private fun PhotoAccessUiState.scanProgressPercent(): Int {
    return (scanProgressFraction() * 100).toInt().coerceIn(0, 100)
}

private fun formatDate(context: Context, timeMillis: Long): String {
    return DateFormat.getDateFormat(context).format(Date(timeMillis))
}
