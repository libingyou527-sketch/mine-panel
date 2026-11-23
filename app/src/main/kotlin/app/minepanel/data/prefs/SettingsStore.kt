package app.minepanel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.minepanel.core.model.PluginType
import app.minepanel.core.security.KeyStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("minepanel_settings")

/** 设置快照；UI 与业务层只读取不可变快照。 */
data class SettingsSnapshot(
    val onboardingCompleted: Boolean = false,
    val recordingPaused: Boolean = false,
    val plugins: Map<PluginType, Boolean> = emptyMap(),
    val exportEnabled: Boolean = false,
    val clipboardCopyEnabled: Boolean = false,
    val autoConfirmEnabled: Boolean = true,
    val fallbackNoticePending: Boolean = false,
    val modelFilePath: String? = null,
    val modelHash: String? = null,
    val baselineStartEpochDay: Long? = null,
    val lastSyncEpochMs: Long? = null,
    val lastExportEpochMs: Long? = null,
    val lastImportMessage: String? = null,
    val lastImportIsError: Boolean = false,
    val lastImportAt: Long? = null,
    val currentChatSessionId: Long? = null,
    /** 是否启用外部（联网）大模型；关闭时一律走内置/模板降级。 */
    val remoteModelEnabled: Boolean = false,
    /** 预置厂商模板标识（custom/deepseek/qwen/kimi/zhipu/openrouter/openai），仅用于回显。 */
    val remoteProvider: String = "custom",
    /** OpenAI 兼容接口根地址（不含 /chat/completions）。 */
    val remoteBaseUrl: String = "",
    /** 远端模型名（如 deepseek-chat、qwen-plus）。 */
    val remoteModelName: String = "",
    /** API Key 的 Keystore 包装密文（Base64）；明文不进入快照与数据库。null 表示未设置。 */
    val remoteApiKeyCipher: String? = null,
    /** 地理围栏常去点 JSON 数组（label/lat/lng/radiusM）；不存连续轨迹。 */
    val geofencePlaces: String = "",
    /** 围栏状态：当前所在常去点 label；空表示处于所有围栏之外。 */
    val geofenceState: String = ""
)

