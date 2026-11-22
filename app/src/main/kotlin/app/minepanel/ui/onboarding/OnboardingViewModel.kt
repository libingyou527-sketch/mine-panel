package app.minepanel.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import app.minepanel.core.PermissionUtils
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

class OnboardingViewModel : ViewModel() {

    private val settings = ServiceLocator.settings()

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step

    private val _agreed = MutableStateFlow(false)
    val agreed: StateFlow<Boolean> = _agreed

    private val _usageAccessOk = MutableStateFlow(false)
    val usageAccessOk: StateFlow<Boolean> = _usageAccessOk

    val plugins: Map<PluginType, MutableStateFlow<Boolean>> =
        PluginType.entries.associateWith { MutableStateFlow(false) }

    fun agree() {
        _agreed.value = true
    }

    fun next() {
        if (_step.value < 2) _step.value += 1
    }

    fun refreshUsageAccess(context: Context) {
        _usageAccessOk.value = PermissionUtils.hasUsageAccess(context)
    }

    fun togglePlugin(type: PluginType) {
        val flow = plugins.getValue(type)
        flow.value = !flow.value
    }

    suspend fun complete() {
        plugins.forEach { (type, flow) -> settings.setPluginEnabled(type, flow.value) }
        settings.setBaselineStartEpochDay(LocalDate.now(ZoneId.systemDefault()).toEpochDay())
        settings.markOnboardingCompleted()
    }
}
