package com.example.photomap.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PhotoPermissionManager {
    fun permissionsToRequest(): Array<String> {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }

        return permissions.toTypedArray()
    }

    fun checkStatus(context: Context): PhotoPermissionStatus {
        val hasFullImageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val hasLimitedImageAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        val accessLevel = when {
            hasFullImageAccess -> PhotoAccessLevel.Full
            hasLimitedImageAccess -> PhotoAccessLevel.Limited
            else -> PhotoAccessLevel.None
        }

        val hasOriginalLocationAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION)

        return PhotoPermissionStatus(
            accessLevel = accessLevel,
            canReadImages = accessLevel != PhotoAccessLevel.None,
            canReadOriginalLocation = hasOriginalLocationAccess
        )
    }

    private fun Context.isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
