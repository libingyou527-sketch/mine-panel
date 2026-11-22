package app.minepanel.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RunStatusUiState(
    val hasRecord: Boolean = false,
    val status: String = "",
    val engineName: String = "",
    val latencyMs: Long = 0L,
    val finishedAt: Long = 0L
)

/** 读取最近一次本地推理记录，供设置区块回显运行状态；不触发推理、不联网。 */
class RunStatusViewModel(app: Application) : AndroidViewModel(app) {

    private val runLogDao = ServiceLocator.database().modelRunLogDao()

    private val _state = MutableStateFlow(RunStatusUiState())
    val state: StateFlow<RunStatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val latest = runLogDao.recent(1).firstOrNull()
            _state.value = if (latest == null) {
                RunStatusUiState()
            } else {
                RunStatusUiState(
                    hasRecord = true,
                    status = latest.status,
                    engineName = latest.engineName,
                    latencyMs = latest.latencyMs,
                    finishedAt = latest.finishedAt
                )
            }
        }
    }
}
