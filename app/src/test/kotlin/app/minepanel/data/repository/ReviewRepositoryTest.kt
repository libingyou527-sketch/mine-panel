package app.minepanel.data.repository

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.time.ReviewPeriod
import app.minepanel.core.time.ReviewWindows
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DailyAggregate
import app.minepanel.data.db.entity.ManualFragment
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReviewRepositoryTest {

    private val aggregateDao = mockk<DailyAggregateDao>(relaxed = true)
    private val scoreDao = mockk<AttributeScoreDao>(relaxed = true)
    private val confirmedDao = mockk<ConfirmedMemoryDao>(relaxed = true)
    private val fragmentDao = mockk<ManualFragmentDao>(relaxed = true)

    private val repo = ReviewRepository(aggregateDao, scoreDao, confirmedDao, fragmentDao)
    private val window = ReviewWindows.window(ReviewPeriod.MONTH, 0, LocalDate.of(2026, 11, 22))

    @Test
    fun `empty window yields zero totals and default scores`() = runBlocking {
        coEvery { aggregateDao.between(any(), any()) } returns emptyList()
        coEvery { confirmedDao.between(any(), any()) } returns emptyList()
        coEvery { fragmentDao.between(any(), any()) } returns emptyList()
        coEvery { scoreDao.latest(any()) } returns null

        val summary = repo.summarize(window)

        assertEquals(0, summary.sampleDays)
        assertEquals(0, summary.totalScreenTimeMin)
        assertEquals(0, summary.memoryCount)
        assertEquals(0, summary.fragmentCount)
        assertTrue(summary.memoryHighlights.isEmpty())
        assertTrue(summary.fragmentHighlights.isEmpty())
        assertTrue(summary.scores.values.all { it == 50 })
    }

    @Test
    fun `aggregates minutes by category and counts highlights`() = runBlocking {
        coEvery { aggregateDao.between(any(), any()) } returns listOf(
            DailyAggregate("2026-11-20", "WORK", 60_000, 3, 1, null, null, 1),
            DailyAggregate("2026-11-21", "WORK", 60_000, 2, 1, null, null, 1),
            DailyAggregate("2026-11-21", "MEDIA", 180_000, 4, 2, null, null, 1)
        )
        coEvery { confirmedDao.between(any(), any()) } returns listOf(
            ConfirmedMemory(1, null, "确认要点一", window.startMs, window.endMs, 1),
            ConfirmedMemory(2, null, "确认要点二", window.startMs, window.endMs, 1)
        )
        coEvery { fragmentDao.between(any(), any()) } returns listOf(
            ManualFragment(1, "主动片段", null, null, null, 1, 1)
        )

        val summary = repo.summarize(window)

        assertEquals(2, summary.sampleDays)
        assertEquals(5, summary.totalScreenTimeMin)
        assertEquals(2, summary.categoryMinutes["WORK"])
        assertEquals(3, summary.categoryMinutes["MEDIA"])
        assertEquals(2, summary.memoryCount)
        assertEquals(1, summary.fragmentCount)
        assertEquals(listOf("确认要点一", "确认要点二"), summary.memoryHighlights)
    }

    @Test
    fun `highlights drop blank duplicates and truncate long text`() = runBlocking {
        coEvery { aggregateDao.between(any(), any()) } returns emptyList()
        coEvery { confirmedDao.between(any(), any()) } returns listOf(
            ConfirmedMemory(1, null, "重复要点", window.startMs, window.endMs, 1),
            ConfirmedMemory(2, null, "重复要点", window.startMs, window.endMs, 1),
            ConfirmedMemory(3, null, "   ", window.startMs, window.endMs, 1),
            ConfirmedMemory(4, null, "字".repeat(50), window.startMs, window.endMs, 1)
        )
        coEvery { fragmentDao.between(any(), any()) } returns emptyList()
        coEvery { scoreDao.latest(any()) } returns null

        val summary = repo.summarize(window)

        assertEquals(2, summary.memoryHighlights.size)
        assertEquals("重复要点", summary.memoryHighlights[0])
        assertEquals(41, summary.memoryHighlights[1].length)
        assertTrue(summary.memoryHighlights[1].endsWith("…"))
    }

    @Test
    fun `summary carries window label`() = runBlocking {
        coEvery { aggregateDao.between(any(), any()) } returns emptyList()
        coEvery { confirmedDao.between(any(), any()) } returns emptyList()
        coEvery { fragmentDao.between(any(), any()) } returns emptyList()

        val summary = repo.summarize(window)

        assertEquals(window.label, summary.window.label)
        assertEquals(AttributeKey.entries.size, summary.scores.size)
    }
}
