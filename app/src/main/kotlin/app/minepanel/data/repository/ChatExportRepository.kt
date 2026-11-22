package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.export.ChatExporter
import app.minepanel.data.export.ChatExportBundle
import app.minepanel.data.export.ExportedChatMemory
import app.minepanel.data.export.ExportedChatMessage

/**
 * 聊天独立导出协调器：把指定会话的消息与全局已采纳要点组成本地 JSON 包，经 SAF 写入选定文件。
 *
 * 全程只读本地加密库与用户选定的文件描述符，不联网、不上传、不写剪贴板；
 * 待确认候选、使用分值与原始事件不纳入导出。本轮导出不含其他会话消息。
 */
class ChatExportRepository private constructor(
    private val messageDao: ChatMessageDao?,
    private val memoryDao: ChatMemoryDao?,
    private val context: Context?
) {

    constructor(context: Context) : this(null, null, context)

    /** 测试构造：注入 DAO，不触碰真库与 ContentResolver。 */
    internal constructor(
        messageDao: ChatMessageDao,
        memoryDao: ChatMemoryDao
    ) : this(messageDao, memoryDao, null)

    sealed interface ExportResult {
        data class Success(val messageCount: Int, val memoryCount: Int) : ExportResult
        data class Failure(val reason: String) : ExportResult
    }

    /** 读取指定会话消息与全局要点并组装导出包；不触碰文件系统。 */
    internal suspend fun buildBundle(
        sessionId: Long,
        sessionTitle: String?
    ): ChatExportBundle {
        val messages = (messageDao ?: ServiceLocator.database().chatMessageDao())
            .allForSession(sessionId)
            .map { ExportedChatMessage(it.role, it.content, it.createdAt) }
        val memories = (memoryDao ?: ServiceLocator.database().chatMemoryDao()).all()
            .map { ExportedChatMemory(it.content, it.createdAt) }
        return ChatExportBundle(
            schemaVersion = ChatExporter.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            sessionTitle = sessionTitle,
            messages = messages,
            memories = memories
        )
    }

    /** 序列化导出包并写入 SAF uri；成功返回条目计数，失败返回原因。 */
    suspend fun exportTo(
        uri: Uri,
        sessionId: Long,
        sessionTitle: String?
    ): ExportResult = runCatching {
        val bundle = buildBundle(sessionId, sessionTitle)
        val payload = ChatExporter.toJson(bundle)
        val resolver = context?.contentResolver
            ?: error("测试构造不支持写文件")
        resolver.openOutputStream(uri, "w").use { out ->
            out ?: error("无法写入所选文件")
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        ExportResult.Success(bundle.messages.size, bundle.memories.size)
    }.getOrElse { ExportResult.Failure(it.message ?: "导出失败") }
}
