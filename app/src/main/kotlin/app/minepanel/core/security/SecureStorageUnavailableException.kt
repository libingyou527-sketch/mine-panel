package app.minepanel.core.security

/**
 * 安全存储失效：系统密钥库中的私钥无法还原已保存的数据库口令
 * （密钥条目被清除、包装损坏或格式不兼容）。此时旧加密库不可恢复，
 * 不应静默重建口令；由 UI 提示用户确认后再物理擦除本地数据。
 */
class SecureStorageUnavailableException(
    message: String,
    cause: Throwable?
) : RuntimeException(message, cause)
