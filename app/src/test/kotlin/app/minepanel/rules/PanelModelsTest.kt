package app.minepanel.rules

import app.minepanel.core.model.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelModelsTest {

    private fun score(key: AttributeKey, value: Int, sampleDays: Int = 7) = WeekScore(
        key = key,
        score = value,
        confidence = 0.8,
        evidence = emptyList(),
        sampleDays = sampleDays
    )

    @Test
    fun `level mapping clamps to range and computes in-level progress`() {
        assertEquals(1, PanelScoring.levelOf(0).level)
        assertEquals(0f, PanelScoring.levelOf(0).progress, 1e-6f)

        assertEquals(5, PanelScoring.levelOf(50).level)
        assertEquals(0f, PanelScoring.levelOf(50).progress, 1e-6f)

        assertEquals(6, PanelScoring.levelOf(65).level)
        assertEquals(0.5f, PanelScoring.levelOf(65).progress, 1e-6f)

        assertEquals(10, PanelScoring.levelOf(100).level)
        assertEquals(1f, PanelScoring.levelOf(100).progress, 1e-6f)

        // 越界输入收敛到 Lv1 / Lv10
        assertEquals(1, PanelScoring.levelOf(-20).level)
        assertEquals(10, PanelScoring.levelOf(120).level)
    }

    @Test
    fun `average of empty list is zero`() {
        assertEquals(0, PanelScoring.average(emptyList()))
    }

    @Test
    fun `average rounds to nearest integer`() {
        val scores = listOf(
            score(AttributeKey.FOCUS, 80),
            score(AttributeKey.REGULARITY, 90),
            score(AttributeKey.VITALITY, 70)
        )
        assertEquals(80, PanelScoring.average(scores))
    }

    @Test
    fun `pair keeps fixed six-dimension order`() {
        val current = AttributeKey.entries.map { score(it, 60) }
        val pairs = PanelScoring.pair(current, emptyList())

        assertEquals(6, pairs.size)
        assertEquals(AttributeKey.entries.toList(), pairs.map { it.key })
    }

    @Test
    fun `last week missing yields null last and null delta`() {
        val current = listOf(score(AttributeKey.FOCUS, 72))
        val pairs = PanelScoring.pair(current, emptyList())
        val pair = pairs.first { it.key == AttributeKey.FOCUS }

        assertNull(pair.last)
        assertNull(pair.delta)
    }

    @Test
    fun `delta is current minus last`() {
        val current = listOf(score(AttributeKey.FOCUS, 72))
        val last = listOf(score(AttributeKey.FOCUS, 60))
        val pair = PanelScoring.pair(current, last).first { it.key == AttributeKey.FOCUS }

        assertEquals(12, pair.delta)
    }
}
