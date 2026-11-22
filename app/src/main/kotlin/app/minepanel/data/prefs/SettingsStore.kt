package app.minepanel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.minepanel.core.model.PluginType
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
    val currentChatSessionId: Long? = null
)

class SettingsStore(private val context: Context) : SettingsAccess {

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
            currentChatSessionId = p[KEY_CURRENT_CHAT_SESSION]
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
    }
}
