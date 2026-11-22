package app.minepanel.data.prefs

import kotlinx.coroutines.flow.Flow

/**
 * 设置读取/写入的最小抽象，便于测试注入内存实现，不依赖 DataStore 与真库。
 * 生产实现为 [SettingsStore]；本期队列视图只用到自动确认开关的读写。
 */
interface SettingsAccess {

    val settings: Flow<SettingsSnapshot>

    suspend fun setAutoConfirmEnabled(enabled: Boolean)
}
