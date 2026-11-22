package app.minepanel.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.data.export.ImportConflictStrategy
import app.minepanel.data.repository.ExportRepository
import app.minepanel.data.repository.ImportRepository
import app.minepanel.data.repository.ImportReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ExportUiState(
    val allowExport: Boolean = false,
    val includeRawEvents: Boolean = false,
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val lastExportAt: Long? = null,
    val strategy: ImportConflictStrategy = ImportConflictStrategy.SKIP_DUPLICATES,
    val lastImportMessage: String? = null,
    val lastImportIsError: Boolean = false,
    val lastImportAt: Long? = null
)

/**
 * 本地明文导出/导入视图模型。
 *
 * 导出总开关默认关闭；开启后用户通过 SAF 选择本地文件，应用不联网、不写剪贴板。
 * 原始事件是否纳入由用户在导出前勾选，导出结果与上次导出时间回显在区块内。
 * 导入仅读取用户手动选定的本地 JSON，默认跳过重复条目，不覆盖已有正式记忆。
 */
class ExportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ExportRepository(app)
    private val importRepository = ImportRepository(app)

    private val _state = androidx.compose.runtime.mutableStateOf(ExportUiState())
    val state: androidx.compose.runtime.State<ExportUiState> = _state

    init {
        ServiceLocator.settings().settings
            .onEach { snap ->
                _state.value = _state.value.copy(
                    allowExport = snap.exportEnabled,
                    lastExportAt = snap.lastExportEpochMs,
                    lastImportMessage = snap.lastImportMessage,
                    lastImportIsError = snap.lastImportIsError,
                    lastImportAt = snap.lastImportAt
                )
            }
            .launchIn(viewModelScope)
    }

    fun setAllowExport(enabled: Boolean) {
        viewModelScope.launch {
            ServiceLocator.settings().setExportEnabled(enabled)
            _state.value = _state.value.copy(allowExport = enabled, message = null)
        }
    }

    fun setIncludeRawEvents(enabled: Boolean) {
        _state.value = _state.value.copy(includeRawEvents = enabled)
    }

    fun setStrategy(strategy: ImportConflictStrategy) {
        _state.value = _state.value.copy(strategy = strategy, message = null)
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, message = null, messageIsError = false)
            val result = repository.exportTo(uri, _state.value.includeRawEvents)
            val (message, isError) = when (result) {
                is ExportRepository.ExportResult.Success -> buildString {
                    append("已导出：记忆 ${result.memoryCount} 条")
                    append("、标签 ${result.tagCount} 条")
                    append("、分值 ${result.scoreCount} 条")
                    append("、徽章 ${result.badgeCount} 枚")
                    append("、片段 ${result.fragmentCount} 条")
                    append("、里程碑 ${result.milestoneCount} 个")
                    result.rawCount?.let { append("、原始事件 $it 条") }
                } to false

                is ExportRepository.ExportResult.Failure -> result.reason to true
            }
            val fresh = ServiceLocator.settings().settings.first()
            _state.value = _state.value.copy(
                exporting = false,
                message = message,
                messageIsError = isError,
                lastExportAt = fresh.lastExportEpochMs
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, message = null, messageIsError = false)
            val result = importRepository.importFrom(uri, _state.value.strategy)
            val (message, isError) = when (result) {
                is ImportRepository.ImportResult.Success -> formatImportReport(result.report) to false
                is ImportRepository.ImportResult.Failure -> result.reason to true
            }
            val at = System.currentTimeMillis()
            ServiceLocator.settings().setLastImportResult(message, isError, at)
            _state.value = _state.value.copy(
                importing = false,
                message = message,
                messageIsError = isError,
                lastImportMessage = message,
                lastImportIsError = isError,
                lastImportAt = at
            )
        }
    }

    private fun formatImportReport(report: ImportReport): String = buildString {
        append("已导入：记忆 ${report.memoriesImported} 条")
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
}
