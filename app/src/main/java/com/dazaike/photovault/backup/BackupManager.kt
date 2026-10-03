package com.dazaike.photovault.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.dazaike.photovault.crypto.VaultCrypto
import com.dazaike.photovault.data.AlbumEntity
import com.dazaike.photovault.data.AlbumItemCrossRef
import com.dazaike.photovault.data.ThemeMode
import com.dazaike.photovault.data.UiSettings
import com.dazaike.photovault.data.VaultDatabase
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.data.VaultRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ImportResult(val imported: Int, val skipped: Int, val albums: Int, val settings: UiSettings)

class BackupManager(
    private val context: Context,
    private val db: VaultDatabase,
    private val repo: VaultRepository,
    private val crypto: VaultCrypto,
) {
    private val idRegex = Regex("[A-Za-z0-9-]{1,64}")

    suspend fun export(target: Uri, password: CharArray, onProgress: (done: Int, total: Int) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            try {
                val dao = db.vaultDao()
                val albumDao = db.albumDao()
                val items = dao.allItems().filter { repo.originalFileFor(it).exists() && repo.thumbFileFor(it).exists() }
                val ids = items.mapTo(HashSet()) { it.id }
                val albums = albumDao.allAlbums()
                val refs = albumDao.allCrossRefs().filter { it.itemId in ids }
                val manifest = buildManifest(items, albums, refs, readSettings()).toByteArray(Charsets.UTF_8)

                val raw = resolver.openOutputStream(target, "wt") ?: throw IOException("cannot open output")
                BackupCodec.encrypt(BufferedOutputStream(raw), password).use { stream ->
                    stream.write(java.nio.ByteBuffer.allocate(4).putInt(manifest.size).array())
                    stream.write(manifest)
                    onProgress(0, items.size)
                    items.forEachIndexed { i, item ->
                        for (file in listOf(repo.originalFileFor(item), repo.thumbFileFor(item))) {
                            val blocks = BlockOutputStream(stream)
                            crypto.decryptToStream(file, blocks)
                            blocks.finish()
                        }
                        onProgress(i + 1, items.size)
                    }
                }
                items.size
            } catch (e: Throwable) {
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
                throw e
            }
        }

    /** Current settings are supplied by the caller-visible store; the manifest embeds them. */
    private fun readSettings(): UiSettings = com.dazaike.photovault.data.UiSettingsStore(context).load()

    private fun buildManifest(
        items: List<VaultItemEntity>,
        albums: List<AlbumEntity>,
        refs: List<AlbumItemCrossRef>,
        s: UiSettings,
    ): String = JSONObject().apply {
        put("version", 1)
        put("createdAt", System.currentTimeMillis())
        put(
            "settings",
            JSONObject()
                .put("theme", s.theme.name)
                .put("accent", s.accent)
                .put("haptics", s.haptics)
                .put("hapticStrength", s.hapticStrength.toDouble())
                .put("animationSpeed", s.animationSpeed.toDouble())
                .put("motionIntensity", s.motionIntensity.toDouble())
                .put("reduceMotion", s.reduceMotion)
                .put("brightness", s.brightness.toDouble())
                .put("deleteCountdownNotification", s.deleteCountdownNotification),
        )
        put("albums", JSONArray().also { a ->
            albums.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAtEpochMs)) }
        })
        put("items", JSONArray().also { a ->
            items.forEach {
                a.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.originalName)
                        .put("mime", it.mimeType)
                        .put("addedAt", it.addedAtEpochMs)
                        .put("isDeleted", it.isDeleted)
                        .put("deletedAt", it.deletedAtEpochMs ?: JSONObject.NULL)
                        .put("durationMs", it.durationMs ?: JSONObject.NULL),
                )
            }
        })
        put("albumItems", JSONArray().also { a ->
            refs.forEach { a.put(JSONObject().put("albumId", it.albumId).put("itemId", it.itemId)) }
        })
    }.toString()

    suspend fun import(source: Uri, password: CharArray, onProgress: (done: Int, total: Int) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val written = ArrayList<VaultItemEntity>()
            try {
                val raw = context.contentResolver.openInputStream(source) ?: throw IOException("cannot open input")
                BackupCodec.decrypt(BufferedInputStream(raw), password).use { stream ->
                    val data = DataInputStream(stream)
                    val mLen = try { data.readInt() } catch (e: java.io.EOFException) { throw CorruptBackupException("Backup is damaged") }
                    if (mLen !in 1..(64 shl 20)) throw CorruptBackupException("Backup is damaged")
                    val mBytes = ByteArray(mLen)
                    try { data.readFully(mBytes) } catch (e: java.io.EOFException) { throw CorruptBackupException("Backup is damaged") }

                    val root = try { JSONObject(String(mBytes, Charsets.UTF_8)) } catch (e: JSONException) {
                        throw CorruptBackupException("Backup is damaged")
                    }
                    if (root.optInt("version") != 1) throw CorruptBackupException("Unsupported backup")

                    val (items, albums, refs, settings) = try { parse(root) } catch (e: JSONException) {
                        throw CorruptBackupException("Backup is damaged")
                    }

                    val dao = db.vaultDao()
                    val albumDao = db.albumDao()
                    val existing = dao.allItems().mapTo(HashSet()) { it.id }
                    var skipped = 0
                    val newItems = ArrayList<VaultItemEntity>()
                    onProgress(0, items.size)
                    items.forEachIndexed { i, item ->
                        if (item.id in existing) {
                            BlockInputStream(stream).drain()
                            BlockInputStream(stream).drain()
                            skipped++
                        } else {
                            written.add(item)
                            crypto.encryptStream(BlockInputStream(stream), repo.originalFileFor(item))
                            crypto.encryptStream(BlockInputStream(stream), repo.thumbFileFor(item))
                            newItems.add(item)
                        }
                        onProgress(i + 1, items.size)
                    }
                    if (stream.read() != -1) throw CorruptBackupException("Backup is damaged")

                    val existingAlbums = albumDao.allAlbums().mapTo(HashSet()) { it.id }
                    val validAlbums = existingAlbums + albums.map { it.id }
                    val newIds = newItems.mapTo(HashSet()) { it.id }
                    db.withTransaction {
                        albumDao.insertAlbums(albums.filter { it.id !in existingAlbums })
                        dao.insertAll(newItems)
                        albumDao.insertCrossRefs(refs.filter { it.itemId in newIds && it.albumId in validAlbums })
                    }
                    ImportResult(newItems.size, skipped, albums.count { it.id !in existingAlbums }, settings)
                }
            } catch (e: Throwable) {
                written.forEach {
                    repo.originalFileFor(it).delete()
                    repo.thumbFileFor(it).delete()
                }
                throw e
            }
        }

    private data class Parsed(
        val items: List<VaultItemEntity>,
        val albums: List<AlbumEntity>,
        val refs: List<AlbumItemCrossRef>,
        val settings: UiSettings,
    )

    private fun parse(root: JSONObject): Parsed {
        val damaged = CorruptBackupException("Backup is damaged")
        val albums = root.getJSONArray("albums").let { a ->
            List(a.length()) {
                val o = a.getJSONObject(it)
                AlbumEntity(o.getString("id"), o.getString("name"), o.getLong("createdAt"))
            }
        }
        if (albums.any { !idRegex.matches(it.id) } || albums.map { it.id }.toSet().size != albums.size) throw damaged
        val items = root.getJSONArray("items").let { a ->
            List(a.length()) {
                val o = a.getJSONObject(it)
                VaultItemEntity(
                    id = o.getString("id"),
                    originalName = o.getString("name"),
                    mimeType = o.getString("mime"),
                    addedAtEpochMs = o.getLong("addedAt"),
                    isDeleted = o.getBoolean("isDeleted"),
                    deletedAtEpochMs = if (o.isNull("deletedAt")) null else o.getLong("deletedAt"),
                    durationMs = if (o.isNull("durationMs")) null else o.getLong("durationMs"),
                )
            }
        }
        if (items.any { !idRegex.matches(it.id) } || items.map { it.id }.toSet().size != items.size) throw damaged
        val refs = root.getJSONArray("albumItems").let { a ->
            List(a.length()) {
                val o = a.getJSONObject(it)
                AlbumItemCrossRef(o.getString("albumId"), o.getString("itemId"))
            }
        }
        return Parsed(items, albums, refs, parseSettings(root.optJSONObject("settings")))
    }

    private fun parseSettings(o: JSONObject?): UiSettings {
        val d = UiSettings()
        if (o == null) return d
        return UiSettings(
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(d.theme),
            accent = runCatching { o.getInt("accent") }.getOrDefault(d.accent),
            haptics = runCatching { o.getBoolean("haptics") }.getOrDefault(d.haptics),
            hapticStrength = runCatching { o.getDouble("hapticStrength").toFloat() }.getOrDefault(d.hapticStrength),
            animationSpeed = runCatching { o.getDouble("animationSpeed").toFloat() }.getOrDefault(d.animationSpeed),
            motionIntensity = runCatching { o.getDouble("motionIntensity").toFloat() }.getOrDefault(d.motionIntensity),
            reduceMotion = runCatching { o.getBoolean("reduceMotion") }.getOrDefault(d.reduceMotion),
            brightness = runCatching { o.getDouble("brightness").toFloat() }.getOrDefault(d.brightness),
            deleteCountdownNotification = runCatching { o.getBoolean("deleteCountdownNotification") }
                .getOrDefault(d.deleteCountdownNotification),
        )
    }
}
