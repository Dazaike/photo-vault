package com.dazaike.photovault.ui

import com.dazaike.photovault.data.VaultItemEntity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.shapes.RoundedRectangle
import kotlin.math.max
import kotlinx.coroutines.launch

@Composable
fun AlbumDetailScreen(
    viewModel: VaultViewModel,
    albumId: String,
    onBack: () -> Unit,
    onOpenViewer: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = LocalPageBackdrop.current
    val toasts = LocalToasts.current
    val haptics = LocalHaptics.current
    val items by viewModel.itemsInAlbum(albumId).collectAsState(initial = emptyList())
    val albums by viewModel.albums.collectAsState()
    val currentAlbum = albums.firstOrNull { it.id == albumId }
    val albumName = currentAlbum?.name ?: "Folder"
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Captured when the dialog opens so its title does not change while it fades out.
    var deleteCount by remember { mutableStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDeviceGallery by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    val galleryItems by viewModel.galleryItems.collectAsState()

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris, albumId)
    }
    val photosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris, albumId)
    }

    fun launchFiles() = filesLauncher.launch(arrayOf("image/*", "video/*"))
    fun launchPhotos() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            photosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        } else {
            launchFiles()
        }
    }
    fun launchDeviceGallery() {
        viewModel.loadGalleryItems()
        showDeviceGallery = true
    }
    fun copySelected() {
        val selected = items.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        scope.launch {
            val uris = viewModel.prepareShareUris(selected)
            val mimeType = commonMimeType(selected.map { it.mimeType })
            val label = if (selected.size == 1) selected.first().originalName else "${selected.size} items"
            copyUrisToClipboard(context, uris, label, mimeType)
            val message = if (selected.size == 1) "Copied to clipboard" else "Copied ${selected.size} items to clipboard"
            selectedIds = emptySet()
            toasts.show(message, ToastKind.Success)
        }
    }

    fun shareSelected() {
        val selected = items.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        scope.launch {
            val uris = viewModel.prepareShareUris(selected)
            shareUris(context, uris, commonMimeType(selected.map { it.mimeType }))
        }
    }

    val isSelecting = selectedIds.isNotEmpty()
    val allSelected = items.isNotEmpty() && selectedIds.size == items.size

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(
                title = if (isSelecting) "${selectedIds.size} selected" else albumName,
                navIcon = if (isSelecting) PrismIcons.Close else PrismIcons.ArrowLeft,
                navLabel = if (isSelecting) "Clear selection" else "Back",
                onNav = { if (isSelecting) selectedIds = emptySet() else onBack() },
            ) {
                if (isSelecting) {
                    val label = if (allSelected) "Deselect all" else "Select all"
                    TooltipBox(label) {
                        GlassIconButton(
                            backdrop,
                            if (allSelected) PrismIcons.Close else PrismIcons.SelectAll,
                            label,
                            { selectedIds = if (allSelected) emptySet() else items.map { it.id }.toSet() },
                        )
                    }
                } else {
                    TooltipBox("Add photos to folder") {
                        GlassIconButton(backdrop, PrismIcons.Plus, "Add photos to folder", { showImportDialog = true })
                    }
                    TooltipBox("More") {
                        GlassIconButton(backdrop, PrismIcons.More, "More", { showOverflowMenu = true })
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrismIcon(PrismIcons.Photo, null, size = 32.dp, tint = Prism.subText)
                        Spacer(Modifier.height(12.dp))
                        PrismText(
                            "No photos in this folder yet",
                            fontSize = 16.sp,
                            color = Prism.subText,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        GlassButton(
                            backdrop,
                            "Add photos",
                            { showImportDialog = true },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                        )
                    }
                } else {
                    PhotoGrid(
                        items = items,
                        viewModel = viewModel,
                        selectedIds = selectedIds,
                        onToggleSelect = { id -> selectedIds = toggle(selectedIds, id) },
                        onOpenItem = onOpenViewer,
                        onLongPressItem = { menuItem = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            SelectionActionBar(visible = isSelecting) {
                SelectionBarItem(PrismIcons.Copy, "Copy", "Copy to clipboard", { copySelected() })
                SelectionBarItem(PrismIcons.Share, "Share", "Share", { shareSelected() })
                SelectionBarItem(PrismIcons.Download, "Save", "Save to storage", { showDownloadDialog = true })
                SelectionBarItem(
                    PrismIcons.FolderMinus,
                    "Remove",
                    "Remove from album",
                    {
                        selectedIds.forEach { viewModel.removeFromAlbum(albumId, it) }
                        selectedIds = emptySet()
                    },
                )
                SelectionBarItem(
                    PrismIcons.Trash,
                    "Trash",
                    "Move to trash",
                    {
                        deleteCount = selectedIds.size
                        showDeleteConfirm = true
                    },
                )
            }
        }

        PhotoContextMenu(
            item = menuItem,
            viewModel = viewModel,
            actions = menuItem?.let { held ->
                listOf(
                    PhotoMenuAction(PrismIcons.SelectAll, "Select") { selectedIds = setOf(held.id) },
                    PhotoMenuAction(PrismIcons.Photo, "Open") { onOpenViewer(held.id) },
                    PhotoMenuAction(PrismIcons.Copy, "Copy") { selectedIds = setOf(held.id); copySelected() },
                    PhotoMenuAction(PrismIcons.Share, "Share") {
                        selectedIds = setOf(held.id)
                        shareSelected()
                        selectedIds = emptySet()
                    },
                    PhotoMenuAction(PrismIcons.Download, "Save to storage") {
                        selectedIds = setOf(held.id)
                        showDownloadDialog = true
                    },
                    PhotoMenuAction(PrismIcons.FolderMinus, "Remove from folder") {
                        viewModel.removeFromAlbum(albumId, held.id)
                    },
                    PhotoMenuAction(PrismIcons.Trash, "Move to trash", destructive = true) {
                        selectedIds = setOf(held.id)
                        deleteCount = 1
                        showDeleteConfirm = true
                    },
                )
            }.orEmpty(),
            onDismiss = { menuItem = null },
        )

        Box(Modifier.fillMaxSize().imePadding()) {
            // Overflow menu
            SheetOverlay(showOverflowMenu, { showOverflowMenu = false }, 0.4f) { _ ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PrismText(
                        albumName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (items.isNotEmpty()) {
                        MenuRow(PrismIcons.SelectAll, "Select all") {
                            showOverflowMenu = false
                            selectedIds = items.map { it.id }.toSet()
                        }
                    }
                    MenuRow(PrismIcons.Text, "Rename folder") {
                        showOverflowMenu = false
                        showRenameDialog = true
                    }
                    MenuRow(PrismIcons.Trash, "Delete folder", tint = Prism.error) {
                        showOverflowMenu = false
                        showDeleteFolderDialog = true
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Add photos: choose a source
            SheetOverlay(showImportDialog, { showImportDialog = false }, 0.5f) { surface ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PrismText(
                        "Add to $albumName",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    PrismText("Choose where to import photos from.", fontSize = 14.sp, color = Prism.subText)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Device gallery", "Google Photos", "Files").forEach { source ->
                            SourceTile(
                                label = source,
                                icon = if (source == "Files") PrismIcons.Folder else PrismIcons.Photo,
                                modifier = Modifier.weight(1f),
                            ) {
                                showImportDialog = false
                                when (source) {
                                    "Device gallery" -> launchDeviceGallery()
                                    "Google Photos" -> launchPhotos()
                                    else -> launchFiles()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrismText("Google Photos imports are copied only.", fontSize = 13.sp, color = Prism.subText)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        GlassButton(surface, "Cancel", { showImportDialog = false })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Rename folder
            SheetOverlay(showRenameDialog, { showRenameDialog = false }, 0.5f) { surface ->
                val newName = rememberTextFieldState(albumName)
                val canRename = newName.text.isNotBlank()
                fun rename() {
                    if (newName.text.isNotBlank()) {
                        viewModel.renameAlbum(albumId, newName.text.toString().trim())
                        haptics.perform(HapticKind.Success)
                        showRenameDialog = false
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PrismText("Rename folder", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    GlassTextField(
                        state = newName,
                        label = "Folder name",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onSubmit = { rename() },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        GlassButton(surface, "Cancel", { showRenameDialog = false })
                        GlassButton(
                            surface,
                            "Rename",
                            { rename() },
                            variant = ButtonVariant.Primary,
                            enabled = canRename,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    ConfirmDialog(
        visible = showDeleteConfirm,
        title = "Move $deleteCount photo(s) to trash?",
        message = "Items stay in trash for 30 days before being permanently deleted.",
        confirmLabel = "Move to trash",
        onConfirm = {
            viewModel.moveToTrash(items.filter { it.id in selectedIds })
            selectedIds = emptySet()
            showDeleteConfirm = false
        },
        onDismiss = { showDeleteConfirm = false },
    )

    ConfirmDialog(
        visible = showDeleteFolderDialog,
        title = "Delete \"$albumName\" folder?",
        message = "Photos inside this folder will remain in your vault and move to unsorted.",
        confirmLabel = "Delete folder",
        onConfirm = {
            showDeleteFolderDialog = false
            if (currentAlbum != null) {
                viewModel.deleteAlbum(currentAlbum)
            }
            onBack()
        },
        onDismiss = { showDeleteFolderDialog = false },
        destructive = true,
    )

    if (showDeviceGallery) {
        GalleryPickerDialog(
            items = galleryItems,
            viewModel = viewModel,
            onDismiss = { showDeviceGallery = false },
            onImport = { uris ->
                showDeviceGallery = false
                viewModel.importUris(uris, albumId)
            },
        )
    }

    if (showDownloadDialog) {
        val selected = items.filter { it.id in selectedIds }
        DownloadDialog(
            items = selected,
            onDismiss = {
                showDownloadDialog = false
                selectedIds = emptySet()
            },
            onConfirm = { delaySeconds, targetTreeUri, updateTimestamp ->
                viewModel.downloadItems(
                    context = context,
                    items = selected,
                    delaySeconds = delaySeconds,
                    targetTreeUri = targetTreeUri,
                    updateTimestamp = updateTimestamp,
                ).isNotEmpty()
            },
        )
    }
}

/** Action row inside the overflow sheet: icon + label on a flat pressable row. */
@Composable
private fun MenuRow(
    icon: Int,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    VaultGhostRow(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        val color = if (tint == Color.Unspecified) Prism.colors.text else tint
        PrismIcon(icon, null, size = 22.dp, tint = color)
        Spacer(Modifier.width(14.dp))
        PrismText(label, fontSize = 16.sp, color = color)
    }
}

/** Flat pressable import-source tile (not glass): weak fill that lifts on press. */
@Composable
private fun SourceTile(
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = rememberPressState()
    val colors = Prism.colors
    val haptics = LocalHaptics.current
    val shape = remember { RoundedRectangle(16.dp) }
    val layer = rememberPressLayer(press)
    Column(
        modifier
            .focusRing(press, shape, Prism.accent.copy(alpha = 0.85f))
            .graphicsLayer(layer)
            .heightIn(min = 96.dp)
            .clip(shape)
            .drawBehind {
                drawRect(colors.fillWeak)
                val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a.coerceAtMost(1f)))
            }
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PrismIcon(icon, null, size = 24.dp, tint = Prism.accent)
        Spacer(Modifier.height(6.dp))
        PrismText(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}
