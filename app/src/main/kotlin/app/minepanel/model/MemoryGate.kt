package app.minepanel.model

import android.app.ActivityManager
import android.content.Context

/**
 * 内存门槛：0.5B 量化模型加载 + 上下文与运行时约需 1–2GB  RAM。
 * 可用内存低于门槛时不加载模型，直接走模板降级，避免 OOM 闪退。
 */
object MemoryGate {

    /** 最低可用内存门槛：2.5GB（保守取值）。 */
    const val MIN_AVAIL_MEM_BYTES = 2_500L * 1024 * 1024

    fun enoughMemory(availMemBytes: Long): Boolean = availMemBytes >= MIN_AVAIL_MEM_BYTES

    fun canLoadModel(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return enoughMemory(info.availMem)
    }
}
