package com.dazaike.photovault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Launches the system share sheet for one or more already-prepared content:// [uris]. */
fun shareUris(context: Context, uris: List<Uri>, mimeType: String) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uris.first())
            type = mimeType
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = mimeType
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

/** Copies one or more already-prepared content:// [uris] to the system clipboard. */
fun copyUrisToClipboard(context: Context, uris: List<Uri>, label: String = "Photos", mimeType: String? = null) {
    if (uris.isEmpty()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val resolver = context.contentResolver
    val firstUri = uris.first()
    val clipData = ClipData.newUri(resolver, label, firstUri)
    for (i in 1 until uris.size) {
        clipData.addItem(resolver, ClipData.Item(uris[i]))
    }
    clipboard.setPrimaryClip(clipData)
}

/** Copies a single already-prepared content:// [uri] to the system clipboard. */
fun copyUriToClipboard(context: Context, uri: Uri, label: String, mimeType: String? = null) {
    copyUrisToClipboard(context, listOf(uri), label, mimeType)
}

/** Picks a single shared MIME type across mixed image/video selections, else a wildcard. */
fun commonMimeType(mimeTypes: List<String>): String {
    val prefixes = mimeTypes.map { it.substringBefore('/') }.toSet()
    return if (prefixes.size == 1) "${prefixes.first()}/*" else "*/*"
}
