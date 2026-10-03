@file:OptIn(ExperimentalFoundationApi::class)

package com.dazaike.photovault.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

private val TileShape = RoundedRectangle(14.dp)

/**
 * Shared thumbnail grid used by the main vault, trash, and album-detail screens.
 * Tapping opens [onOpenItem] with the tapped item's id unless a selection is
 * already active, in which case tap/long-press both toggle selection. Outside selection,
 * long-press calls [onLongPressItem] when given (the photo menu), else starts a selection.
 *
 * Tiles keep per-frame work out of composition (selection scale/veil are read in the
 * graphics layer / draw phase) and decode through [VaultViewModel]'s bounded, cached pipeline.
 */
@Composable
fun PhotoGrid(
    items: List<VaultItemEntity>,
    viewModel: VaultViewModel,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    onLongPressItem: ((VaultItemEntity) -> Unit)? = null,
) {
    val inSelection = selectedIds.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        state = state,
    ) {
        items(items, key = { it.id }, contentType = { "media" }) { item ->
            PhotoTile(
                item = item,
                viewModel = viewModel,
                selected = item.id in selectedIds,
                inSelection = inSelection,
                onToggleSelect = onToggleSelect,
                onOpenItem = onOpenItem,
                onLongPressItem = onLongPressItem,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PhotoTile(
    item: VaultItemEntity,
    viewModel: VaultViewModel,
    selected: Boolean,
    inSelection: Boolean,
    onToggleSelect: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onLongPressItem: ((VaultItemEntity) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val accent = Prism.accent
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, motion.settle(), label = "tileScale")
    val veil by animateFloatAsState(if (selected) 1f else 0f, motion.fade(180), label = "tileVeil")
    val isVideo = item.mimeType.startsWith("video/")

    if (isVideo && item.durationMs == null) {
        LaunchedEffect(item.id) { viewModel.measureDuration(item) }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(TileShape)
            .combinedClickable(
                indication = null,
                interactionSource = null,
                onClick = {
                    if (inSelection) {
                        haptics.perform(HapticKind.Tick)
                        onToggleSelect(item.id)
                    } else {
                        haptics.perform(HapticKind.Press)
                        onOpenItem(item.id)
                    }
                },
                onLongClick = {
                    haptics.perform(HapticKind.LongPress)
                    if (!inSelection && onLongPressItem != null) onLongPressItem(item) else onToggleSelect(item.id)
                },
            ),
    ) {
        VaultImage(
            file = remember(item.id) { viewModel.thumbFile(item) },
            maxDimensionPx = 256,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (veil > 0f) drawRect(accent.copy(alpha = 0.28f * veil))
                },
        )
        if (isVideo) {
            DurationBadge(
                durationMs = item.durationMs,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
        if (inSelection) {
            SelectionIndicator(
                selected = selected,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

/** Small play glyph + `m:ss`; shows only the glyph until the duration is known (or if it can't be read). */
@Composable
private fun DurationBadge(durationMs: Long?, modifier: Modifier = Modifier) {
    val known = durationMs != null && durationMs > 0
    Row(
        modifier = modifier
            .clip(Capsule())
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = if (known) 7.dp else 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismIcon(PrismIcons.Play, null, size = 11.dp, tint = Color.White)
        if (known) {
            PrismText(
                text = formatDuration(durationMs!!),
                modifier = Modifier.padding(start = 3.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier = modifier.size(24.dp).clip(CircleShape).background(Prism.accent),
            contentAlignment = Alignment.Center,
        ) {
            PrismIcon(PrismIcons.Check, "Selected", size = 16.dp, tint = Prism.onAccent)
        }
    } else {
        Box(
            modifier = modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.75.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        )
    }
}

fun toggle(set: Set<String>, id: String): Set<String> =
    if (id in set) set - id else set + id
