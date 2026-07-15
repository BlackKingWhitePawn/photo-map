package com.example.photomap.core.permissions

data class PhotoPermissionStatus(
    val accessLevel: PhotoAccessLevel,
    val canReadImages: Boolean,
    val canReadOriginalLocation: Boolean
)
