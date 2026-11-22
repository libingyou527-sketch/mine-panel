package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 原始使用元数据：只存时间戳/粗类别/前后台/时长/事件类型，不存原始包名。 */
@Entity(
    tableName = "raw_usage_events",
    indices = [Index("timestamp")]
)
data class RawUsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val category: String,
    val durationMs: Long,
    val sessionId: String?
)

/** 日聚合：按日期 + 粗类别汇总。 */
@Entity(
    tableName = "daily_aggregates",
    primaryKeys = ["date", "category"],
    indices = [Index("date")]
)
data class DailyAggregate(
    val date: String,
    val category: String,
    val foregroundMs: Long,
    val eventCount: Int,
    val unlockCount: Int,
    val sleepWindowStart: Long?,
    val sleepWindowEnd: Long?,
    val updatedAt: Long
)
