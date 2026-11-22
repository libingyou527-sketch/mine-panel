package app.minepanel.core

import android.content.Context
import android.util.Log
import app.minepanel.core.security.KeyStoreManager
import app.minepanel.core.security.SecureStorageUnavailableException
import app.minepanel.data.db.AppDatabase
import app.minepanel.data.prefs.SettingsStore

/**
 * 手写轻量 DI 容器：进程内单例。阶段 1 持有数据库、设置存储与密钥包装器；
 * 采集/规则/模型等依赖在后续阶段在此登记。
 */
object ServiceLocator {

    private var database: AppDatabase? = null
    private var settingsStore: SettingsStore? = null
    private var keyStoreManager: KeyStoreManager? = null

    /** 安全存储失效：口令包装无法还原，旧库不可恢复；由 UI 提示用户确认后擦除。 */
    @Volatile
    private var storageInvalid: Boolean = false

    @Synchronized
    fun init(context: Context) {
        val appContext = context.applicationContext
        if (settingsStore == null) settingsStore = SettingsStore(appContext)
        if (keyStoreManager == null) keyStoreManager = KeyStoreManager(appContext)
        if (database == null && !storageInvalid) {
            try {
                val passphrase = keyStoreManager!!.getOrCreatePassphrase()
                database = AppDatabase.get(appContext, passphrase)
            } catch (e: SecureStorageUnavailableException) {
                Log.e("ServiceLocator", "secure storage invalid", e)
                storageInvalid = true
            }
        }
    }

    fun isStorageInvalid(): Boolean = storageInvalid

    fun database(): AppDatabase = database ?: error("ServiceLocator.init 未调用")

    fun settings(): SettingsStore = settingsStore ?: error("ServiceLocator.init 未调用")

    fun keyStore(): KeyStoreManager = keyStoreManager ?: error("ServiceLocator.init 未调用")

    /** 释放并清空单例引用；删除全部数据后调用，下次访问时按全新状态重建。 */
    @Synchronized
    fun reset() {
        database = null
        settingsStore = null
        keyStoreManager = null
        storageInvalid = false
    }
}
