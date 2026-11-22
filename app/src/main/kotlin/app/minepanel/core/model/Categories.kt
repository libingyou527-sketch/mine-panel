package app.minepanel.core.model

/** 粗类别中文展示名；UNMAPPED 在面板折叠为“其他”。 */
val EventCategory.label: String
    get() = when (this) {
        EventCategory.WORK -> "工作"
        EventCategory.SOCIAL -> "社交"
        EventCategory.MEDIA -> "影音"
        EventCategory.TOOL -> "工具"
        EventCategory.REST -> "休息"
        EventCategory.UNMAPPED -> "其他"
    }

/**
 * 内置包名 → 粗类别映射表。
 * 映射只在采集层内存中完成：原始包名不写入数据库、不进入面板、不进入模型输入。
 * 未命中的包名归 [EventCategory.UNMAPPED]，面板折叠为“其他”。
 */
object CategoryRules {

    private val socialPackages = setOf(
        "com.tencent.mm",          // 微信
        "com.tencent.mobileqq",    // QQ
        "com.tencent.tim",         // TIM
        "com.sina.weibo",          // 微博
        "com.immomo.momo"          // 陌陌
    )

    private val mediaPackages = setOf(
        "tv.danmaku.bili",         // B 站
        "com.ss.android.ugc.aweme", // 抖音
        "com.ss.android.ugc.aweme.lite",
        "com.kuaishou.nebula",     // 快手极速版
        "com.tencent.qqlive",      // 腾讯视频
        "com.netease.cloudmusic"   // 网易云音乐
    )

    private val workPackages = setOf(
        "com.alibaba.android.rimet",   // 钉钉
        "com.tencent.wework",          // 企业微信
        "cn.wps.moffice_eng",          // WPS
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint"
    )

    private val toolPackages = setOf(
        "com.android.chrome",
        "com.android.settings",
        "com.android.files"
    )

    fun categorize(packageName: String?): EventCategory {
        if (packageName.isNullOrBlank()) return EventCategory.UNMAPPED
        return when {
            packageName in socialPackages -> EventCategory.SOCIAL
            packageName in mediaPackages -> EventCategory.MEDIA
            packageName in workPackages -> EventCategory.WORK
            packageName in toolPackages -> EventCategory.TOOL
            else -> EventCategory.UNMAPPED
        }
    }
}
