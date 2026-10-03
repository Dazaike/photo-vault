package com.dazaike.photovault.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.ui.theme.accentAlpha
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Slider over 0..1: a capsule track and a glass thumb that refracts it. The track fills with the
 * accent up to the thumb, or [trackBrush] paints the whole track (hue / shade pickers).
 *
 * Tap anywhere to set, drag anywhere to scrub; dragging the thumb itself is relative so it never
 * jumps. While held the thumb lifts (scale, shadow, rim) and [valueLabel] shows above it.
 * [stepCount] > 0 snaps to that many intervals with a tick per step; continuous sliders tick at the ends.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stepCount: Int = 0,
    trackBrush: Brush? = null,
    valueLabel: ((Float) -> String)? = null,
    alwaysShowValue: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val trackBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val press = rememberPressState(enabled)
    val thumbWidthPx = with(density) { 44.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(pressed, motion) {
        lift.animateTo(if (pressed) 1f else 0f, if (pressed) motion.press() else motion.release())
    }

    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val haptics by rememberUpdatedState(LocalHaptics.current)
    val lastZone = remember { intArrayOf(SliderMath.tickZone(value, stepCount)) }
    // Accessibility sees the value captured when the drag began and catches up on release: a semantics
    // change every frame makes Compose diff the tree and notify accessibility services each time.
    val reportedValue = remember(pressed) { value }

    fun travel() = max(widthPx - thumbWidthPx, 1f)
    fun set(v: Float) {
        val snapped = SliderMath.snap(v, stepCount)
        val zone = SliderMath.tickZone(snapped, stepCount)
        if (SliderMath.shouldTick(lastZone[0], zone)) haptics.perform(HapticKind.Tick)
        lastZone[0] = zone
        if (snapped != currentValue) onChange(snapped)
    }
    val keyStep = if (stepCount > 0) 1f / stepCount else 0.05f

    Box(
        modifier
            .zIndex(1f)
            .graphicsLayer { }
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, stepCount) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        val startValue = currentValue
                        val thumbCenter = startValue * travel() + thumbWidthPx / 2f
                        val onThumb = abs(down.position.x - thumbCenter) <= thumbWidthPx / 2f + 8.dp.toPx()
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                        if (slop == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed) {
                                set(SliderMath.valueAt(down.position.x, widthPx, thumbWidthPx))
                            }
                        } else {
                            val at = { x: Float ->
                                if (onThumb) SliderMath.dragged(startValue, x - down.position.x, widthPx, thumbWidthPx)
                                else SliderMath.valueAt(x, widthPx, thumbWidthPx)
                            }
                            set(at(slop.position.x))
                            horizontalDrag(slop.id) { c ->
                                set(at(c.position.x))
                                c.consume()
                            }
                        }
                    } finally {
                        pressed = false
                        onFinished?.invoke()
                    }
                }
            }
            .focusable(enabled, press.interactionSource)
            .onKeyEvent { e ->
                if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> set(currentValue - keyStep)
                    Key.DirectionRight -> set(currentValue + keyStep)
                    Key.MoveHome -> set(0f)
                    Key.MoveEnd -> set(1f)
                    else -> return@onKeyEvent false
                }
                true
            }
            .progressSemantics(reportedValue, 0f..1f, steps = max(0, stepCount - 1))
            .semantics {
                if (enabled) setProgress { v -> set(v); true }
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(8.dp)
                .focusRing(press, Capsule(), accent.copy(alpha = 0.85f))
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .background(colors.track),
        ) {
            if (trackBrush != null) {
                Box(Modifier.fillMaxSize().background(trackBrush))
            } else {
                val fill = accent.copy(alpha = colors.accentAlpha(0.85f))
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(fill, size = Size(value * travel() + thumbWidthPx / 2f, size.height))
                    },
                )
            }
        }
        val m = motion.magnitude
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((value * travel()).roundToInt(), 0) }
                .liquidGlass(
                    backdrop = trackBackdrop,
                    shape = { Capsule() },
                    depth = glassDepth(elevation = 4.dp, lift = { lift.value }),
                    blurRadius = 1.dp,
                    refractionHeight = 6.dp,
                    refractionAmount = 12.dp,
                    surface = colors.thumb.copy(alpha = 1f),
                    layerBlock = {
                        val s = 1f + 0.14f * m * lift.value
                        scaleX = s
                        scaleY = s
                    },
                )
                .size(width = 44.dp, height = 28.dp),
        )
        if (valueLabel != null) {
            val gap = with(density) { 6.dp.roundToPx() }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints())
                        layout(0, 0) {
                            val cx = value * travel() + thumbWidthPx / 2f
                            p.placeWithLayer((cx - p.width / 2f).roundToInt(), -gap - p.height) {
                                alpha = max(if (alwaysShowValue) 1f else 0f, lift.value)
                            }
                        }
                    },
            ) {
                PrismText(
                    valueLabel(value),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onInverse,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .background(colors.inverse, Capsule())
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * Segmented selector (Day / Week / Month). A restrained glass indicator glides critically damped
 * between options — no overshoot — and labels brighten in lockstep with it.
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    val position by animateFloatAsState(selectedIndex.toFloat(), motion.glide(), label = "segmentPosition")
    val paddingPx = with(density) { 4.dp.toPx() }
    val segmentPx = (widthPx - 2f * paddingPx) / options.size

    Box(
        modifier
            .graphicsLayer { }
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(containerBackdrop)
                .clip(Capsule())
                .background(colors.container),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((paddingPx + position * segmentPx).roundToInt(), 0) }
                .liquidGlass(
                    backdrop = containerBackdrop,
                    shape = { Capsule() },
                    depth = glassDepth(elevation = 3.dp),
                    blurRadius = 2.dp,
                    refractionHeight = 8.dp,
                    refractionAmount = 16.dp,
                    tint = accent,
                    tintStrength = colors.accentAlpha(0.30f),
                )
                .size(width = with(density) { segmentPx.coerceAtLeast(0f).toDp() }, height = 40.dp),
        )
        Row(Modifier.matchParentSize().padding(4.dp).selectableGroup()) {
            options.forEachIndexed { index, label ->
                val press = rememberPressState(enabled)
                val closeness = (1f - abs(position - index)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRing(press, Capsule(), accent.copy(alpha = 0.85f))
                        .pressInput(press, enabled)
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = press.interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Tab,
                        ) {
                            if (index != selectedIndex) {
                                haptics.perform(HapticKind.Tick)
                                onSelect(index)
                            }
                        }
                        .graphicsLayer { alpha = 1f - 0.3f * press.progress },
                    contentAlignment = Alignment.Center,
                ) {
                    PrismText(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = lerp(colors.subText, colors.text, closeness),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Switch: the track fills with the accent and a glass thumb (refracting the track) glides across.
 * The thumb stretches while pressed and shows a check mark when on.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val trackBackdrop = rememberLayerBackdrop()
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressState(enabled)
    val progress by animateFloatAsState(if (checked) 1f else 0f, motion.glide(), label = "switchProgress")
    val m = motion.magnitude
    val on = accent.copy(alpha = colors.accentAlpha(0.9f))

    Box(
        modifier
            .graphicsLayer { }
            .focusRing(press, Capsule(), accent.copy(alpha = 0.85f))
            .alpha(if (enabled) 1f else 0.45f)
            .size(width = 64.dp, height = 30.dp)
            .pressInput(press, enabled)
            .toggleable(
                value = checked,
                interactionSource = press.interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { new ->
                haptics.perform(if (new) HapticKind.ToggleOn else HapticKind.ToggleOff)
                onCheckedChange(new)
            }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind { drawRect(lerp(colors.track, on, progress)) },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((2.dp.toPx() + progress * 20.dp.toPx()).roundToInt(), 0) }
                .liquidGlass(
                    backdrop = trackBackdrop,
                    shape = { Capsule() },
                    depth = glassDepth(press, elevation = 3.dp),
                    blurRadius = 1.dp,
                    refractionHeight = 6.dp,
                    refractionAmount = 12.dp,
                    surface = colors.thumb.copy(alpha = 1f),
                    layerBlock = {
                        scaleX = 1f + 0.10f * m * press.progress
                        scaleY = 1f - 0.04f * m * press.progress
                    },
                )
                .size(width = 40.dp, height = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            PrismIcon(
                PrismIcons.Check,
                null,
                size = 14.dp,
                tint = accent.copy(alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)),
            )
        }
    }
}

/**
 * Slide-to-confirm: drag the glass thumb along the track; release past 90% to confirm,
 * otherwise it returns (critically damped).
 */
@Composable
fun SlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val insetPx = with(density) { 4.dp.toPx() }
    val thumbPx = with(density) { 56.dp.toPx() }
    val travel = max(widthPx - thumbPx - 2f * insetPx, 1f)
    val progress = (offset.value / travel).coerceIn(0f, 1f)

    Box(
        modifier
            .graphicsLayer { }
            .fillMaxWidth()
            .height(64.dp)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .background(lerp(colors.container, accent.copy(alpha = colors.accentAlpha(0.5f)), progress)),
        )
        PrismText(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            color = colors.subText.copy(alpha = colors.subText.alpha * (1f - progress * 1.4f).coerceIn(0f, 1f)),
            fontSize = 16.sp,
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .liquidGlass(
                    backdrop = trackBackdrop,
                    shape = { Capsule() },
                    depth = glassDepth(elevation = 6.dp),
                    blurRadius = 1.dp,
                    refractionHeight = 8.dp,
                    refractionAmount = 16.dp,
                    surface = colors.thumb.copy(alpha = 1f),
                )
                .size(56.dp)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, travel)) }
                    },
                    onDragStopped = {
                        if (offset.value >= travel * 0.9f) {
                            haptics.perform(HapticKind.Success)
                            onConfirm()
                        }
                        scope.launch { offset.animateTo(0f, motion.release()) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            PrismIcon(PrismIcons.ChevronRight, null, size = 24.dp, tint = colors.onThumb)
        }
    }
}
