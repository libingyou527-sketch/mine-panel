package app.minepanel.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.ChatRole
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.data.repository.ChatExportRepository
import app.minepanel.data.repository.ChatImportRepository
import app.minepanel.data.repository.ChatMemoryRepository
import app.minepanel.data.repository.ChatRepository
import app.minepanel.data.repository.ChatSessionRepository
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft
import app.minepanel.data.db.entity.ChatSession
import app.minepanel.model.ChatPromptBuilder
import app.minepanel.model.ChatTurn
import app.minepanel.model.EngineRouter
import app.minepanel.model.InferenceEngine
import app.minepanel.model.ModelOutputParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 聊天页渲染状态：会话列表与当前会话、消息、输入框、生成/提炼/导入导出/清空标记与回显。 */
data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val memoryDrafts: List<ChatMemoryDraft> = emptyList(),
    val memories: List<ChatMemory> = emptyList(),
    val extracting: Boolean = false,
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val clearing: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
)

class ChatViewModel : AndroidViewModel {

    private lateinit var sessionRepository: ChatSessionRepository
    private lateinit var repository: ChatRepository
    private lateinit var memoryRepository: ChatMemoryRepository
    private lateinit var chatExportRepository: ChatExportRepository
    private lateinit var chatImportRepository: ChatImportRepository
    private lateinit var engineProvider: (SettingsSnapshot) -> InferenceEngine

    /** 生产构造：从 ServiceLocator 取加密库 DAO 与设置存储。 */
    constructor(app: Application) : super(app) {
        val db = ServiceLocator.database()
        sessionRepository = ChatSessionRepository(
            sessionDao = db.chatSessionDao(),
            messageDao = db.chatMessageDao()
        )
        repository = ChatRepository(db.chatMessageDao())
        memoryRepository = ChatMemoryRepository(
            draftDao = db.chatMemoryDraftDao(),
            memoryDao = db.chatMemoryDao()
        )
        engineProvider = { EngineRouter(app).select(it) }
        chatExportRepository = ChatExportRepository(app)
        chatImportRepository = ChatImportRepository(app)
        load()
    }

