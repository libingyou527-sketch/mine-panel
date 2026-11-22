package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.BadgeDao
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DraftMemoryDao
import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.dao.MilestoneDao
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.db.entity.RawUsageEvent
import app.minepanel.data.export.ExportedBadge
import app.minepanel.data.export.ExportedFragment
import app.minepanel.data.export.ExportedMilestone
import app.minepanel.data.export.ExportedRawEvent
import app.minepanel.data.export.ExportedScore
import app.minepanel.data.export.ImportConflictStrategy
import app.minepanel.data.export.ImportException
import app.minepanel.data.export.ImportParser
import app.minepanel.data.export.ParsedImport

/** 各节导入与跳过计数。 */
data class ImportReport(
    val memoriesImported: Int = 0,
    val memoriesSkipped: Int = 0,
    val tagsImported: Int = 0,
    val tagsSkipped: Int = 0,
    val narrativesImported: Int = 0,
    val narrativesSkipped: Int = 0,
    val scoresImported: Int = 0,
    val scoresSkipped: Int = 0,
    val badgesImported: Int = 0,
    val badgesSkipped: Int = 0,
    val rawImported: Int = 0,
    val rawSkipped: Int = 0,
    val fragmentsImported: Int = 0,
    val fragmentsSkipped: Int = 0,
    val milestonesImported: Int = 0,
    val milestonesSkipped: Int = 0
)

/**
 * 本地明文 JSON 导入协调器。
 *
 * 读取 SAF 选定的本地文件 → schema 校验 → 按冲突策略查重后入库。
 * 全程本地完成，不联网；默认 [ImportConflictStrategy.SKIP_DUPLICATES]，不覆盖已有正式记忆。
 */
class ImportRepository(private val context: Context) {

    sealed interface ImportResult {
        data class Success(val report: ImportReport) : ImportResult
        data class Failure(val reason: String) : ImportResult
    }

    suspend fun importFrom(
        uri: Uri,
        strategy: ImportConflictStrategy = ImportConflictStrategy.SKIP_DUPLICATES
    ): ImportResult = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw ImportException("无法读取所选文件")

