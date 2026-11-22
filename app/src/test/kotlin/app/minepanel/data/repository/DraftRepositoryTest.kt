package app.minepanel.data.repository

import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.core.model.ModelStatus
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DraftMemoryDao
import app.minepanel.data.dao.ModelRunLogDao
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.model.ModelOutput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftRepositoryTest {

    private val draftDao = mockk<DraftMemoryDao>(relaxed = true)
    private val confirmedDao = mockk<ConfirmedMemoryDao>(relaxed = true)
    private val runLogDao = mockk<ModelRunLogDao>(relaxed = true)
    private val repo = DraftRepository(draftDao, confirmedDao, runLogDao)

    private fun draft(id: Long, status: String, type: String = DraftType.MEMORY.name) = DraftMemory(
        id = id,
        modelRunId = null,
        draftType = type,
        contentJson = "原文要点",
        status = status,
        userEditedContent = null,
        createdAt = 1000L,
        reviewedAt = null
    )

    @Test
    fun `confirm pending draft writes confirmed memory`() = runBlocking {
        coEvery { draftDao.byId(1) } returns draft(1, DraftStatus.PENDING.name)

        val result = repo.confirm(1)

        assertTrue(result)
        coVerify { draftDao.review(1, DraftStatus.CONFIRMED.name, null, any()) }
        coVerify { confirmedDao.insert(match<ConfirmedMemory> { it.content == "原文要点" && it.originDraftId == 1L }) }
    }

    @Test
    fun `modify stores edited text as confirmed memory`() = runBlocking {
        coEvery { draftDao.byId(2) } returns draft(2, DraftStatus.PENDING.name)

        val result = repo.modify(2, "修正后的要点")

        assertTrue(result)
        coVerify { draftDao.review(2, DraftStatus.MODIFIED.name, "修正后的要点", any()) }
        coVerify { confirmedDao.insert(match<ConfirmedMemory> { it.content == "修正后的要点" }) }
    }

    @Test
    fun `reject does not write confirmed memory`() = runBlocking {
        coEvery { draftDao.byId(3) } returns draft(3, DraftStatus.PENDING.name)

        val result = repo.reject(3)

        assertTrue(result)
        coVerify { draftDao.review(3, DraftStatus.REJECTED.name, null, any()) }
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `non pending draft cannot be re-adjudicated`() = runBlocking {
        coEvery { draftDao.byId(4) } returns draft(4, DraftStatus.CONFIRMED.name)

        assertFalse(repo.confirm(4))
        assertFalse(repo.reject(4))
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `insert drafts splits narrative tags and memories`() = runBlocking {
        val output = ModelOutput(
            panelNarrative = "一句回顾",
            statusTags = listOf("标签A", "标签B"),
            draftMemories = listOf("记忆A"),
            status = ModelStatus.SUCCESS,
            engineName = "gguf"
        )

        val ids = repo.insertDrafts(runLogId = 9L, output = output, periodStart = 0L, periodEnd = 1L)

        assertEquals(4, ids.size)
        coVerify(exactly = 4) { draftDao.insert(any()) }
    }

    private fun autoOutput() = ModelOutput(
        panelNarrative = "一句回顾",
        statusTags = listOf("标签A"),
        draftMemories = listOf("记忆A"),
        status = ModelStatus.SUCCESS,
        engineName = "gguf"
    )

    @Test
    fun `without auto confirm drafts stay pending`() = runBlocking {
        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = false)

        coVerify(exactly = 0) { confirmedDao.insert(any()) }
        coVerify(exactly = 0) { draftDao.review(any(), any(), any(), any()) }
    }

    @Test
    fun `auto confirm settles non duplicate drafts`() = runBlocking {
        coEvery { confirmedDao.countContent(any()) } returns 0
        coEvery { draftDao.countConfirmedContent(any(), any(), any()) } returns 0

        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = true)

        coVerify(exactly = 3) { draftDao.review(any(), DraftStatus.CONFIRMED.name, null, any()) }
        // 自动确认后仅长期记忆写入正式记忆表；标签与回顾文案只置已确认状态。
        coVerify(exactly = 1) { confirmedDao.insert(match<ConfirmedMemory> { it.content == "记忆A" }) }
    }

    @Test
    fun `duplicate tag is kept pending`() = runBlocking {
        coEvery {
            draftDao.countConfirmedContent(
                "标签A",
                DraftType.STATUS_TAG.name,
                DraftStatus.CONFIRMED.name
            )
        } returns 1
        coEvery { confirmedDao.countContent(any()) } returns 0

        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = true)

        // 标签重复留待人工；仅记忆写入正式记忆表。
        coVerify(exactly = 1) { confirmedDao.insert(match<ConfirmedMemory> { it.content == "记忆A" }) }
    }

    @Test
    fun `duplicate memory is kept pending`() = runBlocking {
        coEvery { confirmedDao.countContent("记忆A") } returns 1
        coEvery { draftDao.countConfirmedContent(any(), any(), any()) } returns 0

        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = true)

        // 记忆重复留待人工；标签与叙述不写正式记忆表。
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `auto confirm lookup failure falls back to pending without crash`() = runBlocking {
        coEvery { confirmedDao.countContent(any()) } throws RuntimeException("db down")
        coEvery { draftDao.countConfirmedContent(any(), any(), any()) } returns 0

        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = true)

        coVerify(exactly = 0) { confirmedDao.insert(match<ConfirmedMemory> { it.content == "记忆A" }) }
        coVerify(exactly = 0) { confirmedDao.insert(match<ConfirmedMemory> { it.content == "一句回顾" }) }
    }

    @Test
    fun `manual confirm writes manual source`() = runBlocking {
        coEvery { draftDao.byId(1) } returns draft(1, DraftStatus.PENDING.name)

        repo.confirm(1)

        coVerify {
            confirmedDao.insert(match<ConfirmedMemory> { it.source == ConfirmedMemory.SOURCE_MANUAL })
        }
    }

    @Test
    fun `auto confirm writes auto model source`() = runBlocking {
        coEvery { confirmedDao.countContent(any()) } returns 0
        coEvery { draftDao.countConfirmedContent(any(), any(), any()) } returns 0

        repo.insertDrafts(9L, autoOutput(), 0L, 1L, autoConfirm = true)

        coVerify(exactly = 1) {
            confirmedDao.insert(match<ConfirmedMemory> {
                it.content == "记忆A" && it.source == ConfirmedMemory.SOURCE_AUTO_MODEL
            })
        }
    }

    @Test
    fun `confirm tag does not write confirmed memory`() = runBlocking {
        coEvery { draftDao.byId(5) } returns draft(5, DraftStatus.PENDING.name, DraftType.STATUS_TAG.name)

        repo.confirm(5)

        coVerify { draftDao.review(5, DraftStatus.CONFIRMED.name, null, any()) }
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `confirm narrative does not write confirmed memory`() = runBlocking {
        coEvery { draftDao.byId(6) } returns draft(6, DraftStatus.PENDING.name, DraftType.NARRATIVE.name)

        repo.modify(6, "润色后的回顾")

        coVerify { draftDao.review(6, DraftStatus.MODIFIED.name, "润色后的回顾", any()) }
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `confirmed tags query confirmed statuses of tag type`() = runBlocking {
        repo.confirmedTags()

        coVerify {
            draftDao.byTypeAndStatuses(
                DraftType.STATUS_TAG.name,
                listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
            )
        }
    }

    @Test
    fun `confirmed narratives query confirmed statuses of narrative type`() = runBlocking {
        repo.confirmedNarratives()

        coVerify {
            draftDao.byTypeAndStatuses(
                DraftType.NARRATIVE.name,
                listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
            )
        }
    }

    @Test
    fun `revoke deletes confirmed memory by id`() = runBlocking {
        repo.revoke(7)

        coVerify { confirmedDao.deleteById(7) }
    }
}
