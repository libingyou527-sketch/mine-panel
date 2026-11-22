package app.minepanel.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 一段属于某自然日的时长（毫秒）。 */
data class DaySegment(val dateKey: String, val ms: Long)

/**
 * 跨天时间工具：统一使用系统默认时区（java.time），避免熬夜使用被错误算到第二天。
 */
object TimeUtils {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun localDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDate()

    fun dateKey(epochMs: Long): String = localDate(epochMs).toString()

    /**
     * 将 [startMs, endMs) 区间按本地自然日切分。
     * 跨天块会在午夜边界拆开，分别计入对应日期的时长。
     */
    fun splitByDay(startMs: Long, endMs: Long): List<DaySegment> {
        if (endMs <= startMs) return emptyList()
        val z = zone()
        var cursor = Instant.ofEpochMilli(startMs)
        val endInstant = Instant.ofEpochMilli(endMs)
        val out = mutableListOf<DaySegment>()

        while (cursor.isBefore(endInstant)) {
            val date = cursor.atZone(z).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(z).toInstant()
            val segmentEnd = if (nextMidnight.isBefore(endInstant)) nextMidnight else endInstant
            out += DaySegment(date.toString(), segmentEnd.toEpochMilli() - cursor.toEpochMilli())
            cursor = segmentEnd
        }
        return out
    }

    /** 当天 00:00 的 epoch ms（本地时区）。 */
    fun startOfToday(): Long = LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toEpochMilli()
}
