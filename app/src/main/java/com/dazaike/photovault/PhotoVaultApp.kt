package com.dazaike.photovault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.dazaike.photovault.lock.LockState
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dazaike.photovault.export.StorageExporter

/**
 * Locks the vault only when the screen turns off (or manually, via the in-app
 * lock button in VaultGridScreen). Backgrounding the app itself does NOT
 * re-lock — the system Photo Picker / SAF picker are separate activities and
 * would otherwise force a spurious re-lock every time the user returns from
 * picking photos.
 */
class PhotoVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    LockState.isLocked.value = true
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Thread { StorageExporter.cleanExpiredRecords(this@PhotoVaultApp) }.start()
            }
        })
    }
}
