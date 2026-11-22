package app.minepanel.data.repository

import android.content.Context
import app.minepanel.core.security.BackupCipher
import app.minepanel.core.security.BackupCryptoException
import app.minepanel.data.export.ExportedBadge
import app.minepanel.data.export.ExportedFragment
import app.minepanel.data.export.ExportedMemory
import app.minepanel.data.export.ExportedScore
import app.minepanel.data.export.ExportedTag
import app.minepanel.data.export.ExportBundle
import app.minepanel.data.export.JsonExporter
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRepositoryTest {

    private val repo = BackupRepository(mockk<Context>(relaxed = true))
    private val charset = Charsets.UTF_8

    private fun sampleBundle() = ExportBundle(
        schemaVersion = JsonExporter.SCHEMA_VERSION,
        exportedAt = 1000L,
        appVersion = "test",
        confirmedMemories = listOf(ExportedMemory(0, "记忆A", 1000L, 2000L, 1500L)),
        statusTags = listOf(ExportedTag(0, "规律", 1600L, 1700L)),
        attributeScores = listOf(ExportedScore("FOCUS", 0L, 7L, 35, 0.62, "{}", 7, 1800L)),
        badges = listOf(ExportedBadge("first_week", 1900L, "{}")),
        rawEvents = null,
        manualFragments = listOf(ExportedFragment("手写片段", "平静", "WORK", null, 4000L, 4100L))
    )

    private fun encryptedBlob(password: String): ByteArray {
        val json = JsonExporter.toJson(sampleBundle())
        return BackupCipher.encrypt(json.toByteArray(charset), password)
    }

    @Test
    fun `decrypt with correct password parses all sections`() {
        val parsed = repo.decryptToParsed(encryptedBlob("backup-pass"), "backup-pass")

        assertEquals(1, parsed.memories.size)
        assertEquals("记忆A", parsed.memories.first().content)
        assertEquals(1, parsed.tags.size)
        assertEquals("规律", parsed.tags.first().label)
        assertEquals(1, parsed.scores.size)
        assertEquals("FOCUS", parsed.scores.first().attributeKey)
        assertEquals(1, parsed.badges.size)
        assertEquals("first_week", parsed.badges.first().badgeKey)
        assertEquals(1, parsed.fragments.size)
        assertEquals("手写片段", parsed.fragments.first().content)
    }

    @Test
    fun `wrong password fails before parsing`() {
        val result = runCatching { repo.decryptToParsed(encryptedBlob("correct"), "wrong") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BackupCryptoException)
    }

    @Test
    fun `non backup binary is rejected`() {
        val blob = "not-a-backup".toByteArray(charset)
        val result = runCatching { repo.decryptToParsed(blob, "pass") }
        assertTrue(result.isFailure)
    }
}
