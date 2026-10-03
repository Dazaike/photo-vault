package com.dazaike.photovault.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val pw = "correct horse".toCharArray()
    private val iter = 1000

    private fun seal(payload: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        BackupCodec.encrypt(bos, pw.copyOf(), iter).use { enc ->
            val blocks = BlockOutputStream(enc)
            blocks.write(payload)
            blocks.finish()
        }
        return bos.toByteArray()
    }

    private fun open(data: ByteArray, password: CharArray = pw): ByteArray {
        val s = BackupCodec.decrypt(ByteArrayInputStream(data), password.copyOf(), 1)
        val out = BlockInputStream(s).readBytes()
        assertEquals(-1, s.read())
        return out
    }

    private fun random(n: Int) = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }

    @Test fun roundTripSizes() {
        for (n in intArrayOf(0, 65_536, 200_000)) {
            val p = random(n)
            assertArrayEquals("size $n", p, open(seal(p)))
        }
    }

    @Test fun wrongPassword() {
        try {
            open(seal(random(100)), "nope".toCharArray())
            fail()
        } catch (_: WrongPasswordException) {
        }
    }

    @Test fun truncatedAtChunkBoundaryDetected() {
        val sealed = seal(random(200_000))
        // chunk wire size: 4 + len + 16. Walk to find the last chunk start.
        var pos = 33
        var lastStart = pos
        while (pos < sealed.size) {
            lastStart = pos
            val len = java.nio.ByteBuffer.wrap(sealed, pos, 4).int
            pos += 4 + len
        }
        try {
            open(sealed.copyOf(lastStart))
            fail()
        } catch (_: CorruptBackupException) {
        }
    }

    @Test fun flippedByteInSecondChunkDetected() {
        val sealed = seal(random(200_000))
        val firstLen = java.nio.ByteBuffer.wrap(sealed, 33, 4).int
        sealed[33 + 4 + firstLen + 4 + 10] = (sealed[33 + 4 + firstLen + 4 + 10].toInt() xor 1).toByte()
        try {
            open(sealed)
            fail()
        } catch (_: CorruptBackupException) {
        }
    }

    @Test fun badMagic() {
        val sealed = seal(random(10))
        sealed[0] = 'X'.code.toByte()
        try {
            open(sealed)
            fail()
        } catch (e: CorruptBackupException) {
            assertEquals("Not a PhotoVault backup", e.message)
        }
    }
}
