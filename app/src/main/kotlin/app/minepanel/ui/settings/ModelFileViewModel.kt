package app.minepanel.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.data.repository.ModelFileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ModelFileUiState(
    val path: String? = null,
    val hash: String? = null,
    val importing: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
)

class ModelFileViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ModelFileRepository(app)

    private val _state = androidx.compose.runtime.mutableStateOf(ModelFileUiState())
    val state: androidx.compose.runtime.State<ModelFileUiState> = _state

    init {
        ServiceLocator.settings().settings
            .onEach { snap ->
                _state.value = _state.value.copy(path = snap.modelFilePath, hash = snap.modelHash)
            }
            .launchIn(viewModelScope)
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, message = null, messageIsError = false)
            val result = repository.importFrom(uri)
            val fresh = ServiceLocator.settings().settings.first()
            val (message, isError) = when (result) {
                is ModelFileRepository.ImportResult.Success -> "模型校验通过，已载入本地配置" to false
                is ModelFileRepository.ImportResult.Rejected -> result.reason to true
            }
            _state.value = ModelFileUiState(
                path = fresh.modelFilePath,
                hash = fresh.modelHash,
                importing = false,
                message = message,
                messageIsError = isError
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            repository.clear()
            _state.value = _state.value.copy(path = null, hash = null, message = "已移除本地模型", messageIsError = false)
        }
    }
}
