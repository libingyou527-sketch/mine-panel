package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本人主动记录的手写片段：区别于需把关的模型草案，提交即入正式记忆，不再过确认队列。
 * [mood] 为可选心情短标签；[category] 为可选粗类别（[app.minepanel.core.model.EventCategory] 名）；
 * [anchorEpochMs] 为可选时间锚点，null 表示以 [createdAt] 为准。
 */
@Entity(
    tableName = "manual_fragments",
    indices = [Index("createdAt")]
)
data class ManualFragment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val mood: String?,
    val category: String?,
    val anchorEpochMs: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
