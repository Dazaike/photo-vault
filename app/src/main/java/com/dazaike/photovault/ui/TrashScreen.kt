package com.dazaike.photovault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.data.VaultItemEntity

@Composable
fun TrashScreen(viewModel: VaultViewModel, onBack: () -> Unit) {
    val items by viewModel.trashItems.collectAsState()
    val backdrop = LocalPageBackdrop.current
    val toasts = LocalToasts.current
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    // Captured when the dialog opens so its title does not change while it fades out.
    var purgeCount by remember { mutableStateOf(0) }

    val isSelecting = selectedIds.isNotEmpty()
    val allSelected = items.isNotEmpty() && selectedIds.size == items.size

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(
                title = if (isSelecting) "${selectedIds.size} selected" else "Trash",
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
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrismIcon(PrismIcons.Trash, null, size = 32.dp, tint = Prism.subText)
                        Spacer(Modifier.height(12.dp))
                        PrismText("Trash is empty", fontSize = 16.sp, color = Prism.subText)
                    }
                } else {
                    PhotoGrid(
                        items = items,
                        viewModel = viewModel,
                        selectedIds = selectedIds,
                        onToggleSelect = { id -> selectedIds = toggle(selectedIds, id) },
                        onOpenItem = { id -> selectedIds = toggle(selectedIds, id) },
                        onLongPressItem = { menuItem = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            SelectionActionBar(visible = isSelecting) {
                SelectionBarItem(
                    icon = PrismIcons.Restore,
                    label = "Restore",
                    description = "Restore",
                    onClick = {
                        val restoring = items.filter { it.id in selectedIds }
                        viewModel.restoreFromTrash(restoring)
                        selectedIds = emptySet()
                        toasts.show(
                            if (restoring.size == 1) "Restored 1 item" else "Restored ${restoring.size} items",
                            ToastKind.Success,
                        )
                    },
                )
                SelectionBarItem(
                    icon = PrismIcons.Trash,
                    label = "Delete forever",
                    description = "Delete forever",
                    onClick = {
                        purgeCount = selectedIds.size
                        showPurgeConfirm = true
                    },
                    variant = ButtonVariant.Destructive,
                )
            }
        }

        PhotoContextMenu(
            item = menuItem,
            viewModel = viewModel,
            actions = menuItem?.let { held ->
                listOf(
                    PhotoMenuAction(PrismIcons.SelectAll, "Select") { selectedIds = setOf(held.id) },
                    PhotoMenuAction(PrismIcons.Restore, "Restore") {
                        viewModel.restoreFromTrash(listOf(held))
                        toasts.show("Restored 1 item", ToastKind.Success)
                    },
                    PhotoMenuAction(PrismIcons.Trash, "Delete forever", destructive = true) {
                        selectedIds = setOf(held.id)
                        purgeCount = 1
                        showPurgeConfirm = true
                    },
                )
            }.orEmpty(),
            onDismiss = { menuItem = null },
        )
    }

    ConfirmDialog(
        visible = showPurgeConfirm,
        title = "Permanently delete $purgeCount item(s)?",
        message = "This cannot be undone.",
        confirmLabel = "Delete forever",
        onConfirm = {
            viewModel.permanentlyDelete(items.filter { it.id in selectedIds })
            selectedIds = emptySet()
            showPurgeConfirm = false
            toasts.show(if (purgeCount == 1) "Deleted 1 item" else "Deleted $purgeCount items")
        },
        onDismiss = { showPurgeConfirm = false },
        destructive = true,
    )
}
