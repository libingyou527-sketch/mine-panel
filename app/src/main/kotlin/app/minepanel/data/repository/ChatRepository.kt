package app.minepanel.data.repository

import app.minepanel.core.model.ChatRole
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.db.entity.ChatMessage

/**
 * 聊天消息的本地读写，消息归属指定会话。对话上下文独立于回顾草案与正式记忆，
 * 全程本地加密库，不联网。仅做非空与限长校验；不参与正式记忆沉淀，也不回写六维数值。
 */
class ChatRepository(private val messageDao: ChatMessageDao) {

    suspend fun list(sessionId: Long): List<ChatMessage> = messageDao.allForSession(sessionId)

    /** 新增一条消息；正文去空白后为空或超长时返回 false，不入库。 */
    suspend fun add(role: ChatRole, content: String, sessionId: Long): Boolean {
        val text = content.trim().take(MAX_CONTENT_LEN)
        if (text.isEmpty()) return false
        messageDao.insert(
            ChatMessage(
                role = role.name,
                content = text,
                createdAt = System.currentTimeMillis(),
                sessionId = sessionId
            )
        )
        return true
    }

    suspend fun delete(id: Long) = messageDao.deleteById(id)

    /** 清空指定会话的全部消息；全局长期记忆不受影响。 */
    suspend fun clearSession(sessionId: Long) = messageDao.deleteBySession(sessionId)

    suspend fun clear() = messageDao.clear()

    companion object {
        const val MAX_CONTENT_LEN = 600
    }
}
