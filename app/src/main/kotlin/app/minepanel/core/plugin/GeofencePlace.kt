package app.minepanel.core.plugin

import app.minepanel.data.export.MiniJson

/** 一个手动录入的常去点；围栏只判定到达/离开，不采集连续轨迹。 */
data class GeofencePlace(
    val label: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int
) {
    fun toJson(): String = buildString {
        append("{\"label\":").append(PluginJsonEscape.escape(label))
        append(",\"lat\":").append(lat)
        append(",\"lng\":").append(lng)
        append(",\"radiusM\":").append(radiusM)
        append('}')
    }

    companion object {
        fun parseList(json: String): List<GeofencePlace> {
            if (json.isBlank()) return emptyList()
            val root = runCatching { MiniJson.parse(json).asArray() }.getOrNull() ?: return emptyList()
            return root.items.mapNotNull { item ->
                val obj = item.asObject() ?: return@mapNotNull null
                val label = obj.map["label"]?.asString().orEmpty()
                val lat = obj.map["lat"]?.asDouble() ?: return@mapNotNull null
                val lng = obj.map["lng"]?.asDouble() ?: return@mapNotNull null
                val radius = obj.map["radiusM"]?.asInt() ?: DEFAULT_RADIUS
                if (label.isBlank()) null else GeofencePlace(label, lat, lng, radius)
            }
        }

        fun toJson(places: List<GeofencePlace>): String =
            places.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJson() }

        const val DEFAULT_RADIUS = 150
    }
}

internal object PluginJsonEscape {
    fun escape(value: String): String {
        val sb = StringBuilder("\"")
        value.forEach { ch ->
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
