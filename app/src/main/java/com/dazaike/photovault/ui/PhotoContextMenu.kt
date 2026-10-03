package com.dazaike.photovault.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.backdrop.Backdrop

class PhotoMenuAction(
    @DrawableRes val icon: Int,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Glass sheet shown when a photo is held: thumbnail + name header, then [actions] as flat rows.
 * Visible while [item] is non-null; the last item is kept while the sheet slides out.
 */
@Composable
fun BoxScope.PhotoContextMenu(
    item: VaultItemEntity?,
    viewModel: VaultViewModel,
    actions: List<PhotoMenuAction>,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(item) }
    if (item != null && item != shown) shown = item
    val fraction = (0.2f + 0.075f * actions.size).coerceAtMost(0.85f)
    SheetOverlay(visible = item != null, onDismiss = onDismiss, heightFraction = fraction) { _ ->
        val current = shown ?: return@SheetOverlay
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VaultImage(
                    file = remember(current.id) { viewModel.thumbFile(current) },
                    maxDimensionPx = 256,
                    viewModel = viewModel,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    PrismText(
                        current.originalName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val isVideo = current.mimeType.startsWith("video/")
                    val length = current.durationMs?.takeIf { isVideo && it > 0 }?.let { " · ${formatDuration(it)}" }.orEmpty()
                    PrismText(
                        (if (isVideo) "Video" else "Photo") + length,
                        fontSize = 13.sp,
                        color = Prism.subText,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            actions.forEach { action ->
                val tint = if (action.destructive) Prism.error else Prism.text
                VaultGhostRow(onClick = {
                    onDismiss()
                    action.onClick()
                }) {
                    PrismIcon(action.icon, null, size = 22.dp, tint = tint)
                    Spacer(Modifier.width(14.dp))
                    PrismText(action.label, fontSize = 16.sp, color = tint, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
