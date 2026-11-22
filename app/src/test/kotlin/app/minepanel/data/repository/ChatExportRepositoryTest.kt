package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.export.ChatExporter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatExportRepositoryTest {

    private val messageDao = mockk<ChatMessageDao>(relaxed = true)
    private val memoryDao = mockk<ChatMemoryDao>(relaxed = true)
    private val repo = ChatExportRepository(messageDao, memoryDao)
    private val sessionId = 7L

    @Test
    fun `buildBundle maps messages memories and title`() = runBlocking {
        coEvery { messageDao.allForSession(sessionId) } returns listOf(
            ChatMessage(
                id = 1, role = "USER", content = "你好",
                createdAt = 5L, sessionId = sessionId
            )
        )
        coEvery { memoryDao.all() } returns listOf(
            ChatMemory(id = 2, content = "要点", source = "chat", createdAt = 6L)
        )

        val bundle = repo.buildBundle(sessionId, "测试会话")

        assertEquals(ChatExporter.SCHEMA_VERSION, bundle.schemaVersion)
        assertEquals("测试会话", bundle.sessionTitle)
        assertEquals(1, bundle.messages.size)
        assertEquals("USER", bundle.messages[0].role)
        assertEquals("你好", bundle.messages[0].content)
        assertEquals(5L, bundle.messages[0].createdAt)
        assertEquals(1, bundle.memories.size)
        assertEquals("要点", bundle.memories[0].content)
        assertEquals(6L, bundle.memories[0].createdAt)
    }

    @Test
    fun `buildBundle carries empty sections when no data`() = runBlocking {
        coEvery { messageDao.allForSession(sessionId) } returns emptyList()
        coEvery { memoryDao.all() } returns emptyList()

        val bundle = repo.buildBundle(sessionId, null)
        assertEquals(null, bundle.sessionTitle)
        assertEquals(0, bundle.messages.size)
        assertEquals(0, bundle.memories.size)
    }
}
