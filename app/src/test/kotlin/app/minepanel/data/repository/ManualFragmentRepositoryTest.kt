package app.minepanel.data.repository

import app.minepanel.data.dao.ManualFragmentDao
import app.minepanel.data.db.entity.ManualFragment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFragmentRepositoryTest {

    private val fragmentDao = mockk<ManualFragmentDao>(relaxed = true)
    private val repo = ManualFragmentRepository(fragmentDao)

    @Test
    fun `add stores fresh fragment with normalized fields`() = runBlocking {
        coEvery { fragmentDao.countContent(any()) } returns 0

        val result = repo.add("  一段手写片段  ", " 平静 ", "WORK", anchorEpochMs = null)

        assertTrue(result)
        coVerify {
            fragmentDao.insert(match<ManualFragment> {
                it.content == "一段手写片段" &&
                    it.mood == "平静" &&
                    it.category == "WORK" &&
                    it.anchorEpochMs == null &&
                    it.createdAt > 0L && it.updatedAt > 0L
            })
        }
    }

    @Test
    fun `blank content is rejected without insert`() = runBlocking {
        assertFalse(repo.add("   ", "心情", "TOOL", null))
        coVerify(exactly = 0) { fragmentDao.insert(any()) }
    }

    @Test
    fun `duplicate content is skipped without insert`() = runBlocking {
        coEvery { fragmentDao.countContent("重复片段") } returns 1

        val result = repo.add("重复片段", null, null, null)

        assertFalse(result)
        coVerify(exactly = 0) { fragmentDao.insert(any()) }
    }

    @Test
    fun `unmapped category and blank mood normalize to null`() = runBlocking {
        coEvery { fragmentDao.countContent(any()) } returns 0

        repo.add("无标注", "   ", "UNMAPPED", null)

        coVerify {
            fragmentDao.insert(match<ManualFragment> {
                it.mood == null && it.category == null
            })
        }
    }

    @Test
    fun `content and mood are truncated to limits`() = runBlocking {
        coEvery { fragmentDao.countContent(any()) } returns 0
        val longContent = "字".repeat(ManualFragmentRepository.MAX_CONTENT_LEN + 10)
        val longMood = "心".repeat(ManualFragmentRepository.MOOD_LEN + 5)

        repo.add(longContent, longMood, "REST", null)

        coVerify {
            fragmentDao.insert(match<ManualFragment> {
                it.content.length == ManualFragmentRepository.MAX_CONTENT_LEN &&
                    it.mood?.length == ManualFragmentRepository.MOOD_LEN
            })
        }
    }
}
