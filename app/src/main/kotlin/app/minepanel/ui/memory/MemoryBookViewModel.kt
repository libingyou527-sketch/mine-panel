package app.minepanel.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.repository.DraftRepository
import app.minepanel.data.repository.ManualFragmentRepository
import app.minepanel.data.repository.MilestoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 记忆本专区的只读浏览状态：正式记忆、状态标签、回顾文案、主动片段与里程碑分区列示。 */
data class MemoryBookUiState(
    val memories: List<ConfirmedMemory> = emptyList(),
    val tags: List<DraftMemory> = emptyList(),
    val narratives: List<DraftMemory> = emptyList(),
    val fragments: List<ManualFragment> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val loading: Boolean = true
)

class MemoryBookViewModel : AndroidViewModel {

    private lateinit var draftRepository: DraftRepository
    private lateinit var fragmentRepository: ManualFragmentRepository
    private lateinit var milestoneRepository: MilestoneRepository

    /** 生产构造：从 ServiceLocator 取加密库 DAO 装配仓储。 */
    constructor(app: Application) : super(app) {
        val db = ServiceLocator.database()
        draftRepository = DraftRepository(
            draftDao = db.draftMemoryDao(),
            confirmedDao = db.confirmedMemoryDao(),
            runLogDao = db.modelRunLogDao()
        )
        fragmentRepository = ManualFragmentRepository(db.manualFragmentDao())
        milestoneRepository = MilestoneRepository(db.milestoneDao())
        refresh()
    }

    /** 测试构造：注入仓储，不触碰真库与 Keystore。 */
    constructor(
        app: Application,
        draftRepository: DraftRepository,
        fragmentRepository: ManualFragmentRepository,
        milestoneRepository: MilestoneRepository
    ) : super(app) {
        this.draftRepository = draftRepository
        this.fragmentRepository = fragmentRepository
        this.milestoneRepository = milestoneRepository
        refresh()
    }

    private val _state = MutableStateFlow(MemoryBookUiState())
    val state: StateFlow<MemoryBookUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _state.value = _state.value.copy(
                memories = draftRepository.confirmed(0L, now),
                tags = draftRepository.confirmedTags(),
                narratives = draftRepository.confirmedNarratives(),
                fragments = fragmentRepository.list(),
                milestones = milestoneRepository.list(),
                loading = false
            )
        }
    }
}
