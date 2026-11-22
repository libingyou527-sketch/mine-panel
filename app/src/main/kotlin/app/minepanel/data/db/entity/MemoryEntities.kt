package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 模型草案：未裁决前不进入正式记忆。 */
@Entity(
    tableName = "draft_memories",
    indices = [Index("status"), Index("modelRunId")]
)
data class DraftMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelRunId: Long?,
    val draftType: String,
    val contentJson: String,
    val status: String,
    val userEditedContent: String?,
    val createdAt: Long,
    val reviewedAt: Long?
)

/** 正式记忆：仅草案经确认/修正后写入。[source] 区分人工确认与自动确认。 */
@Entity(
    tableName = "confirmed_memories",
    indices = [Index("periodStart")]
)
data class ConfirmedMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originDraftId: Long?,
    val content: String,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long,
    val source: String = SOURCE_MANUAL
) {
    companion object {
        /** 人工逐条确认/修正，或本人手写入正式记忆。 */
        const val SOURCE_MANUAL = "manual"

        /** 本地模型产出且开启自动确认后自动入库。 */
        const val SOURCE_AUTO_MODEL = "auto_model"
    }
}
