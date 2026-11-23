package app.minepanel.data.repository

import app.minepanel.core.model.PluginType
import app.minepanel.data.dao.PluginEventDao
import app.minepanel.data.db.entity.PluginEvent

/**
 * 可选插件事件的写入与读取。仅落粗粒度值与脱敏后的元数据 JSON；
 * 通知正文、联系人、号码原文、连续轨迹等一律不入库。全部本地加密库内完成。
 */
class PluginEventRepository(private val dao: PluginEventDao) {

    suspend fun record(type: PluginType, coarseValue: String, payloadJson: String?) {
        val value = coarseValue.trim().take(MAX_VALUE_LEN)
        if (value.isEmpty()) return
        dao.insert(
            PluginEvent(
                pluginType = type.name,
                timestamp = System.currentTimeMillis(),
                coarseValue = value,
                maskedPayloadJson = payloadJson?.take(MAX_PAYLOAD_LEN)
            )
        )
    }

    suspend fun byType(type: PluginType): List<PluginEvent> = dao.byType(type.name)

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_VALUE_LEN = 120
        const val MAX_PAYLOAD_LEN = 400
    }
}
