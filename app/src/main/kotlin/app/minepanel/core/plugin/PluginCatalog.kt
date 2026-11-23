package app.minepanel.core.plugin

import app.minepanel.core.model.PluginType

/** 插件风险分级，仅用于 UI 着色与提示，不参与采集与入库。 */
enum class PluginRiskLevel {
    LOW, MEDIUM_LOW, MEDIUM, HIGH;

    val label: String
        get() = when (this) {
            LOW -> "低"
            MEDIUM_LOW -> "中低"
            MEDIUM -> "中"
            HIGH -> "高风险"
        }
}

/**
 * 可选插件描述（core 层元信息，UI 与未来采集层共用）。
 * [implemented] 为 false 表示当前仅为开关占位：不注册采集组件、不申请对应权限、不写插件事件表；
 * 真实采集在对应插件落地阶段再逐项接入。
 */
data class PluginDescriptor(
    val type: PluginType,
    val title: String,
    val description: String,
    val riskLabel: String,
    val riskLevel: PluginRiskLevel,
    val implemented: Boolean = false
)

object PluginCatalog {

    val all: List<PluginDescriptor> = listOf(
        PluginDescriptor(
            type = PluginType.NOTIFICATION_META,
            title = "通知元数据",
            description = "仅记录包名、频道、时间戳；不读取通知正文。",
            riskLabel = "中：通知元数据仍会暴露使用节律",
            riskLevel = PluginRiskLevel.MEDIUM,
            implemented = true
        ),
        PluginDescriptor(
            type = PluginType.GEOFENCE,
            title = "地理围栏",
            description = "仅记录常去点的到达与离开；不采集连续轨迹。常去点需手动录入。",
            riskLabel = "中低：位置相关",
            riskLevel = PluginRiskLevel.MEDIUM_LOW,
            implemented = true
        ),
        PluginDescriptor(
            type = PluginType.HEALTH_CONNECT,
            title = "健康连接（睡眠/步数）",
            description = "只读睡眠窗口与步数；受系统 Health Connect 版本限制，暂为占位。",
            riskLabel = "低：健康数据",
            riskLevel = PluginRiskLevel.LOW,
            implemented = false
        ),
        PluginDescriptor(
            type = PluginType.CALENDAR,
            title = "日历",
            description = "只读事件时间，用于计划与实际对照。",
            riskLabel = "中：可能暴露日程节律",
            riskLevel = PluginRiskLevel.MEDIUM,
            implemented = true
        ),
        PluginDescriptor(
            type = PluginType.CALL_LOG,
            title = "通话日志",
            description = "只记录通话时长与频率；号码仅存摘要，不记录联系人。",
            riskLabel = "高风险：通话行为敏感，需明确知情同意",
            riskLevel = PluginRiskLevel.HIGH,
            implemented = true
        )
    )

    fun byType(type: PluginType): PluginDescriptor =
        all.first { it.type == type }
}
