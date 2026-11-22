package app.minepanel.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatExporterTest {

    @Test
    fun `serializes messages and memories with counts`() {
        val bundle = ChatExportBundle(
            schemaVersion = ChatExporter.SCHEMA_VERSION,
            exportedAt = 1000L,
            messages = listOf(
                ExportedChatMessage("USER", "你好", 1L),
                ExportedChatMessage("ASSISTANT", "在的", 2L)
            ),
            memories = listOf(ExportedChatMemory("偏好安静", 3L))
        )

        val parsed = MiniJson.parse(ChatExporter.toJson(bundle)).asObject()!!

        assertEquals(1, parsed.map["schemaVersion"]!!.asInt())
        assertEquals(1000L, parsed.map["exportedAt"]!!.asLong())

        val messages = parsed.map["messages"]!!.asArray()!!.items
        assertEquals(2, messages.size)
        val first = messages[0].asObject()!!
        assertEquals("USER", first.map["role"]!!.asString())
        assertEquals("你好", first.map["content"]!!.asString())
        assertEquals(1L, first.map["createdAt"]!!.asLong())

        val memories = parsed.map["memories"]!!.asArray()!!.items
        assertEquals(1, memories.size)
        assertEquals("偏好安静", memories[0].asObject()!!.map["content"]!!.asString())
    }

    @Test
    fun `escapes quotes and newlines in content`() {
        val bundle = ChatExportBundle(
            schemaVersion = 1,
            exportedAt = 1L,
            messages = listOf(ExportedChatMessage("USER", "说\"你好\"\n换行", 1L)),
            memories = emptyList()
        )

        val content = MiniJson.parse(ChatExporter.toJson(bundle)).asObject()!!
            .map["messages"]!!.asArray()!!.items[0].asObject()!!
            .map["content"]!!.asString()!!
        assertEquals("说\"你好\"\n换行", content)
    }

    @Test
    fun `empty sections serialize as empty arrays`() {
        val bundle = ChatExportBundle(
            schemaVersion = 1,
            exportedAt = 1L,
            messages = emptyList(),
            memories = emptyList()
        )

        val parsed = MiniJson.parse(ChatExporter.toJson(bundle)).asObject()!!
        assertEquals(0, parsed.map["messages"]!!.asArray()!!.items.size)
        assertEquals(0, parsed.map["memories"]!!.asArray()!!.items.size)
    }
}
