package app.minepanel.model

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.ModelStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateFallbackEngineTest {

    private fun input(
        totalMin: Int = 245,
        days: Int = 7,
        categories: Map<String, Int> = mapOf("WORK" to 150)
    ) = ModelInput(
        scores = mapOf(AttributeKey.FOCUS to 60),
        totalScreenTimeMin = totalMin,
        sampleDays = days,
        categoryMinutes = categories,
        weekOverWeek = emptyMap()
    )

    @Test
    fun `zero screen time uses near-empty phrase`() = runBlocking {
        val output = TemplateFallbackEngine().infer(input(totalMin = 0))
        assertTrue(output.panelNarrative.contains("几乎没有屏幕使用"))
        assertTrue(!output.panelNarrative.contains("小时"))
    }

    @Test
    fun `zero sample days uses recent phrase`() = runBlocking {
        val output = TemplateFallbackEngine().infer(input(days = 0))
        assertTrue(output.panelNarrative.startsWith("近期"))
    }

    @Test
    fun `empty sample uses insufficient-records phrase`() = runBlocking {
        val output = TemplateFallbackEngine().infer(input(totalMin = 0, days = 0, categories = emptyMap()))
        assertTrue(output.panelNarrative.contains("记录较少"))
        assertTrue(output.draftMemories.any { it.contains("稍后再生成回顾") })
    }

    @Test
    fun `unmapped category falls back to uncategorized phrase`() = runBlocking {
        val output = TemplateFallbackEngine().infer(
            input(categories = mapOf("UNMAPPED" to 30))
        )
        assertTrue(output.panelNarrative.contains("活动类别暂未细分"))
        assertTrue(output.statusTags.contains("其他"))
    }

    @Test
    fun `known category leads with category phrase`() = runBlocking {
        val output = TemplateFallbackEngine().infer(input())
        assertTrue(output.panelNarrative.contains("以工作类活动为主"))
    }

    @Test
    fun `output is fallback template and carries tag and memory`() = runBlocking {
        val output = TemplateFallbackEngine().infer(input())
        assertEquals(ModelStatus.FALLBACK_TEMPLATE, output.status)
        assertEquals("template", output.engineName)
        assertTrue(output.statusTags.isNotEmpty())
        assertTrue(output.draftMemories.isNotEmpty())
    }
}
