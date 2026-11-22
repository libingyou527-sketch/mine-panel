package app.minepanel.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewWindowsTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 11, 22)

    private fun epoch(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `current month window spans whole natural month`() {
        val w = ReviewWindows.window(ReviewPeriod.MONTH, 0, now)
        assertEquals(epoch(2026, 11, 1), w.startMs)
        assertEquals(epoch(2026, 12, 1), w.endMs)
        assertEquals("2026年11月", w.label)
    }

    @Test
    fun `previous month offset aligns to prior natural month`() {
        val w = ReviewWindows.window(ReviewPeriod.MONTH, 1, now)
        assertEquals(epoch(2026, 10, 1), w.startMs)
        assertEquals(epoch(2026, 11, 1), w.endMs)
        assertEquals("2026年10月", w.label)
    }

    @Test
    fun `current quarter starts on first month of quarter`() {
        val w = ReviewWindows.window(ReviewPeriod.QUARTER, 0, now)
        assertEquals(epoch(2026, 10, 1), w.startMs)
        assertEquals(epoch(2027, 1, 1), w.endMs)
        assertEquals("2026年第4季度", w.label)
    }

    @Test
    fun `previous quarter rolls back three months`() {
        val w = ReviewWindows.window(ReviewPeriod.QUARTER, 1, now)
        assertEquals(epoch(2026, 7, 1), w.startMs)
        assertEquals(epoch(2026, 10, 1), w.endMs)
        assertEquals("2026年第3季度", w.label)
    }

    @Test
    fun `year window spans whole year`() {
        val w = ReviewWindows.window(ReviewPeriod.YEAR, 0, now)
        assertEquals(epoch(2026, 1, 1), w.startMs)
        assertEquals(epoch(2027, 1, 1), w.endMs)
        assertEquals("2026年", w.label)
    }

    @Test
    fun `previous year offset crosses year boundary`() {
        val w = ReviewWindows.window(ReviewPeriod.YEAR, 1, now)
        assertEquals(epoch(2025, 1, 1), w.startMs)
        assertEquals(epoch(2026, 1, 1), w.endMs)
        assertEquals("2025年", w.label)
    }

    @Test
    fun `window is half open and non empty`() {
        ReviewPeriod.entries.forEach { period ->
            val w = ReviewWindows.window(period, 0, now)
            assertTrue(w.endMs > w.startMs)
        }
    }

    @Test
    fun `fromName defaults to month on unknown`() {
        assertEquals(ReviewPeriod.MONTH, ReviewPeriod.fromName(null))
        assertEquals(ReviewPeriod.QUARTER, ReviewPeriod.fromName("QUARTER"))
    }
}
