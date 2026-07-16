package com.example.photomap.ui.map

import java.util.Locale
import kotlin.math.roundToInt

internal fun formatClusterDebugLine(
    index: Int,
    photoCount: Int,
    latitude: Double,
    longitude: Double
): String {
    return "${index + 1}. $photoCount фото: ${formatDebugCoordinate(latitude)}, " +
        formatDebugCoordinate(longitude)
}

internal fun formatDebugCoordinate(value: Double): String {
    return String.format(Locale.US, "%.5f", value)
}

internal fun mapDebugPanelStateText(isVisible: Boolean): String {
    return if (isVisible) {
        "Показывается поверх карты"
    } else {
        "Скрыта"
    }
}

internal fun markerTopLeftPx(
    screenX: Float,
    screenY: Float,
    markerSizePx: Float
): Pair<Int, Int> {
    val radius = markerSizePx / 2f
    return (screenX - radius).roundToInt() to (screenY - radius).roundToInt()
}
