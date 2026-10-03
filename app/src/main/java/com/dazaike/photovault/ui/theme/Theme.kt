package com.dazaike.photovault.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.dazaike.photovault.data.ThemeMode
import com.dazaike.photovault.data.UiSettings
import com.dazaike.photovault.ui.LocalHaptics
import com.dazaike.photovault.ui.rememberPrismHaptics

/** Provides colours, accent, motion and haptics derived from [settings] to the whole app. */
@Composable
fun PrismTheme(settings: UiSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val reduced = settings.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val motion = remember(settings.animationSpeed, settings.motionIntensity, reduced) {
        MotionSpec(settings.animationSpeed, settings.motionIntensity, reduced)
    }
    val t by animateFloatAsState(if (dark) 1f else 0f, motion.fade(320), label = "themeFade")
    val colors = remember(t, settings.brightness) {
        lerp(prismColors(false, settings.brightness), prismColors(true, settings.brightness), t)
    }
    val accent = Color(settings.accent)

    CompositionLocalProvider(
        LocalPrismColors provides colors,
        LocalAccent provides accent,
        LocalContentColor provides colors.text,
        LocalMotion provides motion,
        LocalHaptics provides rememberPrismHaptics(settings.haptics, settings.hapticStrength),
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = accent,
            backgroundColor = accent.copy(alpha = 0.32f),
        ),
        content = content,
    )
}
