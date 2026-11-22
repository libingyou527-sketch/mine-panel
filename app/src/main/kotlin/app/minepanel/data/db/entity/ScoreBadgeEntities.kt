package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 六维属性分值：0–100、置信度、依据摘要；数值只由规则引擎写入。 */
@Entity(
    tableName = "attribute_scores",
    indices = [Index(value = ["attributeKey", "periodStart"])]
)
data class AttributeScore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attributeKey: String,
    val periodStart: Long,
    val periodEnd: Long,
    val score: Int,
    val confidence: Double,
    val evidenceJson: String,
    val sampleDays: Int,
    val updatedAt: Long
)

/** 徽章：解锁条件快照留存本地。 */
@Entity(tableName = "badges")
data class Badge(
    @PrimaryKey val badgeKey: String,
    val unlockedAt: Long,
    val conditionSnapshotJson: String
)
