package app.minepanel.data.export

/** 单条聊天消息的导出快照：角色、正文与时间戳，不含任何元数据分值。 */
data class ExportedChatMessage(
    val role: String,
    val content: String,
    val createdAt: Long
)

/** 一条已采纳聊天长期记忆的导出快照。 */
data class ExportedChatMemory(
    val content: String,
    val createdAt: Long
)

/**
 * 聊天独立导出包：仅承载对话消息与已采纳要点，与回顾类综合导出包分离。
 * 待确认候选不导出；包内不含包名、联系人、通知正文或使用分值。
 */
data class ChatExportBundle(
    val schemaVersion: Int,
    val exportedAt: Long,
    /** 导出时的会话标题，便于回读识别；导入时不据此还原会话分组。 */
    val sessionTitle: String? = null,
    val messages: List<ExportedChatMessage>,
    val memories: List<ExportedChatMemory>
)
