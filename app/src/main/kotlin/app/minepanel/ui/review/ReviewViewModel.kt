package app.minepanel.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.minepanel.aggregate.ReviewGenerator
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.ModelStatus
import app.minepanel.core.time.ReviewPeriod
import app.minepanel.core.time.ReviewWindows
import app.minepanel.data.repository.DraftRepository
import app.minepanel.data.repository.ReviewRepository
import app.minepanel.data.repository.ReviewSummary
import app.minepanel.model.EngineRouter
import app.minepanel.model.ModelOutputJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 长周期回顾页渲染状态：周期、历史窗偏移、只读摘要与生成进度/提示。 */
data class ReviewUiState(
    val period: ReviewPeriod = ReviewPeriod.MONTH,
    val stepsBack: Int = 0,
    val summary: ReviewSummary? = null,
    val previous: ReviewSummary? = null,
    val loading: Boolean = true,
    val generating: Boolean = false,
    val message: String? = null
)

class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ServiceLocator.database()
    private val reviewRepository = ReviewRepository(
        aggregateDao = db.dailyAggregateDao(),
        scoreDao = db.attributeScoreDao(),
        confirmedDao = db.confirmedMemoryDao(),
        fragmentDao = db.manualFragmentDao()
    )
    private val draftRepository = DraftRepository(
        draftDao = db.draftMemoryDao(),
        confirmedDao = db.confirmedMemoryDao(),
        runLogDao = db.modelRunLogDao()
    )
    private val generator = ReviewGenerator { EngineRouter(getApplication()).select(it) }

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectPeriod(period: ReviewPeriod) {
        _state.value = _state.value.copy(period = period, stepsBack = 0, message = null)
        refresh()
    }

    fun stepBack() {
        _state.value = _state.value.copy(stepsBack = _state.value.stepsBack + 1, message = null)
        refresh()
    }

    fun stepForward() {
        if (_state.value.stepsBack > 0) {
            _state.value = _state.value.copy(stepsBack = _state.value.stepsBack - 1, message = null)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val state = _state.value
            val window = ReviewWindows.window(state.period, state.stepsBack)
            val summary = runCatching { reviewRepository.summarize(window) }.getOrNull()
            val previousWindow = ReviewWindows.window(state.period, state.stepsBack + 1)
            val previous = runCatching { reviewRepository.summarize(previousWindow) }
                .getOrNull()
                ?.takeIf { it.sampleDays > 0 }
            _state.value = _state.value.copy(summary = summary, previous = previous, loading = false)
        }
    }

    /** 生成回望草稿：模型优先、模板兜底；产物一律进人工确认队列，不自动回写数值。 */
    fun generate() {
        val summary = _state.value.summary ?: return
        if (_state.value.generating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(generating = true, message = null)
            val settings = ServiceLocator.settings().settings.first()
            val startedAt = System.currentTimeMillis()
            val generation = generator.generate(summary, settings)
            val finishedAt = System.currentTimeMillis()

            val autoConfirm = settings.autoConfirmEnabled && generation.status == ModelStatus.SUCCESS
            if (generation.status != ModelStatus.SUCCESS) {
                ServiceLocator.settings().setFallbackNoticePending(true)
            }

            val runLogId = draftRepository.recordRun(
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = generation.status,
                engineName = generation.engineName,
                inputSnapshotHash = generation.inputSnapshotHash,
                outputJson = ModelOutputJson.toJson(generation.output),
                latencyMs = finishedAt - startedAt
            )
            draftRepository.insertDrafts(
                runLogId = runLogId,
                output = generation.output,
                periodStart = summary.window.startMs,
                periodEnd = summary.window.endMs,
                autoConfirm = autoConfirm
            )

            val message = if (generation.status == ModelStatus.SUCCESS) {
                "回望草稿已生成，进入草案队列等待人工确认。"
            } else {
                "本次由兜底模板生成，草稿已进入人工队列，未自动入库。"
            }
            _state.value = _state.value.copy(generating = false, message = message)
        }
    }
}
