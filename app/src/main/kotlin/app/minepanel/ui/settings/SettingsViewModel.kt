package app.minepanel.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.data.repository.WipeRepository
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val wipeRepository = WipeRepository(app)

    /** 物理删除全部本地数据；完成后回调由 UI 重启进程回到首次引导。 */
    fun wipeAll(onDone: () -> Unit) {
        viewModelScope.launch {
            wipeRepository.wipeAll()
            onDone()
        }
    }
}
