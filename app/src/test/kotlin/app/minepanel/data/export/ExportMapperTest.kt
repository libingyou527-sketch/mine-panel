package app.minepanel.data.export

import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.RawUsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportMapperTest {

    @Test
    fun `memory maps fields`() {
        val entity = ConfirmedMemory(
            id = 11,
            originDraftId = 3L,
            content = "一段确认记忆",
            periodStart = 100L,
            periodEnd = 200L,
            createdAt = 150L
        )
        val mapped = ExportMapper.memory(entity)

        assertEquals(11L, mapped.id)
        assertEquals("一段确认记忆", mapped.content)
        assertEquals(100L, mapped.periodStart)
        assertEquals(200L, mapped.periodEnd)
        assertEquals(150L, mapped.createdAt)
    }

    @Test
    fun `tag prefers edited content and falls back to draft text`() {
        val edited = DraftMemory(
            id = 5,
            modelRunId = 2L,
            draftType = "STATUS_TAG",
            contentJson = "原文标签",
            status = "MODIFIED",
            userEditedContent = "修正标签",
            createdAt = 300L,
            reviewedAt = 320L
        )
        assertEquals("修正标签", ExportMapper.tag(edited).label)

        val original = edited.copy(userEditedContent = null)
        assertEquals("原文标签", ExportMapper.tag(original).label)
        assertEquals(320L, ExportMapper.tag(original).reviewedAt)
    }

    @Test
    fun `narrative prefers edited content and falls back to draft text`() {
        val edited = DraftMemory(
            id = 8,
            modelRunId = 2L,
            draftType = "NARRATIVE",
            contentJson = "原文回顾",
            status = "MODIFIED",
            userEditedContent = "修正后的回顾",
            createdAt = 300L,
            reviewedAt = 320L
        )
        assertEquals("修正后的回顾", ExportMapper.narrative(edited).content)

        val original = edited.copy(userEditedContent = null)
        assertEquals("原文回顾", ExportMapper.narrative(original).content)
        assertEquals(320L, ExportMapper.narrative(original).reviewedAt)
    }

    @Test
    fun `score maps fields`() {
        val entity = AttributeScore(
            id = 7,
            attributeKey = "VITALITY",
            periodStart = 1L,
            periodEnd = 8L,
            score = 70,
            confidence = 0.8,
            evidenceJson = "{}",
            sampleDays = 6,
            updatedAt = 999L
        )
        val mapped = ExportMapper.score(entity)

        assertEquals("VITALITY", mapped.attributeKey)
        assertEquals(70, mapped.score)
        assertEquals(0.8, mapped.confidence, 1e-9)
        assertEquals(6, mapped.sampleDays)
        assertEquals(999L, mapped.updatedAt)
    }

    @Test
    fun `badge and raw event map fields`() {
        val badge = Badge(badgeKey = "streak_3", unlockedAt = 555L, conditionSnapshotJson = "{}")
        assertEquals("streak_3", ExportMapper.badge(badge).badgeKey)
        assertEquals(555L, ExportMapper.badge(badge).unlockedAt)

        val raw = RawUsageEvent(
            id = 9,
            timestamp = 777L,
            eventType = "FOREGROUND",
            category = "SOCIAL",
            durationMs = 3210L,
            sessionId = null
        )
        val mapped = ExportMapper.rawEvent(raw)
        assertEquals(777L, mapped.timestamp)
        assertEquals("SOCIAL", mapped.category)
        assertEquals(3210L, mapped.durationMs)
        assertNull(mapped.sessionId)
    }

    @Test
    fun `fragment maps fields`() {
        val entity = ManualFragment(
            id = 13,
            content = "手写片段",
            mood = "平静",
            category = "WORK",
            anchorEpochMs = 4321L,
            createdAt = 4000L,
            updatedAt = 4100L
        )
        val mapped = ExportMapper.fragment(entity)

        assertEquals("手写片段", mapped.content)
        assertEquals("平静", mapped.mood)
        assertEquals("WORK", mapped.category)
        assertEquals(4321L, mapped.anchorEpochMs)
        assertEquals(4000L, mapped.createdAt)
        assertEquals(4100L, mapped.updatedAt)
    }
}
