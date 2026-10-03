package com.dazaike.photovault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

private val PrismShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Maps the Prism palette onto Material 3 so any Material composable that still
 * appears (text-field internals, media3 controls) matches the Prism look.
 * Must be nested inside [PrismTheme].
 */
@Composable
fun PrismMaterialBridge(content: @Composable () -> Unit) {
    val c = Prism.colors
    val accent = Prism.accent
    val flat = { fill: Color -> fill.compositeOver(c.background) }
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent,
        onPrimary = Prism.onAccent,
        primaryContainer = flat(accent.copy(alpha = 0.28f)),
        onPrimaryContainer = c.text,
        secondary = accent,
        onSecondary = Prism.onAccent,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = flat(c.fill),
        onSurfaceVariant = c.subText,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = flat(c.fillWeak),
        surfaceContainer = flat(c.fill),
        surfaceContainerHigh = flat(c.fillStrong),
        surfaceContainerHighest = flat(c.fillStrong),
        outline = c.outline,
        outlineVariant = c.outline,
        error = c.error,
        scrim = c.scrim,
    )
    MaterialTheme(colorScheme = scheme, shapes = PrismShapes, content = content)
}