    /** 测试构造：注入仓储与引擎 provider，不触碰真库与 ContentResolver。 */
    internal constructor(
        app: Application,
        sessionRepository: ChatSessionRepository,
        repository: ChatRepository,
        memoryRepository: ChatMemoryRepository,
        engineProvider: (SettingsSnapshot) -> InferenceEngine,
        chatExportRepository: ChatExportRepository,
        chatImportRepository: ChatImportRepository
    ) : super(app) {
        this.sessionRepository = sessionRepository
        this.repository = repository
        this.memoryRepository = memoryRepository
        this.engineProvider = engineProvider
        this.chatExportRepository = chatExportRepository
        this.chatImportRepository = chatImportRepository
        load()
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private fun load() {
        viewModelScope.launch {
            val settings = ServiceLocator.settings()
            val defaultSession = sessionRepository.ensureDefault()
            val saved = settings.settings.first().currentChatSessionId
            val current = if (saved != null && sessionRepository.byId(saved) != null) {
                saved
            } else {
                defaultSession
            }
            settings.setCurrentChatSessionId(current)
            refreshAll(current)
        }
    }

    private suspend fun refreshAll(sessionId: Long) {
        _state.update {
            it.copy(
                sessions = sessionRepository.list(),
                currentSessionId = sessionId,
                messages = repository.list(sessionId),
                memoryDrafts = memoryRepository.pendingDrafts(),
                memories = memoryRepository.memories()
            )
        }
    }

    private suspend fun refreshMemoryLists() {
        _state.update {
            it.copy(
                memoryDrafts = memoryRepository.pendingDrafts(),
                memories = memoryRepository.memories()
            )
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    /** 切换到指定会话，并持久化为当前会话。 */
    fun selectSession(id: Long) {
        viewModelScope.launch {
            ServiceLocator.settings().setCurrentChatSessionId(id)
            refreshAll(id)
        }
    }

    /** 新建会话并切到该会话。 */
    fun newSession() {
        viewModelScope.launch {
            val id = sessionRepository.create()
            ServiceLocator.settings().setCurrentChatSessionId(id)
            refreshAll(id)
            _state.update { it.copy(message = null) }
        }
    }

    /** 重命名指定会话。 */
    fun renameSession(id: Long, title: String) {
        viewModelScope.launch {
            sessionRepository.rename(id, title)
            refreshAll(id)
        }
    }

    /** 重命名当前会话。 */
    fun renameCurrentSession(title: String) {
        val current = _state.value.currentSessionId ?: return
        renameSession(current, title)
    }

    /** 删除会话及其消息；全局长期记忆保留。删除当前会话时切到剩余会话或新建。 */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            sessionRepository.delete(id)
            val remaining = sessionRepository.list()
            val target = remaining.firstOrNull()?.id ?: sessionRepository.create()
            ServiceLocator.settings().setCurrentChatSessionId(target)
            refreshAll(target)
        }
    }

    /** 发送一条用户消息；把已采纳要点作为背景注入。模型回复失败或为空时落中性兜底语。 */
    fun send() {
        val current = _state.value.currentSessionId ?: return
        val userText = _state.value.input.trim()
        if (userText.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            repository.add(ChatRole.USER, userText, current)
            val now = System.currentTimeMillis()
            sessionRepository.touch(current, now)
            _state.update { it.copy(input = "", sending = true) }

            val history = repository.list(current).map { turnOf(it) }
            val memoryTexts = memoryRepository.memories().map { it.content }
            val settings = ServiceLocator.settings().settings.first()
            val reply = runCatching {
                engineProvider(settings).chat(ChatPromptBuilder.buildChat(history, memoryTexts))
            }.getOrElse { "" }
            val finalReply = reply.ifBlank { FALLBACK_REPLY }

            repository.add(ChatRole.ASSISTANT, finalReply, current)
            sessionRepository.touch(current, System.currentTimeMillis())
            autoTitleIfDefault(current, userText)
            refreshAll(current)
            _state.update { it.copy(sending = false) }
        }
    }

    /** 长按删除单条消息；其余上下文保留。 */
    fun delete(id: Long) {
        val current = _state.value.currentSessionId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(messages = repository.list(current)) }
        }
    }

    /** 让本地模型从当前会话提炼候选要点；结果进待确认队列，不直接生效。 */
    fun extractMemories() {
        val current = _state.value.currentSessionId ?: return
        if (_state.value.extracting) return
        viewModelScope.launch {
            _state.update { it.copy(extracting = true) }
            val history = repository.list(current).map { turnOf(it) }
            if (history.isNotEmpty()) {
                val settings = ServiceLocator.settings().settings.first()
                val raw = runCatching {
                    engineProvider(settings)
                        .chat(ChatPromptBuilder.buildMemoryExtraction(history))
                }.getOrElse { "" }
                ModelOutputParser.parseMemoryItems(raw).forEach { memoryRepository.addDraft(it) }
            }
            refreshMemoryLists()
            _state.update { it.copy(extracting = false) }
        }
    }

    /** 采纳一条候选要点，写入已采纳记忆并参与后续对话注入。 */
    fun acceptMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.accept(id)
            refreshMemoryLists()
        }
    }

    /** 丢弃一条候选要点；不写入已采纳记忆。 */
    fun rejectMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.reject(id)
            refreshMemoryLists()
        }
    }

    /** 删除一条已采纳要点。 */
    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            refreshMemoryLists()
        }
    }

    /** 导出当前会话消息与全局已采纳要点到用户选定的本地 JSON 文件。 */
    fun exportChat(uri: Uri) {
        val current = _state.value.currentSessionId ?: return
        if (_state.value.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, message = null) }
            val title = sessionRepository.byId(current)?.title
            val result = chatExportRepository.exportTo(uri, current, title)
            val (message, isError) = when (result) {
                is ChatExportRepository.ExportResult.Success ->
                    "已导出对话 ${result.messageCount} 条、要点 ${result.memoryCount} 条" to false
                is ChatExportRepository.ExportResult.Failure -> result.reason to true
            }
            _state.update {
                it.copy(exporting = false, message = message, messageIsError = isError)
            }
        }
    }

    /** 导入聊天记录：新建会话承载消息，要点去重还原为已采纳，再切到新会话。 */
    fun importChat(uri: Uri) {
        if (_state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, message = null) }
            val result = chatImportRepository.importFrom(uri)
            val (message, isError, sessionId) = when (result) {
                is ChatImportRepository.ImportResult.Success -> Triple(
                    "已导入对话 ${result.messageCount} 条、要点 ${result.memoryCount} 条",
                    false,
                    result.sessionId
                )
                is ChatImportRepository.ImportResult.Failure -> Triple(result.reason, true, null)
            }
            if (sessionId != null) {
                ServiceLocator.settings().setCurrentChatSessionId(sessionId)
                refreshAll(sessionId)
            }
            _state.update {
                it.copy(importing = false, message = message, messageIsError = isError)
            }
        }
    }

    /** 清空当前会话消息；全局长期记忆保留。 */
    fun clearChat() {
        val current = _state.value.currentSessionId ?: return
        if (_state.value.clearing) return
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            repository.clearSession(current)
            _state.update {
                it.copy(messages = emptyList(), clearing = false, message = null)
            }
        }
    }

    private suspend fun autoTitleIfDefault(sessionId: Long, firstUserText: String) {
        val session = sessionRepository.byId(sessionId) ?: return
        if (session.title == ChatSessionRepository.DEFAULT_TITLE) {
            sessionRepository.rename(sessionId, firstUserText)
        }
    }

    private fun turnOf(message: ChatMessage): ChatTurn = ChatTurn(
        role = runCatching { ChatRole.valueOf(message.role) }.getOrDefault(ChatRole.USER),
        content = message.content
    )

    companion object {
        private const val FALLBACK_REPLY = "本地模型暂不可用，这次先不展开回复，稍后再试。"
    }
}
