package com.dazaike.photovault.backup

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : IOException("Wrong password")
class CorruptBackupException(message: String) : IOException(message)

private const val CHUNK = 64 * 1024
private const val TAG_BYTES = 16
private const val HEADER_SIZE = 33
private const val DAMAGED = "Backup is damaged"

/** Password-based chunked AES-256-GCM stream format ("PVBK" v1). */
object BackupCodec {
    const val ITERATIONS = 600_000
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(out: OutputStream, password: CharArray, iterations: Int = ITERATIONS): EncryptingStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val prefix = ByteArray(8).also(rnd::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(1).putInt(iterations).put(salt).put(prefix).array()
        val key = deriveKey(password, salt, iterations)
        out.write(header)
        return EncryptingStream(out, key, prefix, header)
    }

    /** [minIterations] exists so unit tests can use cheap key derivation; production callers keep the default. */
    fun decrypt(input: InputStream, password: CharArray, minIterations: Int = 100_000): InputStream {
        val header = ByteArray(HEADER_SIZE)
        try {
            readFully(input, header, HEADER_SIZE)
        } catch (e: EOFException) {
            throw CorruptBackupException("Not a PhotoVault backup")
        }
        val bb = ByteBuffer.wrap(header)
        val magic = ByteArray(4).also(bb::get)
        if (!magic.contentEquals(MAGIC) || bb.get().toInt() != 1) throw CorruptBackupException("Not a PhotoVault backup")
        val iterations = bb.getInt()
        if (iterations !in minIterations..10_000_000) {
            throw CorruptBackupException("Unsupported backup")
        }
        val salt = ByteArray(16).also(bb::get)
        val prefix = ByteArray(8).also(bb::get)
        return DecryptingStream(input, deriveKey(password, salt, iterations), prefix, header)
    }

}

private fun readFully(input: InputStream, buf: ByteArray, len: Int) {
    var off = 0
    while (off < len) {
        val n = input.read(buf, off, len - off)
        if (n < 0) throw EOFException()
        off += n
    }
}

class EncryptingStream internal constructor(
    private val out: OutputStream,
    private val key: SecretKeySpec,
    private val prefix: ByteArray,
    private val header: ByteArray,
) : OutputStream() {
    private val buf = ByteArray(CHUNK)
    private var len = 0
    private var counter = 0
    private var finished = false

    private fun flushChunk(final: Boolean) {
        val c = BackupCodecAccess.cipher(key, prefix, counter++, header, final, Cipher.ENCRYPT_MODE)
        val ct = c.doFinal(buf, 0, len)
        out.write(ByteBuffer.allocate(4).putInt(ct.size).array())
        out.write(ct)
        len = 0
    }

    override fun write(b: Int) {
        check(!finished)
        if (len == CHUNK) flushChunk(false)
        buf[len++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, n: Int) {
        check(!finished)
        var o = off
        var remaining = n
        while (remaining > 0) {
            if (len == CHUNK) flushChunk(false)
            val k = minOf(remaining, CHUNK - len)
            System.arraycopy(b, o, buf, len, k)
            len += k; o += k; remaining -= k
        }
    }

    fun finish() {
        if (finished) return
        finished = true
        flushChunk(true)
        out.flush()
    }

    override fun flush() {}

    override fun close() {
        try { finish() } finally { out.close() }
    }
}

internal object BackupCodecAccess {
    fun cipher(key: SecretKeySpec, prefix: ByteArray, counter: Int, header: ByteArray, final: Boolean, mode: Int): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key, GCMParameterSpec(128, nonce))
        c.updateAAD(header)
        c.updateAAD(byteArrayOf(if (final) 1 else 0))
        return c
    }
}

