package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息：独立于回顾草案与正式记忆，单独持久化本地对话上下文。
 * [role] 取 [app.minepanel.core.model.ChatRole] 名；[content] 为单条发言或回复正文；
 * [sessionId] 归属的聊天会话。聊天内容不回写六维数值、不沉淀为长期记忆，可单条长按删除。
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("createdAt"), Index("sessionId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long,
    val sessionId: Long
)
