package com.dazaike.photovault.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.util.Size
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.dazaike.photovault.crypto.VaultCrypto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

private val TRASH_RETENTION_MS = TimeUnit.DAYS.toMillis(30)

/** Imports an encrypted copy into the vault. */
class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val albumDao: AlbumDao,
    private val crypto: VaultCrypto,
) {
    private val originalsDir = File(context.filesDir, "vault/originals").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "vault/thumbs").apply { mkdirs() }
    private val shareDir = File(context.cacheDir, "share").apply { mkdirs() }

    fun observeItems() = dao.observeActive()
    fun observeUnfiled() = dao.observeUnfiled()
    fun observeTrash() = dao.observeTrash()
    fun observeAlbums() = albumDao.observeAlbums()
    fun observeItemsInAlbum(albumId: String) = albumDao.observeItemsInAlbum(albumId)

    fun galleryItems(limit: Int = 500): List<GalleryMediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("image/%", "video/%"))
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Files.FileColumns.DATE_ADDED),
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        return context.contentResolver.query(collection, projection, queryArgs, null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                buildList {
                    while (cursor.moveToNext()) {
                        val mimeType = cursor.getString(mimeColumn).orEmpty()
                        val mediaCollection = when {
                            mimeType.startsWith("image/") ->
                                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            mimeType.startsWith("video/") ->
                                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            else -> collection
                        }
                        add(
                            GalleryMediaItem(
                                uri = ContentUris.withAppendedId(mediaCollection, cursor.getLong(idColumn)),
                                displayName = cursor.getString(nameColumn).orEmpty(),
                                mimeType = mimeType,
                            ),
                        )
                    }
                }
            }
            ?: emptyList()
    }

    fun galleryThumbnail(uri: Uri, mimeType: String, sizePx: Int): Bitmap? = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            mimeType.startsWith("image/") ->
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            mimeType.startsWith("video/") -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }
            else -> null
        }
    }.getOrNull()

    fun deleteSourceUri(uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        when {
            uri.authority == MediaStore.AUTHORITY -> resolver.delete(uri, null, null) > 0
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(resolver, uri)
            else -> false
        }
    }.getOrDefault(false)

    suspend fun importUri(uri: Uri, albumId: String? = null): Result<Unit> {

        val id = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/*"
        val isVideo = mime.startsWith("video/")
        val displayName = queryDisplayName(resolver, uri) ?: id
        val originalFile = File(originalsDir, "$id.enc")
        val thumbFile = File(thumbsDir, "$id.enc")

        val input = resolver.openInputStream(uri)
            ?: return Result.failure(IOException("cannot open $uri"))
        input.use { crypto.encryptStream(it, originalFile) }

        val thumbBitmap = if (isVideo) {
            extractVideoThumbnail(uri, maxDimensionPx = 512)
        } else {
            crypto.decryptBitmap(originalFile, maxDimensionPx = 512)
        }
        if (thumbBitmap == null) {
            originalFile.delete()
            return Result.failure(IOException("cannot decode $uri"))
        }

        val thumbBytes = ByteArrayOutputStream().apply {
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, this)
        }.toByteArray()
        crypto.encryptBytes(thumbBytes, thumbFile)

        val durationMs = if (isVideo) readDurationMs { it.setDataSource(context, uri) } else null
        dao.insert(VaultItemEntity(id, displayName, mime, System.currentTimeMillis(), durationMs = durationMs))
        if (albumId != null) {
            albumDao.addToAlbum(AlbumItemCrossRef(albumId, id))
        }
        return Result.success(Unit)
    }

    /** Opens a [MediaMetadataRetriever] via [source] and returns the container duration, or null if unreadable. */
    private inline fun readDurationMs(source: (MediaMetadataRetriever) -> Unit): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            source(retriever)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Measures a video imported before durations were stored: stages the plaintext in cache, reads the
     * duration, deletes the staging file, persists the result (0 = undeterminable). Heavy; callers serialise it.
     */
    suspend fun backfillDuration(item: VaultItemEntity): Long {
        val staging = File(File(context.cacheDir, "duration").apply { mkdirs() }, "${item.id}.tmp")
        val ms = try {
            crypto.decryptToFile(originalFileFor(item), staging)
            readDurationMs { it.setDataSource(staging.path) } ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            staging.delete()
        }
        dao.setDuration(item.id, ms)
        return ms
    }

    suspend fun setDuration(id: String, durationMs: Long) = dao.setDuration(id, durationMs)

    /** Grabs a representative frame directly from the source [uri] (before encryption), downscaled to [maxDimensionPx]. */
    private fun extractVideoThumbnail(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val largestSide = maxOf(frame.width, frame.height).coerceAtLeast(1)
            if (largestSide <= maxDimensionPx) {
                frame
            } else {
                val scale = maxDimensionPx.toFloat() / largestSide
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Soft-deletes into the trash; underlying encrypted files are kept until [permanentlyDelete] or auto-purge. */
    suspend fun moveToTrash(item: VaultItemEntity) = dao.softDelete(item.id, System.currentTimeMillis())

    suspend fun restoreFromTrash(item: VaultItemEntity) = dao.restore(item.id)

    suspend fun permanentlyDelete(item: VaultItemEntity) {
        File(originalsDir, "${item.id}.enc").delete()
        File(thumbsDir, "${item.id}.enc").delete()
        dao.delete(item)
    }

    /** Hard-deletes trash entries older than the 30-day retention window; call on app startup. */
    suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        dao.trashOlderThan(cutoff).forEach { permanentlyDelete(it) }
    }

    suspend fun createAlbum(name: String): AlbumEntity {
        val album = AlbumEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        albumDao.insertAlbum(album)
        return album
    }

    suspend fun deleteAlbum(album: AlbumEntity) = albumDao.deleteAlbum(album)

    suspend fun addToAlbum(albumId: String, itemIds: List<String>) {
        itemIds.forEach { albumDao.addToAlbum(AlbumItemCrossRef(albumId, it)) }
    }

    suspend fun moveToAlbum(albumId: String, itemIds: List<String>) {
        albumDao.removeItemsFromAllAlbums(itemIds)
        itemIds.forEach { albumDao.addToAlbum(AlbumItemCrossRef(albumId, it)) }
    }

    suspend fun renameAlbum(albumId: String, name: String) = albumDao.renameAlbum(albumId, name)

    suspend fun removeFromAlbum(albumId: String, itemId: String) = albumDao.removeFromAlbum(albumId, itemId)
    /**
     * Decrypts [item]'s original into this app's FileProvider-exposed cache dir and
     * returns a content:// URI safe to hand to another app (share sheet, clipboard).
     * Files are overwritten per call and left in cache for the OS to reclaim.
     */
    fun prepareShareUri(item: VaultItemEntity): Uri {
        val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType)
        val rawExt = item.originalName.substringAfterLast('.', missingDelimiterValue = "")
        val extension = rawExt.ifEmpty { extFromMime.orEmpty() }
        val destFile = File(shareDir, if (extension.isNotEmpty()) "${item.id}.$extension" else item.id)
        crypto.decryptToFile(originalFileFor(item), destFile)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
    }

    fun originalFileFor(item: VaultItemEntity) = File(originalsDir, "${item.id}.enc")
    fun thumbFileFor(item: VaultItemEntity) = File(thumbsDir, "${item.id}.enc")

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
}
