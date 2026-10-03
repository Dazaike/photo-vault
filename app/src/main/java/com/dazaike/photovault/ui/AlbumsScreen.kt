package com.dazaike.photovault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.AlbumEntity
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.shapes.RoundedRectangle
import kotlin.math.max

@Composable
fun AlbumsScreen(viewModel: VaultViewModel, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val albums by viewModel.albums.collectAsState()
    val backdrop = LocalPageBackdrop.current
    val haptics = LocalHaptics.current
    var showCreateDialog by remember { mutableStateOf(false) }
    // The album stays assigned after the dialog closes so the dialog text does not change during its exit fade.
    var pendingAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var showDeleteAlbum by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(
                title = "Albums",
                navIcon = PrismIcons.ArrowLeft,
                navLabel = "Back",
                onNav = onBack,
            ) {
                TooltipBox("New album") {
                    GlassIconButton(backdrop, PrismIcons.FolderPlus, "New album", { showCreateDialog = true })
                }
            }
            if (albums.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrismIcon(PrismIcons.Folder, null, size = 32.dp, tint = Prism.subText)
                    Spacer(Modifier.height(12.dp))
                    PrismText("No albums yet", fontSize = 16.sp, color = Prism.subText)
                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        backdrop,
                        "Create album",
                        { showCreateDialog = true },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        AlbumRow(
                            album = album,
                            viewModel = viewModel,
                            onOpen = { onOpenAlbum(album.id) },
                            onDelete = {
                                pendingAlbum = album
                                showDeleteAlbum = true
                            },
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().imePadding()) {
            SheetOverlay(showCreateDialog, { showCreateDialog = false }, 0.5f) { surface ->
                val name = rememberTextFieldState()
                val canCreate = name.text.isNotBlank()
                fun create() {
                    if (name.text.isNotBlank()) {
                        viewModel.createAlbum(name.text.toString().trim())
                        haptics.perform(HapticKind.Success)
                        showCreateDialog = false
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PrismText("New album", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    GlassTextField(
                        state = name,
                        label = "Album name",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onSubmit = { create() },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        GlassButton(surface, "Cancel", { showCreateDialog = false })
                        GlassButton(
                            surface,
                            "Create",
                            { create() },
                            variant = ButtonVariant.Primary,
                            enabled = canCreate,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    ConfirmDialog(
        visible = showDeleteAlbum,
        title = "Delete album \"${pendingAlbum?.name.orEmpty()}\"?",
        message = "Photos stay in your vault; only the album is removed.",
        confirmLabel = "Delete",
        onConfirm = {
            pendingAlbum?.let { viewModel.deleteAlbum(it) }
            showDeleteAlbum = false
        },
        onDismiss = { showDeleteAlbum = false },
        destructive = true,
    )
}

@Composable
private fun AlbumRow(
    album: AlbumEntity,
    viewModel: VaultViewModel,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val backdrop = LocalPageBackdrop.current
    val itemsFlow = remember(album.id) { viewModel.itemsInAlbum(album.id) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val cover = items.firstOrNull()
    val count = items.size

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultGhostRow(onClick = onOpen, modifier = Modifier.weight(1f), minHeight = 72.dp) {
            Box(
                Modifier.size(56.dp).clip(RoundedRectangle(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    VaultImage(
                        file = remember(cover.id) { viewModel.thumbFile(cover) },
                        maxDimensionPx = 192,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Prism.colors.fillWeak))
                    PrismIcon(PrismIcons.Folder, null, size = 24.dp, tint = Prism.subText)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                PrismText(
                    album.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PrismText(
                    if (count == 1) "1 item" else "$count items",
                    fontSize = 13.sp,
                    color = Prism.subText,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        TooltipBox("Delete album") {
            GlassIconButton(
                backdrop,
                PrismIcons.Trash,
                "Delete album",
                onDelete,
                variant = ButtonVariant.Ghost,
                size = 44.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ---- Shared screen chrome for the album / trash screens ------------------------------------

/** Page top bar: glass back/close button, title, then [actions] (glass icon buttons) at the end. */
@Composable
internal fun CollectionTopBar(
    title: String,
    navIcon: Int,
    navLabel: String,
    onNav: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backdrop = LocalPageBackdrop.current
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TooltipBox(navLabel) { GlassIconButton(backdrop, navIcon, navLabel, onNav) }
        PrismText(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Bottom action strip shown while a selection is active. Slides up like a sheet; fades under reduced motion. */
@Composable
internal fun SelectionActionBar(visible: Boolean, content: @Composable RowScope.() -> Unit) {
    val motion = LocalMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (motion.reduced) fadeIn(motion.fade(200)) else slideInVertically(motion.settle()) { it },
        exit = if (motion.reduced) fadeOut(motion.fade(160)) else slideOutVertically(motion.exit(180)) { it },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

/** One glass icon button with a caption beneath it, for [SelectionActionBar]. */
@Composable
internal fun SelectionBarItem(
    icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Secondary,
) {
    val backdrop = LocalPageBackdrop.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TooltipBox(description) { GlassIconButton(backdrop, icon, description, onClick, variant = variant) }
        Spacer(Modifier.height(4.dp))
        PrismText(label, fontSize = 12.sp, color = Prism.subText, maxLines = 1)
    }
}

/** Flat pressable row (no glass): press/hover fill, focus ring, no ripple. */
@Composable
internal fun VaultGhostRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPressState()
    val colors = Prism.colors
    val haptics = LocalHaptics.current
    val shape = remember { RoundedRectangle(14.dp) }
    Row(
        modifier
            .focusRing(press, shape, Prism.accent.copy(alpha = 0.85f))
            .heightIn(min = minHeight)
            .clip(shape)
            .drawBehind {
                val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a.coerceAtMost(1f)))
            }
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
