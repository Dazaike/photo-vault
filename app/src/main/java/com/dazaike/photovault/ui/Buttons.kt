package com.dazaike.photovault.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.dazaike.photovault.ui.theme.LocalContentColor
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.ui.theme.accentAlpha
import kotlinx.coroutines.launch
import kotlin.math.max

enum class ButtonVariant { Primary, Secondary, Outlined, Destructive, Ghost }

enum class ButtonSize(val height: Dp, val hPad: Dp, val font: TextUnit, val icon: Dp) {
    Small(36.dp, 14.dp, 14.sp, 16.dp),
    Medium(46.dp, 20.dp, 16.sp, 18.dp),
    Large(56.dp, 26.dp, 17.sp, 20.dp),
}

@Composable
fun GlassButton(
    backdrop: Backdrop,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    contentColor: Color = Color.Unspecified,
) {
    GlassPressable(
        backdrop = backdrop,
        variant = variant,
        enabled = enabled,
        interactive = enabled && !loading,
        onClick = onClick,
        modifier = modifier
            .semantics { if (loading) stateDescription = "Loading" },
        sizing = Modifier.heightIn(min = size.height).padding(horizontal = size.hPad),
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier.alpha(if (loading) 0f else 1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) PrismIcon(leadingIcon, null, size = size.icon)
                PrismText(text, fontSize = size.font, fontWeight = FontWeight.Medium, maxLines = 1)
                if (trailingIcon != null) PrismIcon(trailingIcon, null, size = size.icon)
            }
            if (loading) Spinner(size = size.icon)
        }
    }
}

/** Button that runs [onSubmit] once at a time; taps while it runs are ignored. */
@Composable
fun GlassSubmitButton(
    backdrop: Backdrop,
    text: String,
    onSubmit: suspend () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GlassButton(
        backdrop = backdrop,
        text = text,
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    try {
                        onSubmit()
                    } finally {
                        running = false
                    }
                }
            }
        },
        modifier = modifier,
        variant = variant,
        size = size,
        enabled = enabled,
        loading = running,
        leadingIcon = leadingIcon,
    )
}

@Composable
fun GlassIconButton(
    backdrop: Backdrop,
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: Dp = 46.dp,
    enabled: Boolean = true,
) {
    GlassPressable(
        backdrop = backdrop,
        variant = variant,
        enabled = enabled,
        interactive = enabled,
        onClick = onClick,
        modifier = modifier,
        sizing = Modifier.size(size),
    ) {
        PrismIcon(icon, contentDescription, size = 20.dp)
    }
}

/** Shared surface, press, focus and haptics for every button variant. */
@Composable
private fun GlassPressable(
    backdrop: Backdrop,
    variant: ButtonVariant,
    enabled: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    sizing: Modifier,
    contentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val press = rememberPressState(interactive)
    val colors = Prism.colors
    val accent = Prism.accent
    val haptics = LocalHaptics.current
    val layer = rememberPressLayer(press)
    val dim = if (enabled) 1f else 0.4f

    val label = when (variant) {
        ButtonVariant.Primary -> Prism.contentOn(lerp(colors.background, accent, 0.66f))
        ButtonVariant.Destructive -> Prism.contentOn(lerp(colors.background, colors.error, 0.55f))
        else -> colors.text
    }.let { if (contentColor != Color.Unspecified) contentColor else it }

    val surface = if (variant == ButtonVariant.Ghost) {
        Modifier
            .graphicsLayer(layer)
            .clip(Capsule())
            .drawBehind {
                val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a))
            }
    } else {
        val depth = glassDepth(press, elevation = if (enabled) 6.dp else 0.dp)
        val tint = when (variant) {
            ButtonVariant.Primary -> accent
            ButtonVariant.Destructive -> colors.error
            else -> Color.Unspecified
        }
        val tintStrength = when (variant) {
            ButtonVariant.Primary -> colors.accentAlpha(0.66f) * dim
            ButtonVariant.Destructive -> colors.accentAlpha(0.55f) * dim
            else -> 0f
        }
        val fill = if (variant == ButtonVariant.Secondary) {
            colors.fill.copy(alpha = colors.fill.alpha + 0.035f * press.hover)
        } else {
            Color.Unspecified
        }
        Modifier.liquidGlass(
            backdrop = backdrop,
            shape = { Capsule() },
            depth = depth,
            blurRadius = 6.dp,
            refractionHeight = 8.dp,
            refractionAmount = 16.dp,
            tint = tint,
            tintStrength = tintStrength,
            surface = fill,
            layerBlock = layer,
            onDrawFront = if (variant == ButtonVariant.Outlined) {
                {
                    val outline = Capsule().createOutline(size, layoutDirection, this)
                    val c = colors.outline
                    drawOutline(
                        outline,
                        c.copy(alpha = (c.alpha + 0.15f * press.hover).coerceAtMost(1f)),
                        style = Stroke(1.25.dp.toPx()),
                    )
                }
            } else {
                null
            },
        )
    }

    Box(
        modifier
            .graphicsLayer { }
            .focusRing(press, Capsule(), accent.copy(alpha = 0.85f))
            .then(surface)
            .pressInput(press, interactive)
            .clickable(
                interactionSource = press.interactionSource,
                indication = null,
                enabled = interactive,
                role = Role.Button,
            ) {
                haptics.perform(HapticKind.Press)
                onClick()
            }
            .then(sizing),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides label) {
            Box(Modifier.alpha(dim), contentAlignment = Alignment.Center) { content() }
        }
    }
}
