package app.minepanel.data.repository

import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMemoryDraftDao
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft

/**
 * 聊天长期记忆的本地读写：候选草案与已采纳要点分表管理。
 *
 * 模型自动提炼的要点先入 [STATUS_PENDING] 候选队列，经本人采纳后才写入 [ChatMemory] 并参与对话注入；
 * 拒绝不落库。仅做非空、长度与同正文去重，全程本地加密库，不联网，也不回写六维数值。
 */
class ChatMemoryRepository(
    private val draftDao: ChatMemoryDraftDao,
    private val memoryDao: ChatMemoryDao
) {

    suspend fun pendingDrafts(): List<ChatMemoryDraft> = draftDao.byStatus(STATUS_PENDING)

    suspend fun memories(): List<ChatMemory> = memoryDao.all()

    /** 新增一条候选要点；空、超长截断后为空或与既有候选/已采纳重复时返回 false。 */
    suspend fun addDraft(content: String): Boolean {
        val text = content.trim().take(MAX_DRAFT_LEN)
        if (text.isEmpty()) return false
        if (draftDao.countContent(text, STATUS_PENDING) > 0) return false
        if (memoryDao.countContent(text) > 0) return false
        draftDao.insert(
            ChatMemoryDraft(
                content = text,
                status = STATUS_PENDING,
                createdAt = System.currentTimeMillis(),
                reviewedAt = null
            )
        )
        return true
    }

    /** 采纳候选：写入已采纳记忆（重复正文不重复插入），草案置为已采纳。仅 PENDING 可裁决。 */
    suspend fun accept(id: Long): Boolean {
        val draft = draftDao.byId(id) ?: return false
        if (draft.status != STATUS_PENDING) return false
        val now = System.currentTimeMillis()
        if (memoryDao.countContent(draft.content) == 0) {
            memoryDao.insert(
                ChatMemory(
                    content = draft.content,
                    source = SOURCE_CHAT,
                    createdAt = now,
                    originDraftId = draft.id
                )
            )
        }
        draftDao.review(id, STATUS_ACCEPTED, now)
        return true
    }

    /** 丢弃候选；不写入已采纳记忆。仅 PENDING 可裁决。 */
    suspend fun reject(id: Long): Boolean {
        val draft = draftDao.byId(id) ?: return false
        if (draft.status != STATUS_PENDING) return false
        draftDao.review(id, STATUS_REJECTED, System.currentTimeMillis())
        return true
    }

    /** 删除一条已采纳要点；对应候选状态保留不变。 */
    suspend fun deleteMemory(id: Long) = memoryDao.deleteById(id)

    /** 清空全部候选与已采纳要点；与聊天消息一并清除时调用。 */
    suspend fun clearAll() {
        draftDao.clear()
        memoryDao.clear()
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_REJECTED = "REJECTED"
        const val SOURCE_CHAT = "chat"
        const val MAX_DRAFT_LEN = 300
    }
}
