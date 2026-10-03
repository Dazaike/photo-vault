package com.dazaike.photovault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.dazaike.photovault.ui.theme.Prism

/**
 * Multiplier on every glass blur radius (every `blur(...)` in the app goes through it). 1 = original look.
 * Measured on the Pixel 9a: 0.5 made no measurable frame-time difference, so it is left at 1.
 */
const val GlassBlurScale = 1f

/**
 * Rim, drop shadow, inner shadow and press lighting of one glass surface. Every lambda is
 * read at draw time, so animating press / lift never recomposes.
 */
class GlassDepth(
    val highlight: () -> Highlight?,
    val shadow: (() -> Shadow?)?,
    val innerShadow: (() -> InnerShadow?)?,
    val press: () -> Float,
)

/**
 * Restrained depth: token-alpha rim, a soft shadow of [elevation] that tightens while pressed,
 * and a lowered inner shadow while [press] is held. [lift] (0..1) raises the surface toward the user.
 */
@Composable
fun glassDepth(press: PressState? = null, elevation: Dp = 6.dp, lift: () -> Float = { 0f }): GlassDepth {
    val colors = Prism.colors
    val currentLift = rememberUpdatedState(lift)
    return remember(colors, press, elevation) {
        val p = { press?.progress ?: 0f }
        GlassDepth(
            highlight = {
                Highlight.Default.copy(
                    alpha = (colors.highlightAlpha * (1f - 0.35f * p()) + 0.25f * currentLift.value()).coerceAtMost(1f),
                )
            },
            shadow = if (elevation == 0.dp) null else {
                {
                    val l = currentLift.value()
                    val e = elevation * (1f - 0.55f * p()) + 8.dp * l
                    Shadow(
                        radius = e,
                        offset = DpOffset(0.dp, e / 3f),
                        color = Color.Black.copy(alpha = ((0.12f + 0.10f * l) * colors.shadowAlpha).coerceAtMost(0.32f)),
                    )
                }
            },
            innerShadow = press?.let { state ->
                {
                    if (state.progress <= 0.01f) null
                    else InnerShadow(
                        radius = 5.dp,
                        offset = DpOffset(0.dp, 1.5.dp),
                        color = Color.Black.copy(alpha = 0.22f),
                        alpha = state.progress,
                    )
                }
            },
            press = p,
        )
    }
}

/**
 * Liquid glass surface: blurs and refracts [backdrop] inside [shape].
 *
 * [tint] is blended over the glass with [tintStrength]; [surface] is a flat translucent overlay.
 * Glass placed on this surface must refract [exportedBackdrop], never the page behind it.
 * Effects that need RenderEffect / RuntimeShader degrade to the plain tint below API 33.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape,
    depth: GlassDepth,
    blurRadius: Dp = 2.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    tintStrength: Float = 0.75f,
    surface: Color = Color.Unspecified,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(blurRadius.toPx() * GlassBlurScale)
        lens(refractionHeight.toPx(), refractionAmount.toPx())
    },
    highlight = depth.highlight,
    shadow = depth.shadow,
    innerShadow = depth.innerShadow,
    layerBlock = layerBlock,
    exportedBackdrop = exportedBackdrop,
    onDrawSurface = {
        if (tint != Color.Unspecified) {
            drawRect(tint, blendMode = BlendMode.Hue)
            drawRect(tint.copy(alpha = tintStrength))
        }
        if (surface != Color.Unspecified) drawRect(surface)
        val d = depth.press()
        if (d > 0f) drawRect(Color.Black.copy(alpha = 0.06f * d))
    },
    onDrawFront = onDrawFront,
)
