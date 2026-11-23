package app.minepanel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import app.minepanel.core.plugin.GeofencePlace
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 插件开关设置：读取本地 DataStore 中的插件启用状态，切换后即时落盘。
 * 已接入插件的采集组件按开关与系统权限工作；未接入项只保留偏好，不采集。
 */
class PluginSettingsViewModel : ViewModel() {

    private val settings = ServiceLocator.settings()

    val plugins: StateFlow<Map<PluginType, Boolean>> =
        settings.settings
            .map { it.plugins }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    val places: StateFlow<List<GeofencePlace>> =
        settings.settings
            .map { GeofencePlace.parseList(it.geofencePlaces) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun toggle(type: PluginType) {
        val current = plugins.value[type] ?: false
        viewModelScope.launch {
            settings.setPluginEnabled(type, !current)
        }
    }

    /** 新增或同名覆盖一个常去点；经纬度非法时忽略。 */
    fun addPlace(label: String, lat: Double?, lng: Double?, radiusM: Int?) {
        val cleanLabel = label.trim()
        if (cleanLabel.isEmpty() || lat == null || lng == null) return
        val radius = radiusM?.takeIf { it in 50..2000 } ?: GeofencePlace.DEFAULT_RADIUS
        viewModelScope.launch {
            val updated = places.value.filter { it.label != cleanLabel } +
                GeofencePlace(cleanLabel, lat, lng, radius)
            settings.setGeofencePlaces(GeofencePlace.toJson(updated))
        }
    }

    fun removePlace(label: String) {
        viewModelScope.launch {
            val updated = places.value.filter { it.label != label }
            settings.setGeofencePlaces(GeofencePlace.toJson(updated))
        }
    }
}
