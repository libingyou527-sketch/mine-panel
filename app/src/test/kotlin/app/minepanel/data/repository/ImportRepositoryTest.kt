package app.minepanel.data.repository

import android.content.Context
import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.BadgeDao
import app.minepanel.data.dao.ConfirmedMemoryDao
import app.minepanel.data.dao.DraftMemoryDao
import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.dao.MilestoneDao
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.db.entity.RawUsageEvent
import app.minepanel.data.export.ExportedBadge
import app.minepanel.data.export.ExportedFragment
import app.minepanel.data.export.ExportedMemory
import app.minepanel.data.export.ExportedMilestone
import app.minepanel.data.export.ExportedNarrative
import app.minepanel.data.export.ExportedRawEvent
import app.minepanel.data.export.ExportedScore
import app.minepanel.data.export.ExportedTag
import app.minepanel.data.export.ImportConflictStrategy
import app.minepanel.data.export.ParsedImport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRepositoryTest {

    private val confirmedDao = mockk<ConfirmedMemoryDao>(relaxed = true)
    private val draftDao = mockk<DraftMemoryDao>(relaxed = true)
    private val scoreDao = mockk<AttributeScoreDao>(relaxed = true)
    private val badgeDao = mockk<BadgeDao>(relaxed = true)
    private val rawDao = mockk<RawUsageEventDao>(relaxed = true)
    private val fragmentDao = mockk<ManualFragmentDao>(relaxed = true)
    private val milestoneDao = mockk<MilestoneDao>(relaxed = true)
    private val repo = ImportRepository(mockk<Context>(relaxed = true))

    private fun parsed() = ParsedImport(
        memories = listOf(ExportedMemory(0, "专注记录", 1000L, 2000L, 1500L)),
        tags = listOf(ExportedTag(0, "规律", 1600L, 1700L)),
        scores = listOf(ExportedScore("FOCUS", 0L, 7L, 35, 0.62, "{}", 7, 1800L)),
        badges = listOf(ExportedBadge("first_week", 1900L, "{}")),
        rawEvents = listOf(ExportedRawEvent(1234L, "FOREGROUND", "WORK", 5000L, "s1")),
        fragments = listOf(ExportedFragment("手写片段", "平静", "WORK", null, 4000L, 4100L)),
        milestones = listOf(ExportedMilestone("坚持满一周", "习惯", "首周达成", null, 4200L, 4300L))
    )

    private suspend fun persist(strategy: ImportConflictStrategy) = repo.persist(
        parsed(), strategy, confirmedDao, draftDao, scoreDao, badgeDao, rawDao, fragmentDao, milestoneDao
    )

    @Test
    fun `skip duplicates imports all fresh entries and maps entities`() = runBlocking {
        val report = persist(ImportConflictStrategy.SKIP_DUPLICATES)

        assertEquals(1, report.memoriesImported)
        assertEquals(1, report.tagsImported)
        assertEquals(1, report.scoresImported)
        assertEquals(1, report.badgesImported)
        assertEquals(1, report.rawImported)
        assertEquals(1, report.fragmentsImported)
        assertEquals(1, report.milestonesImported)
        assertEquals(0, report.memoriesSkipped)

        coVerify {
            confirmedDao.insert(match<ConfirmedMemory> {
                it.originDraftId == null && it.content == "专注记录" &&
                    it.periodStart == 1000L && it.periodEnd == 2000L
            })
        }
        coVerify {
            draftDao.insert(match<DraftMemory> {
                it.draftType == DraftType.STATUS_TAG.name &&
                    it.status == DraftStatus.CONFIRMED.name && it.contentJson == "规律"
            })
        }
        coVerify { scoreDao.upsert(any()) }
        coVerify { badgeDao.upsert(any()) }
        coVerify {
            rawDao.insert(match<RawUsageEvent> { it.sessionId == "s1" && it.durationMs == 5000L })
        }
        coVerify {
            fragmentDao.insert(match<ManualFragment> {
                it.content == "手写片段" && it.mood == "平静" && it.category == "WORK"
            })
        }
        coVerify {
            milestoneDao.insert(match<Milestone> {
                it.title == "坚持满一周" && it.linkedFragmentId == null
            })
        }
    }

    @Test
    fun `skip duplicates counts repeated fragment as skipped`() = runBlocking {
        coEvery { fragmentDao.countContent("手写片段") } returns 1

        val report = persist(ImportConflictStrategy.SKIP_DUPLICATES)

        assertEquals(1, report.fragmentsSkipped)
        assertEquals(0, report.fragmentsImported)
        coVerify(exactly = 0) { fragmentDao.insert(any()) }
    }

    @Test
    fun `skip duplicates counts repeated memory as skipped without insert`() = runBlocking {
        coEvery { confirmedDao.countSame(any(), any(), any()) } returns 1

        val report = persist(ImportConflictStrategy.SKIP_DUPLICATES)

        assertEquals(1, report.memoriesSkipped)
        assertEquals(0, report.memoriesImported)
        coVerify(exactly = 0) { confirmedDao.insert(any()) }
    }

    @Test
    fun `skip duplicates counts repeated tag as skipped`() = runBlocking {
        coEvery {
            draftDao.countTag(any(), any(), any(), any())
        } returns 1

        val report = persist(ImportConflictStrategy.SKIP_DUPLICATES)

        assertEquals(1, report.tagsSkipped)
        assertEquals(0, report.tagsImported)
        coVerify(exactly = 0) { draftDao.insert(any()) }
    }

    @Test
    fun `imports narrative as confirmed narrative draft`() = runBlocking {
        val withNarrative = parsed().copy(
            narratives = listOf(ExportedNarrative(0, "本期回顾", 1600L, 1700L))
        )

        val report = repo.persist(
            withNarrative,
            ImportConflictStrategy.SKIP_DUPLICATES,
            confirmedDao, draftDao, scoreDao, badgeDao, rawDao, fragmentDao, milestoneDao
        )

        assertEquals(1, report.narrativesImported)
        coVerify {
            draftDao.insert(match<DraftMemory> {
                it.draftType == DraftType.NARRATIVE.name &&
                    it.status == DraftStatus.CONFIRMED.name && it.contentJson == "本期回顾"
            })
        }
    }

    @Test
    fun `append all skips dedup queries and inserts everything`() = runBlocking {
        val report = persist(ImportConflictStrategy.APPEND_ALL)

        assertEquals(1, report.memoriesImported)
        assertEquals(1, report.rawImported)
        coVerify(exactly = 0) { confirmedDao.countSame(any(), any(), any()) }
        coVerify(exactly = 0) { rawDao.countSame(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `overwrite upserts scores and badges and appends memories`() = runBlocking {
        val report = persist(ImportConflictStrategy.OVERWRITE)

        assertEquals(1, report.memoriesImported)
        assertEquals(1, report.scoresImported)
        assertEquals(1, report.badgesImported)
        coVerify { scoreDao.upsert(any()) }
        coVerify { badgeDao.upsert(any()) }
        coVerify { confirmedDao.insert(any()) }
    }

    @Test
    fun `overwrite badge replaces existing by key`() = runBlocking {
        coEvery { badgeDao.countByKey(any()) } returns 1

        val report = persist(ImportConflictStrategy.OVERWRITE)

        assertEquals(1, report.badgesImported)
        coVerify { badgeDao.upsert(match<Badge> { it.badgeKey == "first_week" }) }
    }

    @Test
    fun `skip duplicates counts repeated milestone title as skipped`() = runBlocking {
        coEvery { milestoneDao.countTitle("坚持满一周") } returns 1

        val report = persist(ImportConflictStrategy.SKIP_DUPLICATES)

        assertEquals(1, report.milestonesSkipped)
        assertEquals(0, report.milestonesImported)
        coVerify(exactly = 0) { milestoneDao.insert(any()) }
    }
}
