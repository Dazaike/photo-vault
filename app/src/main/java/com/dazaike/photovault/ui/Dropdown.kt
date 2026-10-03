package com.dazaike.photovault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val MenuRowHeight = 48.dp
private val MenuPadding = 6.dp
private val MenuMaxHeight = 280.dp

/**
 * Single-choice dropdown. [selectedIndex] = -1 shows [placeholder]. The menu opens in the
 * overlay host (refracting the whole app), flips above the anchor when there is no room below,
 * and supports Up / Down / Enter / Escape / Tab while open.
 */
@Composable
fun GlassDropdown(
    label: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Select…",
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressState(enabled)
    val pressLayer = rememberPressLayer(press, depth = 0.02f)
    val anchorFocus = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(enabled) { if (!enabled) open = false }

    val chevron by animateFloatAsState(if (open) 1f else 0f, motion.glide(), label = "dropdownChevron")
    val border by animateColorAsState(
        if (open) accent.copy(alpha = 0.7f) else colors.outline,
        motion.fade(180),
        label = "dropdownBorder",
    )
    val shape = RoundedRectangle(16.dp)
    val selectedText = options.getOrNull(selectedIndex)

    Column(modifier.alpha(if (enabled) 1f else 0.45f)) {
        if (label != null) {
            PrismText(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.subText)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .focusRing(press, shape, accent.copy(alpha = 0.85f))
                .onGloballyPositioned { anchor = it.boundsInRoot() }
                .graphicsLayer(pressLayer)
                .clip(shape)
                .background(colors.field)
                .border(1.dp, border, shape)
                .semantics {
                    if (label != null) contentDescription = label
                    stateDescription = if (open) "Expanded" else "Collapsed"
                }
                .focusRequester(anchorFocus)
                .pressInput(press, enabled)
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.DropdownList,
                ) {
                    haptics.perform(HapticKind.Press)
                    open = !open
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrismText(
                selectedText ?: placeholder,
                modifier = Modifier.weight(1f),
                color = if (selectedText != null) colors.text else colors.subText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PrismIcon(
                PrismIcons.ChevronDown,
                null,
                modifier = Modifier.graphicsLayer { rotationZ = 180f * chevron },
                size = 18.dp,
                tint = colors.subText,
            )
        }
    }

    val visible = open && enabled
    val vis = rememberOverlayVisibility(visible)
    if (vis.isComposed) {
        Portal { backdrop ->
            DropdownMenu(
                vis = vis,
                visible = visible,
                backdrop = backdrop,
                anchor = anchor,
                title = label,
                options = options,
                selectedIndex = selectedIndex,
                onPick = { i ->
                    haptics.perform(HapticKind.Tick)
                    onSelect(i)
                    open = false
                },
                onDismiss = { restoreFocus ->
                    open = false
                    if (restoreFocus) anchorFocus.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun DropdownMenu(
    vis: MutableTransitionState<Boolean>,
    visible: Boolean,
    backdrop: Backdrop,
    anchor: Rect,
    title: String?,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: (restoreFocus: Boolean) -> Unit,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val host = LocalOverlayHost.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)

    BackHandler(enabled = visible) { onDismiss(false) }

    val rowPx = with(density) { MenuRowHeight.toPx() }
    val padPx = with(density) { MenuPadding.toPx() }
    val gapPx = with(density) { 6.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val desired = min(with(density) { MenuMaxHeight.toPx() }, options.size * rowPx + 2 * padPx)
    val spaceBelow = host.size.height - anchor.bottom - navBottom - marginPx - gapPx
    val spaceAbove = anchor.top - statusTop - marginPx - gapPx
    val below = spaceBelow >= desired || spaceBelow >= spaceAbove
    // Never taller than the chosen side allows; rows scroll inside.
    val maxHeight = min(desired, max(if (below) spaceBelow else spaceAbove, rowPx + 2 * padPx)).roundToInt()

    Box(Modifier.fillMaxSize()) {
        if (visible) {
            // Outside tap dismisses; during the exit animation touches reach the app again.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss(false) } })
        }
        AnimatedVisibility(
            visibleState = vis,
            modifier = Modifier.layout { measurable, constraints ->
                val width = anchor.width.roundToInt().coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(
                    Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = anchor.left.roundToInt().coerceIn(0, (constraints.maxWidth - width).coerceAtLeast(0))
                    val y = if (below) {
                        (anchor.bottom + gapPx).roundToInt()
                    } else {
                        (anchor.top - gapPx - placeable.height).roundToInt()
                    }
                    placeable.place(x, y)
                }
            },
            enter = fadeIn(motion.enter(220)) + scaleIn(
                animationSpec = motion.settle(),
                initialScale = 1f - 0.06f * motion.magnitude,
                transformOrigin = TransformOrigin(0.5f, if (below) 0f else 1f),
            ),
            exit = fadeOut(motion.exit(140)),
        ) {
            val scroll = rememberScrollState(((selectedIndex - 2).coerceAtLeast(0) * rowPx).toInt())
            val focus = remember { FocusRequester() }
            var highlight by remember { mutableIntStateOf(selectedIndex) }

            LaunchedEffect(Unit) { focus.requestFocus() }
            LaunchedEffect(highlight) {
                if (highlight < 0) return@LaunchedEffect
                val top = (highlight * rowPx).roundToInt()
                val bottom = top + rowPx.roundToInt()
                val viewport = scroll.viewportSize
                when {
                    top < scroll.value -> scroll.animateScrollTo(top, motion.glide())
                    viewport > 0 && bottom > scroll.value + viewport ->
                        scroll.animateScrollTo(bottom - viewport, motion.glide())
                }
            }

            Column(
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(20.dp) },
                        effects = {
                            vibrancy()
                            blur(24.dp.toPx() * GlassBlurScale)
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        highlight = { Highlight.Default.copy(alpha = colors.highlightAlpha) },
                        shadow = { Shadow(16.dp, color = Color.Black.copy(alpha = 0.2f * colors.shadowAlpha)) },
                        onDrawSurface = { drawRect(colors.sheet) },
                    )
                    .semantics { paneTitle = title ?: "Options" }
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        val down = e.type == KeyEventType.KeyDown
                        val up = e.type == KeyEventType.KeyUp
                        when (e.key) {
                            Key.DirectionDown -> {
                                if (down && options.isNotEmpty()) {
                                    highlight = (highlight + 1).coerceIn(0, options.lastIndex)
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (down && options.isNotEmpty()) {
                                    highlight = (highlight - 1).coerceIn(0, options.lastIndex)
                                }
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                if (up && highlight in options.indices) {
                                    onPick(highlight)
                                    onDismiss(true)
                                }
                                true
                            }
                            // Acting on key-up keeps the stray Escape up from reaching Back.
                            Key.Escape -> {
                                if (up) onDismiss(true)
                                true
                            }
                            Key.Tab -> {
                                if (down) onDismiss(true)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .padding(MenuPadding)
                    .verticalScroll(scroll)
                    .selectableGroup(),
            ) {
                options.forEachIndexed { i, option ->
                    val press = rememberPressState()
                    val selected = i == selectedIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(MenuRowHeight)
                            .clip(RoundedRectangle(12.dp))
                            .drawBehind {
                                if (i == highlight) {
                                    drawRect(colors.fill)
                                } else {
                                    val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                                    if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a))
                                }
                            }
                            .pressInput(press)
                            .selectable(
                                selected = selected,
                                interactionSource = press.interactionSource,
                                indication = null,
                                role = Role.RadioButton,
                            ) { onPick(i) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrismText(
                            option,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) PrismIcon(PrismIcons.Check, null, size = 18.dp, tint = accent)
                    }
                }
            }
        }
    }
}
