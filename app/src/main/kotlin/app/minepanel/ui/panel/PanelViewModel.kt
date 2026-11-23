package app.minepanel.ui.panel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.AttributeKey
import app.minepanel.data.repository.PanelRepository
import app.minepanel.rules.DimensionPair
import app.minepanel.rules.PanelBadge
import app.minepanel.rules.PanelScoring
import app.minepanel.rules.PanelTimelineDay
import app.minepanel.rules.WeekScore
import app.minepanel.service.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 面板渲染状态：本周/上周两组分值 + 基线标记，Compose 直接按状态渲染。 */
data class PanelUiState(
    val isBaseline: Boolean = false,
    val currentWeekScores: List<WeekScore> = emptyList(),
    val lastWeekScores: List<WeekScore> = emptyList(),
    val pairs: List<DimensionPair> = emptyList(),
    val timeline: List<PanelTimelineDay> = emptyList(),
    val badges: List<PanelBadge> = emptyList(),
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val averageScore: Int = 0,
    val lastWeekHasData: Boolean = false,
    val selectedKey: AttributeKey? = null,
    val showFallbackNotice: Boolean = false,
    val fallbackNoticeStatus: String = "",
    val loading: Boolean = true
)

class PanelViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PanelRepository(
        rawDao = ServiceLocator.database().rawUsageEventDao(),
        aggregateDao = ServiceLocator.database().dailyAggregateDao(),
        badgeDao = ServiceLocator.database().badgeDao()
    )
    private val runLogDao = ServiceLocator.database().modelRunLogDao()

    private val _state = MutableStateFlow(PanelUiState())
    val state: StateFlow<PanelUiState> = _state.asStateFlow()

    val paused: StateFlow<Boolean> = ServiceLocator.settings().settings
        .map { it.recordingPaused }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val data = runCatching { repository.load() }.getOrNull()
            if (data == null) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            val avg = PanelScoring.average(data.currentWeekScores)
            val level = PanelScoring.levelOf(avg)
            val settings = ServiceLocator.settings().settings.first()
            val latestRun = runLogDao.recent(1).firstOrNull()
            _state.value = _state.value.copy(
                isBaseline = data.isBaseline,
                currentWeekScores = data.currentWeekScores,
                lastWeekScores = data.lastWeekScores,
                pairs = PanelScoring.pair(data.currentWeekScores, data.lastWeekScores),
                timeline = data.timeline,
                badges = data.badges,
                level = level.level,
                levelProgress = level.progress,
                averageScore = avg,
                lastWeekHasData = data.lastWeekHasData,
                selectedKey = _state.value.selectedKey,
                showFallbackNotice = settings.fallbackNoticePending,
                fallbackNoticeStatus = latestRun?.status ?: "",
                loading = false
            )
        }
    }

    /** 关闭兜底提示弹窗，并清除待提示标记，避免重复弹出。 */
    fun dismissFallbackNotice() {
        viewModelScope.launch {
            ServiceLocator.settings().setFallbackNoticePending(false)
            _state.value = _state.value.copy(showFallbackNotice = false)
        }
    }

    fun select(key: AttributeKey?) {
        _state.value = _state.value.copy(selectedKey = key)
    }

    fun togglePause() {
        viewModelScope.launch {
            val next = !paused.value
            ServiceLocator.settings().setRecordingPaused(next)
            val intent = Intent(getApplication(), RecordingService::class.java).apply {
                action = if (next) RecordingService.ACTION_PAUSE else RecordingService.ACTION_RESUME
            }
            ContextCompat.startForegroundService(getApplication(), intent)
        }
    }
}
