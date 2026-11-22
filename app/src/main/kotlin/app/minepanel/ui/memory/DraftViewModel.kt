package app.minepanel.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.minepanel.core.ServiceLocator
import app.minepanel.core.WorkScheduler
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.prefs.SettingsAccess
import app.minepanel.data.repository.DraftRepository
import app.minepanel.data.repository.ManualFragmentRepository
import app.minepanel.data.repository.MilestoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DraftQueueUiState(
    val pending: List<DraftMemory> = emptyList(),
    val confirmed: List<ConfirmedMemory> = emptyList(),
    val fragments: List<ManualFragment> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val loading: Boolean = true,
    val generating: Boolean = false,
    val autoConfirmEnabled: Boolean = true
)

class DraftViewModel : AndroidViewModel {

    private lateinit var repository: DraftRepository
    private lateinit var fragmentRepository: ManualFragmentRepository
    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var settingsAccess: SettingsAccess

    /** 生产构造：从 ServiceLocator 取加密库 DAO 与设置存储。 */
    constructor(app: Application) : super(app) {
        val db = ServiceLocator.database()
        repository = DraftRepository(
            draftDao = db.draftMemoryDao(),
            confirmedDao = db.confirmedMemoryDao(),
            runLogDao = db.modelRunLogDao()
        )
        fragmentRepository = ManualFragmentRepository(db.manualFragmentDao())
        milestoneRepository = MilestoneRepository(db.milestoneDao())
        settingsAccess = ServiceLocator.settings()
        refresh()
    }

    /** 测试构造：注入内存库仓储与内存设置实现，不触碰真库、Keystore 与 DataStore。 */
    constructor(
        app: Application,
        draftRepository: DraftRepository,
        fragmentRepository: ManualFragmentRepository,
        milestoneRepository: MilestoneRepository,
        settingsAccess: SettingsAccess
    ) : super(app) {
        this.repository = draftRepository
        this.fragmentRepository = fragmentRepository
        this.milestoneRepository = milestoneRepository
        this.settingsAccess = settingsAccess
        refresh()
    }

    private val _state = MutableStateFlow(DraftQueueUiState())
    val state: StateFlow<DraftQueueUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val pending = repository.pendingDrafts()
            val confirmed = repository.confirmed(0L, System.currentTimeMillis())
            val fragments = fragmentRepository.list()
            val milestones = milestoneRepository.list()
            val settings = settingsAccess.settings.first()
            _state.value = _state.value.copy(
                pending = pending,
                confirmed = confirmed,
                fragments = fragments,
                milestones = milestones,
                loading = false,
                autoConfirmEnabled = settings.autoConfirmEnabled
            )
        }
    }

    fun toggleAutoConfirm(enabled: Boolean) {
        viewModelScope.launch {
            settingsAccess.setAutoConfirmEnabled(enabled)
            _state.value = _state.value.copy(autoConfirmEnabled = enabled)
        }
    }

    /** 手动触发一次本地聚合 + 推理；完成后刷新队列。 */
    fun generateOnce() {
        if (_state.value.generating) return
        _state.value = _state.value.copy(generating = true)
        viewModelScope.launch {
            val wm = WorkManager.getInstance(getApplication())
            WorkScheduler.runOnce(getApplication())
            wm.getWorkInfosByTagFlow(WorkScheduler.MANUAL_TAG)
                .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
            refresh()
            _state.value = _state.value.copy(generating = false)
        }
    }

    fun confirm(id: Long) {
        viewModelScope.launch {
            repository.confirm(id)
            refresh()
        }
    }

    fun modify(id: Long, edited: String) {
        viewModelScope.launch {
            repository.modify(id, edited)
            refresh()
        }
    }

    fun reject(id: Long) {
        viewModelScope.launch {
            repository.reject(id)
            refresh()
        }
    }

    /** 撤销一条已写入的正式记忆。 */
    fun revokeMemory(id: Long) {
        viewModelScope.launch {
            repository.revoke(id)
            refresh()
        }
    }

    /** 写入一条本人手写片段；空正文或重复正文不会入库。 */
    fun addFragment(content: String, mood: String?, category: String?, anchorEpochMs: Long? = null) {
        viewModelScope.launch {
            fragmentRepository.add(content, mood, category, anchorEpochMs = anchorEpochMs)
            refresh()
        }
    }

    fun deleteFragment(id: Long) {
        viewModelScope.launch {
            fragmentRepository.delete(id)
            refresh()
        }
    }

    /** 写入一条本人自定义里程碑；空标题或重复标题不会入库。 */
    fun addMilestone(
        title: String,
        label: String?,
        note: String?,
        anchorEpochMs: Long? = null,
        linkedFragmentId: Long? = null
    ) {
        viewModelScope.launch {
            milestoneRepository.add(
                title, label, note,
                anchorEpochMs = anchorEpochMs,
                linkedFragmentId = linkedFragmentId
            )
            refresh()
        }
    }

    fun deleteMilestone(id: Long) {
        viewModelScope.launch {
            milestoneRepository.delete(id)
            refresh()
        }
    }
}
