package app.minepanel.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatImportParserTest {

    @Test
    fun `parses messages memories and session title`() {
        val text = """
            {
              "schemaVersion": 1,
              "exportedAt": 1000,
              "sessionTitle": "旧会话",
              "messages": [
                {"role": "USER", "content": "你好", "createdAt": 1}
              ],
              "memories": [
                {"content": "偏好安静", "createdAt": 2}
              ]
            }
        """.trimIndent()

        val parsed = ChatImportParser.parse(text)

        assertEquals("旧会话", parsed.sessionTitle)
        assertEquals(1, parsed.messages.size)
        assertEquals("USER", parsed.messages[0].role)
        assertEquals("你好", parsed.messages[0].content)
        assertEquals(1L, parsed.messages[0].createdAt)
        assertEquals(1, parsed.memories.size)
        assertEquals("偏好安静", parsed.memories[0].content)
        assertEquals(2L, parsed.memories[0].createdAt)
    }

    @Test
    fun `unknown role falls back to USER`() {
        val text = """
            {"schemaVersion": 1, "messages": [{"role": "BOT", "content": "hi", "createdAt": 1}], "memories": []}
        """.trimIndent()

        val parsed = ChatImportParser.parse(text)
        assertEquals("USER", parsed.messages[0].role)
    }

    @Test
    fun `missing createdAt falls back to zero`() {
        val text = """
            {"schemaVersion": 1, "messages": [{"role": "USER", "content": "hi"}], "memories": []}
        """.trimIndent()

        val parsed = ChatImportParser.parse(text)
        assertEquals(0L, parsed.messages[0].createdAt)
    }

    @Test
    fun `empty sections parse as empty lists`() {
        val parsed = ChatImportParser.parse("""{"schemaVersion": 1, "messages": [], "memories": []}""")
        assertEquals(0, parsed.messages.size)
        assertEquals(0, parsed.memories.size)
    }

    @Test
    fun `missing schema version rejects`() {
        assertThrows(ImportException::class.java) {
            ChatImportParser.parse("""{"messages": []}""")
        }
    }

    @Test
    fun `unsupported schema version rejects`() {
        assertThrows(ImportException::class.java) {
            ChatImportParser.parse("""{"schemaVersion": 99, "messages": []}""")
        }
    }

    @Test
    fun `message missing content rejects`() {
        assertThrows(ImportException::class.java) {
            ChatImportParser.parse(
                """{"schemaVersion": 1, "messages": [{"role": "USER", "createdAt": 1}]}"""
            )
        }
    }

    @Test
    fun `non json text rejects`() {
        assertThrows(ImportException::class.java) {
            ChatImportParser.parse("not a json")
        }
    }
}
