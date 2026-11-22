package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.dao.ChatSessionDao
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.db.entity.ChatSession
import app.minepanel.data.export.ExportedChatMemory
import app.minepanel.data.export.ExportedChatMessage
import app.minepanel.data.export.ParsedChatImport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatImportRepositoryTest {

    private val sessionDao = mockk<ChatSessionDao>(relaxed = true)
    private val messageDao = mockk<ChatMessageDao>(relaxed = true)
    private val memoryDao = mockk<ChatMemoryDao>(relaxed = true)
    private val repo = ChatImportRepository(sessionDao, messageDao, memoryDao)

    @Test
    fun `persist creates session, writes messages and dedupes memories`() = runBlocking {
        coEvery { sessionDao.insert(any()) } returns 42L
        coEvery { memoryDao.countContent("已存在要点") } returns 1

        val parsed = ParsedChatImport(
            messages = listOf(
                ExportedChatMessage("USER", "你好", 1L),
                ExportedChatMessage("ASSISTANT", "在的", 2L),
                ExportedChatMessage("USER", "   ", 3L)
            ),
            memories = listOf(
                ExportedChatMemory("新要点", 4L),
                ExportedChatMemory("已存在要点", 5L)
            ),
            sessionTitle = "旧会话"
        )

        val result = repo.persist(parsed)

        assertEquals(42L, result.sessionId)
        assertEquals(2, result.messageCount)
        assertEquals(1, result.memoryCount)

        coVerify {
            sessionDao.insert(match<ChatSession> { it.title == "旧会话" })
            messageDao.insert(match<ChatMessage> {
                it.sessionId == 42L && it.content == "你好"
            })
            messageDao.insert(match<ChatMessage> {
                it.sessionId == 42L && it.content == "在的"
            })
            memoryDao.insert(match<ChatMemory> {
                it.content == "新要点" && it.source == ChatMemoryRepository.SOURCE_CHAT &&
                    it.originDraftId == null
            })
        }
        coVerify(exactly = 0) {
            memoryDao.insert(match<ChatMemory> { it.content == "已存在要点" })
        }
    }

    @Test
    fun `persist falls back to default session title when missing`() = runBlocking {
        coEvery { sessionDao.insert(any()) } returns 7L

        val result = repo.persist(
            ParsedChatImport(
                messages = emptyList(),
                memories = emptyList(),
                sessionTitle = null
            )
        )

        assertEquals(7L, result.sessionId)
        coVerify {
            sessionDao.insert(match<ChatSession> { it.title == "导入会话" })
        }
    }
}
