package app.minepanel.model.remote

import app.minepanel.data.export.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteChatClientTest {

    @Test
    fun buildRequestBodyIncludesModelAndMessagesAndEscapes() {
        val messages = listOf(
            RemoteMessage("system", "约束 \"简短\""),
            RemoteMessage("user", "第一行\n第二行")
        )
        val body = RemoteChatClient.buildRequestBody("test-model", messages)
        val root = MiniJson.parse(body).asObject()!!

        assertEquals("test-model", root.map["model"]?.asString())
        val arr = root.map["messages"]?.asArray()!!.items
        assertEquals(2, arr.size)
        assertEquals("system", arr[0].asObject()!!.map["role"]?.asString())
        assertEquals("约束 \"简短\"", arr[0].asObject()!!.map["content"]?.asString())
        assertEquals("第一行\n第二行", arr[1].asObject()!!.map["content"]?.asString())
    }

    @Test
    fun parseResponseBodyExtractsFirstChoiceContent() {
        val payload = """
            {
              "choices": [
                { "message": { "role": "assistant", "content": "你好，世界" } }
              ]
            }
        """.trimIndent()
        assertEquals("你好，世界", RemoteChatClient.parseResponseBody(payload))
    }

    @Test(expected = IllegalStateException::class)
    fun parseResponseBodyMissingContentThrows() {
        RemoteChatClient.parseResponseBody("""{"choices":[]}""")
    }

    @Test
    fun configCompleteRequiresAllFields() {
        assertTrue(RemoteChatConfig("https://x/v1", "key", "model").isComplete)
        assertEquals(false, RemoteChatConfig("", "key", "model").isComplete)
        assertEquals(false, RemoteChatConfig("https://x/v1", "", "model").isComplete)
        assertEquals(false, RemoteChatConfig("https://x/v1", "key", "").isComplete)
    }

    @Test
    fun endpointAppendsChatCompletions() {
        assertEquals(
            "https://x/v1/chat/completions",
            RemoteChatConfig("https://x/v1/", "key", "model").endpoint()
        )
    }
}
