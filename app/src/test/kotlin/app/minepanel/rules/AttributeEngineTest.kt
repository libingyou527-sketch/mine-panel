package app.minepanel.rules

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.time.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AttributeEngineTest {

    private fun nightBlock(minute: Int, index: Int): BlockUsage {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone)
            .atTime(23, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli() + index * 30 * 60_000L
        return BlockUsage(start, 20 * 60_000L, "MEDIA")
    }

    @Test
    fun `empty input returns all six dimensions with low confidence and non-empty evidence`() {
        val results = AttributeEngine.evaluate(EngineInput(emptyList(), emptyList()))

        assertEquals(6, results.size)
        results.forEach { result ->
            assertTrue(result.score in 0..100)
            assertTrue(result.confidence in 0.0..1.0)
            assertTrue(result.evidence.isNotEmpty())
        }
    }

    @Test
    fun `night-heavy use lowers digital moderation score`() {
        val blocks = (0..4).map { nightBlock(30, it) }
        val results = AttributeEngine.evaluate(EngineInput(emptyList(), blocks))

        val moderation = results.first { it.key == AttributeKey.DIGITAL_MODERATION }
        assertTrue(moderation.score < 50)
    }

    @Test
    fun `few sample days are in baseline period`() {
        assertTrue(BaselinePolicy.isBaseline(2))
        assertTrue(BaselinePolicy.isBaseline(6))
        assertEquals(false, BaselinePolicy.isBaseline(7))
    }

    @Test
    fun `exploration stays neutral with low confidence when plugin disabled`() {
        val results = AttributeEngine.evaluate(EngineInput(emptyList(), emptyList()))
        val exploration = results.first { it.key == AttributeKey.EXPLORATION }

        assertEquals(50, exploration.score)
        assertTrue(exploration.confidence < 0.5)
        assertTrue(exploration.evidence.first().contains("未启用"))
    }

    @Test
    fun `evidence contains no package names`() {
        val blocks = listOf(
            BlockUsage(Instant.now().toEpochMilli(), 10 * 60_000L, "SOCIAL"),
            BlockUsage(Instant.now().toEpochMilli(), 12 * 60_000L, "MEDIA")
        )
        val results = AttributeEngine.evaluate(EngineInput(emptyList(), blocks))

        results.forEach { result ->
            result.evidence.forEach { evidence ->
                assertTrue(!evidence.startsWith("com."))
            }
        }
    }
}