private class DecryptingStream(
    private val input: InputStream,
    private val key: SecretKeySpec,
    private val prefix: ByteArray,
    private val header: ByteArray,
) : InputStream() {
    private var plain = ByteArray(0)
    private var pos = 0
    private var counter = 0
    private var pendingLen = -1 // peeked length of next chunk
    private var done = false

    /** Reads a 4-byte length prefix; returns -1 on clean EOF. */
    private fun readLen(): Int {
        val b = ByteArray(4)
        val first = input.read()
        if (first < 0) return -1
        b[0] = first.toByte()
        try {
            val rest = ByteArray(3)
            readFully(input, rest, 3)
            System.arraycopy(rest, 0, b, 1, 3)
        } catch (e: EOFException) {
            throw CorruptBackupException(DAMAGED)
        }
        return ByteBuffer.wrap(b).getInt()
    }

    private fun nextChunk(): Boolean {
        if (done) return false
        val len = if (counter == 0) readLen() else pendingLen
        if (len < 0) throw CorruptBackupException(DAMAGED) // no chunks at all
        if (len !in TAG_BYTES..(CHUNK + TAG_BYTES)) throw CorruptBackupException(DAMAGED)
        val ct = ByteArray(len)
        try { readFully(input, ct, len) } catch (e: EOFException) { throw CorruptBackupException(DAMAGED) }
        val next = readLen()
        val final = next < 0
        if (!final && next !in TAG_BYTES..(CHUNK + TAG_BYTES)) throw CorruptBackupException(DAMAGED)
        try {
            plain = BackupCodecAccess.cipher(key, prefix, counter, header, final, Cipher.DECRYPT_MODE).doFinal(ct)
        } catch (e: GeneralSecurityException) {
            throw if (counter == 0) WrongPasswordException() else CorruptBackupException(DAMAGED)
        }
        counter++
        pos = 0
        pendingLen = next
        if (final) done = true
        return true
    }

    override fun read(): Int {
        while (pos >= plain.size) if (!nextChunk()) return -1
        return plain[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, n: Int): Int {
        if (n == 0) return 0
        while (pos >= plain.size) if (!nextChunk()) return -1
        val k = minOf(n, plain.size - pos)
        System.arraycopy(plain, pos, b, off, k)
        pos += k
        return k
    }

    override fun close() = input.close()
}

/** Emits `int32 n` + n bytes blocks; terminated by `int32 0` on [finish]. Does not close [out]. */
class BlockOutputStream(private val out: OutputStream) : OutputStream() {
    private val buf = ByteArray(CHUNK)
    private var len = 0

    private fun flushBlock() {
        if (len == 0) return
        out.write(ByteBuffer.allocate(4).putInt(len).array())
        out.write(buf, 0, len)
        len = 0
    }

    override fun write(b: Int) {
        if (len == CHUNK) flushBlock()
        buf[len++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, n: Int) {
        var o = off
        var remaining = n
        while (remaining > 0) {
            if (len == CHUNK) flushBlock()
            val k = minOf(remaining, CHUNK - len)
            System.arraycopy(b, o, buf, len, k)
            len += k; o += k; remaining -= k
        }
    }

    fun finish() {
        flushBlock()
        out.write(ByteArray(4))
    }
}

class BlockInputStream(private val input: InputStream) : InputStream() {
    private var remaining = 0
    private var ended = false

    private fun advance(): Boolean {
        while (remaining == 0) {
            if (ended) return false
            val b = ByteArray(4)
            try { readFully(input, b, 4) } catch (e: EOFException) { throw CorruptBackupException(DAMAGED) }
            val n = ByteBuffer.wrap(b).getInt()
            if (n < 0 || n > (1 shl 20)) throw CorruptBackupException(DAMAGED)
            if (n == 0) { ended = true; return false }
            remaining = n
        }
        return true
    }

    override fun read(): Int {
        if (!advance()) return -1
        val v = input.read()
        if (v < 0) throw CorruptBackupException(DAMAGED)
        remaining--
        return v
    }

    override fun read(b: ByteArray, off: Int, n: Int): Int {
        if (n == 0) return 0
        if (!advance()) return -1
        val k = input.read(b, off, minOf(n, remaining))
        if (k < 0) throw CorruptBackupException(DAMAGED)
        remaining -= k
        return k
    }
}

fun InputStream.drain() {
    val buf = ByteArray(CHUNK)
    while (read(buf) >= 0) { /* discard */ }
}
