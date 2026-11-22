package app.minepanel.data.repository

import android.content.Context
import app.minepanel.core.ServiceLocator
import app.minepanel.data.db.AppDatabase

/**
 * 「删除全部数据」协调器：物理销毁本地数据。
 * 顺序：关闭并删除加密数据库文件 → 清空 DataStore → 撤销并重置 Keystore 密钥 → 释放单例引用。
 * 执行后应重启应用回到首次引导；本仓储不做任何网络上报。
 */
class WipeRepository(private val context: Context) {

    suspend fun wipeAll() {
        val settings = ServiceLocator.settings()

        // 1. 关闭连接并删除数据库文件（db / wal / shm）
        AppDatabase.closeAndReset(context)

        // 2. 清空全部设置与本地引用（含模型路径/哈希引用）
        settings.clearAll()

        // 2.1 删除私有目录内的本地模型文件
        ModelFileRepository(context).wipeModelDirectory()

        // 3. 撤销旧 Keystore 密钥并删除口令包装文件；下次启动生成全新密钥
        ServiceLocator.keyStore().reset()

        // 4. 释放内存中的单例，下次访问按全新状态重建
        ServiceLocator.reset()
    }
}
