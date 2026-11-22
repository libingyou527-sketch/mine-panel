package app.minepanel.model

import app.minepanel.core.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {

    @Test
    fun `empty chat only carries system and assistant turn`() {
        val chat = ChatPromptBuilder.buildChat(emptyList())
        assertTrue(chat.contains("<|im_start|>system"))
        assertTrue(chat.contains("<|im_start|>assistant\n"))
        assertFalse(chat.contains("<|im_start|>user"))
    }

    @Test
    fun `builds user and assistant turns in chatml`() {
        val turns = listOf(
            ChatTurn(ChatRole.USER, "最近怎么样"),
            ChatTurn(ChatRole.ASSISTANT, "一切平稳")
        )
        val chat = ChatPromptBuilder.buildChat(turns)
        assertTrue(chat.contains("<|im_start|>user\n最近怎么样"))
        assertTrue(chat.contains("<|im_start|>assistant\n一切平稳"))
        assertTrue(chat.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `truncates oldest turns beyond token budget but keeps latest`() {
        val long = "字".repeat(700)
        val turns = listOf(
            ChatTurn(ChatRole.USER, "${long}OLD1"),
            ChatTurn(ChatRole.ASSISTANT, "${long}OLD2"),
            ChatTurn(ChatRole.USER, "${long}KEEP3")
        )
        val chat = ChatPromptBuilder.buildChat(turns)
        assertTrue(chat.contains("KEEP3"))
        assertFalse(chat.contains("OLD1"))
        assertFalse(chat.contains("OLD2"))
    }

    @Test
    fun `keeps at most recent history message count`() {
        val turns = (0 until 14).map { index ->
            val role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT
            ChatTurn(role, "ITEM%02d".format(index))
        }
        val chat = ChatPromptBuilder.buildChat(turns)
        assertFalse(chat.contains("ITEM00"))
        assertFalse(chat.contains("ITEM01"))
        assertTrue(chat.contains("ITEM12"))
        assertTrue(chat.contains("ITEM13"))
    }

    @Test
    fun `memory items are appended as system background`() {
        val chat = ChatPromptBuilder.buildChat(
            listOf(ChatTurn(ChatRole.USER, "在吗")),
            memories = listOf("偏好安静环境")
        )
        assertTrue(chat.contains("已知背景"))
        assertTrue(chat.contains("偏好安静环境"))
        assertTrue(chat.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `without memory items background section is omitted`() {
        val chat = ChatPromptBuilder.buildChat(listOf(ChatTurn(ChatRole.USER, "在吗")))
        assertFalse(chat.contains("已知背景"))
    }

    @Test
    fun `extraction prompt asks for memories json and replays dialogue`() {
        val turns = listOf(
            ChatTurn(ChatRole.USER, "我习惯早起"),
            ChatTurn(ChatRole.ASSISTANT, "了解")
        )
        val prompt = ChatPromptBuilder.buildMemoryExtraction(turns)
        assertTrue(prompt.contains("\"memories\""))
        assertTrue(prompt.contains("我：我习惯早起"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `estimate tokens counts cjk characters`() {
        assertEquals(2, ChatPromptBuilder.estimateTokens("中文"))
    }

    @Test
    fun `system prompt sets neutral non-diagnostic boundary`() {
        val chat = ChatPromptBuilder.buildChat(listOf(ChatTurn(ChatRole.USER, "hi")))
        assertTrue(chat.contains("不做健康、心理或人格诊断"))
        assertTrue(chat.contains("不提及具体应用名称"))
    }
}
