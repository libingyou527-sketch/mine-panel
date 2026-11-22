package app.minepanel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.minepanel.data.db.entity.ChatMessage

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(message: ChatMessage): Long

    /** 按会话、时间升序返回消息，用于还原该会话对话上下文。 */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun allForSession(sessionId: Long): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 删除某一会话的全部消息；删除会话时级联调用。 */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
