package app.minepanel.model

import app.minepanel.core.model.ModelStatus

/**
 * 本地 GGUF 推理引擎封装（JNI）。
 * 原生库不可用、模型校验失败、内存不足或推理抛 OOM 时，由上层路由卸载并回退模板。
 * 模型文件只在私有目录本地读取，不联网、不上传。
 */
class GgufInferenceEngine(private val modelPath: String) : InferenceEngine {

    override val engineName: String = "gguf"

    private var handle: Long = 0L
    val isLoaded: Boolean

    init {
        val loadResult = runCatching {
            System.loadLibrary("minepanel-llama")
            handle = nativeInit(modelPath)
            handle != 0L
        }
        isLoaded = loadResult.getOrDefault(false)
        if (!isLoaded) release()
    }

    override suspend fun infer(input: ModelInput): ModelOutput {
        check(isLoaded) { "原生推理库不可用" }
        val prompt = PromptBuilder.buildChatML(input)
        // OOM 由调用方捕获；此处失败时释放原生上下文。
        val raw = try {
            nativeGenerate(handle, prompt, MAX_TOKENS)
        } catch (oom: OutOfMemoryError) {
            release()
            throw oom
        }
        val parsed = ModelOutputParser.parse(raw)
            ?: throw IllegalStateException("模型输出未通过 schema 校验")
        return ModelOutput(
            panelNarrative = parsed.narrative,
            statusTags = parsed.tags,
            draftMemories = parsed.memories,
            status = ModelStatus.SUCCESS,
            engineName = engineName
        )
    }

    /** 自由文本聊天：直接透传拼好的 ChatML，返回原始回复，不经 JSON schema 校验。 */
    override suspend fun chat(prompt: String): String {
        check(isLoaded) { "原生推理库不可用" }
        val raw = try {
            nativeGenerate(handle, prompt, MAX_CHAT_TOKENS)
        } catch (oom: OutOfMemoryError) {
            release()
            throw oom
        }
        return raw.trim()
    }

    fun release() {
        if (handle != 0L) {
            runCatching { nativeRelease(handle) }
            handle = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeRelease(handle: Long)

    companion object {
        private const val MAX_TOKENS = 128
        private const val MAX_CHAT_TOKENS = 256
    }
}
