package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天会话：一段独立对话的容器。消息归属某一会话；长期记忆（候选/已采纳）全局共享，
 * 不归属单一会话，删除会话只级联删除该会话消息，不影响全局要点。
 */
@Entity(
    tableName = "chat_sessions",
    indices = [Index("lastMessageAt")]
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val lastMessageAt: Long
)
