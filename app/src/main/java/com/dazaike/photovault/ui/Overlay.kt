package com.dazaike.photovault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---- Host & portal -------------------------------------------------------------------------

/**
 * In-window overlay layer. `Popup` is deliberately not used: a separate window cannot refract
 * the app's [LayerBackdrop], and overlays composed after the app make their `BackHandler`s win.
 */
@Stable
class OverlayHostState {
    internal val layers = mutableStateListOf<OverlayLayer>()
    var size by mutableStateOf(IntSize.Zero)
        internal set
    lateinit var backdrop: LayerBackdrop
        internal set
}

internal class OverlayLayer(val content: MutableState<@Composable (Backdrop) -> Unit>)

val LocalOverlayHost = staticCompositionLocalOf<OverlayHostState> { error("OverlayHost missing") }

@Composable
fun OverlayHost(content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val host = remember { OverlayHostState() }
    host.backdrop = backdrop
    val toasts = remember { ToastState() }
    CompositionLocalProvider(LocalOverlayHost provides host, LocalToasts provides toasts) {
        Box(Modifier.fillMaxSize().onSizeChanged { host.size = it }) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
            host.layers.forEach { layer -> key(layer) { layer.content.value(backdrop) } }
            ToastLayer(toasts, backdrop)
        }
    }
}

/** Renders [content] in the [OverlayHost] above the whole app; it receives the app backdrop. */
@Composable
fun Portal(content: @Composable (backdrop: Backdrop) -> Unit) {
    val host = LocalOverlayHost.current
    val layer = remember { OverlayLayer(mutableStateOf(content)) }
    SideEffect { layer.content.value = content }
    DisposableEffect(host) {
        host.layers += layer
        onDispose { host.layers -= layer }
    }
}

/** Keeps a portal composed while [visible] or while its exit animation runs. */
@Composable
fun rememberOverlayVisibility(visible: Boolean): MutableTransitionState<Boolean> {
    val vis = remember { MutableTransitionState(false) }
    vis.targetState = visible
    return vis
}

val MutableTransitionState<Boolean>.isComposed: Boolean get() = currentState || targetState

// ---- Toasts --------------------------------------------------------------------------------

enum class ToastKind { Info, Success, Error }

class ToastData(
    val id: Long,
    val message: String,
    val kind: ToastKind,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMs: Long,
)

@Stable
class ToastState {
    private var nextId = 0L

    var current by mutableStateOf<ToastData?>(null)
        private set

    /** Shows a toast, replacing the current one. */
    fun show(
        message: String,
        kind: ToastKind = ToastKind.Info,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = 2800,
    ) {
        current = ToastData(++nextId, message, kind, actionLabel, onAction, durationMs)
    }

    fun dismiss() {
        current = null
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalToasts = staticCompositionLocalOf<ToastState> { error("ToastState not provided; wrap in OverlayHost") }

@Composable
private fun ToastLayer(toasts: ToastState, backdrop: Backdrop) {
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val slide = with(LocalDensity.current) { (16.dp * motion.magnitude).roundToPx() }
    val current = toasts.current

    LaunchedEffect(current?.id) {
        val t = current ?: return@LaunchedEffect
        when (t.kind) {
            ToastKind.Success -> haptics.perform(HapticKind.Success)
            ToastKind.Error -> haptics.perform(HapticKind.Error)
            ToastKind.Info -> Unit
        }
        delay(t.durationMs)
        toasts.dismiss(t.id)
    }

    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = current,
            contentKey = { it?.id },
            transitionSpec = {
                (fadeIn(motion.enter(240)) + slideInVertically(motion.settle()) { slide }) togetherWith
                    fadeOut(motion.exit(160)) using null
            },
            label = "toast",
        ) { t ->
            if (t != null) ToastBody(t, backdrop, toasts)
        }
    }
}

@Composable
private fun ToastBody(t: ToastData, backdrop: Backdrop, toasts: ToastState) {
    val colors = Prism.colors
    val accent = Prism.accent
    val (icon, tint) = when (t.kind) {
        ToastKind.Info -> PrismIcons.Info to accent
        ToastKind.Success -> PrismIcons.Check to colors.success
        ToastKind.Error -> PrismIcons.Alert to colors.error
    }
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(max = 360.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(20.dp.toPx() * GlassBlurScale)
                    lens(8.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = colors.highlightAlpha) },
                shadow = { Shadow(16.dp, color = Color.Black.copy(alpha = 0.18f * colors.shadowAlpha)) },
                onDrawSurface = { drawRect(colors.sheet) },
            )
            .clickable(interactionSource = null, indication = null) { toasts.dismiss() }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = if (t.actionLabel != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismIcon(icon, null, size = 18.dp, tint = tint)
        PrismText(t.message, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
        val label = t.actionLabel
        if (label != null) {
            GlassButton(
                backdrop = backdrop,
                text = label,
                onClick = {
                    t.onAction?.invoke()
                    toasts.dismiss(t.id)
                },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                contentColor = accent,
            )
        }
    }
}

