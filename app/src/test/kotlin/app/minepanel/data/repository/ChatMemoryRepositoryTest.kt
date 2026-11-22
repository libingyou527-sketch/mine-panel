package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMemoryDraftDao
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryRepositoryTest {

    private val draftDao = mockk<ChatMemoryDraftDao>(relaxed = true)
    private val memoryDao = mockk<ChatMemoryDao>(relaxed = true)
    private val repo = ChatMemoryRepository(draftDao, memoryDao)

    @Test
    fun `addDraft stores trimmed pending item`() = runBlocking {
        val result = repo.addDraft("  偏好安静环境  ")

        assertTrue(result)
        coVerify {
            draftDao.insert(match<ChatMemoryDraft> {
                it.content == "偏好安静环境" && it.status == ChatMemoryRepository.STATUS_PENDING
            })
        }
    }

    @Test
    fun `blank draft is rejected without insert`() = runBlocking {
        assertFalse(repo.addDraft("   "))
        coVerify(exactly = 0) { draftDao.insert(any()) }
    }

    @Test
    fun `long draft is truncated to limit`() = runBlocking {
        val long = "中".repeat(ChatMemoryRepository.MAX_DRAFT_LEN + 40)
        val result = repo.addDraft(long)

        assertTrue(result)
        coVerify {
            draftDao.insert(match<ChatMemoryDraft> {
                it.content.length == ChatMemoryRepository.MAX_DRAFT_LEN
            })
        }
    }

    @Test
    fun `duplicate pending draft is rejected`() = runBlocking {
        coEvery { draftDao.countContent("重复", ChatMemoryRepository.STATUS_PENDING) } returns 1

        assertFalse(repo.addDraft("重复"))
        coVerify(exactly = 0) { draftDao.insert(any()) }
    }

    @Test
    fun `duplicate accepted memory is rejected as draft`() = runBlocking {
        coEvery { memoryDao.countContent("已存在") } returns 1

        assertFalse(repo.addDraft("已存在"))
        coVerify(exactly = 0) { draftDao.insert(any()) }
    }

    @Test
    fun `accept writes memory and settles draft`() = runBlocking {
        val draft = ChatMemoryDraft(
            id = 9, content = "晚上不聊工作", status = ChatMemoryRepository.STATUS_PENDING,
            createdAt = 1L, reviewedAt = null
        )
        coEvery { draftDao.byId(9) } returns draft
        coEvery { memoryDao.countContent("晚上不聊工作") } returns 0

        assertTrue(repo.accept(9))
        coVerify {
            memoryDao.insert(match<ChatMemory> {
                it.content == "晚上不聊工作" && it.source == ChatMemoryRepository.SOURCE_CHAT
            })
            draftDao.review(9, ChatMemoryRepository.STATUS_ACCEPTED, any())
        }
    }

    @Test
    fun `accept duplicate memory does not insert again but settles draft`() = runBlocking {
        val draft = ChatMemoryDraft(
            id = 10, content = "旧要点", status = ChatMemoryRepository.STATUS_PENDING,
            createdAt = 1L, reviewedAt = null
        )
        coEvery { draftDao.byId(10) } returns draft
        coEvery { memoryDao.countContent("旧要点") } returns 1

        assertTrue(repo.accept(10))
        coVerify(exactly = 0) { memoryDao.insert(any()) }
        coVerify { draftDao.review(10, ChatMemoryRepository.STATUS_ACCEPTED, any()) }
    }

    @Test
    fun `non pending draft cannot be accepted`() = runBlocking {
        val draft = ChatMemoryDraft(
            id = 11, content = "x", status = ChatMemoryRepository.STATUS_REJECTED,
            createdAt = 1L, reviewedAt = 2L
        )
        coEvery { draftDao.byId(11) } returns draft

        assertFalse(repo.accept(11))
        coVerify(exactly = 0) { memoryDao.insert(any()) }
    }

    @Test
    fun `reject settles draft without memory`() = runBlocking {
        val draft = ChatMemoryDraft(
            id = 12, content = "y", status = ChatMemoryRepository.STATUS_PENDING,
            createdAt = 1L, reviewedAt = null
        )
        coEvery { draftDao.byId(12) } returns draft

        assertTrue(repo.reject(12))
        coVerify { draftDao.review(12, ChatMemoryRepository.STATUS_REJECTED, any()) }
        coVerify(exactly = 0) { memoryDao.insert(any()) }
    }

    @Test
    fun `list and delete delegate to dao`() = runBlocking {
        val memories = listOf(ChatMemory(id = 1, content = "a", source = "chat", createdAt = 1L))
        coEvery { memoryDao.all() } returns memories

        assertEquals(memories, repo.memories())
        repo.deleteMemory(1)
        coVerify { memoryDao.deleteById(1) }
    }

    @Test
    fun `clearAll delegates to both dao clears`() = runBlocking {
        repo.clearAll()
        coVerify { draftDao.clear() }
        coVerify { memoryDao.clear() }
    }
}
