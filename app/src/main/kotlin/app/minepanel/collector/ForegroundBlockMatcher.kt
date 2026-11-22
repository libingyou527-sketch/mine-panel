package app.minepanel.collector

/**
 * 配对后的前台块。
 * [packageName] 只存在于内存：落库前映射为粗类别，包名本身不写入数据库。
 */
data class ForegroundBlock(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
)

/**
 * 前后台配对器：UsageStatsManager 只给事件时间戳，不直接给使用时长，
 * 因此由 RESUMED 开块、PAUSED / 灭屏闭合块，两两配对计算时长。
 *
 * 规则：
 * - RESUMED：若已有未闭合块，按异常切换在本次 RESUMED 时间闭合前一块，再开新块；
 * - PAUSED：仅在包名与当前块一致时闭合，错配的 PAUSED 忽略；
 * - SCREEN_NON_INTERACTIVE（灭屏）：闭合当前块；
 * - 流末尾仍残留的块，用最后已知时间闭合。
 */
class ForegroundBlockMatcher {

    private var current: OpenBlock? = null
    private val closed = mutableListOf<ForegroundBlock>()

    private data class OpenBlock(val packageName: String, val startMs: Long)

    fun resume(packageName: String, timestamp: Long) {
        current?.let { closeBlock(it.packageName, it.startMs, timestamp) }
        current = OpenBlock(packageName, timestamp)
    }

    fun pause(packageName: String, timestamp: Long) {
        val block = current ?: return
        if (block.packageName == packageName) {
            closeBlock(block.packageName, block.startMs, timestamp)
            current = null
        }
    }

    fun screenOff(timestamp: Long) {
        current?.let {
            closeBlock(it.packageName, it.startMs, timestamp)
            current = null
        }
    }

    /** 事件流结束：用最后已知时间闭合残留块，返回全部闭合块。 */
    fun finish(lastTimestamp: Long): List<ForegroundBlock> {
        current?.let {
            closeBlock(it.packageName, it.startMs, lastTimestamp)
            current = null
        }
        return closed.toList()
    }

    private fun closeBlock(packageName: String, startMs: Long, endMs: Long) {
        if (endMs > startMs) {
            closed += ForegroundBlock(packageName, startMs, endMs, endMs - startMs)
        }
    }
}
