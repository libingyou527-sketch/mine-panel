package app.minepanel

import android.app.Application
import app.minepanel.core.BundledModelBootstrapper
import app.minepanel.core.ServiceLocator
import app.minepanel.core.WorkScheduler

/**
 * 应用入口：加载 SQLCipher 原生库并初始化本地依赖容器。
 * 不启动任何网络组件；采集服务由主页按设置状态启动。
 */
class MinePanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        ServiceLocator.init(this)
        BundledModelBootstrapper.ensure(this)
        WorkScheduler.schedule(this)
    }
}