class SettingsStore(
    private val context: Context,
    private val keyStore: KeyStoreManager? = null
) : SettingsAccess {

    private val ds = context.dataStore

    override val settings: Flow<SettingsSnapshot> = ds.data.map { p ->
        SettingsSnapshot(
            onboardingCompleted = p[KEY_ONBOARDING] ?: false,
            recordingPaused = p[KEY_PAUSED] ?: false,
            plugins = PluginType.entries.associateWith { type ->
                p[pluginKey(type)] ?: false
            },
            exportEnabled = p[KEY_EXPORT] ?: false,
            clipboardCopyEnabled = p[KEY_CLIPBOARD] ?: false,
            autoConfirmEnabled = p[KEY_AUTO_CONFIRM] ?: true,
            fallbackNoticePending = p[KEY_FALLBACK_NOTICE] ?: false,
            modelFilePath = p[KEY_MODEL_PATH],
            modelHash = p[KEY_MODEL_HASH],
            baselineStartEpochDay = p[KEY_BASELINE_START],
            lastSyncEpochMs = p[KEY_LAST_SYNC],
            lastExportEpochMs = p[KEY_LAST_EXPORT],
            lastImportMessage = p[KEY_IMPORT_MESSAGE],
            lastImportIsError = p[KEY_IMPORT_IS_ERROR] ?: false,
            lastImportAt = p[KEY_IMPORT_AT],
            currentChatSessionId = p[KEY_CURRENT_CHAT_SESSION],
            remoteModelEnabled = p[KEY_REMOTE_ENABLED] ?: false,
            remoteProvider = p[KEY_REMOTE_PROVIDER] ?: "custom",
            remoteBaseUrl = p[KEY_REMOTE_BASE_URL] ?: "",
            remoteModelName = p[KEY_REMOTE_MODEL_NAME] ?: "",
            remoteApiKeyCipher = p[KEY_REMOTE_KEY_CIPHER],
            geofencePlaces = p[KEY_GEOFENCE_PLACES] ?: "",
            geofenceState = p[KEY_GEOFENCE_STATE] ?: ""
        )
    }

    suspend fun markOnboardingCompleted() {
        ds.edit { it[KEY_ONBOARDING] = true }
    }

    suspend fun setRecordingPaused(paused: Boolean) {
        ds.edit { it[KEY_PAUSED] = paused }
    }

    suspend fun setPluginEnabled(type: PluginType, enabled: Boolean) {
        ds.edit { it[pluginKey(type)] = enabled }
    }

    suspend fun setExportEnabled(enabled: Boolean) {
        ds.edit { it[KEY_EXPORT] = enabled }
    }

    suspend fun setClipboardCopyEnabled(enabled: Boolean) {
        ds.edit { it[KEY_CLIPBOARD] = enabled }
    }

    override suspend fun setAutoConfirmEnabled(enabled: Boolean) {
        ds.edit { it[KEY_AUTO_CONFIRM] = enabled }
    }

    suspend fun setFallbackNoticePending(pending: Boolean) {
        ds.edit {
            if (pending) it[KEY_FALLBACK_NOTICE] = true else it.remove(KEY_FALLBACK_NOTICE)
        }
    }

    suspend fun setModelFile(path: String?, hash: String?) {
        ds.edit {
            if (path == null) it.remove(KEY_MODEL_PATH) else it[KEY_MODEL_PATH] = path
            if (hash == null) it.remove(KEY_MODEL_HASH) else it[KEY_MODEL_HASH] = hash
        }
    }

    suspend fun setBaselineStartEpochDay(epochDay: Long) {
        ds.edit { it[KEY_BASELINE_START] = epochDay }
    }

    suspend fun setLastSyncEpochMs(epochMs: Long) {
        ds.edit { it[KEY_LAST_SYNC] = epochMs }
    }

    suspend fun setLastExportEpochMs(epochMs: Long) {
        ds.edit { it[KEY_LAST_EXPORT] = epochMs }
    }

    /** 持久化最近一次导入结果（成功计数摘要或失败原因），供设置页长期留痕查看。 */
    suspend fun setLastImportResult(message: String, isError: Boolean, at: Long) {
        ds.edit {
            it[KEY_IMPORT_MESSAGE] = message
            it[KEY_IMPORT_IS_ERROR] = isError
            it[KEY_IMPORT_AT] = at
        }
    }

    /** 记录当前选中的聊天会话；传 null 清除标记。 */
    suspend fun setCurrentChatSessionId(id: Long?) {
        ds.edit { prefs ->
            if (id == null) prefs.remove(KEY_CURRENT_CHAT_SESSION) else prefs[KEY_CURRENT_CHAT_SESSION] = id
        }
    }

    /**
     * 保存外部（联网）大模型配置。API Key 明文只在内存中出现，落库前经 Keystore AES-GCM 包装；
     * [apiKeyPlain] 传 null 或空白表示清除已存 Key。其余字段为非敏感配置，直接存偏好。
     */
    suspend fun setRemoteModelConfig(
        enabled: Boolean,
        provider: String,
        baseUrl: String,
        modelName: String,
        apiKeyPlain: String?,
        preserveKey: Boolean = false
    ) {
        val key = apiKeyPlain?.trim().orEmpty()
        val cipher = when {
            preserveKey -> null // 保留已存密文，本次不触碰 Key 字段
            key.isEmpty() -> null
            else -> {
                val ks = keyStore ?: error("Keystore 未注入，无法加密外部模型 API Key")
                ks.wrapString(key)
            }
        }
        ds.edit { prefs ->
            prefs[KEY_REMOTE_ENABLED] = enabled
            prefs[KEY_REMOTE_PROVIDER] = provider
            prefs[KEY_REMOTE_BASE_URL] = baseUrl.trim()
            prefs[KEY_REMOTE_MODEL_NAME] = modelName.trim()
            when {
                preserveKey -> Unit
                cipher == null -> prefs.remove(KEY_REMOTE_KEY_CIPHER)
                else -> prefs[KEY_REMOTE_KEY_CIPHER] = cipher
            }
        }
    }

    /** 解开快照中的 API Key 密文；未设置或包装失效时返回 null，由上层降级内置模型。 */
    fun decryptRemoteApiKey(cipher: String?): String? {
        if (cipher.isNullOrBlank()) return null
        val ks = keyStore ?: return null
        return runCatching { ks.unwrapString(cipher) }.getOrNull()
    }

    /** 保存围栏常去点 JSON 数组；空串表示未配置任何地点。 */
    suspend fun setGeofencePlaces(placesJson: String) {
        ds.edit { it[KEY_GEOFENCE_PLACES] = placesJson }
    }

    /** 记录当前所在常去点；空串表示处于围栏之外。 */
    suspend fun setGeofenceState(placeLabel: String) {
        ds.edit {
            if (placeLabel.isBlank()) it.remove(KEY_GEOFENCE_STATE) else it[KEY_GEOFENCE_STATE] = placeLabel
        }
    }

    /** 清空全部本地设置键（删除全部数据时调用）。 */
    suspend fun clearAll() {
        ds.edit { it.clear() }
    }

    /** 删除全部数据时调用：清空开关与模型引用，保留 onboarding 标记由删除流程决定。 */
    suspend fun clearLocalReferences() {
        ds.edit { prefs ->
            PluginType.entries.forEach { prefs.remove(pluginKey(it)) }
            prefs.remove(KEY_MODEL_PATH)
            prefs.remove(KEY_MODEL_HASH)
            prefs.remove(KEY_BASELINE_START)
            prefs.remove(KEY_REMOTE_ENABLED)
            prefs.remove(KEY_REMOTE_PROVIDER)
            prefs.remove(KEY_REMOTE_BASE_URL)
            prefs.remove(KEY_REMOTE_MODEL_NAME)
            prefs.remove(KEY_REMOTE_KEY_CIPHER)
            prefs.remove(KEY_GEOFENCE_PLACES)
            prefs.remove(KEY_GEOFENCE_STATE)
        }
    }

    private fun pluginKey(type: PluginType) = booleanPreferencesKey("plugin_${type.name}")

    companion object {
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        private val KEY_PAUSED = booleanPreferencesKey("recording_paused")
        private val KEY_EXPORT = booleanPreferencesKey("export_enabled")
        private val KEY_CLIPBOARD = booleanPreferencesKey("clipboard_copy_enabled")
        private val KEY_AUTO_CONFIRM = booleanPreferencesKey("auto_confirm_enabled")
        private val KEY_FALLBACK_NOTICE = booleanPreferencesKey("fallback_notice_pending")
        private val KEY_MODEL_PATH = stringPreferencesKey("model_file_path")
        private val KEY_MODEL_HASH = stringPreferencesKey("model_file_hash")
        private val KEY_BASELINE_START = longPreferencesKey("baseline_start_epoch_day")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
        private val KEY_LAST_EXPORT = longPreferencesKey("last_export_epoch_ms")
        private val KEY_IMPORT_MESSAGE = stringPreferencesKey("last_import_message")
        private val KEY_IMPORT_IS_ERROR = booleanPreferencesKey("last_import_is_error")
        private val KEY_IMPORT_AT = longPreferencesKey("last_import_at_ms")
        private val KEY_CURRENT_CHAT_SESSION = longPreferencesKey("current_chat_session_id")
        private val KEY_REMOTE_ENABLED = booleanPreferencesKey("remote_model_enabled")
        private val KEY_REMOTE_PROVIDER = stringPreferencesKey("remote_model_provider")
        private val KEY_REMOTE_BASE_URL = stringPreferencesKey("remote_model_base_url")
        private val KEY_REMOTE_MODEL_NAME = stringPreferencesKey("remote_model_name")
        private val KEY_REMOTE_KEY_CIPHER = stringPreferencesKey("remote_model_key_cipher")
        private val KEY_GEOFENCE_PLACES = stringPreferencesKey("geofence_places")
        private val KEY_GEOFENCE_STATE = stringPreferencesKey("geofence_state")
    }
}
