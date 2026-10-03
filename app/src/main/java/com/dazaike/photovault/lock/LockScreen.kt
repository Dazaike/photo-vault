package com.dazaike.photovault.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.dazaike.photovault.ui.ButtonSize
import com.dazaike.photovault.ui.ButtonVariant
import com.dazaike.photovault.ui.GlassButton
import com.dazaike.photovault.ui.LocalPageBackdrop
import com.dazaike.photovault.ui.PrismIcon
import com.dazaike.photovault.ui.PrismIcons
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    var status by remember { mutableStateOf<BiometricStatus?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val backdrop = LocalPageBackdrop.current
    val motion = LocalMotion.current

    fun promptAuth() {
        errorMessage = null
        BiometricAuthHelper.authenticate(
            activity,
            onSuccess = onUnlock,
            onError = { message -> errorMessage = message },
        )
    }

    LaunchedEffect(Unit) {
        val current = BiometricAuthHelper.status(context)
        status = current
        if (current == BiometricStatus.READY) promptAuth()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Lock graphic
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .background(Prism.colors.fillWeak, CircleShape),
            ) {
                PrismIcon(
                    PrismIcons.Lock,
                    contentDescription = "Vault Locked",
                    size = 48.dp,
                    tint = Prism.accent,
                )
            }

            Spacer(Modifier.height(24.dp))

            when (status) {
                BiometricStatus.NO_SECURE_LOCK -> {
                    PrismText(
                        "Set up a screen lock (PIN, pattern, fingerprint, or face) in Android Settings to use Photo Vault.",
                        fontSize = 16.sp,
                        color = Prism.subText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        backdrop,
                        "Open Settings",
                        { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                        variant = ButtonVariant.Primary,
                    )
                }
                BiometricStatus.READY -> {
                    PrismText(
                        "Photo Vault is locked",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(motion.fade(200)),
                        exit = fadeOut(motion.fade(150)),
                    ) {
                        errorMessage?.let {
                            PrismText(
                                it,
                                color = Prism.colors.error,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    GlassButton(
                        backdrop,
                        "Unlock Vault",
                        { promptAuth() },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Large,
                        leadingIcon = PrismIcons.Lock,
                    )
                }
                null -> Unit
            }
        }
    }
}
