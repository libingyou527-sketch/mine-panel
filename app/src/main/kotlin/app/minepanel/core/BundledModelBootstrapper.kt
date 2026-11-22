package app.minepanel.core

import android.content.Context
import app.minepanel.data.repository.ModelFileRepository
import kotlinx.coroutines.runBlocking

/**
 * 应用启动后在后台准备随包内置模型：首次运行把 assets 中的 GGUF 拷贝到私有目录并校验落定。
 * 不阻塞主线程；未就绪前推理自动回退模板。进程内幂等，已就绪则直接跳过。
 */
object BundledModelBootstrapper {

    @Volatile
    private var started = false

    fun ensure(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        Thread {
            runCatching {
                runBlocking { ModelFileRepository(appContext).ensureBundled() }
            }
        }.start()
    }
}