// ---- Confirm dialog ------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmLoading: Boolean = false,
) {
    val vis = rememberOverlayVisibility(visible)
    if (!vis.isComposed) return
    Portal { backdrop ->
        val colors = Prism.colors
        val motion = LocalMotion.current
        val m = motion.magnitude
        BackHandler(enabled = visible && !confirmLoading, onBack = onDismiss)
        AnimatedVisibility(
            visibleState = vis,
            modifier = Modifier.fillMaxSize(),
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .animateEnterExit(enter = fadeIn(motion.fade(200)), exit = fadeOut(motion.fade(200)))
                        .fillMaxSize()
                        .background(colors.scrim)
                        .clickable(interactionSource = null, indication = null) {
                            if (!confirmLoading) onDismiss()
                        },
                )
                val cardBackdrop = rememberLayerBackdrop()
                Column(
                    Modifier
                        .animateEnterExit(
                            enter = fadeIn(motion.enter(240)) + scaleIn(motion.settle(), initialScale = 1f - 0.06f * m),
                            exit = fadeOut(motion.exit(150)) + scaleOut(motion.exit(150), targetScale = 1f - 0.03f * m),
                        )
                        .padding(24.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(28.dp) },
                            depth = glassDepth(elevation = 20.dp),
                            blurRadius = 28.dp,
                            refractionHeight = 16.dp,
                            refractionAmount = 32.dp,
                            surface = colors.sheet,
                            exportedBackdrop = cardBackdrop,
                        )
                        .clickable(interactionSource = null, indication = null) {}
                        .semantics { paneTitle = title }
                        .padding(24.dp),
                ) {
                    PrismText(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    PrismText(message, fontSize = 15.sp, color = colors.subText)
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        GlassButton(cardBackdrop, dismissLabel, onDismiss, enabled = !confirmLoading)
                        GlassButton(
                            cardBackdrop,
                            confirmLabel,
                            onConfirm,
                            variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
                            loading = confirmLoading,
                        )
                    }
                }
            }
        }
    }
}

// ---- Tooltip -------------------------------------------------------------------------------

/** Shows [text] above (or below) [content] on long-press, 500 ms hover, or keyboard focus. */
@Composable
fun TooltipBox(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var pressShown by remember { mutableStateOf(false) }
    var hoverShown by remember { mutableStateOf(false) }
    var focusShown by remember { mutableStateOf(false) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(500)
            hoverShown = true
        } else {
            hoverShown = false
        }
    }

    Box(
        modifier
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .hoverable(hoverSource)
            .onFocusChanged { focusShown = it.hasFocus }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var ended = false
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation(PointerEventPass.Initial)
                        ended = true
                    }
                    if (!ended) {
                        pressShown = true
                        haptics.perform(HapticKind.LongPress)
                        // Swallow the rest of the gesture so the child's click does not fire.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        scope.launch {
                            delay(1500)
                            pressShown = false
                        }
                    }
                }
            },
    ) { content() }

    TooltipBubble(text, visible = pressShown || hoverShown || focusShown, anchor = { anchor })
}

@Composable
private fun TooltipBubble(text: String, visible: Boolean, anchor: () -> Rect) {
    val vis = rememberOverlayVisibility(visible)
    if (!vis.isComposed) return
    Portal {
        val colors = Prism.colors
        val motion = LocalMotion.current
        val density = LocalDensity.current
        val statusTop = WindowInsets.statusBars.getTop(density)
        val initialScale = 1f - 0.04f * motion.magnitude
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                AnimatedVisibility(
                    visibleState = vis,
                    enter = fadeIn(motion.fade(150)) + scaleIn(motion.fade(150), initialScale = initialScale),
                    exit = fadeOut(motion.fade(150)) + scaleOut(motion.fade(150), targetScale = initialScale),
                ) {
                    PrismText(
                        text,
                        fontSize = 13.sp,
                        color = colors.onInverse,
                        maxLines = 2,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .background(colors.inverse, RoundedRectangle(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (placeable != null) {
                    val a = anchor()
                    val gap = 8.dp.toPx()
                    val x = (a.center.x - placeable.width / 2f)
                        .coerceIn(gap, (constraints.maxWidth - placeable.width - gap).coerceAtLeast(gap))
                    val above = a.top - gap - placeable.height
                    val y = if (above < statusTop + gap) a.bottom + gap else above
                    placeable.place(x.roundToInt(), y.roundToInt())
                }
            }
        }
    }
}
