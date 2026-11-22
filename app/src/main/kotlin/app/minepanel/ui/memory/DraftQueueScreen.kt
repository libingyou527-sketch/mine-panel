package app.minepanel.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.model.EventCategory
import app.minepanel.core.model.DraftType
import app.minepanel.core.model.label
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.repository.ManualFragmentRepository
import app.minepanel.data.repository.MilestoneRepository
import app.minepanel.ui.settings.ModelFileSection
import app.minepanel.ui.settings.PluginSection
import app.minepanel.ui.settings.ExportSection
import app.minepanel.ui.settings.BackupSection
import app.minepanel.ui.settings.RunStatusSection
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DraftQueueScreen(
    onBack: () -> Unit,
    viewModel: DraftViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFragmentForm by remember { mutableStateOf(false) }
    var showMilestoneForm by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showFragmentForm -> showFragmentForm = false
            showMilestoneForm -> showMilestoneForm = false
            else -> onBack()
        }
    }

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
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "草案确认",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showFragmentForm = true }) { Text("记一笔") }
            Spacer(Modifier.size(8.dp))
            if (state.generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                OutlinedButton(onClick = { viewModel.generateOnce() }) { Text("生成一次") }
            }
        }

        if (state.loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            return@Column
        }

        if (showFragmentForm) {
            FragmentForm(
                onSubmit = { content, mood, category, anchorMs ->
                    viewModel.addFragment(content, mood, category, anchorMs)
                    showFragmentForm = false
                },
                onCancel = { showFragmentForm = false }
            )
        }

        if (showMilestoneForm) {
            MilestoneForm(
                fragments = state.fragments,
                onSubmit = { title, label, note, anchorMs, linkedFragmentId ->
                    viewModel.addMilestone(title, label, note, anchorMs, linkedFragmentId)
                    showMilestoneForm = false
                },
                onCancel = { showMilestoneForm = false }
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { ModelFileSection() }

            item { RunStatusSection() }

            item { PluginSection() }

            item { AutoConfirmSection(viewModel) }

            item { ExportSection() }

            item { BackupSection() }

            if (state.fragments.isNotEmpty()) {
                item {
                    Text(
                        "我的片段 · ${state.fragments.size} 条（本人手写，已直接记入正式记忆）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(state.fragments, key = { "frag-${it.id}" }) { fragment ->
                    FragmentCard(fragment, onDelete = { viewModel.deleteFragment(fragment.id) })
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "我的里程碑 · ${state.milestones.size} 个（本人自定义阶段节点）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(onClick = { showMilestoneForm = true }) { Text("新建里程碑") }
                }
            }
            items(state.milestones, key = { "ms-${it.id}" }) { milestone ->
                MilestoneCard(milestone, onDelete = { viewModel.deleteMilestone(milestone.id) })
            }

            item {
                Text(
                    "待确认 ${state.pending.size} 条 · 确认后才会写入正式记忆",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.pending, key = { "draft-${it.id}" }) { draft ->
                DraftCard(
                    draft = draft,
                    onConfirm = { viewModel.confirm(draft.id) },
                    onModify = { edited -> viewModel.modify(draft.id, edited) },
                    onReject = { viewModel.reject(draft.id) }
                )
            }

            if (state.confirmed.isNotEmpty()) {
                item {
                    Text(
                        "已确认",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(state.confirmed, key = { "mem-${it.id}" }) { memory ->
                    ConfirmedCard(memory, onRevoke = { viewModel.revokeMemory(memory.id) })
                }
            }

            if (state.pending.isEmpty() && state.confirmed.isEmpty()) {
                item {
                    Text(
                        "暂无可确认的草案。本地推理完成后，草案会先出现在这里，由你逐条确认。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DraftCard(
    draft: DraftMemory,
    onConfirm: () -> Unit,
    onModify: (String) -> Unit,
    onReject: () -> Unit
) {
    var edited by remember(draft.id) { mutableStateOf(draft.contentJson) }
    val typeLabel = remember(draft.draftType) {
        runCatching { DraftType.valueOf(draft.draftType).label }.getOrDefault("草案")
    }

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatDate(draft.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("草案内容") },
                minLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm) { Text("确认原文") }
                OutlinedButton(onClick = { onModify(edited) }) { Text("修改并确认") }
                TextButton(onClick = onReject) { Text("丢弃") }
            }
        }
    }
}

@Composable
private fun ConfirmedCard(memory: ConfirmedMemory, onRevoke: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sourceLabel(memory.source),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatDate(memory.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRevoke) { Text("撤销") }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    ConfirmedMemory.SOURCE_AUTO_MODEL -> "自动确认"
    else -> "人工确认"
}

private fun formatDate(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

/** 解析可选的 `yyyy-MM-dd HH:mm` 锚点；空白或格式不符返回 null（不设锚点）。 */
private fun parseAnchorEpoch(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

@Composable
private fun FragmentForm(
    onSubmit: (content: String, mood: String?, category: String?, anchorEpochMs: Long?) -> Unit,
    onCancel: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var anchorText by remember { mutableStateOf("") }
    val options = remember {
        listOf<EventCategory?>(null) + listOf(
            EventCategory.WORK,
            EventCategory.SOCIAL,
            EventCategory.MEDIA,
            EventCategory.TOOL,
            EventCategory.REST
        )
    }

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("记一笔", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= ManualFragmentRepository.MAX_CONTENT_LEN) content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("片段内容") },
                minLines = 3
            )
            OutlinedTextField(
                value = mood,
                onValueChange = { if (it.length <= ManualFragmentRepository.MOOD_LEN) mood = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("心情（可选）") },
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = category == option?.name,
                        onClick = { category = option?.name },
                        label = { Text(option?.label ?: "不限") }
                    )
                }
            }
            OutlinedTextField(
                value = anchorText,
                onValueChange = { anchorText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("时间锚点（可选，格式 yyyy-MM-dd HH:mm）") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = content.isNotBlank(),
                    onClick = { onSubmit(content.trim(), mood.ifBlank { null }, category, parseAnchorEpoch(anchorText)) }
                ) { Text("保存") }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

@Composable
private fun FragmentCard(fragment: ManualFragment, onDelete: () -> Unit) {
    val meta = listOfNotNull(
        fragment.mood,
        fragment.category?.let { EventCategory.fromName(it).label }
    ).joinToString(" · ")

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fragment.content, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.size(0.dp))
                }
                Text(formatDate(fragment.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun MilestoneForm(
    fragments: List<ManualFragment>,
    onSubmit: (title: String, label: String?, note: String?, anchorEpochMs: Long?, linkedFragmentId: Long?) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var anchorText by remember { mutableStateOf("") }
    var linkedFragmentId by remember { mutableStateOf<Long?>(null) }

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("新建里程碑", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= MilestoneRepository.MAX_TITLE_LEN) title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("里程碑名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= MilestoneRepository.LABEL_LEN) label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义标签（可选）") },
                singleLine = true
            )
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= MilestoneRepository.NOTE_LEN) note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("说明（可选）") },
                minLines = 2
            )
            OutlinedTextField(
                value = anchorText,
                onValueChange = { anchorText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("时间锚点（可选，格式 yyyy-MM-dd HH:mm）") },
                singleLine = true
            )
            if (fragments.isNotEmpty()) {
                Text("关联片段（可选）", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = linkedFragmentId == null,
                        onClick = { linkedFragmentId = null },
                        label = { Text("不关联") }
                    )
                    fragments.forEach { fragment ->
                        FilterChip(
                            selected = linkedFragmentId == fragment.id,
                            onClick = { linkedFragmentId = fragment.id },
                            label = { Text(fragment.content.take(12)) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onSubmit(
                            title.trim(),
                            label.ifBlank { null },
                            note.ifBlank { null },
                            parseAnchorEpoch(anchorText),
                            linkedFragmentId
                        )
                    }
                ) { Text("保存") }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

@Composable
private fun MilestoneCard(milestone: Milestone, onDelete: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(milestone.title, style = MaterialTheme.typography.bodyMedium)
                milestone.label?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            milestone.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDate(milestone.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
