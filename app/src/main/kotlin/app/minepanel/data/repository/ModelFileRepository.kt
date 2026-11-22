package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.model.ModelFileValidator
import java.io.File
import java.io.FileOutputStream

/**
 * 本地 GGUF 模型文件导入与清理。
 *
 * SAF 选取的 uri 长期权限不可靠，因此先流式复制到私有目录临时文件，
 * 校验通过（体积 + SHA-256 基线）后才落定；校验失败或读取异常立即删除临时文件，
 * 不在私有目录残留未通过校验的大文件。模型文件不进 APK、不下载、不上传。
 */
class ModelFileRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, MODELS_DIR)
    private val targetFile: File get() = File(dir, MODEL_FILE_NAME)
    private val tempFile: File get() = File(dir, TEMP_FILE_NAME)

    sealed interface ImportResult {
        data class Success(val hash: String) : ImportResult
        data class Rejected(val reason: String, val hash: String?) : ImportResult
    }

    /** 当前已落定的模型文件；不存在时返回 null。 */
    fun currentFile(): File? = targetFile.takeIf { it.exists() && it.isFile }

    /** 从 SAF uri 复制并校验；成功写入设置，失败清理临时文件。 */
    suspend fun importFrom(uri: Uri): ImportResult {
        dir.mkdirs()
        tempFile.delete()
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件" }
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            val result = ModelFileValidator.verify(tempFile)
            if (result.valid) {
                if (targetFile.exists()) targetFile.delete()
                val moved = tempFile.renameTo(targetFile)
                if (!moved) {
                    tempFile.delete()
                    throw IllegalStateException("模型文件落定失败")
                }
                ServiceLocator.settings().setModelFile(targetFile.absolutePath, result.hash)
                ImportResult.Success(result.hash ?: "")
            } else {
                tempFile.delete()
                ImportResult.Rejected(result.reason, result.hash)
            }
        }.getOrElse {
            tempFile.delete()
            ImportResult.Rejected(it.message ?: "读取文件失败", null)
        }
    }

    /** 删除私有目录内的模型文件并清除设置引用。 */
    suspend fun clear() {
        targetFile.delete()
        tempFile.delete()
        ServiceLocator.settings().setModelFile(null, null)
    }

    /**
     * 确保随包内置模型已落定到私有目录：副本存在且基线校验通过则直接就绪；
     * 否则从 assets 流式拷贝到临时文件，校验体积与 SHA-256 基线通过后落定并写入设置。
     * 拷贝与校验在后台线程执行；任一步失败即清理临时文件并返回 false（推理回退模板）。
     */
    suspend fun ensureBundled(): Boolean {
        currentFile()?.let { existing ->
            if (ModelFileValidator.verify(existing).valid) return true
        }
        return runCatching {
            dir.mkdirs()
            tempFile.delete()
            context.assets.open(ASSET_MODEL_PATH).use { input ->
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            val result = ModelFileValidator.verify(tempFile)
            if (result.valid) {
                if (targetFile.exists()) targetFile.delete()
                check(tempFile.renameTo(targetFile)) { "内置模型落定失败" }
                ServiceLocator.settings().setModelFile(targetFile.absolutePath, result.hash)
                true
            } else {
                tempFile.delete()
                false
            }
        }.getOrElse {
            tempFile.delete()
            false
        }
    }

    /** 擦除全部数据时调用：删除模型目录下所有文件。 */
    fun wipeModelDirectory() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val MODELS_DIR = "models"
        private const val MODEL_FILE_NAME = "local-model.gguf"
        private const val TEMP_FILE_NAME = "local-model.gguf.tmp"

        /** 随包内置模型在 assets 中的路径。 */
        const val ASSET_MODEL_PATH = "models/bundled-model.gguf"
    }
}
