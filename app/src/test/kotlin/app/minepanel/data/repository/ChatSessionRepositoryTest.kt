package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.dao.ChatSessionDao
import app.minepanel.data.db.entity.ChatSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionRepositoryTest {

    private val sessionDao = mockk<ChatSessionDao>(relaxed = true)
    private val messageDao = mockk<ChatMessageDao>(relaxed = true)
    private val repo = ChatSessionRepository(sessionDao, messageDao)

    @Test
    fun `ensureDefault returns existing session id`() = runBlocking {
        coEvery { sessionDao.all() } returns listOf(
            ChatSession(id = 9, title = "旧会话", createdAt = 1L, lastMessageAt = 1L)
        )

        assertEquals(9L, repo.ensureDefault())
        coVerify(exactly = 0) { sessionDao.insert(any()) }
    }

    @Test
    fun `ensureDefault creates default session when empty`() = runBlocking {
        coEvery { sessionDao.all() } returns emptyList()
        coEvery { sessionDao.insert(any()) } returns 5L

        assertEquals(5L, repo.ensureDefault())
        coVerify {
            sessionDao.insert(match<ChatSession> { it.title == ChatSessionRepository.DEFAULT_TITLE })
        }
    }

    @Test
    fun `create uses default title for blank input`() = runBlocking {
        coEvery { sessionDao.insert(any()) } returns 3L

        repo.create("   ")
        coVerify {
            sessionDao.insert(match<ChatSession> { it.title == ChatSessionRepository.DEFAULT_TITLE })
        }
    }

    @Test
    fun `create title is trimmed and truncated to limit`() = runBlocking {
        coEvery { sessionDao.insert(any()) } returns 4L
        val long = "标".repeat(ChatSessionRepository.TITLE_LEN + 20)

        repo.create("  $long  ")
        coVerify {
            sessionDao.insert(match<ChatSession> {
                it.title.length == ChatSessionRepository.TITLE_LEN
            })
        }
    }

    @Test
    fun `rename ignores blank title`() = runBlocking {
        repo.rename(1L, "   ")
        coVerify(exactly = 0) { sessionDao.updateTitle(any(), any()) }
    }

    @Test
    fun `rename trims and truncates title`() = runBlocking {
        repo.rename(1L, "  晚间闲聊  ")
        coVerify { sessionDao.updateTitle(1L, "晚间闲聊") }
    }

    @Test
    fun `delete session cascades messages but keeps memories`() = runBlocking {
        repo.delete(8L)
        coVerify { messageDao.deleteBySession(8L) }
        coVerify { sessionDao.deleteById(8L) }
    }
}
