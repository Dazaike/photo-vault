package com.dazaike.photovault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.Prism
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhotoViewerScreen(
    viewModel: VaultViewModel,
    items: List<VaultItemEntity>,
    targetItemId: String? = null,
    startIndex: Int = 0,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isSlideshowActive by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val bgColor by animateColorAsState(
        targetValue = if (showControls) Prism.colors.background else Color.Black,
        animationSpec = tween(240),
        label = "bgColor",
    )
    val toasts = LocalToasts.current
    val backdrop = LocalPageBackdrop.current

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )
        return
    }

    val initialIndex = remember(items, targetItemId) {
        if (targetItemId != null) {
            val idx = items.indexOfFirst { it.id == targetItemId }
            if (idx >= 0) idx else startIndex.coerceIn(0, items.size - 1)
        } else {
            startIndex.coerceIn(0, items.size - 1)
        }
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

    fun currentItem() = items[pagerState.currentPage.coerceIn(0, items.size - 1)]

    // Reset zoom state on page change
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    // Slideshow automation loop
    LaunchedEffect(isSlideshowActive, items.size) {
        if (isSlideshowActive && items.size > 1) {
            showControls = false
            while (isSlideshowActive) {
                delay(3000L)
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.mimeType.startsWith("video/")) {
                VaultVideoPlayer(
                    item = item,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ZoomableVaultImage(
                    file = viewModel.originalFile(item),
                    maxDimensionPx = screenWidthPx,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = item.originalName,
                    onTap = {
                        if (isSlideshowActive) {
                            isSlideshowActive = false
                            showControls = true
                        } else {
                            showControls = !showControls
                        }
                    },
                    onZoomChanged = { zoomed ->
                        isZoomed = zoomed
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(220),
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200),
            ) + fadeOut(animationSpec = tween(200)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TooltipBox("Back") {
                    GlassIconButton(backdrop, PrismIcons.ArrowLeft, "Back", {
                        if (isSlideshowActive) isSlideshowActive = false
                        onBack()
                    }, size = ViewerButtonSize)
                }
                Spacer(Modifier.weight(1f))
                val slideshowLabel = if (isSlideshowActive) "Pause slideshow" else "Play slideshow"
                TooltipBox(slideshowLabel) {
                    GlassIconButton(
                        backdrop,
                        if (isSlideshowActive) PrismIcons.Pause else PrismIcons.Play,
                        slideshowLabel,
                        {
                            isSlideshowActive = !isSlideshowActive
                            if (isSlideshowActive) showControls = false
                        },
                        size = ViewerButtonSize,
                    )
                }
                TooltipBox("Item details") {
                    GlassIconButton(backdrop, PrismIcons.Info, "Item details", { showInfoDialog = true }, size = ViewerButtonSize)
                }
                TooltipBox("Copy to clipboard") {
                    GlassIconButton(backdrop, PrismIcons.Copy, "Copy to clipboard", {
                        val item = currentItem()
                        scope.launch {
                            val uri = viewModel.prepareShareUris(listOf(item)).first()
                            copyUriToClipboard(context, uri, item.originalName, item.mimeType)
                            toasts.show("Copied to clipboard", ToastKind.Success)
                        }
                    }, size = ViewerButtonSize)
                }
                TooltipBox("Share") {
                    GlassIconButton(backdrop, PrismIcons.Share, "Share", {
                        val item = currentItem()
                        scope.launch {
                            val uri = viewModel.prepareShareUris(listOf(item)).first()
                            shareUris(context, listOf(uri), item.mimeType)
                        }
                    }, size = ViewerButtonSize)
                }
                TooltipBox("Save to device storage") {
                    GlassIconButton(
                        backdrop,
                        PrismIcons.Download,
                        "Save to device storage",
                        { showDownloadDialog = true },
                        size = ViewerButtonSize,
                    )
                }
                TooltipBox("Move to trash") {
                    GlassIconButton(
                        backdrop,
                        PrismIcons.Trash,
                        "Move to trash",
                        { showDeleteConfirm = true },
                        variant = ButtonVariant.Destructive,
                        size = ViewerButtonSize,
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        val current = currentItem()
        MediaInfoDialog(
            item = current,
            file = viewModel.originalFile(current),
            onDismiss = { showInfoDialog = false },
        )
    }

    ConfirmDialog(
        visible = showDeleteConfirm,
        title = "Move this photo to trash?",
        message = "Items stay in trash for 30 days before being permanently deleted.",
        confirmLabel = "Move to trash",
        onConfirm = {
            val target = currentItem()
            val wasLast = items.size == 1
            showDeleteConfirm = false
            viewModel.moveToTrash(listOf(target))
            if (wasLast) onBack()
        },
        onDismiss = { showDeleteConfirm = false },
        destructive = true,
    )
    if (showDownloadDialog) {
        val item = currentItem()
        DownloadDialog(
            items = listOf(item),
            onDismiss = { showDownloadDialog = false },
            onConfirm = { delaySeconds, targetTreeUri, updateTimestamp ->
                viewModel.downloadItems(
                    context = context,
                    items = listOf(item),
                    delaySeconds = delaySeconds,
                    targetTreeUri = targetTreeUri,
                    updateTimestamp = updateTimestamp,
                ).isNotEmpty()
            },
        )
    }
}

private val ViewerButtonSize = 42.dp
