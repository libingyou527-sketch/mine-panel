package app.minepanel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumsTest {

    @Test
    fun unknownCategoryFallsBackToUnmapped() {
        assertEquals(EventCategory.UNMAPPED, EventCategory.fromName(null))
        assertEquals(EventCategory.UNMAPPED, EventCategory.fromName("does_not_exist"))
        assertEquals(EventCategory.SOCIAL, EventCategory.fromName("SOCIAL"))
    }

    @Test
    fun hasSixAttributes() {
        assertEquals(6, AttributeKey.entries.size)
    }

    @Test
    fun hasFiveOptionalPlugins() {
        assertEquals(5, PluginType.entries.size)
    }

    @Test
    fun draftStatusStateCoversLifecycle() {
        assertEquals(
            setOf(DraftStatus.PENDING, DraftStatus.CONFIRMED, DraftStatus.MODIFIED, DraftStatus.REJECTED),
            DraftStatus.entries.toSet()
        )
    }
}
