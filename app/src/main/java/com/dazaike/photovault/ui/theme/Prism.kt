package com.dazaike.photovault.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp as lerpColor

val DefaultAccent = Color(0xFFFF9F0A)

/** The user-selectable accent; provided by [PrismTheme]. */
val LocalAccent = compositionLocalOf { DefaultAccent }

/** Semantic palette for one theme at one surface brightness. */
@Immutable
data class PrismColors(
    val isDark: Boolean,
    val brightness: Float,
    val background: Color,
    val text: Color,
    val subText: Color,
    val disabled: Color,
    val ink: Color,
    val fillWeak: Color,
    val fill: Color,
    val fillStrong: Color,
    val track: Color,
    val container: Color,
    val outline: Color,
    val field: Color,
    val thumb: Color,
    val onThumb: Color,
    val sheet: Color,
    val scrim: Color,
    val inverse: Color,
    val onInverse: Color,
    val skeleton: Color,
    val error: Color,
    val success: Color,
    val highlightAlpha: Float,
    val shadowAlpha: Float,
)

fun prismColors(dark: Boolean, brightness: Float): PrismColors {
    val b = brightness
    val ink = if (dark) Color(0xFFE9E9EE) else Color(0xFF17171C)
    fun ink(alpha: Float) = ink.copy(alpha = (alpha * b).coerceAtMost(1f))
    return if (dark) {
        PrismColors(
            isDark = true,
            brightness = b,
            background = Color(0xFF0D0D0D),
            text = ink,
            subText = ink.copy(alpha = 0.62f),
            disabled = ink.copy(alpha = 0.32f),
            ink = ink,
            fillWeak = ink(0.05f),
            fill = ink(0.08f),
            fillStrong = ink(0.13f),
            track = ink(0.16f),
            container = ink(0.09f),
            outline = ink(0.20f),
            field = Color(0xFF15151B).copy(alpha = 0.72f),
            thumb = Color(0xFFD6D6DD).copy(alpha = (0.97f + 0.03f * (b - 1f)).coerceIn(0.94f, 1f)),
            onThumb = Color(0xFF1A1A20),
            sheet = Color.Black.copy(alpha = 0.55f),
            scrim = Color.Black.copy(alpha = 0.40f),
            inverse = Color(0xFF2C2C34).copy(alpha = 0.96f),
            onInverse = Color(0xFFE9E9EE),
            skeleton = ink(0.07f),
            error = Color(0xFFF0736B),
            success = Color(0xFF4CC38A),
            highlightAlpha = 0.45f * b,
            shadowAlpha = 1f,
        )
    } else {
        PrismColors(
            isDark = false,
            brightness = b,
            background = Color(0xFFEEEFF3),
            text = ink,
            subText = ink.copy(alpha = 0.62f),
            disabled = ink.copy(alpha = 0.34f),
            ink = ink,
            fillWeak = ink(0.035f),
            fill = ink(0.055f),
            fillStrong = ink(0.09f),
            track = ink(0.10f),
            container = ink(0.05f),
            outline = ink(0.16f),
            field = Color.White.copy(alpha = 0.78f),
            thumb = Color.White.copy(alpha = 0.99f),
            onThumb = Color(0xFF17171C),
            sheet = Color.White.copy(alpha = 0.72f),
            scrim = Color.Black.copy(alpha = 0.18f),
            inverse = Color(0xFF26262D).copy(alpha = 0.94f),
            onInverse = Color(0xFFF2F2F6),
            skeleton = ink(0.06f),
            error = Color(0xFFC8433B),
            success = Color(0xFF1E9460),
            highlightAlpha = (0.70f * b).coerceAtMost(1f),
            shadowAlpha = 1.6f,
        )
    }
}

/** Blends two palettes; used for the animated light ↔ dark cross-fade. */
fun lerp(a: PrismColors, b: PrismColors, t: Float): PrismColors {
    fun c(x: Color, y: Color) = lerpColor(x, y, t)
    fun f(x: Float, y: Float) = x + (y - x) * t
    return PrismColors(
        isDark = t >= 0.5f,
        brightness = f(a.brightness, b.brightness),
        background = c(a.background, b.background),
        text = c(a.text, b.text),
        subText = c(a.subText, b.subText),
        disabled = c(a.disabled, b.disabled),
        ink = c(a.ink, b.ink),
        fillWeak = c(a.fillWeak, b.fillWeak),
        fill = c(a.fill, b.fill),
        fillStrong = c(a.fillStrong, b.fillStrong),
        track = c(a.track, b.track),
        container = c(a.container, b.container),
        outline = c(a.outline, b.outline),
        field = c(a.field, b.field),
        thumb = c(a.thumb, b.thumb),
        onThumb = c(a.onThumb, b.onThumb),
        sheet = c(a.sheet, b.sheet),
        scrim = c(a.scrim, b.scrim),
        inverse = c(a.inverse, b.inverse),
        onInverse = c(a.onInverse, b.onInverse),
        skeleton = c(a.skeleton, b.skeleton),
        error = c(a.error, b.error),
        success = c(a.success, b.success),
        highlightAlpha = f(a.highlightAlpha, b.highlightAlpha),
        shadowAlpha = f(a.shadowAlpha, b.shadowAlpha),
    )
}

/** Dynamic (not static) so brightness drags only recompose readers. */
val LocalPrismColors = compositionLocalOf { prismColors(dark = true, brightness = 1f) }

/** The "currentColor" that text and icons default to. */
val LocalContentColor = compositionLocalOf { Color(0xFFE9E9EE) }

object Prism {
    val colors: PrismColors
        @Composable get() = LocalPrismColors.current
    val background: Color
        @Composable get() = LocalPrismColors.current.background
    val text: Color
        @Composable get() = LocalPrismColors.current.text
    val subText: Color
        @Composable get() = LocalPrismColors.current.subText
    val error: Color
        @Composable get() = LocalPrismColors.current.error

    val accent: Color
        @Composable get() = LocalAccent.current

    /** Dark or light ink, whichever reads better on the current accent. */
    val onAccent: Color
        @Composable get() = contentOn(LocalAccent.current)

    /** Dark or light ink, whichever reads better on [color]. */
    fun contentOn(color: Color): Color =
        if (color.luminance() > 0.42f) Color(0xFF141418) else Color(0xFFF2F2F6)
}

/** Accent alpha / tint strength scaled by surface brightness, capped at 0.95. */
fun PrismColors.accentAlpha(base: Float): Float = (base * brightness).coerceAtMost(0.95f)

@Composable
fun PrismText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = prismStyle(color, fontSize, fontWeight, letterSpacing, textAlign),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}

@Composable
fun PrismText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = prismStyle(color, fontSize, fontWeight, letterSpacing, textAlign),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}

private fun prismStyle(
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    letterSpacing: TextUnit,
    textAlign: TextAlign,
) = TextStyle(
    color = color,
    fontFamily = Outfit,
    fontSize = fontSize,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing,
    textAlign = textAlign,
)
