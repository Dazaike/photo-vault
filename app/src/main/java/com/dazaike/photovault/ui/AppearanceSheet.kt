package com.dazaike.photovault.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import com.dazaike.photovault.data.ThemeMode
import com.dazaike.photovault.data.UiSettings
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText

/** Current appearance settings and how to change them; provided once by `MainActivity`. */
class Appearance(val settings: UiSettings, val update: ((UiSettings) -> UiSettings) -> Unit)

val LocalAppearance = staticCompositionLocalOf<Appearance> {
    error("LocalAppearance not provided; wrap in the Appearance provider")
}

/** Theme, accent colour and auto-delete notification. Accent presets apply instantly; slider drags apply on release. */
@Composable
fun BoxScope.AppearanceSheet(visible: Boolean, onDismiss: () -> Unit) {
    val appearance = LocalAppearance.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            appearance.update { it.copy(deleteCountdownNotification = true) }
        } else {
            toasts.show("Allow notifications to show the countdown", ToastKind.Error)
        }
    }
    SheetOverlay(visible, onDismiss, heightFraction = 0.82f) { surface ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PrismText("Settings", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            PrismText("Theme", fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            GlassSegmented(
                options = listOf("System", "Light", "Dark"),
                selectedIndex = appearance.settings.theme.ordinal,
                onSelect = { i -> appearance.update { it.copy(theme = ThemeMode.entries[i]) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            PrismText("Accent colour", fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            AccentPicker(
                backdrop = surface,
                accent = Color(appearance.settings.accent),
                onAccent = { color -> appearance.update { it.copy(accent = color.toArgb()) } },
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    PrismText("Auto-delete countdown", fontSize = 16.sp)
                    PrismText(
                        "Show a notification counting down to when a saved photo is deleted",
                        fontSize = 13.sp,
                        color = Prism.subText,
                    )
                }
                Spacer(Modifier.width(12.dp))
                GlassSwitch(
                    checked = appearance.settings.deleteCountdownNotification,
                    onCheckedChange = { on ->
                        if (!on) {
                            appearance.update { it.copy(deleteCountdownNotification = false) }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            appearance.update { it.copy(deleteCountdownNotification = true) }
                        }
                    },
                    contentDescription = "Auto-delete countdown notification",
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
