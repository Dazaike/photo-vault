package com.dazaike.photovault.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.PressDepth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one press rule every pressable uses: the dip starts on touch-down (not after clickable's
 * scroll delay), lasts at least 70 ms so fast taps still read, and releases critically damped.
 */
@Stable
class PressState internal constructor(val interactionSource: MutableInteractionSource) {
    internal var pointerDown by mutableStateOf(false)
    internal val pressAnim = Animatable(0f)
    internal val hoverAnim = Animatable(0f)

    /** 0 = resting, 1 = fully pressed. */
    val progress: Float get() = pressAnim.value

    /** 0 = not hovered, 1 = hovered (mouse / stylus). */
    val hover: Float get() = hoverAnim.value

    /** Keyboard focus (clickables are focusable only in non-touch mode). */
    var focused by mutableStateOf(false)
        internal set
}

@Composable
fun rememberPressState(enabled: Boolean = true): PressState {
    val source = remember { MutableInteractionSource() }
    val state = remember(source) { PressState(source) }
    val keyPressed = source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    SideEffect { state.focused = focused }
    val isEnabled = rememberUpdatedState(enabled)
    val motion = LocalMotion.current

    LaunchedEffect(state, motion) {
        var start = 0L
        snapshotFlow { isEnabled.value && (state.pointerDown || keyPressed.value) }
            .collectLatest { down ->
                if (down) {
                    start = SystemClock.uptimeMillis()
                    state.pressAnim.animateTo(1f, motion.press())
                } else {
                    val remaining = motion.duration(70) - (SystemClock.uptimeMillis() - start)
                    if (remaining > 0) {
                        withTimeoutOrNull(remaining) { state.pressAnim.animateTo(1f, motion.press()) }
                    }
                    state.pressAnim.animateTo(0f, motion.release())
                }
            }
    }
    LaunchedEffect(hovered && enabled, motion) {
        state.hoverAnim.animateTo(if (hovered && enabled) 1f else 0f, motion.fade(150))
    }
    return state
}

/**
 * Immediate touch feedback. Chain before `clickable(state.interactionSource, indication = null)`.
 * A scroll that consumes the move, or leaving the bounds, cancels the press.
 */
fun Modifier.pressInput(state: PressState, enabled: Boolean = true): Modifier =
    pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            state.pointerDown = true
            try {
                waitForUpOrCancellation()
            } finally {
                state.pointerDown = false
            }
        }
    }

/** Scale dip plus a 1dp sink, read in the layer phase only. */
@Composable
fun rememberPressLayer(state: PressState, depth: Float = PressDepth): GraphicsLayerScope.() -> Unit {
    val motion = LocalMotion.current
    return remember(state, depth, motion) {
        {
            val p = state.progress
            val m = motion.magnitude
            val s = 1f - depth * m * p
            scaleX = s
            scaleY = s
            translationY = 1.dp.toPx() * m * p
        }
    }
}

/** Keyboard focus ring drawn 3dp outside [shape]. Must be the outermost modifier. */
fun Modifier.focusRing(state: PressState, shape: Shape, color: Color): Modifier = drawWithContent {
    drawContent()
    if (state.focused) {
        val inset = 3.dp.toPx()
        val outline = shape.createOutline(
            Size(size.width + 2 * inset, size.height + 2 * inset),
            layoutDirection,
            this,
        )
        translate(-inset, -inset) { drawOutline(outline, color, style = Stroke(2.dp.toPx())) }
    }
}
