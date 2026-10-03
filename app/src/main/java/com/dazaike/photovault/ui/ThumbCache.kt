package com.dazaike.photovault.ui

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-wide decoded-bitmap cache for grid thumbnails. Sized in bytes (1/8 of the heap, capped at
 * 64 MiB) so scrolling back over tiles never re-decrypts and re-decodes them.
 */
object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(
        minOf(Runtime.getRuntime().maxMemory() / 8, 64L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
