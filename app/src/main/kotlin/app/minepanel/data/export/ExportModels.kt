package app.minepanel.data.export

/**
 * 本地明文导出包的数据结构。
 *
 * 只承载本人已确认的记忆、状态标签、规则分值与徽章；原始使用事件按用户选择纳入或省略。
 * 包名、联系人、通知正文、聊天内容从不入库，因此也不会出现在导出文件中。
 */
data class ExportedMemory(
    val id: Long,
    val content: String,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long
)

data class ExportedTag(
    val id: Long,
    val label: String,
    val createdAt: Long,
    val reviewedAt: Long?
)

data class ExportedNarrative(
    val id: Long,
    val content: String,
    val createdAt: Long,
    val reviewedAt: Long?
)

data class ExportedScore(
    val attributeKey: String,
    val periodStart: Long,
    val periodEnd: Long,
    val score: Int,
    val confidence: Double,
    val evidenceJson: String,
    val sampleDays: Int,
    val updatedAt: Long
)

data class ExportedBadge(
    val badgeKey: String,
    val unlockedAt: Long,
    val conditionSnapshotJson: String
)

data class ExportedRawEvent(
    val timestamp: Long,
    val eventType: String,
    val category: String,
    val durationMs: Long,
    val sessionId: String?
)

data class ExportedFragment(
    val content: String,
    val mood: String?,
    val category: String?,
    val anchorEpochMs: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ExportedMilestone(
    val title: String,
    val label: String?,
    val note: String?,
    val anchorEpochMs: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ExportBundle(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val confirmedMemories: List<ExportedMemory>,
    val statusTags: List<ExportedTag>,
    val narratives: List<ExportedNarrative> = emptyList(),
    val attributeScores: List<ExportedScore>,
    val badges: List<ExportedBadge>,
    /** 原始事件为可选项；null 时导出文件不输出该节。 */
    val rawEvents: List<ExportedRawEvent>?,
    val manualFragments: List<ExportedFragment> = emptyList(),
    val milestones: List<ExportedMilestone> = emptyList()
)
