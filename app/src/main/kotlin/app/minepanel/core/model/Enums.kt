package app.minepanel.core.model

/** 粗类别：面板与模型输入只允许出现这些类别，原始包名不出采集层。 */
enum class EventCategory {
    WORK, SOCIAL, MEDIA, TOOL, REST, UNMAPPED;

    companion object {
        fun fromName(name: String?): EventCategory =
            entries.firstOrNull { it.name == name } ?: UNMAPPED
    }
}

/** 采集事件类型。解锁/亮屏在系统 API 稳定获取时纳入，否则列可选。 */
enum class EventType {
    FOREGROUND, BACKGROUND, UNLOCK, SCREEN_ON
}

/** 六维属性（数值仅由规则引擎计算）。 */
enum class AttributeKey {
    FOCUS, REGULARITY, VITALITY, SOCIAL, EXPLORATION, DIGITAL_MODERATION;

    /** 面板中文展示名；仅用于 UI，不参与采集与入库。 */
    val label: String
        get() = when (this) {
            FOCUS -> "专注"
            REGULARITY -> "规律"
            VITALITY -> "活力"
            SOCIAL -> "社交"
            EXPLORATION -> "探索"
            DIGITAL_MODERATION -> "数字节制"
        }
}

/** 草案类型。 */
enum class DraftType {
    MEMORY, NARRATIVE, STATUS_TAG;

    val label: String
        get() = when (this) {
            MEMORY -> "记忆要点"
            NARRATIVE -> "回顾文案"
            STATUS_TAG -> "状态标签"
        }
}

/** 草案裁决状态机：PENDING → CONFIRMED / MODIFIED / REJECTED。 */
enum class DraftStatus { PENDING, CONFIRMED, MODIFIED, REJECTED }

/** 一次本地推理的结果状态。 */
enum class ModelStatus { SUCCESS, FALLBACK_TEMPLATE, TIMEOUT, ERROR }

/** 可选插件类型（默认关闭）。 */
enum class PluginType { NOTIFICATION_META, GEOFENCE, HEALTH_CONNECT, CALENDAR, CALL_LOG }

/** 聊天消息角色：用户发言 / 本地模型回复。 */
enum class ChatRole { USER, ASSISTANT }
