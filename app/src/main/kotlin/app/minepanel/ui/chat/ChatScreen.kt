package app.minepanel.ui.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.model.ChatRole
import app.minepanel.model.remote.RemoteProviders
import app.minepanel.data.db.entity.ChatMessage
import app.minepanel.data.db.entity.ChatMemory
import app.minepanel.data.db.entity.ChatMemoryDraft
import app.minepanel.data.db.entity.ChatSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMemories by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showModelSettings by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    val timestamp = remember {
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(
            Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportChat(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importChat(it) }
    }

    BackHandler(onBack = {
        when {
            deletingId != null -> deletingId = null
            renamingId != null -> renamingId = null
            showClearConfirm -> showClearConfirm = false
            showMemories -> showMemories = false
            showSessions -> showSessions = false
            else -> onBack()
        }
    })

    val hasMessages = state.messages.isNotEmpty()
    val hasContent = hasMessages || state.memoryDrafts.isNotEmpty() || state.memories.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        showMemories -> "记忆管理"
                        showSessions -> "会话列表"
                        else -> "聊天"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showModelSettings = true }) { Text("设置") }
                    TextButton(onClick = {
                        showSessions = !showSessions
                        showMemories = false
                    }) { Text("会话") }
                    TextButton(onClick = {
                        showMemories = !showMemories
                        showSessions = false
                    }) { Text(if (showMemories) "返回聊天" else "记忆") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showMemories && !showSessions) {
                    TextButton(
                        onClick = { viewModel.extractMemories() },
                        enabled = !state.extracting && hasContent
                    ) {
                        if (state.extracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("整理记忆")
                        }
                    }
                }
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !state.importing
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("导入聊天")
                    }
                }
                TextButton(
                    onClick = { exportLauncher.launch("minepanel-chat-$timestamp.json") },
                    enabled = !state.exporting && !state.clearing && hasContent
                ) { Text("导出聊天") }
                TextButton(
                    onClick = { showClearConfirm = true },
                    enabled = !state.exporting && !state.clearing && hasMessages
                ) { Text("清空当前") }
            }

            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            state.engineSource?.let { source ->
                Text(
                    text = when (source) {
                        "remote" -> "当前回复来自外部模型（联网）"
                        "local" -> "当前回复来自内置模型"
                        else -> "内置模型暂不可用，已用模板兜底"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                showMemories -> MemoryPanel(
                    drafts = state.memoryDrafts,
                    memories = state.memories,
                    onAccept = viewModel::acceptMemory,
                    onReject = viewModel::rejectMemory,
                    onDeleteMemory = viewModel::deleteMemory
                )
                showSessions -> SessionPanel(
                    sessions = state.sessions,
                    currentSessionId = state.currentSessionId,
                    onNew = {
                        viewModel.newSession()
                        showSessions = false
                    },
                    onSelect = {
                        viewModel.selectSession(it)
                        showSessions = false
                    },
                    onRename = { id ->
                        val session = state.sessions.firstOrNull { it.id == id }
                        renameText = session?.title ?: ""
                        renamingId = id
                    },
                    onDelete = { deletingId = it }
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.messages.isEmpty()) {
                            item {
                                Text(
                                    "暂无对话。发送一条消息开始聊天，长按消息可删除；对话仅保存在本地。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(state.messages, key = { "chat-${it.id}" }) { message ->
                            MessageBubble(message, onDelete = { viewModel.delete(message.id) })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = viewModel::onInputChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("说点什么…") },
                            maxLines = 3
                        )
                        if (state.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Button(
                                onClick = { viewModel.send() },
                                enabled = state.input.isNotBlank()
                            ) { Text("发送") }
                        }
                    }
                }
            }
        }
    }

    if (showModelSettings) {
        ModelSettingsDialog(
            state = state,
            onSave = { enabled, provider, baseUrl, modelName, apiKey ->
                viewModel.saveRemoteConfig(enabled, provider, baseUrl, modelName, apiKey)
                showModelSettings = false
            },
            onDismiss = { showModelSettings = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.clearing) showClearConfirm = false },
            title = { Text("清空当前会话？") },
            text = {
                Text(
                    "将删除当前会话的全部对话消息；已采纳要点与待确认候选保留，此操作不可恢复。" +
                        "全程在设备本地完成，不联网、不上传。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearChat()
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (renamingId != null) {
        AlertDialog(
            onDismissRequest = { renamingId = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSession(renamingId!!, renameText)
                    renamingId = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = null }) { Text("取消") }
            }
        )
    }

    if (deletingId != null) {
        val deletingTitle = state.sessions.firstOrNull { it.id == deletingId }?.title
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除会话？") },
            text = {
                Text(
                    (deletingTitle?.let { "将删除会话「$it」及其全部对话消息；" }
                        ?: "将删除该会话及其全部对话消息；") +
                        "已采纳要点与待确认候选保留，此操作不可恢复。" +
                        "全程在设备本地完成，不联网、不上传。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(deletingId!!)
                    deletingId = null
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ModelSettingsDialog(
    state: ChatUiState,
    onSave: (enabled: Boolean, provider: String, baseUrl: String, modelName: String, apiKey: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(state.remoteEnabled) }
    var provider by remember { mutableStateOf(state.remoteProvider) }
    var baseUrl by remember { mutableStateOf(state.remoteBaseUrl) }
    var modelName by remember { mutableStateOf(state.remoteModelName) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text("启用外部大模型（需联网）")
                }
                Text(
                    "启用后，对话内容会发送到你填写的第三方模型服务；关闭、未配置完整或网络/调用失败时，自动降级为本地内置模型，不发起联网请求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RemoteProviders.presets.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = provider == preset.id,
                            onClick = {
                                provider = preset.id
                                if (preset.id != "custom") {
                                    baseUrl = preset.baseUrl
                                    modelName = preset.defaultModel
                                }
                            }
                        )
                        Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址 Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(if (state.remoteKeySet) "API Key（已保存，留空不修改）" else "API Key")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(enabled, provider, baseUrl, modelName, apiKey.ifBlank { null })
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SessionPanel(
    sessions: List<ChatSession>,
    currentSessionId: Long?,
    onNew: () -> Unit,
    onSelect: (Long) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(onClick = onNew) { Text("新建会话") }
        }
        if (sessions.isEmpty()) {
            item { Text("暂无会话。", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(sessions, key = { "session-${it.id}" }) { session ->
                val isCurrent = session.id == currentSessionId
                Surface(
                    onClick = { onSelect(session.id) },
                    color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (isCurrent) {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { onRename(session.id) }) { Text("重命名") }
                        TextButton(onClick = { onDelete(session.id) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPanel(
    drafts: List<ChatMemoryDraft>,
    memories: List<ChatMemory>,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onDeleteMemory: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "待确认要点（模型从对话中提炼，采纳后才会参与后续聊天）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (drafts.isEmpty()) {
            item { Text("暂无待确认要点。", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(drafts, key = { "draft-${it.id}" }) { draft ->
                DraftRow(draft, onAccept, onReject)
            }
        }

        item {
            Text(
                "已采纳要点",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (memories.isEmpty()) {
            item { Text("暂无已采纳要点。", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(memories, key = { "memory-${it.id}" }) { memory ->
                MemoryRow(memory, onDeleteMemory)
            }
        }
    }
}

@Composable
private fun DraftRow(
    draft: ChatMemoryDraft,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(draft.content, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAccept(draft.id) }) { Text("采纳") }
                TextButton(onClick = { onReject(draft.id) }) { Text("丢弃") }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    memory: ChatMemory,
    onDelete: (Long) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onDelete(memory.id) }) { Text("删除") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onDelete: () -> Unit
) {
    val isUser = message.role == ChatRole.USER.name
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .width(300.dp)
                .combinedClickable(onClick = {}, onLongClick = onDelete)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = shortDate(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val shortFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun shortDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(shortFormatter)
