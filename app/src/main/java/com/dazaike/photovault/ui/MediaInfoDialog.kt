package com.dazaike.photovault.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A [SheetOverlay] that mounts itself in the [Portal] layer (above the whole app, refracting it),
 * slides in on first composition and slides out before calling [onDismiss]. The caller simply
 * composes it while the sheet should exist. [content] receives the sheet's own backdrop and a
 * `close` function that plays the exit animation, then dismisses. Scrim tap / Back close it only
 * while [canDismiss] is true.
 */
@Composable
internal fun TransientSheet(
    heightFraction: Float,
    onDismiss: () -> Unit,
    canDismiss: Boolean = true,
    content: @Composable ColumnScope.(surface: Backdrop, close: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    LaunchedEffect(Unit) { visible = true }

    val close: () -> Unit = {
        if (visible) {
            visible = false
            scope.launch {
                delay(260)
                currentOnDismiss()
            }
        }
    }

    Portal { backdrop ->
        Box(Modifier.fillMaxSize()) {
            SheetLayer(
                backdrop = backdrop,
                visible = visible,
                onDismiss = { if (currentCanDismiss) close() },
                heightFraction = heightFraction,
            ) { surface -> content(surface, close) }
        }
    }
}

@Composable
fun MediaInfoDialog(
    item: VaultItemEntity,
    file: File,
    onDismiss: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(item.addedAtEpochMs))

    val fileSizeStr = formatFileSize(file.length())

    TransientSheet(heightFraction = 0.6f, onDismiss = onDismiss) { surface, close ->
        PrismText("Media Details", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            InfoRow(label = "File Name", value = item.originalName)
            InfoRow(label = "Type", value = item.mimeType)
            InfoRow(label = "Encrypted Size", value = fileSizeStr)
            InfoRow(label = "Date Added", value = formattedDate)
            InfoRow(label = "Item ID", value = item.id)
        }
        Spacer(Modifier.height(12.dp))
        GlassButton(surface, "Close", close, Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        PrismText(label, fontSize = 13.sp, color = Prism.subText)
        Spacer(Modifier.height(2.dp))
        PrismText(value, fontSize = 16.sp)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val formatted = String.format(Locale.getDefault(), "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}
