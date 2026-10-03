package com.dazaike.photovault.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

enum class HapticKind { Press, Tick, ToggleOn, ToggleOff, Success, Error, LongPress }

/**
 * Subtle haptics. Uses vibration primitives (strength-scaled, touch usage so the system
 * intensity / off settings apply) where supported, else View haptic constants.
 */
@Stable
class PrismHaptics(
    private val view: View,
    private val vibrator: Vibrator?,
    val enabled: Boolean,
    val strength: Float,
) {
    private var lastTick = 0L

    private val primitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator != null &&
            vibrator.hasVibrator() &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            )

    fun perform(kind: HapticKind) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0
        ) return
        if (kind == HapticKind.Tick) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTick < 40) return
            lastTick = now
        }
        if (primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) composed(kind) else fallback(kind)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun composed(kind: HapticKind) {
        val v = vibrator ?: return
        val s = strength
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val c = VibrationEffect.startComposition()
        when (kind) {
            HapticKind.Press -> c.addPrimitive(click, 0.5f * s)
            HapticKind.Tick -> c.addPrimitive(tick, 0.65f * s)
            HapticKind.ToggleOn -> c.addPrimitive(click, 0.6f * s)
            HapticKind.ToggleOff -> c.addPrimitive(tick, 0.6f * s)
            HapticKind.Success -> c.addPrimitive(tick, 0.5f * s).addPrimitive(click, 0.7f * s, 60)
            HapticKind.Error -> c.addPrimitive(click, 0.7f * s).addPrimitive(click, 0.7f * s, 90)
            HapticKind.LongPress -> c.addPrimitive(click, 0.8f * s)
        }
        val effect = c.compose()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }

    private fun fallback(kind: HapticKind) {
        val sdk = Build.VERSION.SDK_INT
        val constant = when (kind) {
            HapticKind.Press -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticKind.Tick -> HapticFeedbackConstants.CLOCK_TICK
            HapticKind.ToggleOn ->
                if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_ON
                else HapticFeedbackConstants.CLOCK_TICK
            HapticKind.ToggleOff ->
                if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_OFF
                else HapticFeedbackConstants.CLOCK_TICK
            HapticKind.Success ->
                if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.KEYBOARD_TAP
            HapticKind.Error ->
                if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            HapticKind.LongPress -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberPrismHaptics(enabled: Boolean, strength: Float): PrismHaptics {
    val view = LocalView.current
    return remember(view, enabled, strength) {
        val context = view.context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        PrismHaptics(view, vibrator, enabled, strength)
    }
}

val LocalHaptics = staticCompositionLocalOf<PrismHaptics> { error("PrismHaptics not provided") }
