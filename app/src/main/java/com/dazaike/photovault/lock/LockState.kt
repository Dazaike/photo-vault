package com.dazaike.photovault.lock

import kotlinx.coroutines.flow.MutableStateFlow

/** Global lock flag flipped by [com.dazaike.photovault.PhotoVaultApp] and consumed by MainActivity. */
object LockState {
    val isLocked = MutableStateFlow(true)
}
