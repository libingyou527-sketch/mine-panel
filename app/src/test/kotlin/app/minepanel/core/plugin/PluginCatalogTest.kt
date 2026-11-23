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
    fun descriptorsDescribeRiskAndImplementationState() {
        PluginCatalog.all.forEach { descriptor ->
            assertTrue(descriptor.title.isNotBlank())
            assertTrue(descriptor.description.isNotBlank())
            assertTrue(descriptor.riskLabel.isNotBlank())
        }
        // 健康连接受系统 SDK 版本限制，本期仍为占位；其余插件已接入采集。
        assertFalse(PluginCatalog.byType(PluginType.HEALTH_CONNECT).implemented)
        assertTrue(PluginCatalog.byType(PluginType.NOTIFICATION_META).implemented)
        assertTrue(PluginCatalog.byType(PluginType.CALENDAR).implemented)
        assertTrue(PluginCatalog.byType(PluginType.CALL_LOG).implemented)
        assertTrue(PluginCatalog.byType(PluginType.GEOFENCE).implemented)
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
