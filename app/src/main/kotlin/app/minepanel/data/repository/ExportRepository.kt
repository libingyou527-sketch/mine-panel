package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.data.export.ExportBundle
import app.minepanel.data.export.ExportMapper
import app.minepanel.data.export.JsonExporter

/**
 * 本地明文 JSON 导出协调器。
 *
 * 全程只读取本地加密库与 SAF 用户选定的文件描述符：不联网、不上传、不写剪贴板。
 * 导出内容为已确认记忆、状态标签、规则分值与徽章；原始使用事件按用户选择纳入。
 * 待确认 / 已丢弃草案、推理日志、插件占位事件不进入导出包。
 */
class ExportRepository(private val context: Context) {

    sealed interface ExportResult {
        data class Success(
            val memoryCount: Int,
            val tagCount: Int,
            val narrativeCount: Int,
            val scoreCount: Int,
            val badgeCount: Int,
            val rawCount: Int?,
            val fragmentCount: Int,
            val milestoneCount: Int
        ) : ExportResult

        data class Failure(val reason: String) : ExportResult
    }

    /** 读取本地库并组装导出包；不触碰文件系统。 */
    internal suspend fun buildBundle(includeRawEvents: Boolean): ExportBundle {
        val db = ServiceLocator.database()
        val memories = db.confirmedMemoryDao().all().map(ExportMapper::memory)
        val tags = db.draftMemoryDao()
            .byTypeAndStatuses(
                DraftType.STATUS_TAG.name,
                listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
            )
            .map(ExportMapper::tag)
        val narratives = db.draftMemoryDao()
            .byTypeAndStatuses(
                DraftType.NARRATIVE.name,
                listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
            )
            .map(ExportMapper::narrative)
        val scores = db.attributeScoreDao().all().map(ExportMapper::score)
        val badges = db.badgeDao().all().map(ExportMapper::badge)
        val fragments = db.manualFragmentDao().all().map(ExportMapper::fragment)
        val milestones = db.milestoneDao().all().map(ExportMapper::milestone)
        val rawEvents = if (includeRawEvents) {
            db.rawUsageEventDao().all().map(ExportMapper::rawEvent)
        } else {
            null
        }
        return ExportBundle(
            schemaVersion = JsonExporter.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = currentAppVersion(),
            confirmedMemories = memories,
            statusTags = tags,
            narratives = narratives,
            attributeScores = scores,
            badges = badges,
            rawEvents = rawEvents,
            manualFragments = fragments,
            milestones = milestones
        )
    }

    /** 序列化导出包并写入 SAF uri；成功后记录本次导出时间。 */
    suspend fun exportTo(uri: Uri, includeRawEvents: Boolean): ExportResult = runCatching {
        val bundle = buildBundle(includeRawEvents)
        val payload = JsonExporter.toJson(bundle)
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("无法写入所选文件")

        ServiceLocator.settings().setLastExportEpochMs(bundle.exportedAt)
        ExportResult.Success(
            memoryCount = bundle.confirmedMemories.size,
            tagCount = bundle.statusTags.size,
            narrativeCount = bundle.narratives.size,
            scoreCount = bundle.attributeScores.size,
            badgeCount = bundle.badges.size,
            rawCount = bundle.rawEvents?.size,
            fragmentCount = bundle.manualFragments.size,
            milestoneCount = bundle.milestones.size
        )
    }.getOrElse {
        ExportResult.Failure(it.message ?: "导出失败")
    }

    private fun currentAppVersion(): String = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "unknown"
    }.getOrDefault("unknown")
}
