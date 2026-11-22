package app.minepanel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 插件开关设置：读取本地 DataStore 中的插件启用状态，切换后即时落盘。
 * 阶段 5 插件均为占位：开关只改变本地偏好，不注册采集组件、不申请权限、不写插件事件表。
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

    fun toggle(type: PluginType) {
        val current = plugins.value[type] ?: false
        viewModelScope.launch {
            settings.setPluginEnabled(type, !current)
        }
    }
}
