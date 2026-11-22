package app.minepanel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ModelFileValidatorTest {

    @Test
    fun `sha256 matches independent digest computation`() {
        val content = "minepanel-model-baseline".toByteArray()
        val file = createTempFile("model", ".gguf").apply { writeBytes(content) }
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, ModelFileValidator.sha256(file))
        assertEquals(64, ModelFileValidator.sha256(file).length)
    }

    @Test
    fun `missing file is invalid`() {
        val result = ModelFileValidator.verify(null)
        assertFalse(result.valid)
        assertNotNull(result.reason)
    }

    @Test
    fun `tiny file is rejected as not a model`() {
        val file = createTempFile("tiny", ".gguf").apply { writeBytes(ByteArray(16)) }
        val result = ModelFileValidator.verify(file)
        assertFalse(result.valid)
        assertEquals("文件过小，不是有效的 GGUF 模型", result.reason)
    }

    @Test
    fun `memory gate threshold boundary`() {
        val threshold = MemoryGate.MIN_AVAIL_MEM_BYTES
        assertFalse(MemoryGate.enoughMemory(threshold - 1))
        assertTrue(MemoryGate.enoughMemory(threshold))
        assertTrue(MemoryGate.enoughMemory(3L * 1024 * 1024 * 1024))
    }
}
