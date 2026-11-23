package app.minepanel.model

import android.content.Context
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.data.repository.ModelFileRepository
import java.io.File

/**
 * 引擎路由：模型文件校验通过、内存达标且原生库可用时选用内置 GGUF；
 * 任一条件不满足即回退模板降级。路由本身不联网。
 */
class EngineRouter(private val context: Context) {

    fun select(snapshot: SettingsSnapshot): InferenceEngine {
        val path = snapshot.modelFilePath?.takeIf { it.isNotBlank() }
            ?: ModelFileRepository(context).currentFile()?.absolutePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            val verified = ModelFileValidator.verify(file).valid
            val memoryOk = MemoryGate.canLoadModel(context)
            if (verified && memoryOk) {
                val engine = runCatching { GgufInferenceEngine(path) }.getOrNull()
                if (engine != null && engine.isLoaded) return engine
            }
        }
        return TemplateFallbackEngine()
    }
}
