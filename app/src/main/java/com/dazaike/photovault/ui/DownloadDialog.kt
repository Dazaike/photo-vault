package com.dazaike.photovault.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawBehind
import kotlin.math.max
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.dazaike.photovault.ui.theme.accentAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SaveDialogState {
    Config,
    Saving,
    Success,
    Error,
}

fun getMediaNoun(items: List<VaultItemEntity>, plural: Boolean = false): String {
    val allVideos = items.isNotEmpty() && items.all { it.mimeType.startsWith("video/") }
    val allPhotos = items.isNotEmpty() && items.all { it.mimeType.startsWith("image/") }
    return when {
        allVideos -> if (plural) "videos" else "video"
        allPhotos -> if (plural) "photos" else "photo"
        else -> if (plural) "items" else "item"
    }
}

@Composable
fun DownloadDialog(
    items: List<VaultItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: suspend (delaySeconds: Int, targetTreeUri: Uri?, updateTimestamp: Boolean) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Freeze snapshot of items to prevent count drop when selection is cleared during success animation
    val snapshotItems = remember { items }
    val frozenCount = snapshotItems.size
    val nounSingular = remember(snapshotItems) { getMediaNoun(snapshotItems, plural = false) }
    val nounPlural = remember(snapshotItems) { getMediaNoun(snapshotItems, plural = true) }
    val nounCapitalized = remember(nounPlural) { nounPlural.replaceFirstChar { it.uppercase() } }

    var dialogState by remember { mutableStateOf(SaveDialogState.Config) }
    var selectedDelay by remember { mutableIntStateOf(30) }
    var updateTimestampToNow by remember { mutableStateOf(true) }
    var customDirUri by remember { mutableStateOf<Uri?>(null) }
    var customDirName by remember { mutableStateOf<String?>(null) }
    var useDefaultLocation by remember { mutableStateOf(true) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            customDirUri = uri
            val decodedPath = Uri.decode(uri.lastPathSegment ?: uri.toString())
            customDirName = decodedPath.substringAfterLast(':').ifEmpty { "Selected folder" }
            useDefaultLocation = false
        }
    }

    val timerOptions = listOf(
        30 to "30 sec",
        60 to "1 min",
        180 to "3 min",
        300 to "5 min",
        0 to "Keep permanently",
    )

    TransientSheet(
        heightFraction = if (dialogState == SaveDialogState.Config) 0.82f else 0.38f,
        onDismiss = onDismiss,
        canDismiss = dialogState != SaveDialogState.Saving,
    ) { surface, close ->
        val motion = LocalMotion.current
        val colors = Prism.colors
        val accent = Prism.accent

        // Auto-close shortly after a successful export.
        LaunchedEffect(dialogState) {
            if (dialogState == SaveDialogState.Success) {
                delay(1400L)
                close()
            }
        }

        // Status icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = dialogState,
                transitionSpec = { fadeIn(motion.fade(220)).togetherWith(fadeOut(motion.fade(120))) },
                label = "DialogIconAnimation",
            ) { state ->
                when (state) {
                    SaveDialogState.Config -> {
                        PrismIcon(PrismIcons.Download, null, size = 28.dp, tint = accent)
                    }
                    SaveDialogState.Saving -> {
                        Spinner(size = 32.dp, color = accent, strokeWidth = 3.dp)
                    }
                    SaveDialogState.Success -> {
                        var checkAnim by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { checkAnim = true }
                        val checkScale by animateFloatAsState(
                            targetValue = if (checkAnim) 1f else 0.2f,
                            animationSpec = motion.glide(),
                            label = "CheckmarkScale",
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(checkScale)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            PrismIcon(PrismIcons.Check, null, size = 28.dp, tint = Prism.onAccent)
                        }
                    }
                    SaveDialogState.Error -> {
                        PrismIcon(PrismIcons.Alert, null, size = 32.dp, tint = colors.error)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Title
        AnimatedContent(
            targetState = dialogState,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { fadeIn(motion.fade(220)).togetherWith(fadeOut(motion.fade(120))) },
            label = "DialogTitleAnimation",
        ) { state ->
            when (state) {
                SaveDialogState.Config -> {
                    PrismText(
                        if (frozenCount == 1) "Save $nounSingular to storage" else "Save $frozenCount $nounPlural to storage",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SaveDialogState.Saving -> {
                    PrismText(
                        if (frozenCount == 1) "Saving $nounSingular..." else "Saving $frozenCount $nounPlural...",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SaveDialogState.Success -> {
                    PrismText(
                        if (frozenCount == 1) {
                            "Saved ${nounSingular.replaceFirstChar { it.uppercase() }} to Storage!"
                        } else {
                            "Saved $frozenCount $nounCapitalized to Storage!"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SaveDialogState.Error -> {
                    PrismText(
                        "Failed to Save",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = dialogState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(motion.fade(220)).togetherWith(fadeOut(motion.fade(120))) },
                label = "DialogContentAnimation",
            ) { state ->
                when (state) {
                    SaveDialogState.Config -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            // Location Section
                            PrismText(
                                "Save location:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Prism.subText,
                            )
                            Spacer(Modifier.height(4.dp))

                            Row(
                                Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OptionTile(
                                    selected = useDefaultLocation,
                                    onClick = { useDefaultLocation = true },
                                    icon = PrismIcons.Folder,
                                    title = "Default folder",
                                    caption = "Pictures / Movies",
                                    modifier = Modifier.weight(1f),
                                )
                                OptionTile(
                                    selected = !useDefaultLocation,
                                    onClick = {
                                        // Re-tapping the active custom folder reopens the picker to change it.
                                        if (customDirUri == null || !useDefaultLocation) {
                                            folderPickerLauncher.launch(null)
                                        } else {
                                            useDefaultLocation = false
                                        }
                                    },
                                    icon = PrismIcons.FolderPlus,
                                    title = "Custom folder",
                                    caption = customDirName ?: "Choose any folder",
                                    captionColor = if (customDirName != null) accent else Prism.subText,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Auto-delete Timer Section
                            PrismText(
                                "Auto-delete timer:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Prism.subText,
                            )
                            Spacer(Modifier.height(4.dp))

                            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                timerOptions.chunked(3).forEach { rowOptions ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowOptions.forEach { (seconds, label) ->
                                            OptionTile(
                                                selected = selectedDelay == seconds,
                                                onClick = {
                                                    selectedDelay = seconds
                                                    if (seconds > 0) updateTimestampToNow = true
                                                },
                                                icon = if (seconds > 0) PrismIcons.Timer else PrismIcons.Download,
                                                title = label,
                                                caption = if (seconds > 0) "then delete" else "no timer",
                                                modifier = Modifier.weight(if (seconds == 0) 2f else 1f),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Timestamp / Gallery sorting option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.fillWeak, RoundedRectangle(14.dp))
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    PrismText("Set date & time to now", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    PrismText(
                                        if (frozenCount == 1) {
                                            "Places saved $nounSingular at the top of your gallery"
                                        } else {
                                            "Places saved $nounPlural at the top of your gallery"
                                        },
                                        fontSize = 13.sp,
                                        color = Prism.subText,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                GlassSwitch(
                                    checked = updateTimestampToNow,
                                    onCheckedChange = { updateTimestampToNow = it },
                                    contentDescription = "Set date & time to now",
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    SaveDialogState.Saving -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PrismText(
                                "Decrypting & writing to storage...",
                                fontSize = 15.sp,
                                color = Prism.subText,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassProgressBar(null, Modifier.fillMaxWidth())
                            if (updateTimestampToNow) {
                                Spacer(Modifier.height(10.dp))
                                PrismText(
                                    "Updating timestamp to now (top of gallery)",
                                    fontSize = 13.sp,
                                    color = accent,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    SaveDialogState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.outline, RoundedRectangle(16.dp))
                                    .padding(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PrismIcon(PrismIcons.Folder, null, size = 18.dp, tint = accent)
                                    Spacer(Modifier.width(8.dp))
                                    PrismText(
                                        if (useDefaultLocation) {
                                            if (snapshotItems.all { it.mimeType.startsWith("video/") }) {
                                                "Movies/PhotoVault"
                                            } else {
                                                "Pictures/PhotoVault"
                                            }
                                        } else {
                                            (customDirName ?: "Selected folder")
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (selectedDelay > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PrismIcon(PrismIcons.Timer, null, size = 18.dp, tint = accent)
                                        Spacer(Modifier.width(8.dp))
                                        PrismText(
                                            "Auto-deletes from storage in ${formatDelay(selectedDelay)}",
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                                if (updateTimestampToNow) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PrismIcon(PrismIcons.Calendar, null, size = 18.dp, tint = accent)
                                        Spacer(Modifier.width(8.dp))
                                        PrismText(
                                            "Date & time updated • Top of gallery",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SaveDialogState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PrismText(
                                "Could not export files to storage. Please check permissions and try again.",
                                fontSize = 15.sp,
                                color = Prism.subText,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Actions
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = when (dialogState) {
                SaveDialogState.Config, SaveDialogState.Saving -> SaveDialogState.Config
                else -> dialogState
            },
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { fadeIn(motion.fade(220)).togetherWith(fadeOut(motion.fade(120))) },
            label = "DialogActionsAnimation",
        ) { group ->
            when (group) {
                SaveDialogState.Config, SaveDialogState.Saving -> {
                    val saving = dialogState == SaveDialogState.Saving
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlassButton(surface, "Cancel", close, Modifier.weight(1f), enabled = !saving)
                        GlassButton(
                            surface,
                            if (saving) "Saving" else "Save",
                            {
                                dialogState = SaveDialogState.Saving
                                scope.launch {
                                    val targetUri = if (!useDefaultLocation) customDirUri else null
                                    val success = onConfirm(selectedDelay, targetUri, updateTimestampToNow)
                                    dialogState = if (success) SaveDialogState.Success else SaveDialogState.Error
                                }
                            },
                            Modifier.weight(1f),
                            variant = ButtonVariant.Primary,
                            enabled = !saving,
                            loading = saving,
                            leadingIcon = PrismIcons.Download,
                        )
                    }
                }
                SaveDialogState.Success -> {
                    GlassButton(surface, "Done", close, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
                }
                SaveDialogState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlassButton(surface, "Cancel", close, Modifier.weight(1f))
                        GlassButton(
                            surface,
                            "Try again",
                            { dialogState = SaveDialogState.Config },
                            Modifier.weight(1f),
                            variant = ButtonVariant.Primary,
                        )
                    }
                }
            }
        }
    }
}

/** Seconds → "30 sec" / "1 min" / "1 min 30 sec". */
fun formatDelay(seconds: Int): String = when {
    seconds < 60 -> "$seconds sec"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} sec"
}

private val OptionShape = RoundedRectangle(16.dp)

/** Selectable flat tile (radio semantics) for the option grids: icon, title, optional caption. */
@Composable
private fun OptionTile(
    selected: Boolean,
    onClick: () -> Unit,
    @androidx.annotation.DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = Prism.subText,
) {
    val press = rememberPressState()
    val haptics = LocalHaptics.current
    val colors = Prism.colors
    val accent = Prism.accent
    Column(
        modifier = modifier
            .focusRing(press, OptionShape, accent.copy(alpha = 0.85f))
            .height(84.dp)
            .graphicsLayer(rememberPressLayer(press))
            .clip(OptionShape)
            .background(if (selected) accent.copy(alpha = colors.accentAlpha(0.16f)) else colors.fillWeak)
            .drawBehind {
                val a = colors.fillWeak.alpha * max(press.hover, press.progress * 1.6f)
                if (a > 0f) drawRect(colors.fillWeak.copy(alpha = a.coerceAtMost(1f)))
            }
            .border(1.5.dp, if (selected) accent else Color.Transparent, OptionShape)
            .pressInput(press)
            .selectable(
                selected = selected,
                interactionSource = press.interactionSource,
                indication = null,
                role = Role.RadioButton,
            ) {
                haptics.perform(HapticKind.Tick)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PrismIcon(icon, null, size = 22.dp, tint = if (selected) accent else Prism.subText)
        Spacer(Modifier.height(5.dp))
        PrismText(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            PrismText(
                caption,
                fontSize = 11.sp,
                color = captionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