        val parsed = ImportParser.parse(text)
        val db = ServiceLocator.database()
        val report = persist(
            parsed = parsed,
            strategy = strategy,
            confirmedDao = db.confirmedMemoryDao(),
            draftDao = db.draftMemoryDao(),
            scoreDao = db.attributeScoreDao(),
            badgeDao = db.badgeDao(),
            rawDao = db.rawUsageEventDao(),
            fragmentDao = db.manualFragmentDao(),
            milestoneDao = db.milestoneDao()
        )
        ImportResult.Success(report)
    }.getOrElse {
        ImportResult.Failure(it.message ?: "导入失败")
    }

    /** 解析结果按策略查重入库；DAO 显式注入，便于纯 JVM 单测覆盖计数与映射。 */
    internal suspend fun persist(
        parsed: ParsedImport,
        strategy: ImportConflictStrategy,
        confirmedDao: ConfirmedMemoryDao,
        draftDao: DraftMemoryDao,
        scoreDao: AttributeScoreDao,
        badgeDao: BadgeDao,
        rawDao: RawUsageEventDao,
        fragmentDao: ManualFragmentDao,
        milestoneDao: MilestoneDao
    ): ImportReport {
        val now = System.currentTimeMillis()
        var report = ImportReport()

        parsed.memories.forEach { memory ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                confirmedDao.countSame(memory.content, memory.periodStart, memory.periodEnd) > 0
            if (duplicate) {
                report = report.copy(memoriesSkipped = report.memoriesSkipped + 1)
            } else {
                confirmedDao.insert(
                    ConfirmedMemory(
                        originDraftId = null,
                        content = memory.content,
                        periodStart = memory.periodStart,
                        periodEnd = memory.periodEnd,
                        createdAt = memory.createdAt
                    )
                )
                report = report.copy(memoriesImported = report.memoriesImported + 1)
            }
        }

        parsed.tags.forEach { tag ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                draftDao.countTag(
                    label = tag.label,
                    createdAt = tag.createdAt,
                    type = DraftType.STATUS_TAG.name,
                    status = DraftStatus.CONFIRMED.name
                ) > 0
            if (duplicate) {
                report = report.copy(tagsSkipped = report.tagsSkipped + 1)
            } else {
                draftDao.insert(
                    DraftMemory(
                        modelRunId = null,
                        draftType = DraftType.STATUS_TAG.name,
                        contentJson = tag.label,
                        status = DraftStatus.CONFIRMED.name,
                        userEditedContent = null,
                        createdAt = tag.createdAt,
                        reviewedAt = tag.reviewedAt ?: now
                    )
                )
                report = report.copy(tagsImported = report.tagsImported + 1)
            }
        }

        parsed.narratives.forEach { narrative ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                draftDao.countTag(
                    label = narrative.content,
                    createdAt = narrative.createdAt,
                    type = DraftType.NARRATIVE.name,
                    status = DraftStatus.CONFIRMED.name
                ) > 0
            if (duplicate) {
                report = report.copy(narrativesSkipped = report.narrativesSkipped + 1)
            } else {
                draftDao.insert(
                    DraftMemory(
                        modelRunId = null,
                        draftType = DraftType.NARRATIVE.name,
                        contentJson = narrative.content,
                        status = DraftStatus.CONFIRMED.name,
                        userEditedContent = null,
                        createdAt = narrative.createdAt,
                        reviewedAt = narrative.reviewedAt ?: now
                    )
                )
                report = report.copy(narrativesImported = report.narrativesImported + 1)
            }
        }

        parsed.scores.forEach { score ->
            val duplicate = strategy == ImportConflictStrategy.SKIP_DUPLICATES &&
                scoreDao.countSame(score.attributeKey, score.periodStart, score.periodEnd) > 0
            if (duplicate) {
                report = report.copy(scoresSkipped = report.scoresSkipped + 1)
            } else {
                scoreDao.upsert(score.toEntity())
                report = report.copy(scoresImported = report.scoresImported + 1)
            }
        }

        parsed.badges.forEach { badge ->
            val duplicate = strategy == ImportConflictStrategy.SKIP_DUPLICATES &&
                badgeDao.countByKey(badge.badgeKey) > 0
            if (duplicate) {
                report = report.copy(badgesSkipped = report.badgesSkipped + 1)
            } else {
                badgeDao.upsert(badge.toEntity())
                report = report.copy(badgesImported = report.badgesImported + 1)
            }
        }

        parsed.rawEvents.forEach { event ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                rawDao.countSame(
                    timestamp = event.timestamp,
                    eventType = event.eventType,
                    category = event.category,
                    durationMs = event.durationMs,
                    sessionId = event.sessionId
                ) > 0
            if (duplicate) {
                report = report.copy(rawSkipped = report.rawSkipped + 1)
            } else {
                rawDao.insert(event.toEntity())
                report = report.copy(rawImported = report.rawImported + 1)
            }
        }

        parsed.fragments.forEach { fragment ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                fragmentDao.countContent(fragment.content) > 0
            if (duplicate) {
                report = report.copy(fragmentsSkipped = report.fragmentsSkipped + 1)
            } else {
                fragmentDao.insert(fragment.toEntity())
                report = report.copy(fragmentsImported = report.fragmentsImported + 1)
            }
        }

        parsed.milestones.forEach { milestone ->
            val duplicate = strategy != ImportConflictStrategy.APPEND_ALL &&
                milestoneDao.countTitle(milestone.title) > 0
            if (duplicate) {
                report = report.copy(milestonesSkipped = report.milestonesSkipped + 1)
            } else {
                milestoneDao.insert(milestone.toEntity())
                report = report.copy(milestonesImported = report.milestonesImported + 1)
            }
        }

        return report
    }

    private fun ExportedScore.toEntity() = AttributeScore(
        attributeKey = attributeKey,
        periodStart = periodStart,
        periodEnd = periodEnd,
        score = score,
        confidence = confidence,
        evidenceJson = evidenceJson,
        sampleDays = sampleDays,
        updatedAt = updatedAt
    )

    private fun ExportedBadge.toEntity() = Badge(
        badgeKey = badgeKey,
        unlockedAt = unlockedAt,
        conditionSnapshotJson = conditionSnapshotJson
    )

    private fun ExportedRawEvent.toEntity() = RawUsageEvent(
        timestamp = timestamp,
        eventType = eventType,
        category = category,
        durationMs = durationMs,
        sessionId = sessionId
    )

    private fun ExportedFragment.toEntity() = ManualFragment(
        content = content,
        mood = mood,
        category = category,
        anchorEpochMs = anchorEpochMs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportedMilestone.toEntity() = Milestone(
        title = title,
        label = label,
        note = note,
        anchorEpochMs = anchorEpochMs,
        // 跨库不保留片段内部主键，避免悬空软关联。
        linkedFragmentId = null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
