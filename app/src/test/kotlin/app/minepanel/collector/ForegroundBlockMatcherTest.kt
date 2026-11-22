package app.minepanel.collector

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundBlockMatcherTest {

    @Test
    fun `resume and pause pair to compute duration`() {
        val matcher = ForegroundBlockMatcher()
        matcher.resume("pkg.a", 10_000)
        matcher.pause("pkg.a", 25_000)
        val blocks = matcher.finish(25_000)

        assertEquals(1, blocks.size)
        assertEquals(15_000, blocks[0].durationMs)
        assertEquals("pkg.a", blocks[0].packageName)
    }

    @Test
    fun `screen off closes current block`() {
        val matcher = ForegroundBlockMatcher()
        matcher.resume("pkg.a", 5_000)
        matcher.screenOff(20_000)
        val blocks = matcher.finish(20_000)

        assertEquals(1, blocks.size)
        assertEquals(15_000, blocks[0].durationMs)
    }

    @Test
    fun `mismatched pause is ignored`() {
        val matcher = ForegroundBlockMatcher()
        matcher.resume("pkg.a", 5_000)
        matcher.pause("pkg.b", 12_000)
        val blocks = matcher.finish(20_000)

        assertEquals(1, blocks.size)
        assertEquals("pkg.a", blocks[0].packageName)
        assertEquals(15_000, blocks[0].durationMs)
    }

    @Test
    fun `consecutive resume closes previous block at resume time`() {
        val matcher = ForegroundBlockMatcher()
        matcher.resume("pkg.a", 10_000)
        matcher.resume("pkg.b", 20_000)
        matcher.pause("pkg.b", 30_000)
        val blocks = matcher.finish(30_000)

        assertEquals(2, blocks.size)
        assertEquals(10_000, blocks[0].durationMs)
        assertEquals(10_000, blocks[1].durationMs)
    }

    @Test
    fun `trailing block is closed at finish timestamp`() {
        val matcher = ForegroundBlockMatcher()
        matcher.resume("pkg.a", 8_000)
        val blocks = matcher.finish(18_000)

        assertEquals(1, blocks.size)
        assertEquals(10_000, blocks[0].durationMs)
    }
}
