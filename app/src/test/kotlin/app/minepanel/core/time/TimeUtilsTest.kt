package app.minepanel.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeUtilsTest {

    private fun epochAt(hour: Int, minute: Int, dayOffset: Long = 0): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone)
            .plusDays(dayOffset)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun `block crossing midnight is split into two days`() {
        val start = epochAt(23, 50)
        val end = start + 20 * 60_000

        val segments = TimeUtils.splitByDay(start, end)

        assertEquals(2, segments.size)
        assertEquals(10 * 60_000, segments[0].ms)
        assertEquals(10 * 60_000, segments[1].ms)
    }

    @Test
    fun `same day block stays in one segment`() {
        val start = epochAt(10, 0)
        val end = start + 30 * 60_000

        val segments = TimeUtils.splitByDay(start, end)

        assertEquals(1, segments.size)
        assertEquals(30 * 60_000, segments[0].ms)
    }

    @Test
    fun `empty interval returns no segments`() {
        val start = epochAt(10, 0)
        assertEquals(0, TimeUtils.splitByDay(start, start).size)
    }
}
