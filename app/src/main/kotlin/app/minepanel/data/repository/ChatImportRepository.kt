package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.data.dao.ChatMemoryDao
import app.minepanel.data.dao.ChatMessageDao
import app.minepanel.data.dao.ChatSessionDao
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.db.entity.ChatSession
import app.minepanel.data.export.ChatImportParser
import app.minepanel.data.export.ImportException
import app.minepanel.data.export.ParsedChatImport

/**
 * 聊天记录本地导入协调器：读取 SAF 选定的聊天导出 JSON → schema 校验 → 新建会话承载消息，
 * 要点按正文去重后还原为全局已采纳记忆。全程本地完成，不联网；候选要点不在导入范围内。
 */
class ChatImportRepository private constructor(
    private val sessionDao: ChatSessionDao?,
    private val messageDao: ChatMessageDao?,
    private val memoryDao: ChatMemoryDao?,
    private val context: Context?
) {

    constructor(context: Context) : this(null, null, null, context)

    /** 测试构造：注入 DAO，不触碰真库与 ContentResolver。 */
    internal constructor(
        sessionDao: ChatSessionDao,
        messageDao: ChatMessageDao,
        memoryDao: ChatMemoryDao
    ) : this(sessionDao, messageDao, memoryDao, null)

    sealed interface ImportResult {
        data class Success(
            val sessionId: Long,
            val messageCount: Int,
            val memoryCount: Int
        ) : ImportResult
        data class Failure(val reason: String) : ImportResult
    }

    suspend fun importFrom(uri: Uri): ImportResult = runCatching {
        val text = context?.contentResolver?.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw ImportException("无法读取所选文件")
        val parsed = ChatImportParser.parse(text)
        persist(parsed)
    }.getOrElse { ImportResult.Failure(it.message ?: "导入失败") }

    /** 解析结果入库；DAO 显式注入，便于纯 JVM 单测覆盖计数与映射。 */
    internal suspend fun persist(parsed: ParsedChatImport): ImportResult.Success {
        val sessions = sessionDao ?: ServiceLocator.database().chatSessionDao()
        val messages = messageDao ?: ServiceLocator.database().chatMessageDao()
        val memories = memoryDao ?: ServiceLocator.database().chatMemoryDao()

        val now = System.currentTimeMillis()
        val title = parsed.sessionTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?.take(ChatSessionRepository.TITLE_LEN) ?: IMPORT_DEFAULT_TITLE
        val sessionId = sessions.insert(
            ChatSession(title = title, createdAt = now, lastMessageAt = now)
        )

        var messageCount = 0
        parsed.messages.forEach { message ->
            val content = message.content.trim().take(ChatRepository.MAX_CONTENT_LEN)
            if (content.isNotEmpty()) {
                messages.insert(
                    ChatMessage(
                        role = message.role,
                        content = content,
                        createdAt = message.createdAt,
                        sessionId = sessionId
                    )
                )
                messageCount++
            }
        }

        var memoryCount = 0
        parsed.memories.forEach { memory ->
            val content = memory.content.trim()
            if (content.isNotEmpty() && memories.countContent(content) == 0) {
                memories.insert(
                    ChatMemory(
                        content = content,
                        source = ChatMemoryRepository.SOURCE_CHAT,
                        createdAt = memory.createdAt,
                        originDraftId = null
                    )
                )
                memoryCount++
            }
        }

        return ImportResult.Success(sessionId, messageCount, memoryCount)
    }

    companion object {
        private const val IMPORT_DEFAULT_TITLE = "导入会话"
    }
}
