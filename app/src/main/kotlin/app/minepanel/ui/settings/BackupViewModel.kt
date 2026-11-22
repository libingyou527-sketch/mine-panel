package app.minepanel.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.data.repository.BackupRepository
import app.minepanel.data.repository.ImportReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

/** 口令对话框用途：备份需输入并确认两次；恢复只需输入一次。 */
enum class BackupDialogMode { BACKUP, RESTORE }

data class BackupUiState(
    val includeRawEvents: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val lastBackupAt: Long? = null,
    val showDialog: Boolean = false,
    val mode: BackupDialogMode = BackupDialogMode.BACKUP,
    val password: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null
)

/**
 * 加密备份 / 恢复视图模型。
 *
 * 口令只在内存中暂存到 SAF 文件选择回调完成本次操作，不写入 DataStore、不落地明文。
 * 备份与恢复均在本地完成，不联网；恢复默认跳过重复条目，不覆盖已有正式记忆。
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BackupRepository(app)
    private val _state = mutableStateOf(BackupUiState())
    val state: State<BackupUiState> = _state

    /** 本次待执行的口令；SAF 回调成功取走后置空。 */
    private var pendingPassword: String? = null

    init {
        ServiceLocator.settings().settings
            .onEach { snap ->
                _state.value = _state.value.copy(lastBackupAt = snap.lastExportEpochMs)
            }
            .launchIn(viewModelScope)
    }

    fun setIncludeRawEvents(enabled: Boolean) {
        _state.value = _state.value.copy(includeRawEvents = enabled)
    }

    fun showBackupDialog() {
        _state.value = _state.value.copy(
            showDialog = true,
            mode = BackupDialogMode.BACKUP,
            password = "",
            confirmPassword = "",
            passwordError = null
        )
    }

    fun showRestoreDialog() {
        _state.value = _state.value.copy(
            showDialog = true,
            mode = BackupDialogMode.RESTORE,
            password = "",
            confirmPassword = "",
            passwordError = null
        )
    }

    fun dismissDialog() {
        _state.value = _state.value.copy(showDialog = false, passwordError = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, passwordError = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, passwordError = null)
    }

    /** 校验口令；通过后暂存口令并关闭对话框，由界面随后拉起 SAF 选择文件。 */
    fun submitDialog(): Boolean {
        val s = _state.value
        if (s.password.length < MIN_PASSWORD_LENGTH) {
            _state.value = s.copy(passwordError = "口令至少 $MIN_PASSWORD_LENGTH 位")
            return false
        }
        if (s.mode == BackupDialogMode.BACKUP && s.password != s.confirmPassword) {
            _state.value = s.copy(passwordError = "两次输入的口令不一致")
            return false
        }
        pendingPassword = s.password
        _state.value = s.copy(showDialog = false, password = "", confirmPassword = "", passwordError = null)
        return true
    }

    fun backupTo(uri: Uri) {
        val password = pendingPassword ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, messageIsError = false)
            val result = repository.backupTo(uri, password, _state.value.includeRawEvents)
            val (message, isError) = when (result) {
                is BackupRepository.BackupResult.Success -> buildString {
                    append("已加密备份：记忆 ${result.memoryCount} 条")
                    append("、标签 ${result.tagCount} 条")
                    append("、分值 ${result.scoreCount} 条")
                    append("、徽章 ${result.badgeCount} 枚")
                    append("、片段 ${result.fragmentCount} 条")
                    append("、里程碑 ${result.milestoneCount} 个")
                    result.rawCount?.let { append("、原始事件 $it 条") }
                } to false

                is BackupRepository.BackupResult.Failure -> result.reason to true
            }
            pendingPassword = null
            val fresh = ServiceLocator.settings().settings.first()
            _state.value = _state.value.copy(
                busy = false,
                message = message,
                messageIsError = isError,
                lastBackupAt = fresh.lastExportEpochMs
            )
        }
    }

    fun restoreFrom(uri: Uri) {
        val password = pendingPassword ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, messageIsError = false)
            val result = repository.restoreFrom(uri, password)
            val (message, isError) = when (result) {
                is BackupRepository.RestoreResult.Success -> formatReport(result.report) to false
                is BackupRepository.RestoreResult.Failure -> result.reason to true
            }
            pendingPassword = null
            _state.value = _state.value.copy(
                busy = false,
                message = message,
                messageIsError = isError
            )
        }
    }

    private fun formatReport(report: ImportReport): String = buildString {
        append("已恢复：记忆 ${report.memoriesImported} 条")
        append("、标签 ${report.tagsImported} 条")
        append("、分值 ${report.scoresImported} 条")
        append("、徽章 ${report.badgesImported} 枚")
        append("、片段 ${report.fragmentsImported} 条")
        append("、里程碑 ${report.milestonesImported} 个")
        if (report.rawImported > 0) append("、原始事件 ${report.rawImported} 条")
        val skipped = report.memoriesSkipped + report.tagsSkipped + report.scoresSkipped +
            report.badgesSkipped + report.rawSkipped + report.fragmentsSkipped + report.milestonesSkipped
        if (skipped > 0) append("；跳过重复 $skipped 条")
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 4
    }
}
