package app.minepanel.data.repository

import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.core.model.ModelStatus
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DraftMemoryDao
import app.minepanel.data.dao.ModelRunLogDao
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ModelRunLog
import app.minepanel.model.ModelOutput

/**
 * 草案状态机与正式记忆写入：仅 CONFIRMED / MODIFIED 的草案进入 ConfirmedMemory；
 * REJECTED 不落正式记忆。所有流转只在本地加密库内完成。
 */
class DraftRepository(
    private val draftDao: DraftMemoryDao,
    private val confirmedDao: ConfirmedMemoryDao,
    private val runLogDao: ModelRunLogDao
) {

    suspend fun pendingDrafts(): List<DraftMemory> =
        draftDao.byStatus(DraftStatus.PENDING.name)

    suspend fun confirmed(from: Long, to: Long): List<ConfirmedMemory> =
        confirmedDao.between(from, to)

    suspend fun allByStatus(status: DraftStatus): List<DraftMemory> =
        draftDao.byStatus(status.name)

    /** 已确认（含修改后确认）的状态标签，供记忆本专区浏览。 */
    suspend fun confirmedTags(): List<DraftMemory> = draftDao.byTypeAndStatuses(
        DraftType.STATUS_TAG.name,
        listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
    )

    /** 已确认（含修改后确认）的回顾文案，供记忆本专区浏览。 */
    suspend fun confirmedNarratives(): List<DraftMemory> = draftDao.byTypeAndStatuses(
        DraftType.NARRATIVE.name,
        listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
    )

    /**
     * 将一次模型输出拆成草案行写入 PENDING 队列；返回新插入行 id。
     * [autoConfirm] 为 true 时（仅本地模型 SUCCESS 且用户开启自动确认），逐条尝试自动裁决；
     * 重复条目或单条异常回落 PENDING 人工队列，不写半成品。
     */
    suspend fun insertDrafts(
        runLogId: Long?,
        output: ModelOutput,
        periodStart: Long,
        periodEnd: Long,
        autoConfirm: Boolean = false
    ): List<Long> {
        val now = System.currentTimeMillis()
        val rows = buildList {
            add(
                DraftMemory(
                    modelRunId = runLogId,
                    draftType = DraftType.NARRATIVE.name,
                    contentJson = output.panelNarrative,
                    status = DraftStatus.PENDING.name,
                    userEditedContent = null,
                    createdAt = now,
                    reviewedAt = null
                )
            )
            output.statusTags.forEach { tag ->
                add(
                    DraftMemory(
                        modelRunId = runLogId,
                        draftType = DraftType.STATUS_TAG.name,
                        contentJson = tag,
                        status = DraftStatus.PENDING.name,
                        userEditedContent = null,
                        createdAt = now,
                        reviewedAt = null
                    )
                )
            }
            output.draftMemories.forEach { memory ->
                add(
                    DraftMemory(
                        modelRunId = runLogId,
                        draftType = DraftType.MEMORY.name,
                        contentJson = memory,
                        status = DraftStatus.PENDING.name,
                        userEditedContent = null,
                        createdAt = now,
                        reviewedAt = null
                    )
                )
            }
        }
        return rows.map { row ->
            val id = draftDao.insert(row)
            if (autoConfirm) {
                runCatching { autoSettle(row.copy(id = id), now) }
            }
            id
        }
    }

    /**
     * 自动裁决：非重复则置 CONFIRMED；仅 MEMORY 额外写入正式记忆表，
     * 状态标签与回顾文案只在草案表保留已确认身份，不沉淀为长期记忆。重复则保留 PENDING 交人工把关。
     */
    private suspend fun autoSettle(draft: DraftMemory, now: Long) {
        if (isDuplicate(draft)) return
        draftDao.review(draft.id, DraftStatus.CONFIRMED.name, null, now)
        if (draft.draftType == DraftType.MEMORY.name) {
            confirmedDao.insert(
                ConfirmedMemory(
                    originDraftId = draft.id,
                    content = draft.contentJson,
                    periodStart = draft.createdAt,
                    periodEnd = now,
                    createdAt = now,
                    source = ConfirmedMemory.SOURCE_AUTO_MODEL
                )
            )
        }
    }

    private suspend fun isDuplicate(draft: DraftMemory): Boolean {
        val content = draft.contentJson
        return when (draft.draftType) {
            DraftType.STATUS_TAG.name -> draftDao.countConfirmedContent(
                content,
                DraftType.STATUS_TAG.name,
                DraftStatus.CONFIRMED.name
            ) > 0
            DraftType.MEMORY.name -> confirmedDao.countContent(content) > 0
            // 回顾文案每期不同，不参与跨期查重。
            else -> false
        }
    }

    /** 确认草案；未编辑时保留原文。仅 PENDING 可裁决。 */
    suspend fun confirm(id: Long): Boolean = settle(id, DraftStatus.CONFIRMED, null)

    /** 修改后确认；以编辑后文本作为正式记忆内容。 */
    suspend fun modify(id: Long, edited: String): Boolean =
        settle(id, DraftStatus.MODIFIED, edited.takeIf { it.isNotBlank() })

    /** 丢弃草案；不写入正式记忆。 */
    suspend fun reject(id: Long): Boolean {
        val draft = draftDao.byId(id) ?: return false
        if (draft.status != DraftStatus.PENDING.name) return false
        draftDao.review(id, DraftStatus.REJECTED.name, null, System.currentTimeMillis())
        return true
    }

    private suspend fun settle(
        id: Long,
        status: DraftStatus,
        edited: String?
    ): Boolean {
        val draft = draftDao.byId(id) ?: return false
        if (draft.status != DraftStatus.PENDING.name) return false

        val finalContent = edited ?: draft.userEditedContent ?: draft.contentJson
        val now = System.currentTimeMillis()
        draftDao.review(id, status.name, edited, now)
        // 仅长期记忆写入正式记忆表；标签与回顾文案只更新草案状态。
        if (draft.draftType == DraftType.MEMORY.name) {
            confirmedDao.insert(
                ConfirmedMemory(
                    originDraftId = id,
                    content = finalContent,
                    periodStart = draft.createdAt,
                    periodEnd = now,
                    createdAt = now,
                    source = ConfirmedMemory.SOURCE_MANUAL
                )
            )
        }
        return true
    }

    /** 撤销一条已写入的正式记忆（仅删除正式记忆行，不把对应草案回退到待确认队列）。 */
    suspend fun revoke(id: Long) {
        confirmedDao.deleteById(id)
    }

    /** 记录一次本地推理运行；输入只留存哈希。 */
    suspend fun recordRun(
        startedAt: Long,
        finishedAt: Long,
        status: ModelStatus,
        engineName: String,
        inputSnapshotHash: String,
        outputJson: String?,
        latencyMs: Long
    ): Long = runLogDao.insert(
        ModelRunLog(
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status.name,
            engineName = engineName,
            inputSnapshotHash = inputSnapshotHash,
            outputJson = outputJson,
            latencyMs = latencyMs
        )
    )
}
