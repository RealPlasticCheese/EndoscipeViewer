package com.plasticcheese.endoscopeviewer

import kotlin.math.roundToInt

internal data class PreviewTransform(
    val quarterTurns: Int = 0,
    val mirrored: Boolean = false,
    val zoom: Float = 1f,
) {
    val sideways: Boolean get() = quarterTurns % 2 != 0
    val degrees: Float get() = quarterTurns * 90f
    val horizontalSign: Float get() = if (mirrored) -1f else 1f

    fun fit(frame: Resolution, width: Int, height: Int): Resolution {
        val orientedWidth = if (sideways) frame.height else frame.width
        val orientedHeight = if (sideways) frame.width else frame.height
        val scale = minOf(width.toFloat() / orientedWidth, height.toFloat() / orientedHeight)
        return Resolution(
            (orientedWidth * scale).roundToInt(),
            (orientedHeight * scale).roundToInt(),
        )
    }
}
