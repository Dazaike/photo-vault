package com.dazaike.photovault

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dazaike.photovault.data.UiSettingsStore
import com.dazaike.photovault.lock.LockScreen
import com.dazaike.photovault.lock.LockState
import com.dazaike.photovault.ui.Appearance
import com.dazaike.photovault.ui.LocalAppearance
import com.dazaike.photovault.ui.OverlayHost
import com.dazaike.photovault.ui.PrismPageHost
import com.dazaike.photovault.ui.VaultNavHost
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismMaterialBridge
import com.dazaike.photovault.ui.theme.PrismTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        requestExactAlarmOnce()
        setContent {
            val store = remember { UiSettingsStore(applicationContext) }
            var settings by remember { mutableStateOf(store.load()) }
            val appearance = remember(settings) {
                Appearance(settings) { transform ->
                    settings = transform(settings).also(store::save)
                }
            }
            PrismTheme(settings) {
                val dark = Prism.colors.isDark
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(style, style)
                    onDispose {}
                }
                PrismMaterialBridge {
                    CompositionLocalProvider(LocalAppearance provides appearance) {
                        OverlayHost {
                            PrismPageHost {
                                val locked by LockState.isLocked.collectAsStateWithLifecycle()
                                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                                    if (locked) {
                                        LockScreen(onUnlock = { LockState.isLocked.value = false })
                                    } else {
                                        VaultNavHost()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Escape dismisses the topmost overlay (every overlay registers a BackHandler). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP &&
            onBackPressedDispatcher.hasEnabledCallbacks()
        ) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }

    /** Android 14+ denies exact alarms by default; ask once so timed auto-delete fires on time. */
    private fun requestExactAlarmOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (am.canScheduleExactAlarms() || prefs.getBoolean("exact_alarm_asked", false)) return
        prefs.edit().putBoolean("exact_alarm_asked", true).apply()
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
        )
    }
}
