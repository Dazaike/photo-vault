package com.dazaike.photovault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism

/**
 * Scrim + floating liquid glass sheet that rises over the page. Dismissed by scrim tap or Back.
 * [content] receives the sheet's own exported backdrop: glass inside the sheet must refract it.
 *
 * Mounts in the [Portal] layer above the whole app, so the sheet frosts and refracts everything
 * behind it (not just the flat page colour). Because it composes at the host, it can be called
 * from any `BoxScope` and does not depend on that scope's size or insets.
 */
@Composable
fun BoxScope.SheetOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    heightFraction: Float,
    content: @Composable ColumnScope.(surface: Backdrop) -> Unit,
) {
    Portal { appBackdrop ->
        Box(Modifier.fillMaxSize().imePadding()) {
            SheetLayer(appBackdrop, visible, onDismiss, heightFraction, content)
        }
    }
}

/** The sheet itself, drawn over [backdrop]. Must not be composed inside the layer [backdrop] records. */
@Composable
internal fun BoxScope.SheetLayer(
    backdrop: Backdrop,
    visible: Boolean,
    onDismiss: () -> Unit,
    heightFraction: Float,
    content: @Composable ColumnScope.(surface: Backdrop) -> Unit,
) {
    // Lets a sheet whose content changes shape (e.g. config -> saved) resize smoothly instead of jumping.
    val height by animateFloatAsState(heightFraction, LocalMotion.current.settle(), label = "sheetHeight")
    BackHandler(enabled = visible, onBack = onDismiss)
    val colors = Prism.colors
    val motion = LocalMotion.current
    // Starts hidden and flips to [visible] after the first composition, so the slide-in always plays,
    // even when the sheet is mounted already visible (e.g. a portal that composes a frame late).
    val shown = rememberOverlayVisibility(visible)

    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(motion.fade(250)),
        exit = fadeOut(motion.fade(200)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        ) {}
    }

    AnimatedVisibility(
        visibleState = shown,
        modifier = Modifier.align(Alignment.BottomCenter),
        // Sliding in from off-screen needs no fade; an alpha on the glass forces an offscreen pass over the blur.
        enter = if (motion.reduced) fadeIn(motion.fade(250)) else slideInVertically(motion.settle()) { it },
        exit = if (motion.reduced) fadeOut(motion.fade(200)) else slideOutVertically(motion.exit(220)) { it },
    ) {
        val surface = rememberLayerBackdrop()
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .fillMaxHeight(height)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(40.dp) },
                    effects = {
                        vibrancy()
                        blur(28.dp.toPx() * GlassBlurScale)
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = colors.highlightAlpha) },
                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.18f * colors.shadowAlpha)) },
                    exportedBackdrop = surface,
                    onDrawSurface = { drawRect(colors.sheet) },
                )
                .clickable(interactionSource = null, indication = null) {}
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) { content(surface) }
    }
}
