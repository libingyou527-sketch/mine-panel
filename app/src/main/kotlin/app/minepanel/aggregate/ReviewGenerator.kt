package app.minepanel.aggregate

import android.content.Context
import android.util.Log
import app.minepanel.core.model.ModelStatus
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.data.repository.ReviewSummary
import app.minepanel.model.EngineRouter
import app.minepanel.model.GgufInferenceEngine
import app.minepanel.model.InferenceEngine
import app.minepanel.model.ModelInput
import app.minepanel.model.ModelOutput
import app.minepanel.model.ModelSnapshotHasher
import app.minepanel.model.SchemaValidationException
import app.minepanel.model.TemplateFallbackEngine
import kotlinx.coroutines.withTimeoutOrNull

/** 一次长周期回顾推理的结果：输出、状态、引擎名与输入快照哈希（仅留存哈希）。 */
data class ReviewGeneration(
    val output: ModelOutput,
    val status: ModelStatus,
    val engineName: String,
    val inputSnapshotHash: String
)

/**
 * 长周期回顾双轨生成器：与周回顾同一套「模型优先、模板兜底」范式。
 * 先在本地把自然窗结论性摘要汇编成精简输入，路由本地 GGUF；超时 / OOM / 异常 / schema 校验不过
 * 一律回退模板汇编。两条路径产出的草稿都进入人工确认队列，生成器本身不回写数值、不出诊断结论、不联网。
 */
class ReviewGenerator(
    private val engineProvider: (SettingsSnapshot) -> InferenceEngine
) {

    constructor(context: Context) : this({ EngineRouter(context).select(it) })

    suspend fun generate(
        summary: ReviewSummary,
        settings: SettingsSnapshot
    ): ReviewGeneration {
        val input = toInput(summary)
        val inputHash = ModelSnapshotHasher.hash(input)

        // 空窗口：没有任何可总结的样本，不加载/调用模型，直接模板兜底。
        if (input.isEmptyWindow()) {
            val fallback = TemplateFallbackEngine()
            val output = fallback.infer(input)
            return ReviewGeneration(
                output = output,
                status = ModelStatus.FALLBACK_TEMPLATE,
                engineName = fallback.engineName,
                inputSnapshotHash = inputHash
            )
        }

        val engine = engineProvider(settings)
        val startedAt = System.currentTimeMillis()
        var status = ModelStatus.SUCCESS
        var output: ModelOutput? = null

        try {
            output = withTimeoutOrNull(TIMEOUT_MS) { engine.infer(input) }
            if (output == null) status = ModelStatus.TIMEOUT
        } catch (e: SchemaValidationException) {
            // 输出不合规属预期降级，回退模板并记模板降级，不记为崩溃错误。
            status = ModelStatus.FALLBACK_TEMPLATE
            Log.e("ReviewGenerator", "output schema rejected", e)
            releaseEngine(engine)
        } catch (oom: OutOfMemoryError) {
            status = ModelStatus.ERROR
            Log.e("ReviewGenerator", "infer OOM", oom)
            releaseEngine(engine)
        } catch (t: Throwable) {
            status = ModelStatus.ERROR
            Log.e("ReviewGenerator", "infer failed", t)
            releaseEngine(engine)
        }

        // 超时 / OOM / 异常 / 校验失败：回退模板降级，草稿仍进待确认队列。
        if (output == null) {
            output = TemplateFallbackEngine().infer(input)
            if (status == ModelStatus.SUCCESS) status = ModelStatus.FALLBACK_TEMPLATE
        } else if (engine is TemplateFallbackEngine) {
            status = ModelStatus.FALLBACK_TEMPLATE
        }

        return ReviewGeneration(
            output = output,
            status = status,
            engineName = engine.engineName,
            inputSnapshotHash = inputHash
        )
    }

    private fun toInput(summary: ReviewSummary): ModelInput = ModelInput(
        scores = summary.scores,
        totalScreenTimeMin = summary.totalScreenTimeMin,
        sampleDays = summary.sampleDays,
        categoryMinutes = summary.categoryMinutes,
        weekOverWeek = emptyMap(),
        periodLabel = summary.window.label,
        memoryCount = summary.memoryCount,
        fragmentCount = summary.fragmentCount,
        highlights = summary.memoryHighlights + summary.fragmentHighlights
    )

    private fun releaseEngine(engine: InferenceEngine) {
        (engine as? GgufInferenceEngine)?.release()
    }

    companion object {
        private const val TIMEOUT_MS = 60_000L
    }
}
