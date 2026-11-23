package app.minepanel.aggregate

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.ModelStatus
import app.minepanel.core.time.ReviewPeriod
import app.minepanel.core.time.ReviewWindows
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.data.repository.ReviewSummary
import app.minepanel.model.InferenceEngine
import app.minepanel.model.ModelInput
import app.minepanel.model.ModelOutput
import app.minepanel.model.SchemaValidationException
import app.minepanel.model.TemplateFallbackEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReviewGeneratorTest {

    private val window = ReviewWindows.window(ReviewPeriod.MONTH, 0, LocalDate.of(2026, 11, 22))
    private val settings = SettingsSnapshot()

    private fun summary(): ReviewSummary = ReviewSummary(
        window = window,
        sampleDays = 12,
        totalScreenTimeMin = 300,
        categoryMinutes = mapOf("WORK" to 200),
        scores = AttributeKey.entries.associateWith { 60 },
        memoryCount = 2,
        memoryHighlights = listOf("记忆一", "记忆二"),
        fragmentCount = 1,
        fragmentHighlights = listOf("片段一")
    )

    private fun successOutput() = ModelOutput(
        panelNarrative = "一段回顾",
        statusTags = listOf("专注"),
        draftMemories = listOf("要点"),
        status = ModelStatus.SUCCESS,
        engineName = "gguf"
    )

    @Test
    fun `successful model run keeps success status and snapshot hash`() = runBlocking {
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        coEvery { engine.infer(any()) } returns successOutput()
        val generator = ReviewGenerator({ engine })

        val result = generator.generate(summary(), settings)

        assertEquals(ModelStatus.SUCCESS, result.status)
        assertEquals("gguf", result.engineName)
        assertNotNull(result.inputSnapshotHash)
        assertEquals("一段回顾", result.output.panelNarrative)
    }

    @Test
    fun `schema rejected output falls back to template with fallback status`() = runBlocking {
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        coEvery { engine.infer(any()) } throws SchemaValidationException("schema 校验不过")
        val generator = ReviewGenerator({ engine })

        val result = generator.generate(summary(), settings)

        assertEquals(ModelStatus.FALLBACK_TEMPLATE, result.status)
        assertEquals("gguf", result.engineName)
        assertEquals("template", result.output.engineName)
        assertTrue(result.output.panelNarrative.isNotBlank())
    }

    @Test
    fun `out of memory falls back to template`() = runBlocking {
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        coEvery { engine.infer(any()) } throws OutOfMemoryError("堆不足")
        val generator = ReviewGenerator({ engine })

        val result = generator.generate(summary(), settings)

        assertEquals(ModelStatus.ERROR, result.status)
        assertNotNull(result.output)
    }

    @Test
    fun `template engine provider marks fallback`() = runBlocking {
        val generator = ReviewGenerator({ TemplateFallbackEngine() })

        val result = generator.generate(summary(), settings)

        assertEquals(ModelStatus.FALLBACK_TEMPLATE, result.status)
        assertEquals("template", result.engineName)
    }

    @Test
    fun `empty window short-circuits to template without calling model`() = runBlocking {
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        val generator = ReviewGenerator({ engine })
        val emptySummary = ReviewSummary(
            window = window,
            sampleDays = 0,
            totalScreenTimeMin = 0,
            categoryMinutes = emptyMap(),
            scores = AttributeKey.entries.associateWith { 0 },
            memoryCount = 0,
            memoryHighlights = emptyList(),
            fragmentCount = 0,
            fragmentHighlights = emptyList()
        )

        val result = generator.generate(emptySummary, settings)

        assertEquals(ModelStatus.FALLBACK_TEMPLATE, result.status)
        assertEquals("template", result.engineName)
        assertTrue(result.output.panelNarrative.isNotBlank())
        io.mockk.coVerify(exactly = 0) { engine.infer(any()) }
    }

    @Test
    fun `unexpected throwable keeps error status`() = runBlocking {
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        coEvery { engine.infer(any()) } throws RuntimeException("native crash")
        val generator = ReviewGenerator({ engine })

        val result = generator.generate(summary(), settings)

        assertEquals(ModelStatus.ERROR, result.status)
        assertEquals("gguf", result.engineName)
    }

    @Test
    fun `input carries period label counts and truncated highlights`() = runBlocking {
        var captured: ModelInput? = null
        val engine = mockk<InferenceEngine>()
        coEvery { engine.engineName } returns "gguf"
        coEvery { engine.infer(any()) } coAnswers {
            captured = firstArg<ModelInput>()
            successOutput()
        }
        val generator = ReviewGenerator({ engine })

        generator.generate(summary(), settings)
        val input = requireNotNull(captured)

        assertEquals(window.label, input.periodLabel)
        assertEquals(2, input.memoryCount)
        assertEquals(1, input.fragmentCount)
        assertEquals(listOf("记忆一", "记忆二", "片段一"), input.highlights)
    }
}
