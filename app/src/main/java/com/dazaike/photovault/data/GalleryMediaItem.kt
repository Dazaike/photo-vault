package com.dazaike.photovault.data

import android.net.Uri

/** An ID-addressed local MediaStore item that may be imported and later deleted on confirmation. */
data class GalleryMediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)
