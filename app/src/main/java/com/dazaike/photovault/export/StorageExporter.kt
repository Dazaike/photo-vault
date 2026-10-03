package com.dazaike.photovault.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dazaike.photovault.data.UiSettingsStore
import com.dazaike.photovault.crypto.VaultCrypto
import com.dazaike.photovault.data.VaultItemEntity
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AutoDeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (StorageExporter.cleanExpiredRecords(applicationContext)) Result.retry() else Result.success()
    }
}

class AutoDeleteReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                if (intent.action == DeleteCountdownNotifier.ACTION_DELETE_NOW) {
                    val batch = intent.getLongExtra(DeleteCountdownNotifier.EXTRA_EXPIRY, 0L)
                    StorageExporter.deleteBatchNow(appContext, batch)
                    DeleteCountdownNotifier.cancel(appContext, batch)
                } else {
                    StorageExporter.cleanExpiredRecords(appContext)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object StorageExporter {
    private const val PREFS_NAME = "storage_export_cleanup"
    private const val GIVE_UP_AFTER_MS = 24L * 60 * 60 * 1000
    private const val KEY_EXPORTS = "pending_exports"
    private val scope = CoroutineScope(Dispatchers.IO)
    suspend fun exportItemToStorage(
        context: Context,
        item: VaultItemEntity,
        originalEncryptedFile: File,
        crypto: VaultCrypto,
        targetTreeUri: Uri? = null,
        updateTimestampToNow: Boolean = false,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val isVideo = item.mimeType.startsWith("video/")
            val nowMs = System.currentTimeMillis()
            val nowSec = nowMs / 1000L

            // If we need to update timestamp/EXIF, decrypt to a temp file, update metadata, then stream out
            val tempFile = if (updateTimestampToNow && !isVideo) {
                val temp = File(context.cacheDir, "export_${System.currentTimeMillis()}_${item.id}.tmp")
                crypto.decryptToFile(originalEncryptedFile, temp)
                runCatching {
                    val exif = ExifInterface(temp)
                    val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getDefault()
                    }
                    val dateStr = sdf.format(java.util.Date(nowMs))
                    exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                    exif.saveAttributes()
                }
                temp.setLastModified(nowMs)
                temp
            } else null

            try {
                if (targetTreeUri != null) {
                    // User-specified custom directory via Storage Access Framework
                    val targetDir = DocumentFile.fromTreeUri(context, targetTreeUri) ?: return@runCatching null
                    val newFile = targetDir.createFile(item.mimeType, item.originalName) ?: return@runCatching null
                    val outStream = resolver.openOutputStream(newFile.uri) ?: run {
                        newFile.delete()
                        return@runCatching null
                    }
                    outStream.use { output ->
                        if (tempFile != null) {
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        } else {
                            crypto.decryptToStream(originalEncryptedFile, output)
                        }
                    }
                    newFile.uri
                } else {
                    // Default MediaStore directory
                    val collection = if (isVideo) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                    }

                    val relativeFolder = if (isVideo) "Movies/PhotoVault" else "Pictures/PhotoVault"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        if (updateTimestampToNow) {
                            put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
                            put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
                            if (isVideo) {
                                put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                            } else {
                                put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val uri = resolver.insert(collection, values) ?: return@runCatching null

                    val outStream = resolver.openOutputStream(uri) ?: run {
                        resolver.delete(uri, null, null)
                        return@runCatching null
                    }

                    outStream.use { output ->
                        if (tempFile != null) {
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        } else {
                            crypto.decryptToStream(originalEncryptedFile, output)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        if (updateTimestampToNow) {
                            values.put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
                            if (isVideo) {
                                values.put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                            } else {
                                values.put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                            }
                        }
                        resolver.update(uri, values, null, null)
                    }

                    uri
                }
            } finally {
                tempFile?.delete()
            }
        }.getOrNull()
    }

    /** Returns true if the target is gone (deleted or already missing); false if deletion failed and should be retried. */
    fun deleteExportedUri(context: Context, uri: Uri): Boolean {
        return try {
            val authority = uri.authority.orEmpty()
            if (DocumentsContract.isDocumentUri(context, uri) || authority.contains("documents") || authority.contains("externalstorage")) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (e: java.io.FileNotFoundException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    fun scheduleAutoDelete(context: Context, uris: List<Uri>, delaySeconds: Int) {
        if (uris.isEmpty() || delaySeconds <= 0) return
        val appContext = context.applicationContext
        val expiryEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds.toLong())

        // 1. Persistent record: the source of truth. Any later run (worker, foreground, app start) deletes what is expired.
        recordPendingExports(appContext, uris.map { it.toString() }, expiryEpochMs)

        // 2. WorkManager fallback for when the process is dead (may be deferred by Doze).
        val request = OneTimeWorkRequestBuilder<AutoDeleteWorker>()
            .setInitialDelay(delaySeconds.toLong(), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
        scheduleAlarm(appContext, expiryEpochMs)
        if (UiSettingsStore(appContext).load().deleteCountdownNotification) {
            DeleteCountdownNotifier.show(appContext, uris.size, expiryEpochMs)
        }

        // 3. In-process timer for the foreground case. delay() pauses during deep sleep, so the other paths cover that.
        scope.launch {
            delay(TimeUnit.SECONDS.toMillis(delaySeconds.toLong()))
            cleanExpiredRecords(appContext)
        }
    }

    private fun scheduleAlarm(context: Context, atMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = android.app.PendingIntent.getBroadcast(
            context,
            (atMs / 1000).toInt(),
            Intent(context, AutoDeleteReceiver::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMs, pi)
        } else {
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    @Synchronized
    private fun recordPendingExports(context: Context, uris: List<String>, expiryEpochMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_EXPORTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(existingJson) }.getOrDefault(JSONArray())
        uris.forEach { uri ->
            val obj = JSONObject().apply {
                put("uri", uri)
                put("expiry", expiryEpochMs)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_EXPORTS, array.toString()).commit()
    }

    /**
     * Deletes every expired record. A record is dropped only once the file is gone;
     * failures are kept for retry (abandoned [GIVE_UP_AFTER_MS] past expiry).
     * Returns true if expired records remain that should be retried.
     */
    @Synchronized
    fun cleanExpiredRecords(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_EXPORTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(existingJson) }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        val remaining = JSONArray()
        var retry = false

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val expiry = obj.optLong("expiry", 0)
            if (now < expiry) {
                remaining.put(obj)
            } else if (!deleteExportedUri(context, Uri.parse(obj.optString("uri"))) && now - expiry < GIVE_UP_AFTER_MS) {
                remaining.put(obj)
                retry = true
            }
        }
        prefs.edit().putString(KEY_EXPORTS, remaining.toString()).commit()
        return retry
    }

    /** Deletes the batch scheduled to expire at [batchExpiryEpochMs] right now (the notification's "Delete now"). */
    @Synchronized
    fun deleteBatchNow(context: Context, batchExpiryEpochMs: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString(KEY_EXPORTS, "[]") ?: "[]") }.getOrDefault(JSONArray())
        val expiredNow = System.currentTimeMillis() - 1
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optLong("expiry", 0) == batchExpiryEpochMs) obj.put("expiry", expiredNow)
        }
        prefs.edit().putString(KEY_EXPORTS, array.toString()).commit()
        return cleanExpiredRecords(context)
    }
}
