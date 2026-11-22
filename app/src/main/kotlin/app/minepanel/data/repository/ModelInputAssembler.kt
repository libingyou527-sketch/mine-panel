package app.minepanel.data.repository

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.time.TimeUtils
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.model.ModelInput

/**
 * 组装模型输入：只取近一周期的结论性聚合数值（六维分值、总时长、粗类别分钟、样本天数）。
 * 不读取原始事件流、包名或正文；周环比仅用于本地面板，不下发模型。
 */
class ModelInputAssembler(
    private val aggregateDao: DailyAggregateDao,
    private val scoreDao: AttributeScoreDao
) {

    suspend fun assemble(fromMs: Long, toMs: Long): ModelInput {
        val rows = aggregateDao.between(TimeUtils.dateKey(fromMs), TimeUtils.dateKey(toMs))

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

        return ModelInput(
            scores = scores,
            totalScreenTimeMin = totalMinutes,
            sampleDays = sampleDays,
            categoryMinutes = categoryMinutes,
            weekOverWeek = emptyMap()
        )
    }
}
