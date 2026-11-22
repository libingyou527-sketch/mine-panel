package app.minepanel.data.repository

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.time.ReviewWindow
import app.minepanel.core.time.TimeUtils
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.dao.ManualFragmentDao

/**
 * 长周期回顾汇编：按给定自然窗汇总样本天数、总时长、粗类别分钟、六维最新分值，
 * 以及窗内已确认记忆与主动片段的条数和截断要点。
 *
 * 约束：只取结论性聚合数值与截断短句，不读原始事件流、包名或正文全文；分值仍由规则引擎写入，
 * 仓储本身不回写任何数值。
 */
class ReviewRepository(
    private val aggregateDao: DailyAggregateDao,
    private val scoreDao: AttributeScoreDao,
    private val confirmedDao: ConfirmedMemoryDao,
    private val fragmentDao: ManualFragmentDao
) {

    suspend fun summarize(window: ReviewWindow): ReviewSummary {
        val rows = aggregateDao.between(
            TimeUtils.dateKey(window.startMs),
            TimeUtils.dateKey(window.endMs - 1)
        )

        val categoryMinutes = rows
            .groupBy { it.category }
            .mapValues { (_, categoryRows) ->
                (categoryRows.sumOf { it.foregroundMs } / 60_000L).toInt()
            }
        val totalMinutes = (rows.sumOf { it.foregroundMs } / 60_000L).toInt()
        val sampleDays = rows.map { it.date }.distinct().size

        val scores = AttributeKey.entries.associateWith { key ->
            scoreDao.latest(key.name)?.score ?: 50
        }

        val memories = confirmedDao.between(window.startMs, window.endMs)
        val fragments = fragmentDao.between(window.startMs, window.endMs)

        return ReviewSummary(
            window = window,
            sampleDays = sampleDays,
            totalScreenTimeMin = totalMinutes,
            categoryMinutes = categoryMinutes,
            scores = scores,
            memoryCount = memories.size,
            memoryHighlights = highlights(memories.map { it.content }),
            fragmentCount = fragments.size,
            fragmentHighlights = highlights(fragments.map { it.content })
        )
    }

    private fun highlights(raw: List<String>): List<String> {
        return raw.mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
            .distinct()
            .take(MAX_HIGHLIGHTS)
            .map { content ->
                if (content.length > MAX_HIGHLIGHT_LEN) content.take(MAX_HIGHLIGHT_LEN) + "…" else content
            }
    }

    companion object {
        private const val MAX_HIGHLIGHTS = 5
        private const val MAX_HIGHLIGHT_LEN = 40
    }
}

/** 回顾窗的只读汇总快照，供 UI 展示与本地模型/模板输入使用。 */
data class ReviewSummary(
    val window: ReviewWindow,
    val sampleDays: Int,
    val totalScreenTimeMin: Int,
    val categoryMinutes: Map<String, Int>,
    val scores: Map<AttributeKey, Int>,
    val memoryCount: Int,
    val memoryHighlights: List<String>,
    val fragmentCount: Int,
    val fragmentHighlights: List<String>
)
