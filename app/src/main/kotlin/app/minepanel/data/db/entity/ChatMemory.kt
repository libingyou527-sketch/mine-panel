package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天记忆候选：本地模型从对话中自动提炼的要点，先进入待确认队列。
 * [status] 取 [app.minepanel.data.repository.ChatMemoryRepository] 中的状态常量；
 * 仅 ACCEPTED 的要点写入 [ChatMemory]，REJECTED 不沉淀。全程本地加密库，不联网。
 */
@Entity(
    tableName = "chat_memory_drafts",
    indices = [Index("status"), Index("createdAt")]
)
data class ChatMemoryDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val status: String,
    val createdAt: Long,
    val reviewedAt: Long?
)

/**
 * 已采纳的聊天长期记忆：确认后入库，装配新对话时作为背景要点注入。
 * [source] 区分来源；[originDraftId] 回链对应候选，便于追溯与撤销。
 */
@Entity(
    tableName = "chat_memories",
    indices = [Index("createdAt")]
)
data class ChatMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val source: String,
    val createdAt: Long,
    val originDraftId: Long? = null
)
