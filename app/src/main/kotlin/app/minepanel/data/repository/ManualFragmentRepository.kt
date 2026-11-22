package app.minepanel.data.repository

import app.minepanel.core.model.EventCategory
import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.db.entity.ManualFragment

/**
 * 本人主动片段的写入与读取。
 *
 * 手写内容提交即入正式记忆，不经过草案确认队列；仅做非空、长度与同正文去重。
 * 全部操作在本地加密库内完成，不联网。
 */
class ManualFragmentRepository(private val fragmentDao: ManualFragmentDao) {

    suspend fun list(): List<ManualFragment> = fragmentDao.all()

    /**
     * 新增一条手写片段。正文去空白后为空、与已有正文重复时返回 false，不入库。
     * [mood] 去空白后为空则存 null；[category] 无法识别为已知粗类别时存 null（不限）。
     */
    suspend fun add(
        content: String,
        mood: String?,
        category: String?,
        anchorEpochMs: Long?
    ): Boolean {
        val text = content.trim().take(MAX_CONTENT_LEN)
        if (text.isEmpty()) return false
        if (fragmentDao.countContent(text) > 0) return false

        val finalMood = mood?.trim()?.takeIf { it.isNotEmpty() }?.take(MOOD_LEN)
        val finalCategory = category?.let { EventCategory.fromName(it) }
            ?.takeIf { it != EventCategory.UNMAPPED }
            ?.name
        val now = System.currentTimeMillis()

        fragmentDao.insert(
            ManualFragment(
                content = text,
                mood = finalMood,
                category = finalCategory,
                anchorEpochMs = anchorEpochMs,
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    suspend fun delete(id: Long) = fragmentDao.deleteById(id)

    companion object {
        const val MAX_CONTENT_LEN = 500
        const val MOOD_LEN = 20
    }
}
