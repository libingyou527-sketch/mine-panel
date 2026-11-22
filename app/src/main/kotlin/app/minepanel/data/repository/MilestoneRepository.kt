package app.minepanel.data.repository

import app.minepanel.data.dao.MilestoneDao
import app.minepanel.data.db.entity.Milestone

/**
 * 本人自定义里程碑的写入与读取。
 *
 * 里程碑由用户主动建立，不经过草案确认队列；仅做非空、长度与同标题去重。
 * 全部操作在本地加密库内完成，不联网。
 */
class MilestoneRepository(private val milestoneDao: MilestoneDao) {

    suspend fun list(): List<Milestone> = milestoneDao.all()

    /**
     * 新增一条里程碑。标题去空白后为空、与已有标题重复时返回 false，不入库。
     * [label]/[note] 去空白后为空则存 null；[anchorEpochMs]/[linkedFragmentId] 可选，原样保留。
     */
    suspend fun add(
        title: String,
        label: String?,
        note: String?,
        anchorEpochMs: Long? = null,
        linkedFragmentId: Long? = null
    ): Boolean {
        val finalTitle = title.trim().take(MAX_TITLE_LEN)
        if (finalTitle.isEmpty()) return false
        if (milestoneDao.countTitle(finalTitle) > 0) return false

        val finalLabel = label?.trim()?.takeIf { it.isNotEmpty() }?.take(LABEL_LEN)
        val finalNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(NOTE_LEN)
        val now = System.currentTimeMillis()

        milestoneDao.insert(
            Milestone(
                title = finalTitle,
                label = finalLabel,
                note = finalNote,
                anchorEpochMs = anchorEpochMs,
                linkedFragmentId = linkedFragmentId,
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    suspend fun delete(id: Long) = milestoneDao.deleteById(id)

    companion object {
        const val MAX_TITLE_LEN = 40
        const val LABEL_LEN = 20
        const val NOTE_LEN = 200
    }
}
