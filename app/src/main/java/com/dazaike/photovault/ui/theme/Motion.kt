package com.dazaike.photovault.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt

val EaseStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EaseDecelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
val EaseAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

/** Scale dip of a pressed control at full motion intensity. */
const val PressDepth = 0.035f

/**
 * Every duration, spring and reduced-motion decision. Springs are critically damped
 * (no overshoot) and velocity-continuous on retarget; they snap when [reduced].
 * Tweens keep running when [reduced] because they only drive opacity and colour.
 */
@Immutable
data class MotionSpec(
    val speed: Float = 1f,
    val intensity: Float = 1f,
    val reduced: Boolean = false,
) {
    /** Multiplier for every positional or scale delta. */
    val magnitude: Float get() = if (reduced) 0f else intensity

    fun duration(ms: Int): Int = (ms / speed).roundToInt()

    private fun <T> critical(stiffness: Float): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = stiffness * speed * speed)

    fun <T> press(): FiniteAnimationSpec<T> = critical(1400f)
    fun <T> release(): FiniteAnimationSpec<T> = critical(380f)
    fun <T> glide(): FiniteAnimationSpec<T> = critical(260f)
    fun <T> settle(): FiniteAnimationSpec<T> = critical(400f)

    fun <T> fade(ms: Int = 200, delayMs: Int = 0): FiniteAnimationSpec<T> =
        tween(duration(ms), delayMillis = duration(delayMs), easing = EaseStandard)

    fun <T> enter(ms: Int = 240): FiniteAnimationSpec<T> = tween(duration(ms), easing = EaseDecelerate)
    fun <T> exit(ms: Int = 160): FiniteAnimationSpec<T> = tween(duration(ms), easing = EaseAccelerate)

    /** Period for infinite animations (spinners, shimmer, indeterminate progress). */
    fun loop(ms: Int, easing: Easing = LinearEasing): DurationBasedAnimationSpec<Float> =
        tween(duration(ms), easing = easing)
}

val LocalMotion = compositionLocalOf { MotionSpec() }
