package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft

@Dao
interface ChatMemoryDraftDao {

    @Insert
    suspend fun insert(draft: ChatMemoryDraft): Long

    @Query("SELECT * FROM chat_memory_drafts WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: String): List<ChatMemoryDraft>

    @Query("SELECT * FROM chat_memory_drafts WHERE id = :id")
    suspend fun byId(id: Long): ChatMemoryDraft?

    @Query(
        "UPDATE chat_memory_drafts SET status = :status, reviewedAt = :reviewedAt WHERE id = :id"
    )
    suspend fun review(id: Long, status: String, reviewedAt: Long)

    @Query(
        "SELECT COUNT(*) FROM chat_memory_drafts WHERE content = :content AND status = :status"
    )
    suspend fun countContent(content: String, status: String): Int

    @Query("DELETE FROM chat_memory_drafts")
    suspend fun clear()
}

@Dao
interface ChatMemoryDao {

    @Insert
    suspend fun insert(memory: ChatMemory): Long

    /** 按时间升序返回已采纳要点，供对话背景注入与列表展示。 */
    @Query("SELECT * FROM chat_memories ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<ChatMemory>

    @Query("DELETE FROM chat_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM chat_memories WHERE content = :content")
    suspend fun countContent(content: String): Int

    @Query("DELETE FROM chat_memories")
    suspend fun clear()
}
