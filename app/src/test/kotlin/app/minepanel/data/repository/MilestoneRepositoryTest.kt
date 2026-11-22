package app.minepanel.data.repository

import app.minepanel.data.dao.MilestoneDao
import app.minepanel.data.db.entity.Milestone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MilestoneRepositoryTest {

    private val milestoneDao = mockk<MilestoneDao>(relaxed = true)
    private val repo = MilestoneRepository(milestoneDao)

    @Test
    fun `add stores fresh milestone with normalized fields`() = runBlocking {
        coEvery { milestoneDao.countTitle(any()) } returns 0

        val result = repo.add("  坚持满一周  ", " 习惯 ", " 首周达成 ", anchorEpochMs = 1234L, linkedFragmentId = 9L)

        assertTrue(result)
        coVerify {
            milestoneDao.insert(match<Milestone> {
                it.title == "坚持满一周" &&
                    it.label == "习惯" &&
                    it.note == "首周达成" &&
                    it.anchorEpochMs == 1234L &&
                    it.linkedFragmentId == 9L &&
                    it.createdAt > 0L && it.updatedAt > 0L
            })
        }
    }

    @Test
    fun `blank title is rejected without insert`() = runBlocking {
        assertFalse(repo.add("   ", null, null, null, null))
        coVerify(exactly = 0) { milestoneDao.insert(any()) }
    }

    @Test
    fun `duplicate title is skipped without insert`() = runBlocking {
        coEvery { milestoneDao.countTitle("重复里程碑") } returns 1

        assertFalse(repo.add("重复里程碑", null, null, null, null))
        coVerify(exactly = 0) { milestoneDao.insert(any()) }
    }

    @Test
    fun `blank label and note normalize to null`() = runBlocking {
        coEvery { milestoneDao.countTitle(any()) } returns 0

        repo.add("无标注", "   ", "  ", null, null)

        coVerify {
            milestoneDao.insert(match<Milestone> { it.label == null && it.note == null })
        }
    }

    @Test
    fun `title label note are truncated to limits`() = runBlocking {
        coEvery { milestoneDao.countTitle(any()) } returns 0
        val longTitle = "标".repeat(MilestoneRepository.MAX_TITLE_LEN + 10)
        val longLabel = "签".repeat(MilestoneRepository.LABEL_LEN + 5)
        val longNote = "注".repeat(MilestoneRepository.NOTE_LEN + 20)

        repo.add(longTitle, longLabel, longNote, null, null)

        coVerify {
            milestoneDao.insert(match<Milestone> {
                it.title.length == MilestoneRepository.MAX_TITLE_LEN &&
                    it.label?.length == MilestoneRepository.LABEL_LEN &&
                    it.note?.length == MilestoneRepository.NOTE_LEN
            })
        }
    }
}
