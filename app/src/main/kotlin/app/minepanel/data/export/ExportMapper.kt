package app.minepanel.data.export

import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.db.entity.RawUsageEvent

/** 实体到导出 DTO 的纯映射；不读库、不触碰 Android 框架，便于纯 JVM 单测。 */
object ExportMapper {

    fun memory(entity: ConfirmedMemory) = ExportedMemory(
        id = entity.id,
        content = entity.content,
        periodStart = entity.periodStart,
        periodEnd = entity.periodEnd,
        createdAt = entity.createdAt
    )

    fun tag(entity: DraftMemory) = ExportedTag(
        id = entity.id,
        label = entity.userEditedContent ?: entity.contentJson,
        createdAt = entity.createdAt,
        reviewedAt = entity.reviewedAt
    )

    fun narrative(entity: DraftMemory) = ExportedNarrative(
        id = entity.id,
        content = entity.userEditedContent ?: entity.contentJson,
        createdAt = entity.createdAt,
        reviewedAt = entity.reviewedAt
    )

    fun score(entity: AttributeScore) = ExportedScore(
        attributeKey = entity.attributeKey,
        periodStart = entity.periodStart,
        periodEnd = entity.periodEnd,
        score = entity.score,
        confidence = entity.confidence,
        evidenceJson = entity.evidenceJson,
        sampleDays = entity.sampleDays,
        updatedAt = entity.updatedAt
    )

    fun badge(entity: Badge) = ExportedBadge(
        badgeKey = entity.badgeKey,
        unlockedAt = entity.unlockedAt,
        conditionSnapshotJson = entity.conditionSnapshotJson
    )

    fun rawEvent(entity: RawUsageEvent) = ExportedRawEvent(
        timestamp = entity.timestamp,
        eventType = entity.eventType,
        category = entity.category,
        durationMs = entity.durationMs,
        sessionId = entity.sessionId
    )

    fun fragment(entity: ManualFragment) = ExportedFragment(
        content = entity.content,
        mood = entity.mood,
        category = entity.category,
        anchorEpochMs = entity.anchorEpochMs,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun milestone(entity: Milestone) = ExportedMilestone(
        title = entity.title,
        label = entity.label,
        note = entity.note,
        anchorEpochMs = entity.anchorEpochMs,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
