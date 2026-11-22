package app.minepanel.core.plugin

import app.minepanel.core.model.PluginType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogTest {

    @Test
    fun catalogCoversAllPluginTypes() {
        assertEquals(PluginType.entries.size, PluginCatalog.all.size)
        PluginType.entries.forEach { type ->
            assertNotNull(PluginCatalog.all.firstOrNull { it.type == type })
        }
    }

    @Test
    fun placeholdersAreNotImplementedAndDescribeRisk() {
        PluginCatalog.all.forEach { descriptor ->
            assertFalse("${descriptor.type} 阶段5 应为占位", descriptor.implemented)
            assertTrue(descriptor.title.isNotBlank())
            assertTrue(descriptor.description.isNotBlank())
            assertTrue(descriptor.riskLabel.isNotBlank())
        }
    }

    @Test
    fun callLogIsHighRisk() {
        val callLog = PluginCatalog.byType(PluginType.CALL_LOG)
        assertEquals(PluginRiskLevel.HIGH, callLog.riskLevel)
    }

    @Test
    fun byTypeReturnsMatchingDescriptor() {
        val notification = PluginCatalog.byType(PluginType.NOTIFICATION_META)
        assertEquals(PluginType.NOTIFICATION_META, notification.type)
        assertEquals(notification, PluginCatalog.all.first { it.type == notification.type })
    }
}
