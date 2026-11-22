package app.minepanel.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCipherTest {

    private val sample = "已确认记忆：专注 38；标签：规律；片段：手写一段中文".toByteArray(Charsets.UTF_8)

    @Test
    fun `encrypt then decrypt restores plaintext including chinese`() {
        val blob = BackupCipher.encrypt(sample, "my-pass-123")
        val restored = BackupCipher.decrypt(blob, "my-pass-123")
        assertArrayEquals(sample, restored)
    }

    @Test
    fun `round trip handles empty plaintext`() {
        val empty = ByteArray(0)
        val blob = BackupCipher.encrypt(empty, "口令")
        assertArrayEquals(empty, BackupCipher.decrypt(blob, "口令"))
    }

    @Test
    fun `round trip handles binary bytes`() {
        val binary = ByteArray(64) { (it * 37 % 256).toByte() }
        val blob = BackupCipher.encrypt(binary, "x")
        assertArrayEquals(binary, BackupCipher.decrypt(blob, "x"))
    }

    @Test
    fun `wrong password fails authentication`() {
        val blob = BackupCipher.encrypt(sample, "correct-password")
        val result = runCatching { BackupCipher.decrypt(blob, "wrong-password") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BackupCryptoException)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val blob = BackupCipher.encrypt(sample, "pass")
        blob[blob.size - 1] = blob[blob.size - 1].inc()
        val result = runCatching { BackupCipher.decrypt(blob, "pass") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `unknown magic header is rejected`() {
        val blob = BackupCipher.encrypt(sample, "pass")
        blob[0] = 'X'.code.toByte()
        val result = runCatching { BackupCipher.decrypt(blob, "pass") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `truncated blob is rejected`() {
        val blob = BackupCipher.encrypt(sample, "pass")
        val truncated = blob.copyOf(blob.size - 8)
        val result = runCatching { BackupCipher.decrypt(truncated, "pass") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `same password produces different blobs due to random salt and iv`() {
        val first = BackupCipher.encrypt(sample, "pass")
        val second = BackupCipher.encrypt(sample, "pass")
        assertNotEquals(first.contentToString(), second.contentToString())
        assertArrayEquals(sample, BackupCipher.decrypt(first, "pass"))
        assertArrayEquals(sample, BackupCipher.decrypt(second, "pass"))
    }

    @Test
    fun `empty password is rejected on both directions`() {
        assertFalse(runCatching { BackupCipher.encrypt(sample, "") }.isSuccess)
        val blob = BackupCipher.encrypt(sample, "pass")
        assertFalse(runCatching { BackupCipher.decrypt(blob, "") }.isSuccess)
    }
}
