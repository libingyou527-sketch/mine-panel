package app.minepanel.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParserTest {

    private fun base(
        schema: Int = 1,
        memories: String = "[]",
        tags: String = "[]",
        scores: String = "[]",
        badges: String = "[]",
        rawEvents: String? = null,
        fragments: String? = null
    ): String {
        val raw = rawEvents?.let { ",\"rawEvents\":$it" } ?: ""
        val frag = fragments?.let { ",\"manualFragments\":$it" } ?: ""
        return """
            {
              "schemaVersion":$schema,
              "exportedAt":1700000000000,
              "appVersion":"0.1.0",
              "confirmedMemories":$memories,
              "statusTags":$tags,
              "attributeScores":$scores,
              "badges":$badges$raw$frag
            }
        """.trimIndent()
    }

    @Test
    fun `parses empty sections without raw events`() {
        val parsed = ImportParser.parse(base())
        assertTrue(parsed.memories.isEmpty())
        assertTrue(parsed.tags.isEmpty())
        assertTrue(parsed.scores.isEmpty())
        assertTrue(parsed.badges.isEmpty())
        assertTrue(parsed.rawEvents.isEmpty())
        assertTrue(parsed.fragments.isEmpty())
    }

    @Test
    fun `maps full bundle fields`() {
        val json = base(
            memories = """[{"id":1,"content":"专注记录","periodStart":1000,"periodEnd":2000,"createdAt":1500}]""",
            tags = """[{"id":2,"label":"规律","createdAt":1600,"reviewedAt":1700}]""",
            scores = """[{"attributeKey":"FOCUS","periodStart":0,"periodEnd":7,"score":35,"confidence":0.62,"evidenceJson":"{}","sampleDays":7,"updatedAt":1800}]""",
            badges = """[{"badgeKey":"first_week","unlockedAt":1900,"conditionSnapshotJson":"{}"}]""",
            rawEvents = """[{"timestamp":1234,"eventType":"FOREGROUND","category":"WORK","durationMs":5000,"sessionId":"s1"}]"""
        )

        val parsed = ImportParser.parse(json)

        assertEquals("专注记录", parsed.memories[0].content)
        assertEquals(1000L, parsed.memories[0].periodStart)
        assertEquals("规律", parsed.tags[0].label)
        assertEquals(1700L, parsed.tags[0].reviewedAt)
        assertEquals("FOCUS", parsed.scores[0].attributeKey)
        assertEquals(35, parsed.scores[0].score)
        assertEquals(0.62, parsed.scores[0].confidence, 1e-9)
        assertEquals("first_week", parsed.badges[0].badgeKey)
        assertEquals("s1", parsed.rawEvents[0].sessionId)
    }

    @Test
    fun `null reviewedAt and sessionId stay null`() {
        val json = base(
            tags = """[{"id":2,"label":"早","createdAt":1600,"reviewedAt":null}]""",
            rawEvents = """[{"timestamp":1234,"eventType":"UNLOCK","category":"REST","durationMs":0,"sessionId":null}]"""
        )
        val parsed = ImportParser.parse(json)
        assertNull(parsed.tags[0].reviewedAt)
        assertNull(parsed.rawEvents[0].sessionId)
    }

    @Test
    fun `parses fragment section with nullable fields`() {
        val json = base(
            fragments = """[{"content":"手写片段","mood":"平静","category":"WORK","anchorEpochMs":4321,"createdAt":4000,"updatedAt":4100}]"""
        )
        val parsed = ImportParser.parse(json)

        assertEquals(1, parsed.fragments.size)
        val fragment = parsed.fragments[0]
        assertEquals("手写片段", fragment.content)
        assertEquals("平静", fragment.mood)
        assertEquals("WORK", fragment.category)
        assertEquals(4321L, fragment.anchorEpochMs)
        assertEquals(4000L, fragment.createdAt)
        assertEquals(4100L, fragment.updatedAt)
    }

    @Test
    fun `fragment nullable mood category anchor stay null`() {
        val json = base(
            fragments = """[{"content":"无标注片段","mood":null,"category":null,"anchorEpochMs":null,"createdAt":1,"updatedAt":2}]"""
        )
        val fragment = ImportParser.parse(json).fragments[0]
        assertNull(fragment.mood)
        assertNull(fragment.category)
        assertNull(fragment.anchorEpochMs)
    }

    @Test
    fun `rejects unsupported schema version`() {
        try {
            ImportParser.parse(base(schema = 2))
            assertTrue(false)
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("不支持的导出版本 2"))
        }
    }

    @Test
    fun `rejects missing schema version`() {
        val json = """{"confirmedMemories":[],"statusTags":[],"attributeScores":[],"badges":[]}"""
        try {
            ImportParser.parse(json)
            assertTrue(false)
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun `rejects non object root`() {
        try {
            ImportParser.parse("[1,2,3]")
            assertTrue(false)
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("根节点必须是对象"))
        }
    }

    @Test
    fun `rejects memory with non string content`() {
        val json = base(memories = """[{"id":1,"content":123,"periodStart":1,"periodEnd":2,"createdAt":3}]""")
        try {
            ImportParser.parse(json)
            assertTrue(false)
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("content"))
        }
    }

    @Test
    fun `rejects malformed json`() {
        try {
            ImportParser.parse("{not json")
            assertTrue(false)
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("无法解析 JSON"))
        }
    }
}
