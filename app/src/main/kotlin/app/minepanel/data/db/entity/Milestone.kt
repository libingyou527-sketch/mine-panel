package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本人自定义里程碑（阶段节点）：与规则解锁徽章、模型状态标签并存，由用户主动建立。
 *
 * [title] 节点名称（必填）；[label] 可选自定义短标签；[note] 可选说明。
 * [anchorEpochMs] 可选时间锚点，null 表示以 [createdAt] 为准；
 * [linkedFragmentId] 可选关联的手写片段 id（软关联，片段删除后不级联清除，仅留空引用）。
 */
@Entity(
    tableName = "milestones",
    indices = [Index("anchorEpochMs")]
)
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val label: String?,
    val note: String?,
    val anchorEpochMs: Long?,
    val linkedFragmentId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
