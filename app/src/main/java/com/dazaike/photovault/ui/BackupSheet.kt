package com.dazaike.photovault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dazaike.photovault.ui.theme.PrismText

enum class BackupMode { Export, Import }

private const val MIN_PASSWORD = 8

@Composable
fun BackupSheet(
    mode: BackupMode?,
    status: BackupStatus,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    val running = status as? BackupStatus.Running
    Box(Modifier.fillMaxSize().imePadding()) {
        SheetOverlay(
            visible = mode != null,
            onDismiss = { if (status !is BackupStatus.Running) onDismiss() },
            heightFraction = 0.62f,
        ) { surface ->
            val password = rememberTextFieldState()
            val confirm = rememberTextFieldState()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (running != null) {
                    PrismText(
                        if (running.importing) "Importing…" else "Exporting…",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(24.dp))
                    GlassProgressBar(
                        progress = if (running.total == 0) null else running.done.toFloat() / running.total,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    PrismText("${running.done} of ${running.total}")
                } else if (mode == BackupMode.Export) {
                    val pw = password.text
                    val mismatch = confirm.text.isNotEmpty() && confirm.text.toString() != pw.toString()
                    val canSubmit = pw.length >= MIN_PASSWORD && confirm.text.toString() == pw.toString()
                    PrismText("Export backup", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    PrismText(
                        "Saves all photos, videos, folders and settings into one file protected by a password. " +
                            "If you forget the password the backup cannot be opened.",
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassPasswordField(
                        password,
                        "Password",
                        Modifier.fillMaxWidth(),
                        supportingText = "At least 8 characters",
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassPasswordField(
                        confirm,
                        "Confirm password",
                        Modifier.fillMaxWidth(),
                        error = if (mismatch) "Passwords don't match" else null,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        GlassButton(surface, "Cancel", onDismiss)
                        GlassButton(
                            surface,
                            "Choose location",
                            { if (canSubmit) onSubmit(pw.toString().toCharArray()) },
                            variant = ButtonVariant.Primary,
                            enabled = canSubmit,
                        )
                    }
                } else {
                    val pw = password.text
                    PrismText("Import backup", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    PrismText(
                        "Photos already in your vault are skipped; folders are merged and settings are replaced by the backup's.",
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassPasswordField(password, "Password", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        GlassButton(surface, "Cancel", onDismiss)
                        GlassButton(
                            surface,
                            "Import",
                            { if (pw.isNotEmpty()) onSubmit(pw.toString().toCharArray()) },
                            variant = ButtonVariant.Primary,
                            enabled = pw.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}
