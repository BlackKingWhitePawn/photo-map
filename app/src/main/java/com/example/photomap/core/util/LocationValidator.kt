package com.example.photomap.core.util

object LocationValidator {
    fun normalize(latitude: Double?, longitude: Double?): GeoCoordinates? {
        if (latitude == null || longitude == null) {
            return null
        }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }

        if (latitude == 0.0 && longitude == 0.0) {
            return null
        }

        return GeoCoordinates(latitude = latitude, longitude = longitude)
    }
}
