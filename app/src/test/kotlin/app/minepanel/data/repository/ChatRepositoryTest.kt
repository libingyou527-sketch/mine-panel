package app.minepanel.data.repository

import app.minepanel.core.model.ChatRole
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.db.entity.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    private val chatDao = mockk<ChatMessageDao>(relaxed = true)
    private val repo = ChatRepository(chatDao)
    private val sessionId = 7L

    @Test
    fun `add stores trimmed user message with role and session`() = runBlocking {
        val result = repo.add(ChatRole.USER, "  你好  ", sessionId)

        assertTrue(result)
        coVerify {
            chatDao.insert(match<ChatMessage> {
                it.role == "USER" && it.content == "你好" &&
                    it.createdAt > 0L && it.sessionId == sessionId
            })
        }
    }

    @Test
    fun `blank message is rejected without insert`() = runBlocking {
        assertFalse(repo.add(ChatRole.USER, "   ", sessionId))
        coVerify(exactly = 0) { chatDao.insert(any()) }
    }

    @Test
    fun `long content is truncated to limit`() = runBlocking {
        val long = "中".repeat(ChatRepository.MAX_CONTENT_LEN + 50)
        val result = repo.add(ChatRole.ASSISTANT, long, sessionId)

        assertTrue(result)
        coVerify {
            chatDao.insert(match<ChatMessage> {
                it.content.length == ChatRepository.MAX_CONTENT_LEN &&
                    it.role == "ASSISTANT" && it.sessionId == sessionId
            })
        }
    }

    @Test
    fun `delete and clearSession delegate to dao`() = runBlocking {
        repo.delete(7L)
        repo.clearSession(sessionId)
        coVerify { chatDao.deleteById(7L) }
        coVerify { chatDao.deleteBySession(sessionId) }
    }

    @Test
    fun `list delegates to dao for session`() = runBlocking {
        val messages = listOf(
            ChatMessage(id = 1, role = "USER", content = "你好", createdAt = 1L, sessionId = sessionId),
            ChatMessage(
                id = 2, role = "ASSISTANT", content = "你好呀",
                createdAt = 2L, sessionId = sessionId
            )
        )
        coEvery { chatDao.allForSession(sessionId) } returns messages

        assertEquals(messages, repo.list(sessionId))
    }
}
