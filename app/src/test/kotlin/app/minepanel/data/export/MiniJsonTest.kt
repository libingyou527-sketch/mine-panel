package app.minepanel.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `parses nested object and array`() {
        val root = MiniJson.parse("""{"a":1,"b":[true,null,"x"],"c":{"d":2}}""").asObject()!!

        assertEquals(1L, root.map["a"]!!.asLong()!!)
        val arr = root.map["b"]!!.asArray()!!.items
        assertEquals(true, arr[0].asBoolean())
        assertTrue(arr[1] is JsonValue.Null)
        assertEquals("x", arr[2].asString())
        assertEquals(2L, root.map["c"]!!.asObject()!!.map["d"]!!.asLong()!!)
    }

    @Test
    fun `decodes escapes and unicode`() {
        val value = MiniJson.parse("\"a\\\"b\\\\c\\nd\\t\\u4e2d\\u6587\"").asString()!!
        assertEquals("a\"b\\c\nd\t中文", value)
    }

    @Test
    fun `parses integer double and boolean null`() {
        val num = MiniJson.parse("42").asLong()
        assertEquals(42L, num!!)
        val dbl = MiniJson.parse("3.14").asDouble()
        assertEquals(3.14, dbl!!, 1e-9)
        assertEquals(true, MiniJson.parse("true").asBoolean())
        assertNull(MiniJson.parse("null").asString())
    }

    @Test
    fun `rejects trailing content`() {
        try {
            MiniJson.parse("1 2")
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("多余内容"))
        }
    }

    @Test
    fun `rejects unterminated string`() {
        try {
            MiniJson.parse("\"abc")
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("未闭合"))
        }
    }

    @Test
    fun `rejects invalid escape`() {
        try {
            MiniJson.parse("\"\\q\"")
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("转义"))
        }
    }

    @Test
    fun `rejects empty input`() {
        try {
            MiniJson.parse("")
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            // 意外结束
        }
    }

    @Test
    fun `rejects malformed literal`() {
        try {
            MiniJson.parse("tru")
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("布尔"))
        }
    }
}
