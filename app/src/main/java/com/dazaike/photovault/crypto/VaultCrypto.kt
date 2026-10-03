package com.dazaike.photovault.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Sole owner of at-rest encryption. Every other component operates on plaintext
 * [Bitmap]/[ByteArray] values only; nothing outside this class touches
 * [EncryptedFile]/[MasterKey] directly.
 */
class VaultCrypto(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun encryptStream(source: InputStream, destFile: File) {
        if (destFile.exists()) destFile.delete()
        EncryptedFile.Builder(
            appContext,
            destFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build().openFileOutput().use { out -> source.copyTo(out, bufferSize = 64 * 1024) }
    }

    fun encryptBytes(bytes: ByteArray, destFile: File) = encryptStream(ByteArrayInputStream(bytes), destFile)

    private fun decryptBytes(encFile: File): ByteArray =
        EncryptedFile.Builder(
            appContext,
            encFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build().openFileInput().use { it.readBytes() }

    /** Decrypts [encFile] and decodes it downsampled so its largest side is close to [maxDimensionPx]. */
    fun decryptBitmap(encFile: File, maxDimensionPx: Int): Bitmap? {
        val bytes = decryptBytes(encFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (largestSide / (sample * 2) >= maxDimensionPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /**
     * Decrypts [encFile] directly to [destination] stream without buffering entire file in memory.
     */
    fun decryptToStream(encFile: File, destination: java.io.OutputStream) {
        EncryptedFile.Builder(
            appContext,
            encFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build().openFileInput().use { input ->
            input.copyTo(destination, bufferSize = 64 * 1024)
        }
    }

    /**
     * Decrypts [encFile] and writes the plaintext bytes to [destFile]. Used only to
     * stage a video for local playback (VideoView cannot read EncryptedFile
     * directly); callers own deleting [destFile] once playback ends.
     */
    fun decryptToFile(encFile: File, destFile: File) {
        if (destFile.exists()) destFile.delete()
        destFile.outputStream().use { out ->
            decryptToStream(encFile, out)
        }
    }
}
