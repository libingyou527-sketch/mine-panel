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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.model.EventCategory
import app.minepanel.core.model.label
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MemoryBookScreen(
    onBack: () -> Unit,
    viewModel: MemoryBookViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

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
                    text = "记忆本",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                return@Column
            }

            val fragmentById = state.fragments.associateBy { it.id }
            val allEmpty = state.memories.isEmpty() && state.tags.isEmpty() &&
                state.narratives.isEmpty() && state.fragments.isEmpty() &&
                state.milestones.isEmpty()

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (allEmpty) {
                    item {
                        Text(
                            "暂无沉淀内容。草案确认后的记忆、标签与回顾文案，以及本人手写片段和里程碑会汇总到这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.memories.isNotEmpty()) {
                    item { SectionTitle("正式记忆 · ${state.memories.size} 条") }
                    items(state.memories, key = { "mem-${it.id}" }) { memory ->
                        MemoryCard(memory)
                    }
                }

                if (state.tags.isNotEmpty()) {
                    item { SectionTitle("状态标签 · ${state.tags.size} 条") }
                    items(state.tags, key = { "tag-${it.id}" }) { tag ->
                        TagCard(tag)
                    }
                }

                if (state.narratives.isNotEmpty()) {
                    item { SectionTitle("回顾文案 · ${state.narratives.size} 条") }
                    items(state.narratives, key = { "nar-${it.id}" }) { narrative ->
                        NarrativeCard(narrative)
                    }
                }

                if (state.fragments.isNotEmpty()) {
                    item { SectionTitle("主动片段 · ${state.fragments.size} 条") }
                    items(state.fragments, key = { "frag-${it.id}" }) { fragment ->
                        FragmentBookCard(fragment)
                    }
                }

                if (state.milestones.isNotEmpty()) {
                    item { SectionTitle("里程碑 · ${state.milestones.size} 个") }
                    items(state.milestones, key = { "ms-${it.id}" }) { milestone ->
                        MilestoneBookCard(milestone, fragmentById[milestone.linkedFragmentId])
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MemoryCard(memory: ConfirmedMemory) {
    BookSurface {
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
                shortDate(memory.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TagCard(tag: DraftMemory) {
    BookSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                draftText(tag),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                shortDate(tag.reviewedAt ?: tag.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NarrativeCard(narrative: DraftMemory) {
    BookSurface {
        Text(draftText(narrative), style = MaterialTheme.typography.bodyMedium)
        Text(
            shortDate(narrative.reviewedAt ?: narrative.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FragmentBookCard(fragment: ManualFragment) {
    val meta = listOfNotNull(
        fragment.mood,
        fragment.category?.let { EventCategory.fromName(it).label }
    ).joinToString(" · ")

    BookSurface {
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
            Text(
                shortDate(fragment.anchorEpochMs ?: fragment.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MilestoneBookCard(milestone: Milestone, linkedFragment: ManualFragment?) {
    BookSurface {
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
        linkedFragment?.let {
            Text(
                "关联片段：${it.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            fullDate(milestone.anchorEpochMs ?: milestone.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

private fun draftText(draft: DraftMemory): String =
    draft.userEditedContent ?: draft.contentJson

private fun sourceLabel(source: String): String = when (source) {
    ConfirmedMemory.SOURCE_AUTO_MODEL -> "自动确认"
    else -> "人工确认"
}

private val shortFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun shortDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(shortFormatter)

private fun fullDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(fullFormatter)
