package app.minepanel.model

import app.minepanel.core.model.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSnapshotHasherTest {

    private fun input(totalMin: Int) = ModelInput(
        scores = mapOf(AttributeKey.FOCUS to 60),
        totalScreenTimeMin = totalMin,
        sampleDays = 7,
        categoryMinutes = mapOf("WORK" to 120),
        weekOverWeek = emptyMap()
    )

    @Test
    fun `hash is stable and lowercase hex`() {
        val h1 = ModelSnapshotHasher.hash(input(300))
        val h2 = ModelSnapshotHasher.hash(input(300))
        assertEquals(h1, h2)
        assertEquals(64, h1.length)
        assertTrue(h1.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `different snapshot produces different hash`() {
        assertNotEquals(
            ModelSnapshotHasher.hash(input(300)),
            ModelSnapshotHasher.hash(input(60))
        )
    }
}
