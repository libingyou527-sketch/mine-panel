package app.minepanel.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExporterTest {

    private fun bundle(
        rawEvents: List<ExportedRawEvent>? = null,
        fragments: List<ExportedFragment> = emptyList()
    ) = ExportBundle(
        schemaVersion = JsonExporter.SCHEMA_VERSION,
        exportedAt = 1_700_000_000_000L,
        appVersion = "0.1.0",
        confirmedMemories = listOf(
            ExportedMemory(
                id = 1,
                content = "专注记录",
                periodStart = 1000L,
                periodEnd = 2000L,
                createdAt = 1500L
            )
        ),
        statusTags = listOf(
            ExportedTag(id = 2, label = "规律", createdAt = 1600L, reviewedAt = 1700L)
        ),
        attributeScores = listOf(
            ExportedScore(
                attributeKey = "FOCUS",
                periodStart = 0L,
                periodEnd = 7L,
                score = 35,
                confidence = 0.62,
                evidenceJson = "{}",
                sampleDays = 7,
                updatedAt = 1800L
            )
        ),
        badges = listOf(
            ExportedBadge(badgeKey = "first_week", unlockedAt = 1900L, conditionSnapshotJson = "{}")
        ),
        rawEvents = rawEvents,
        manualFragments = fragments
    )

    @Test
    fun `escape quotes backslashes and control chars`() {
        val escaped = JsonExporter.escape("a\"b\\c\nd\t")
        assertEquals("a\\\"b\\\\c\\nd\\t", escaped)
    }

    @Test
    fun `writes schema version meta and sections`() {
        val json = JsonExporter.toJson(bundle())

        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.contains("\"exportedAt\":1700000000000"))
        assertTrue(json.contains("\"appVersion\":\"0.1.0\""))
        assertTrue(json.contains("\"confirmedMemories\""))
        assertTrue(json.contains("\"statusTags\""))
        assertTrue(json.contains("\"attributeScores\""))
        assertTrue(json.contains("\"badges\""))
    }

    @Test
    fun `keeps chinese characters and does not escape them`() {
        val json = JsonExporter.toJson(bundle())
        assertTrue(json.contains("专注记录"))
        assertTrue(json.contains("规律"))
    }

    @Test
    fun `omits raw events section when null`() {
        val json = JsonExporter.toJson(bundle(rawEvents = null))
        assertFalse(json.contains("\"rawEvents\""))
    }

    @Test
    fun `includes raw events section when present`() {
        val event = ExportedRawEvent(
            timestamp = 1234L,
            eventType = "FOREGROUND",
            category = "WORK",
            durationMs = 5000L,
            sessionId = "s1"
        )
        val json = JsonExporter.toJson(bundle(rawEvents = listOf(event)))

        assertTrue(json.contains("\"rawEvents\":["))
        assertTrue(json.contains("\"sessionId\":\"s1\""))
    }

    @Test
    fun `null session id serializes as null`() {
        val event = ExportedRawEvent(
            timestamp = 1234L,
            eventType = "UNLOCK",
            category = "REST",
            durationMs = 0L,
            sessionId = null
        )
        val json = JsonExporter.toJson(bundle(rawEvents = listOf(event)))
        assertTrue(json.contains("\"sessionId\":null"))
    }

    @Test
    fun `serializes fragment with nullable fields`() {
        val fragment = ExportedFragment(
            content = "手写片段",
            mood = "平静",
            category = "WORK",
            anchorEpochMs = null,
            createdAt = 4000L,
            updatedAt = 4100L
        )
        val json = JsonExporter.toJson(bundle(fragments = listOf(fragment)))

        assertTrue(json.contains("\"manualFragments\":["))
        assertTrue(json.contains("\"content\":\"手写片段\""))
        assertTrue(json.contains("\"mood\":\"平静\""))
        assertTrue(json.contains("\"category\":\"WORK\""))
        assertTrue(json.contains("\"anchorEpochMs\":null"))
    }
}
