package com.dazaike.photovault.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dazaike.photovault.crypto.VaultCrypto
import com.dazaike.photovault.data.AlbumEntity
import com.dazaike.photovault.data.GalleryMediaItem
import com.dazaike.photovault.data.VaultDatabase
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.data.VaultRepository
import android.content.Context
import com.dazaike.photovault.export.StorageExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val decodeGate = Semaphore(DECODE_PARALLELISM)
    private val durationGate = Mutex()
    private val measured: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val crypto = VaultCrypto(app)
    private val db = VaultDatabase.getInstance(app)
    private val repo = VaultRepository(app, db.vaultDao(), db.albumDao(), crypto)

    val items: StateFlow<List<VaultItemEntity>> =
        repo.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unfiledItems: StateFlow<List<VaultItemEntity>> =
        repo.observeUnfiled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashItems: StateFlow<List<VaultItemEntity>> =
        repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumEntity>> =
        repo.observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importErrors = MutableSharedFlow<String>()
    val importErrors: SharedFlow<String> = _importErrors.asSharedFlow()
    private val _galleryItems = MutableStateFlow<List<GalleryMediaItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryMediaItem>> = _galleryItems.asStateFlow()
    private val _sourceUrisReadyForDeletion = MutableSharedFlow<List<Uri>>()
    val sourceUrisReadyForDeletion: SharedFlow<List<Uri>> = _sourceUrisReadyForDeletion.asSharedFlow()

    fun itemsInAlbum(albumId: String): Flow<List<VaultItemEntity>> = repo.observeItemsInAlbum(albumId)

    fun loadGalleryItems() {
        viewModelScope.launch(Dispatchers.IO) {
            _galleryItems.value = repo.galleryItems()
        }
    }

    fun importUris(
        uris: List<Uri>,
        albumId: String? = null,
        deleteOriginalsAfterImport: Boolean = false,
    ) {
        viewModelScope.launch {
            var failures = 0
            val importedSourceUris = buildList {
                uris.forEach { uri ->
                    repo.importUri(uri, albumId)
                        .onSuccess {
                            if (deleteOriginalsAfterImport) add(uri)
                        }
                        .onFailure { failures++ }
                }
            }
            if (failures > 0) _importErrors.emit("$failures of ${uris.size} photos failed to import")
            if (importedSourceUris.isNotEmpty()) {
                _sourceUrisReadyForDeletion.emit(importedSourceUris)
            }
        }
    }

    suspend fun deleteSourceUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count(repo::deleteSourceUri)
    }

    fun moveToTrash(items: List<VaultItemEntity>) {
        viewModelScope.launch { items.forEach { repo.moveToTrash(it) } }
    }

    fun restoreFromTrash(items: List<VaultItemEntity>) {
        viewModelScope.launch { items.forEach { repo.restoreFromTrash(it) } }
    }

    fun permanentlyDelete(items: List<VaultItemEntity>) {
        viewModelScope.launch { items.forEach { repo.permanentlyDelete(it) } }
    }

    fun createAlbum(name: String, itemIds: List<String> = emptyList()) {
        viewModelScope.launch {
            val album = repo.createAlbum(name)
            if (itemIds.isNotEmpty()) repo.moveToAlbum(album.id, itemIds)
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        viewModelScope.launch { repo.renameAlbum(albumId, name) }
    }

    fun deleteAlbum(album: AlbumEntity) {
        viewModelScope.launch { repo.deleteAlbum(album) }
    }

    fun addToAlbum(albumId: String, itemIds: List<String>) {
        viewModelScope.launch { repo.addToAlbum(albumId, itemIds) }
    }

    fun moveToAlbum(albumId: String, itemIds: List<String>) {
        viewModelScope.launch { repo.moveToAlbum(albumId, itemIds) }
    }

    fun removeFromAlbum(albumId: String, itemId: String) {
        viewModelScope.launch { repo.removeFromAlbum(albumId, itemId) }
    }

    /** Decrypts originals into FileProvider-exposed cache files for sharing/clipboard; runs on IO. */
    suspend fun prepareShareUris(items: List<VaultItemEntity>): List<Uri> =
        withContext(Dispatchers.IO) { items.map { repo.prepareShareUri(it) } }
    /** Exports items directly to device storage with optional user directory and auto-deletion delay. */
    suspend fun downloadItems(
        context: Context,
        items: List<VaultItemEntity>,
        delaySeconds: Int,
        targetTreeUri: Uri? = null,
        updateTimestamp: Boolean = (delaySeconds > 0),
    ): List<Uri> = withContext(Dispatchers.IO) {
        val uris = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    StorageExporter.exportItemToStorage(
                        context = context,
                        item = item,
                        originalEncryptedFile = repo.originalFileFor(item),
                        crypto = crypto,
                        targetTreeUri = targetTreeUri,
                        updateTimestampToNow = updateTimestamp,
                    )
                }
            }.awaitAll().filterNotNull()
        }
        if (delaySeconds > 0 && uris.isNotEmpty()) {
            StorageExporter.scheduleAutoDelete(context, uris, delaySeconds)
        }
        uris
    }


    fun originalFile(item: VaultItemEntity): File = repo.originalFileFor(item)
    fun thumbFile(item: VaultItemEntity): File = repo.thumbFileFor(item)

    /** Cached bitmap for [file] at [maxDimensionPx], or null if not decoded yet. Cheap; call from composition. */
    fun cachedThumb(file: File, maxDimensionPx: Int): Bitmap? =
        if (maxDimensionPx <= CACHE_MAX_DIMENSION) ThumbCache.get("${file.path}@$maxDimensionPx") else null

    suspend fun galleryThumbnail(uri: Uri, mimeType: String, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        ThumbCache.get(key)?.let { return it }
        return decodeGate.withPermit {
            withContext(Dispatchers.IO) { repo.galleryThumbnail(uri, mimeType, sizePx) }
        }?.also { ThumbCache.put(key, it) }
    }

    /** Decrypts + decodes with at most [DECODE_PARALLELISM] decodes in flight; small sizes are served from [ThumbCache]. */
    suspend fun decryptForDisplay(file: File, maxDimensionPx: Int): Bitmap? {
        val cacheable = maxDimensionPx <= CACHE_MAX_DIMENSION
        val key = "${file.path}@$maxDimensionPx"
        if (cacheable) ThumbCache.get(key)?.let { return it }
        return decodeGate.withPermit {
            withContext(Dispatchers.IO) { runCatching { crypto.decryptBitmap(file, maxDimensionPx) }.getOrNull() }
        }?.also { if (cacheable) ThumbCache.put(key, it) }
    }

    /**
     * Measures a legacy video's duration (imported before durations were stored). Serialised because each
     * measurement decrypts the whole file; cancelling (tile scrolled away) drops it from the queue.
     */
    suspend fun measureDuration(item: VaultItemEntity) {
        if (item.durationMs != null || !item.mimeType.startsWith("video/") || item.id in measured) return
        durationGate.withLock {
            if (!measured.add(item.id)) return
            withContext(Dispatchers.IO) { repo.backfillDuration(item) }
        }
    }

    /** Records the exact duration reported by the player. */
    fun reportDuration(item: VaultItemEntity, durationMs: Long) {
        if (durationMs <= 0 || item.durationMs == durationMs) return
        measured.add(item.id)
        viewModelScope.launch(Dispatchers.IO) { repo.setDuration(item.id, durationMs) }
    }

    /**
     * Decrypts a video's original into a plaintext temp file under cacheDir for
     * local VideoView playback (VideoView cannot read EncryptedFile directly).
     * Caller must pass the returned file to [deleteTempPlayback] once done.
     */
    suspend fun decryptVideoToTemp(item: VaultItemEntity): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(getApplication<Application>().cacheDir, "playback").apply { mkdirs() }
            val temp = File(dir, "${item.id}.tmp")
            crypto.decryptToFile(repo.originalFileFor(item), temp)
            temp
        }.getOrNull()
    }

    fun deleteTempPlayback(file: File) {
        viewModelScope.launch(Dispatchers.IO) { file.delete() }
    }

    private companion object {
        const val DECODE_PARALLELISM = 4
        const val CACHE_MAX_DIMENSION = 512
    }
}
