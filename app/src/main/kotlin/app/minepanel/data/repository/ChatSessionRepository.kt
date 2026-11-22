package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.dao.ChatSessionDao
import app.minepanel.data.db.entity.ChatSession

/**
 * 聊天会话的本地读写。会话只组织消息容器；长期记忆（候选/已采纳）全局共享，
 * 删除会话级联删除其消息，不删除全局要点。
 */
class ChatSessionRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {

    suspend fun list(): List<ChatSession> = sessionDao.all()

    suspend fun byId(id: Long): ChatSession? = sessionDao.byId(id)

    /** 确保至少存在一个会话；无会话时创建默认会话并返回其 id。 */
    suspend fun ensureDefault(): Long {
        val existing = sessionDao.all()
        if (existing.isNotEmpty()) return existing.first().id
        val now = System.currentTimeMillis()
        return sessionDao.insert(
            ChatSession(title = DEFAULT_TITLE, createdAt = now, lastMessageAt = now)
        )
    }

    /** 新建会话；标题为空时用默认标题。 */
    suspend fun create(title: String? = null): Long {
        val now = System.currentTimeMillis()
        val finalTitle = title?.trim()?.takeIf { it.isNotEmpty() }?.take(TITLE_LEN) ?: DEFAULT_TITLE
        return sessionDao.insert(
            ChatSession(title = finalTitle, createdAt = now, lastMessageAt = now)
        )
    }

    /** 重命名会话；空白标题不生效。 */
    suspend fun rename(id: Long, title: String) {
        val finalTitle = title.trim().takeIf { it.isNotEmpty() }?.take(TITLE_LEN) ?: return
        sessionDao.updateTitle(id, finalTitle)
    }

    /** 刷新会话最近消息时间，用于列表排序。 */
    suspend fun touch(id: Long, at: Long) = sessionDao.touch(id, at)

    /** 删除会话并级联删除其全部消息；全局长期记忆保留。 */
    suspend fun delete(id: Long) {
        messageDao.deleteBySession(id)
        sessionDao.deleteById(id)
    }

    companion object {
        const val DEFAULT_TITLE = "新会话"
        const val TITLE_LEN = 40
    }
}
