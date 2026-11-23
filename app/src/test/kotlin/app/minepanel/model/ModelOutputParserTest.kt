package app.minepanel.model

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOutputParserTest {

    private fun input() = ModelInput(
        scores = mapOf(AttributeKey.FOCUS to 80),
        totalScreenTimeMin = 360,
        sampleDays = 7,
        categoryMinutes = mapOf("WORK" to 120),
        weekOverWeek = emptyMap()
    )

    @Test
    fun `strips markdown json fence and parses fields`() {
        val raw = """
            ```json
            {"panelNarrative":"本周节奏平稳","statusTags":["专注","夜间较少"],"draftMemories":["工作时段较集中"]}
            ```
        """.trimIndent()

        val result = ModelOutputParser.parse(raw)
        assertEquals("本周节奏平稳", result?.narrative)
        assertEquals(listOf("专注", "夜间较少"), result?.tags)
        assertEquals(listOf("工作时段较集中"), result?.memories)
    }

    @Test
    fun `accepts short alias field names`() {
        val raw = """{"narrative":"以影音为主","tags":["影音"],"memories":["晚间使用偏多"]}"""
        val result = ModelOutputParser.parse(raw)
        assertEquals("以影音为主", result?.narrative)
        assertEquals(listOf("影音"), result?.tags)
        assertEquals(listOf("晚间使用偏多"), result?.memories)
    }

    @Test
    fun `rejects output that writes back numeric score fields`() {
        val raw = """{"panelNarrative":"还行","score":80,"statusTags":[],"draftMemories":[]}"""
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `rejects diagnostic wording`() {
        val raw = """{"panelNarrative":"可能有焦虑症倾向","statusTags":[],"draftMemories":[]}"""
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `rejects narrative that echoes raw input data dictionary`() {
        val raw = """
            {"panelNarrative":"数据：{'scores': {'FOCUS': 50}, 'total_screen_time_min': 0,
            'sample_days': 0, 'categories': {}, 'memory_count': 0, 'fragment_count': 0, 'highlights': []}",
            "statusTags": ["标签"], "draftMemories": ["记忆要点"]}
        """.trimIndent()
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `filters placeholder words copied into tags and memories`() {
        val raw = """
            {"panelNarrative":"本周节奏平稳","statusTags":["标签"],"draftMemories":["记忆要点"]}
        """.trimIndent()
        val result = ModelOutputParser.parse(raw)
        assertEquals("本周节奏平稳", result?.narrative)
        assertTrue(result?.tags.isNullOrEmpty())
        assertTrue(result?.memories.isNullOrEmpty())
    }

    @Test
    fun `rejects placeholder angle brackets not replaced`() {
        val raw = """{"panelNarrative":"<回顾文案>","statusTags":[],"draftMemories":[]}"""
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `rejects field name echoed as narrative`() {
        val raw = """{"panelNarrative":"见 panelNarrative 字段","statusTags":[],"draftMemories":[]}"""
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `rejects snake_case key value inside narrative`() {
        val raw = """{"panelNarrative":"total_screen_time_min: 0","statusTags":[],"draftMemories":[]}"""
        assertNull(ModelOutputParser.parse(raw))
    }

    @Test
    fun `blank or garbled output returns null`() {
        assertNull(ModelOutputParser.parse(null))
        assertNull(ModelOutputParser.parse(""))
        assertNull(ModelOutputParser.parse("no json here"))
    }

    @Test
    fun `prompt data contains only conclusion numbers not event stream`() {
        val json = PromptBuilder.compactJson(input())
        assert(json.contains("\"total_screen_time_min\":360"))
        assert(!json.contains("com."))
        assert(!json.contains("event"))
    }

    @Test
    fun `template fallback produces neutral draft and fallback status`() = kotlinx.coroutines.runBlocking {
        val output = TemplateFallbackEngine().infer(input())
        assertEquals(ModelStatus.FALLBACK_TEMPLATE, output.status)
        assertEquals("template", output.engineName)
        assert(output.panelNarrative.contains("工作"))
        assert(output.statusTags.isNotEmpty())
        assert(output.draftMemories.isNotEmpty())
    }

    @Test
    fun `snapshot hash is stable sha256 hex`() {
        val hash1 = ModelSnapshotHasher.hash(input())
        val hash2 = ModelSnapshotHasher.hash(input())
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }
}
