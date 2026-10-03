package com.dazaike.photovault.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.ui.theme.accentAlpha
import kotlin.math.abs
import kotlin.math.max

class SidebarItem(val key: String, val label: String, @DrawableRes val icon: Int)

private val CollapsedWidth = 72.dp
private val ExpandedWidth = 248.dp
private val HeaderHeight = 40.dp
private val RowHeight = 48.dp
private val RowGap = 4.dp
private val IconStart = 13.dp

/**
 * Glass navigation rail that glides between a 72dp icon rail and a 248dp labelled sidebar.
 * Icons never move between states; labels fade with the width. When the available height is
 * short, the item list scrolls and the footer toggle stays visible.
 */
@Composable
fun GlassSidebar(
    backdrop: Backdrop,
    items: List<SidebarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    header: String? = null,
    onToggleExpanded: (() -> Unit)? = null,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val panelBackdrop = rememberLayerBackdrop()

    val width by animateDpAsState(if (expanded) ExpandedWidth else CollapsedWidth, motion.glide(), label = "sidebarWidth")
    val expandProgress = ((width - CollapsedWidth) / (ExpandedWidth - CollapsedWidth)).coerceIn(0f, 1f)
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }
    val position by animateFloatAsState(selectedIndex.coerceAtLeast(0).toFloat(), motion.glide(), label = "sidebarIndicator")
    val rowShape = RoundedRectangle(16.dp)
    val ring = accent.copy(alpha = 0.85f)

    Column(
        modifier
            .width(width)
            .liquidGlass(
                backdrop = backdrop,
                shape = { RoundedRectangle(28.dp) },
                depth = glassDepth(elevation = 10.dp),
                blurRadius = 16.dp,
                refractionHeight = 12.dp,
                refractionAmount = 24.dp,
                surface = colors.container,
                exportedBackdrop = panelBackdrop,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            if (header != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HeaderHeight)
                        .clipToBounds()
                        .padding(start = IconStart),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    PrismText(
                        header,
                        modifier = Modifier.graphicsLayer { alpha = expandProgress },
                        color = colors.subText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clipToBounds()
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                if (selectedIndex >= 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .graphicsLayer { translationY = position * (RowHeight + RowGap).toPx() }
                            .background(accent.copy(alpha = colors.accentAlpha(0.18f)), rowShape),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val closeness = if (selectedIndex < 0) 0f else (1f - abs(position - index)).coerceIn(0f, 1f)
                        val row: @Composable () -> Unit = {
                            val press = rememberPressState()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(RowHeight)
                                    .focusRing(press, rowShape, ring)
                                    .clip(rowShape)
                                    .drawBehind {
                                        val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                                        if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a))
                                    }
                                    .pressInput(press)
                                    .selectable(
                                        selected = selected,
                                        interactionSource = press.interactionSource,
                                        indication = null,
                                        role = Role.Tab,
                                    ) {
                                        if (!selected) haptics.perform(HapticKind.Tick)
                                        onSelect(item.key)
                                    }
                                    .padding(start = IconStart),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PrismIcon(item.icon, null, size = 22.dp, tint = lerp(colors.subText, accent, closeness))
                                Spacer(Modifier.width(14.dp))
                                PrismText(
                                    item.label,
                                    modifier = Modifier.graphicsLayer { alpha = expandProgress },
                                    color = lerp(colors.subText, colors.text, closeness),
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        if (expanded) {
                            row()
                        } else {
                            TooltipBox(item.label, Modifier.fillMaxWidth()) { row() }
                        }
                    }
                }
            }
        }
        if (onToggleExpanded != null) {
            GlassIconButton(
                backdrop = panelBackdrop,
                icon = if (expanded) PrismIcons.ChevronLeft else PrismIcons.ChevronRight,
                contentDescription = if (expanded) "Collapse sidebar" else "Expand sidebar",
                onClick = onToggleExpanded,
                modifier = Modifier.padding(top = RowGap),
                variant = ButtonVariant.Ghost,
                size = 48.dp,
            )
        }
    }
}

/**
 * Modal navigation drawer: a scrim plus an expanded [GlassSidebar] sliding in from the start.
 * Swipe it toward the start edge, tap the scrim, or press Back to dismiss.
 */
@Composable
fun GlassDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    items: List<SidebarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    header: String? = null,
) {
    val motion = LocalMotion.current
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            drag = 0f
            composed = true
            // Reduced motion has no slide, so fade in instead of snapping.
            val spec: FiniteAnimationSpec<Float> = if (motion.reduced) motion.enter(240) else motion.settle()
            progress.animateTo(1f, spec)
        } else {
            progress.animateTo(0f, motion.exit(200))
            composed = false
        }
    }
    if (!composed) return

    Portal { backdrop ->
        val colors = Prism.colors
        var panelWidth by remember { mutableFloatStateOf(0f) }
        val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceAtMost(0f) }

        BackHandler(enabled = visible, onBack = onDismiss)

        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = visible,
                    onDragStopped = { velocity ->
                        if (-drag > 0.3f * panelWidth || velocity < -800f) {
                            onDismiss()
                        } else {
                            animate(drag, 0f, animationSpec = motion.release()) { value, _ -> drag = value }
                        }
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val follow = if (panelWidth > 0f) (1f + drag / panelWidth).coerceIn(0f, 1f) else 1f
                        alpha = progress.value * follow
                    }
                    .background(colors.scrim)
                    .then(
                        if (visible) {
                            Modifier.clickable(interactionSource = null, indication = null, onClick = onDismiss)
                        } else {
                            Modifier
                        },
                    ),
            )
            GlassSidebar(
                backdrop = backdrop,
                items = items,
                selectedKey = selectedKey,
                onSelect = { key ->
                    onSelect(key)
                    onDismiss()
                },
                expanded = true,
                modifier = Modifier
                    .graphicsLayer {
                        val t = progress.value
                        val slide = if (motion.reduced) 0f else 1f
                        translationX = -(size.width + 24.dp.toPx()) * (1f - t) * slide + drag
                        alpha = if (motion.reduced) t else 1f
                    }
                    .onSizeChanged { panelWidth = it.width.toFloat() }
                    // Taps on the panel's empty areas must not reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = header ?: "Navigation" }
                    .width(288.dp)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(12.dp),
                header = header,
                onToggleExpanded = null,
            )
        }
    }
}
