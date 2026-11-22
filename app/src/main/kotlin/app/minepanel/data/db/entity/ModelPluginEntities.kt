package app.minepanel.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 本地推理记录：成功/降级/超时/错误与耗时，输入只留存哈希。 */
@Entity(tableName = "model_run_logs")
data class ModelRunLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long,
    val status: String,
    val engineName: String,
    val inputSnapshotHash: String,
    val outputJson: String?,
    val latencyMs: Long
)

/** 可选插件元数据：插件关闭时不产生行；正文/联系人/轨迹一律不入库。 */
@Entity(
    tableName = "plugin_events",
    indices = [Index("timestamp"), Index("pluginType")]
)
data class PluginEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginType: String,
    val timestamp: Long,
    val coarseValue: String,
    val maskedPayloadJson: String?
)
