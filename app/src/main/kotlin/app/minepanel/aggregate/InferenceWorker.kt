package app.minepanel.aggregate

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.ModelStatus
import app.minepanel.data.repository.DraftRepository
import app.minepanel.data.repository.ModelInputAssembler
import app.minepanel.model.EngineRouter
import app.minepanel.model.GgufInferenceEngine
import app.minepanel.model.ModelOutput
import app.minepanel.model.ModelOutputJson
import app.minepanel.model.ModelSnapshotHasher
import app.minepanel.model.TemplateFallbackEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 本地后台推理 Worker：组装精简输入 → 路由选择引擎 → 超时/OOM/异常时回退模板降级，
 * 记录运行日志后把草案写入 PENDING 队列。全程本地，不联网。
 */
class InferenceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val context = applicationContext
            val db = ServiceLocator.database()
            val settings = ServiceLocator.settings().settings.first()

            val now = System.currentTimeMillis()
            val from = now - LOOKBACK_MS
            val input = ModelInputAssembler(
                aggregateDao = db.dailyAggregateDao(),
                scoreDao = db.attributeScoreDao()
            ).assemble(from, now)
            val inputHash = ModelSnapshotHasher.hash(input)

            val engine = EngineRouter(context).select(settings)
            val startedAt = System.currentTimeMillis()
            var status = ModelStatus.SUCCESS
            var output: ModelOutput? = null

            try {
                output = withTimeoutOrNull(TIMEOUT_MS) { engine.infer(input) }
                if (output == null) status = ModelStatus.TIMEOUT
            } catch (oom: OutOfMemoryError) {
                status = ModelStatus.ERROR
                releaseEngine(engine)
            } catch (t: Throwable) {
                status = ModelStatus.ERROR
                releaseEngine(engine)
            }

            // 超时 / OOM / 异常：回退模板降级，草案仍进入待确认队列。
            if (output == null) {
                output = TemplateFallbackEngine().infer(input)
                if (status == ModelStatus.SUCCESS) status = ModelStatus.FALLBACK_TEMPLATE
            } else if (engine is TemplateFallbackEngine) {
                status = ModelStatus.FALLBACK_TEMPLATE
            }

            val finishedAt = System.currentTimeMillis()
            val draftRepo = DraftRepository(
                draftDao = db.draftMemoryDao(),
                confirmedDao = db.confirmedMemoryDao(),
                runLogDao = db.modelRunLogDao()
            )
            val runLogId = draftRepo.recordRun(
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = status,
                engineName = engine.engineName,
                inputSnapshotHash = inputHash,
                outputJson = ModelOutputJson.toJson(output),
                latencyMs = finishedAt - startedAt
            )

            // 仅本地模型 SUCCESS 且用户开启自动确认时，草案直接自动裁决；
            // 兜底模板/超时/错误一律进人工队列，并在主界面提示一次。
            val autoConfirm = settings.autoConfirmEnabled && status == ModelStatus.SUCCESS
            if (status != ModelStatus.SUCCESS) {
                ServiceLocator.settings().setFallbackNoticePending(true)
            }
            draftRepo.insertDrafts(runLogId, output, from, now, autoConfirm = autoConfirm)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun releaseEngine(engine: Any) {
        (engine as? GgufInferenceEngine)?.release()
    }

    companion object {
        const val WORK_NAME = "local_inference"
        private const val LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
        private const val TIMEOUT_MS = 60_000L
    }
}
