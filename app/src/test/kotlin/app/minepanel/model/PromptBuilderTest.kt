package app.minepanel.model

import app.minepanel.core.model.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private fun input(totalMin: Int = 245, days: Int = 7) = ModelInput(
        scores = mapOf(AttributeKey.FOCUS to 70, AttributeKey.VITALITY to 55),
        totalScreenTimeMin = totalMin,
        sampleDays = days,
        categoryMinutes = mapOf("WORK" to 150, "MEDIA" to 95),
        weekOverWeek = mapOf(AttributeKey.FOCUS to null)
    )

    @Test
    fun `compact json carries scores totals and categories only`() {
        val json = PromptBuilder.compactJson(input())
        assertTrue(json.contains("\"FOCUS\":70"))
        assertTrue(json.contains("\"VITALITY\":55"))
        assertTrue(json.contains("\"total_screen_time_min\":245"))
        assertTrue(json.contains("\"sample_days\":7"))
        assertTrue(json.contains("\"WORK\":150"))
        assertFalse(json.contains("com."))
        assertFalse(json.contains("event"))
    }

    @Test
    fun `chat ml has system user assistant turns`() {
        val chat = PromptBuilder.buildChatML(input())
        assertTrue(chat.startsWith("<|im_start|>system"))
        assertTrue(chat.contains("<|im_start|>user"))
        assertTrue(chat.contains("<|im_start|>assistant\n"))
        assertTrue(chat.contains("数据："))
        // system 约束不下发分值、不做诊断、不提及应用名
        assertTrue(chat.contains("输出分值"))
        assertTrue(chat.contains("不做健康、心理或人格诊断"))
    }

    @Test
    fun `plain build prepends system prompt`() {
        val plain = PromptBuilder.build(input())
        assertTrue(plain.startsWith("你是本地设备使用回顾助手"))
        assertTrue(plain.contains("数据："))
    }

    @Test
    fun `same input yields identical prompt`() {
        assertEquals(PromptBuilder.compactJson(input()), PromptBuilder.compactJson(input()))
    }
}
