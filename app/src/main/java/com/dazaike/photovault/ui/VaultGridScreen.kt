package com.dazaike.photovault.ui

import com.dazaike.photovault.data.VaultItemEntity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.kyant.shapes.Capsule
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.AlbumEntity
import com.dazaike.photovault.data.GalleryMediaItem
import com.dazaike.photovault.lock.LockState
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.max

private val TileShape = RoundedRectangle(14.dp)
private val CardShape = RoundedRectangle(16.dp)
private val RowShape = RoundedRectangle(14.dp)

@Composable
fun VaultGridScreen(
    viewModel: VaultViewModel,
    onOpenViewer: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val context = LocalContext.current
    val allItems by viewModel.items.collectAsState()
    val unfiledItems by viewModel.unfiledItems.collectAsState()
    val albums by viewModel.albums.collectAsState()

    var showAllPhotos by remember { mutableStateOf(false) }
    val displayedItems = if (showAllPhotos || albums.isEmpty()) allItems else unfiledItems
    var showImportDialog by remember { mutableStateOf(false) }
    var showGalleryPicker by remember { mutableStateOf(false) }
    var deleteOriginalsAfterImport by remember { mutableStateOf(true) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteCount by remember { mutableStateOf(0) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showAppearance by remember { mutableStateOf(false) }
    val galleryItems by viewModel.galleryItems.collectAsState()
    val toasts = LocalToasts.current
    val backdrop = LocalPageBackdrop.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()

    fun openDeviceGallery() {
        viewModel.loadGalleryItems()
        showGalleryPicker = true
    }

    val manageFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Environment.isExternalStorageManager()) {
            openDeviceGallery()
        } else {
            toasts.show(
                "All-files access is required to delete device-gallery originals automatically",
                ToastKind.Error,
            )
        }
    }

    fun launchDeviceGallery() {
        if (!deleteOriginalsAfterImport || Environment.isExternalStorageManager()) {
            openDeviceGallery()
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            manageFilesAccessLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            toasts.show("Automatic deletion requires Android 11 or later", ToastKind.Error)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importErrors.collect { message -> toasts.show(message, ToastKind.Error) }
    }
    LaunchedEffect(Unit) {
        viewModel.sourceUrisReadyForDeletion.collect { uris ->
            val deleted = viewModel.deleteSourceUris(uris)
            val retained = uris.size - deleted
            val message = when {
                retained == 0 -> "Deleted $deleted original(s)"
                deleted == 0 -> "Couldn't delete imported originals"
                else -> "Deleted $deleted original(s); $retained were kept"
            }
            toasts.show(
                message,
                when {
                    retained == 0 -> ToastKind.Success
                    deleted == 0 -> ToastKind.Error
                    else -> ToastKind.Info
                },
            )
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val uris = buildList {
            data?.data?.let(::add)
            data?.clipData?.let { clipData ->
                repeat(clipData.itemCount) { index -> add(clipData.getItemAt(index).uri) }
            }
        }.distinct()
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, deleteOriginalsAfterImport = deleteOriginalsAfterImport)
        }
    }

    fun launchFiles() {
        filesLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                ),
        )
    }
    fun launchGooglePhotos() {
        photoPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
            ),
        )
    }

    fun copySelected() {
        val selected = allItems.filter { it.id in selectedIds }
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
        val selected = allItems.filter { it.id in selectedIds }
        scope.launch {
            val uris = viewModel.prepareShareUris(selected)
            shareUris(context, uris, commonMimeType(selected.map { it.mimeType }))
        }
    }

    val isSelecting = selectedIds.isNotEmpty()
    val allSelected = displayedItems.isNotEmpty() && selectedIds.size == displayedItems.size

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            VaultTopBar(
                backdrop = backdrop,
                selecting = isSelecting,
                count = selectedIds.size,
                allSelected = allSelected,
                onClearSelection = { selectedIds = emptySet() },
                onToggleSelectAll = {
                    selectedIds = if (allSelected) emptySet() else displayedItems.map { it.id }.toSet()
                },
                onNewFolder = { showCreateFolderDialog = true },
                onAddPhotos = { showImportDialog = true },
                onLock = { LockState.isLocked.value = true },
                onMore = { showOverflowMenu = true },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (allItems.isEmpty() && albums.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrismIcon(PrismIcons.Folder, null, size = 64.dp, tint = Prism.subText)
                        Spacer(Modifier.height(16.dp))
                        PrismText("Photo Vault is empty", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                backdrop,
                                "New folder",
                                { showCreateFolderDialog = true },
                                leadingIcon = PrismIcons.FolderPlus,
                            )
                            GlassButton(
                                backdrop,
                                "Add photos",
                                { showImportDialog = true },
                                variant = ButtonVariant.Primary,
                                leadingIcon = PrismIcons.Plus,
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Folders section
                        if (albums.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PrismIcon(PrismIcons.Folder, null, size = 18.dp, tint = Prism.subText)
                                Spacer(Modifier.width(8.dp))
                                PrismText(
                                    "Folders (${albums.size})",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                GlassButton(
                                    backdrop,
                                    "New",
                                    { showCreateFolderDialog = true },
                                    variant = ButtonVariant.Ghost,
                                    size = ButtonSize.Small,
                                    leadingIcon = PrismIcons.Plus,
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    FolderCard(
                                        album = album,
                                        viewModel = viewModel,
                                        onClick = { onOpenAlbum(album.id) },
                                    )
                                }
                                item {
                                    NewFolderCard(onClick = { showCreateFolderDialog = true })
                                }
                            }
                        }

                        // Photos section header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                PrismIcon(PrismIcons.Photo, null, size = 18.dp, tint = Prism.subText)
                                Spacer(Modifier.width(8.dp))
                                PrismText(
                                    if (showAllPhotos || albums.isEmpty()) "All Photos (${allItems.size})" else "Unsorted Photos (${unfiledItems.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (albums.isNotEmpty()) {
                                GlassButton(
                                    backdrop,
                                    if (showAllPhotos) "Showing All" else "Show All",
                                    { showAllPhotos = !showAllPhotos },
                                    variant = if (showAllPhotos) ButtonVariant.Primary else ButtonVariant.Outlined,
                                    size = ButtonSize.Small,
                                )
                            }
                        }

                        // Photo grid / empty state
                        if (displayedItems.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (!showAllPhotos && unfiledItems.isEmpty() && albums.isNotEmpty()) {
                                    PrismIcon(PrismIcons.Folder, null, size = 32.dp, tint = Prism.subText)
                                    Spacer(Modifier.height(12.dp))
                                    PrismText(
                                        "All photos are organized into folders.",
                                        color = Prism.subText,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    GlassButton(
                                        backdrop,
                                        "View All Photos (${allItems.size})",
                                        { showAllPhotos = true },
                                        variant = ButtonVariant.Outlined,
                                        size = ButtonSize.Small,
                                    )
                                } else {
                                    PrismIcon(PrismIcons.Photo, null, size = 32.dp, tint = Prism.subText)
                                    Spacer(Modifier.height(12.dp))
                                    PrismText("No photos yet", color = Prism.subText, fontSize = 15.sp)
                                    Spacer(Modifier.height(16.dp))
                                    GlassButton(
                                        backdrop,
                                        "Add photos",
                                        { showImportDialog = true },
                                        variant = ButtonVariant.Primary,
                                        size = ButtonSize.Small,
                                        leadingIcon = PrismIcons.Plus,
                                    )
                                }
                            }
                        } else {
                            PhotoGrid(
                                items = displayedItems,
                                viewModel = viewModel,
                                selectedIds = selectedIds,
                                onToggleSelect = { id -> selectedIds = toggle(selectedIds, id) },
                                onOpenItem = onOpenViewer,
                                onLongPressItem = { menuItem = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSelecting,
                enter = expandVertically(motion.settle()) + fadeIn(motion.fade(200)),
                exit = shrinkVertically(motion.exit(180)) + fadeOut(motion.fade(150)),
            ) {
                SelectionActionBar(
                    backdrop = backdrop,
                    onCopy = { copySelected() },
                    onShare = { shareSelected() },
                    onSave = { showDownloadDialog = true },
                    onMove = { showAlbumPicker = true },
                    onTrash = {
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
                    PhotoMenuAction(PrismIcons.Folder, "Move to folder") {
                        selectedIds = setOf(held.id)
                        showAlbumPicker = true
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

        AppearanceSheet(showAppearance) { showAppearance = false }

        // Overflow menu
        SheetOverlay(showOverflowMenu, { showOverflowMenu = false }, heightFraction = 0.54f) { _ ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PrismText("More", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                if (displayedItems.isNotEmpty()) {
                    GhostRow(PrismIcons.SelectAll, "Select all", {
                        showOverflowMenu = false
                        selectedIds = displayedItems.map { it.id }.toSet()
                    })
                }
                GhostRow(PrismIcons.FolderPlus, "New folder", {
                    showOverflowMenu = false
                    showCreateFolderDialog = true
                })
                GhostRow(PrismIcons.Folder, "Albums / Folders", { showOverflowMenu = false; onOpenAlbums() })
                GhostRow(PrismIcons.Trash, "Trash", { showOverflowMenu = false; onOpenTrash() })
                GhostRow(PrismIcons.Sliders, "Settings", { showOverflowMenu = false; showAppearance = true })
            }
        }

        // Import source chooser
        SheetOverlay(showImportDialog, { showImportDialog = false }, heightFraction = 0.58f) { surface ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PrismText("Add photos", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                PrismText("Choose where to import photos from.", color = Prism.subText, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportSourceTile(PrismIcons.Photo, "Device gallery", Modifier.weight(1f)) {
                        showImportDialog = false
                        launchDeviceGallery()
                    }
                    ImportSourceTile(PrismIcons.Photo, "Google Photos", Modifier.weight(1f)) {
                        showImportDialog = false
                        launchGooglePhotos()
                    }
                    ImportSourceTile(PrismIcons.Folder, "Files", Modifier.weight(1f)) {
                        showImportDialog = false
                        launchFiles()
                    }
                }
                Spacer(Modifier.height(16.dp))
                GlassCheckbox(
                    deleteOriginalsAfterImport,
                    { deleteOriginalsAfterImport = it },
                    Modifier.fillMaxWidth(),
                    label = "Delete originals automatically",
                )
                PrismText(
                    "Applies to Device Gallery and Files. Google Photos imports are copied only.",
                    color = Prism.subText,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassButton(surface, "Cancel", { showImportDialog = false })
                }
            }
        }

        CreateFolderSheet(
            visible = showCreateFolderDialog,
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                viewModel.createAlbum(name)
                showCreateFolderDialog = false
            },
        )

        AlbumPickerSheet(
            visible = showAlbumPicker,
            albums = albums,
            onDismiss = { showAlbumPicker = false },
            onPickAlbum = { albumId ->
                val targetName = albums.firstOrNull { it.id == albumId }?.name ?: "folder"
                val count = selectedIds.size
                viewModel.moveToAlbum(albumId, selectedIds.toList())
                selectedIds = emptySet()
                showAlbumPicker = false
                toasts.show("Moved $count photo(s) to $targetName", ToastKind.Success)
            },
            onCreateAlbum = { name ->
                val count = selectedIds.size
                viewModel.createAlbum(name, selectedIds.toList())
                selectedIds = emptySet()
                showAlbumPicker = false
                toasts.show("Moved $count photo(s) to $name", ToastKind.Success)
            },
        )

        if (showDownloadDialog) {
            val selected = allItems.filter { it.id in selectedIds }
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

    if (showGalleryPicker) {
        GalleryPickerDialog(
            items = galleryItems,
            viewModel = viewModel,
            onDismiss = { showGalleryPicker = false },
            onImport = { uris ->
                showGalleryPicker = false
                viewModel.importUris(
                    uris = uris,
                    deleteOriginalsAfterImport = deleteOriginalsAfterImport,
                )
            },
        )
    }

    ConfirmDialog(
        visible = showDeleteConfirm,
        title = "Move $deleteCount photo(s) to trash?",
        message = "Items stay in trash for 30 days before being permanently deleted.",
        confirmLabel = "Move to trash",
        onConfirm = {
            viewModel.moveToTrash(allItems.filter { it.id in selectedIds })
            selectedIds = emptySet()
            showDeleteConfirm = false
        },
        onDismiss = { showDeleteConfirm = false },
        destructive = true,
    )
}

@Composable
private fun VaultTopBar(
    backdrop: Backdrop,
    selecting: Boolean,
    count: Int,
    allSelected: Boolean,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onNewFolder: () -> Unit,
    onAddPhotos: () -> Unit,
    onLock: () -> Unit,
    onMore: () -> Unit,
) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = selecting,
        transitionSpec = { fadeIn(motion.fade(220)) togetherWith fadeOut(motion.fade(120)) },
        label = "vaultTopBar",
    ) { isSelecting ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelecting) {
                TopBarIcon(backdrop, PrismIcons.Close, "Clear selection", onClearSelection)
                PrismText(
                    "$count selected",
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TopBarIcon(
                    backdrop,
                    if (allSelected) PrismIcons.Minus else PrismIcons.SelectAll,
                    if (allSelected) "Deselect all" else "Select all",
                    onToggleSelectAll,
                )
            } else {
                PrismText(
                    "Photo Vault",
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TopBarIcon(backdrop, PrismIcons.FolderPlus, "New folder", onNewFolder)
                TopBarIcon(backdrop, PrismIcons.Plus, "Add photos", onAddPhotos, ButtonVariant.Primary)
                TopBarIcon(backdrop, PrismIcons.Lock, "Lock vault", onLock)
                TopBarIcon(backdrop, PrismIcons.More, "More", onMore)
            }
        }
    }
}

@Composable
private fun TopBarIcon(
    backdrop: Backdrop,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Secondary,
) {
    TooltipBox(label) {
        GlassIconButton(backdrop, icon, label, onClick, variant = variant, size = 44.dp)
    }
}

@Composable
private fun SelectionActionBar(
    backdrop: Backdrop,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        SelectionAction(backdrop, PrismIcons.Copy, "Copy to clipboard", "Copy", onCopy)
        SelectionAction(backdrop, PrismIcons.Share, "Share", "Share", onShare)
        SelectionAction(backdrop, PrismIcons.Download, "Save to storage", "Save", onSave)
        SelectionAction(backdrop, PrismIcons.Folder, "Move to folder", "Folder", onMove)
        SelectionAction(backdrop, PrismIcons.Trash, "Move to trash", "Trash", onTrash, ButtonVariant.Destructive)
    }
}

@Composable
private fun SelectionAction(
    backdrop: Backdrop,
    @DrawableRes icon: Int,
    description: String,
    label: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Secondary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TooltipBox(description) {
            GlassIconButton(backdrop, icon, description, onClick, variant = variant)
        }
        Spacer(Modifier.height(4.dp))
        PrismText(label, color = Prism.subText, fontSize = 12.sp, maxLines = 1)
    }
}

/** Hover / press wash shared by the flat pressable rows and tiles. */
private fun Modifier.pressFill(press: PressState, shape: Shape, color: Color): Modifier = drawBehind {
    val a = max(press.hover, press.progress * 1.6f)
    if (a > 0f) {
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            color.copy(alpha = (color.alpha * a).coerceAtMost(1f)),
        )
    }
}

/** Flat ghost row: icon + label, immediate press lighting, no ripple. */
@Composable
private fun GhostRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberPressState()
    val haptics = LocalHaptics.current
    Row(
        modifier = modifier
            .focusRing(press, RowShape, Prism.accent.copy(alpha = 0.85f))
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressFill(press, RowShape, Prism.colors.fillWeak)
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismIcon(icon, null, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        PrismText(label, modifier = Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ImportSourceTile(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = rememberPressState()
    val haptics = LocalHaptics.current
    val colors = Prism.colors
    Column(
        modifier = modifier
            .focusRing(press, CardShape, Prism.accent.copy(alpha = 0.85f))
            .height(96.dp)
            .graphicsLayer(rememberPressLayer(press))
            .clip(CardShape)
            .background(colors.fillWeak)
            .pressFill(press, CardShape, colors.fillWeak)
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PrismIcon(icon, null, size = 26.dp, tint = Prism.accent)
        Spacer(Modifier.height(6.dp))
        PrismText(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CreateFolderSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().imePadding()) {
        SheetOverlay(visible, onDismiss, heightFraction = 0.55f) { surface ->
            val name = rememberTextFieldState()
            val canCreate = name.text.isNotBlank()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PrismText("New Folder", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                GlassTextField(name, "Folder name", Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    GlassButton(surface, "Cancel", onDismiss)
                    GlassButton(
                        surface,
                        "Create",
                        { if (canCreate) onCreate(name.text.toString().trim()) },
                        variant = ButtonVariant.Primary,
                        enabled = canCreate,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumPickerSheet(
    visible: Boolean,
    albums: List<AlbumEntity>,
    onDismiss: () -> Unit,
    onPickAlbum: (String) -> Unit,
    onCreateAlbum: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().imePadding()) {
        SheetOverlay(visible, onDismiss, heightFraction = 0.7f) { surface ->
            val newAlbumName = rememberTextFieldState()
            val canCreate = newAlbumName.text.isNotBlank()
            Column(Modifier.fillMaxSize()) {
                PrismText("Move to folder", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (albums.isNotEmpty()) {
                        item {
                            PrismText(
                                "Existing folders",
                                modifier = Modifier.padding(bottom = 6.dp),
                                color = Prism.subText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        items(albums, key = { it.id }) { album ->
                            GhostRow(PrismIcons.Folder, album.name, { onPickAlbum(album.id) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassTextField(newAlbumName, "Or create new folder", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    GlassButton(surface, "Cancel", onDismiss)
                    GlassButton(
                        surface,
                        "Create",
                        { if (canCreate) onCreateAlbum(newAlbumName.text.toString().trim()) },
                        variant = ButtonVariant.Primary,
                        enabled = canCreate,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GalleryPickerDialog(
    items: List<GalleryMediaItem>,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onImport: (List<Uri>) -> Unit,
) {
    var selectedUris by remember { mutableStateOf(emptySet<Uri>()) }
    Portal {
        val pageBackdrop = rememberLayerBackdrop()
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Prism.background)
                .clickable(interactionSource = null, indication = null) {},
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(Prism.background))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrismText(
                        "Device gallery",
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TopBarIcon(pageBackdrop, PrismIcons.Close, "Close", onDismiss)
                }
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrismIcon(PrismIcons.Photo, null, size = 32.dp, tint = Prism.subText)
                        Spacer(Modifier.height(12.dp))
                        PrismText("No device photos are available.", color = Prism.subText, fontSize = 15.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        gridItems(items, key = { it.uri }) { item ->
                            val selected = item.uri in selectedUris
                            GalleryPickTile(
                                item = item,
                                viewModel = viewModel,
                                selected = selected,
                                onToggle = {
                                    selectedUris = if (selected) {
                                        selectedUris - item.uri
                                    } else {
                                        selectedUris + item.uri
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    GlassButton(pageBackdrop, "Cancel", onDismiss)
                    GlassButton(
                        pageBackdrop,
                        if (selectedUris.isEmpty()) {
                            "Select photos to import"
                        } else {
                            "Import ${selectedUris.size} photo(s)"
                        },
                        { onImport(selectedUris.toList()) },
                        variant = ButtonVariant.Primary,
                        enabled = selectedUris.isNotEmpty(),
                    )
                }
            }
        }
    }
}

/** One selectable device-gallery tile: thumbnail, video badge, accent veil and selection indicator. */
@Composable
private fun GalleryPickTile(
    item: GalleryMediaItem,
    viewModel: VaultViewModel,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val accent = Prism.accent
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, motion.settle(), label = "pickTileScale")
    val veil by animateFloatAsState(if (selected) 1f else 0f, motion.fade(180), label = "pickTileVeil")
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(TileShape)
            .clickable(interactionSource = null, indication = null) {
                haptics.perform(HapticKind.Tick)
                onToggle()
            },
    ) {
        GalleryThumbnail(
            item = item,
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
        if (item.mimeType.startsWith("video/")) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(Capsule())
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrismIcon(PrismIcons.Play, null, size = 11.dp, tint = Color.White)
            }
        }
        GalleryPickIndicator(
            selected = selected,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

@Composable
private fun GalleryPickIndicator(selected: Boolean, modifier: Modifier = Modifier) {
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

@Composable
private fun GalleryThumbnail(
    item: GalleryMediaItem,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = viewModel.galleryThumbnail(item.uri, item.mimeType, 256)
    }
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(Prism.colors.fillWeak),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PrismIcon(PrismIcons.Photo, item.displayName, size = 24.dp, tint = Prism.subText)
        }
    }
}

@Composable
private fun FolderCard(
    album: AlbumEntity,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumItems by viewModel.itemsInAlbum(album.id).collectAsState(initial = emptyList())
    val press = rememberPressState()
    val haptics = LocalHaptics.current
    val colors = Prism.colors
    Column(
        modifier = modifier
            .focusRing(press, CardShape, Prism.accent.copy(alpha = 0.85f))
            .width(130.dp)
            .height(140.dp)
            .graphicsLayer(rememberPressLayer(press))
            .clip(CardShape)
            .background(colors.fillWeak)
            .pressFill(press, CardShape, colors.fillWeak)
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            if (albumItems.isNotEmpty()) {
                VaultImage(
                    file = viewModel.thumbFile(albumItems.first()),
                    maxDimensionPx = 256,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                PrismIcon(PrismIcons.Folder, null, size = 36.dp, tint = colors.subText)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            PrismText(
                text = album.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PrismText(
                text = "${albumItems.size} ${if (albumItems.size == 1) "photo" else "photos"}",
                color = colors.subText,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPressState()
    val haptics = LocalHaptics.current
    val colors = Prism.colors
    Column(
        modifier = modifier
            .focusRing(press, CardShape, Prism.accent.copy(alpha = 0.85f))
            .width(130.dp)
            .height(140.dp)
            .graphicsLayer(rememberPressLayer(press))
            .clip(CardShape)
            .border(1.dp, colors.outline, CardShape)
            .pressFill(press, CardShape, colors.fillWeak)
            .pressInput(press)
            .clickable(interactionSource = press.interactionSource, indication = null, role = Role.Button) {
                haptics.perform(HapticKind.Press)
                onClick()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrismIcon(PrismIcons.FolderPlus, null, size = 30.dp, tint = Prism.accent)
        Spacer(Modifier.height(8.dp))
        PrismText("New Folder", color = Prism.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
