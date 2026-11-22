package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.minepanel.data.db.entity.ChatSession

@Dao
interface ChatSessionDao {

    @Insert
    suspend fun insert(session: ChatSession): Long

    /** 按最近消息时间倒序列出会话，供会话列表展示。 */
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC, id DESC")
    suspend fun all(): List<ChatSession>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun byId(id: Long): ChatSession?

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE chat_sessions SET lastMessageAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun count(): Int
}
