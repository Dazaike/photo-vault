package com.dazaike.photovault.ui

import kotlin.math.max
import kotlin.math.round

/** Pure slider geometry and tick rules. Every value result is clamped to 0..1. */
object SliderMath {
    fun snap(value: Float, stepCount: Int): Float {
        val v = value.coerceIn(0f, 1f)
        if (stepCount <= 0) return v
        return (round(v * stepCount) / stepCount).coerceIn(0f, 1f)
    }

    fun valueAt(x: Float, width: Float, thumbWidth: Float): Float =
        ((x - thumbWidth / 2f) / max(width - thumbWidth, 1f)).coerceIn(0f, 1f)

    fun dragged(startValue: Float, dx: Float, width: Float, thumbWidth: Float): Float =
        (startValue + dx / max(width - thumbWidth, 1f)).coerceIn(0f, 1f)

    /** Detent index: the step for stepped sliders, every 5% of travel for continuous ones. */
    fun tickZone(value: Float, stepCount: Int): Int =
        round(value * (if (stepCount > 0) stepCount else CONTINUOUS_DETENTS)).toInt()

    fun shouldTick(old: Int, new: Int): Boolean = old != new

    private const val CONTINUOUS_DETENTS = 20
}
