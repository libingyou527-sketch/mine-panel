package app.minepanel.data.repository

import android.content.Context
import android.net.Uri
import app.minepanel.core.ServiceLocator
import app.minepanel.core.security.BackupCipher
import app.minepanel.core.security.BackupCryptoException
import app.minepanel.data.export.ImportConflictStrategy
import app.minepanel.data.export.ImportParser
import app.minepanel.data.export.JsonExporter

/**
 * 加密备份 / 恢复协调器。
 *
 * 与明文导出共用同一套可迁移数据组包、JSON 序列化与解析查重链路；差异只在外层：
 * 明文 JSON 载荷经 [BackupCipher] 用用户口令派生的密钥做 AES-GCM 加密后再写入 SAF 文件。
 *
 * 备份包只含已确认记忆、状态标签、规则分值、徽章、主动片段与（可选）原始使用事件；
 * 不含包名、联系人、通知正文或聊天内容，也不迁移库内 SQLCipher 口令与 Keystore 条目——
 * 新设备在本机生成自己的库密钥包装，业务数据解密后按查重策略入库。
 *
 * 全程本地完成，不联网、不写剪贴板；口令遗忘即不可恢复，无云端找回。
 */
class BackupRepository(private val context: Context) {

    sealed interface BackupResult {
        data class Success(
            val memoryCount: Int,
            val tagCount: Int,
            val scoreCount: Int,
            val badgeCount: Int,
            val rawCount: Int?,
            val fragmentCount: Int,
            val milestoneCount: Int
        ) : BackupResult

        data class Failure(val reason: String) : BackupResult
    }

    sealed interface RestoreResult {
        data class Success(val report: ImportReport) : RestoreResult
        data class Failure(val reason: String) : RestoreResult
    }

    /** 组装可迁移数据 → 序列化为 JSON → 口令加密 → 写入 SAF 选定文件。 */
    suspend fun backupTo(
        uri: Uri,
        password: String,
        includeRawEvents: Boolean
    ): BackupResult = runCatching {
        val bundle = ExportRepository(context).buildBundle(includeRawEvents)
        val payload = JsonExporter.toJson(bundle).toByteArray(Charsets.UTF_8)
        val blob = BackupCipher.encrypt(payload, password)

        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(blob)
            out.flush()
        } ?: throw IllegalStateException("无法写入所选文件")

        ServiceLocator.settings().setLastExportEpochMs(bundle.exportedAt)
        BackupResult.Success(
            memoryCount = bundle.confirmedMemories.size,
            tagCount = bundle.statusTags.size,
            scoreCount = bundle.attributeScores.size,
            badgeCount = bundle.badges.size,
            rawCount = bundle.rawEvents?.size,
            fragmentCount = bundle.manualFragments.size,
            milestoneCount = bundle.milestones.size
        )
    }.getOrElse {
        BackupResult.Failure(it.message ?: "加密备份失败")
    }

    /** 读取 SAF 选定文件 → 口令解密校验 → 解析 → 查重入库。 */
    suspend fun restoreFrom(uri: Uri, password: String): RestoreResult = runCatching {
        val blob = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw BackupCryptoException("无法读取所选文件")

        val parsed = decryptToParsed(blob, password)

        val db = ServiceLocator.database()
        val report = ImportRepository(context).persist(
            parsed = parsed,
            strategy = ImportConflictStrategy.SKIP_DUPLICATES,
            confirmedDao = db.confirmedMemoryDao(),
            draftDao = db.draftMemoryDao(),
            scoreDao = db.attributeScoreDao(),
            badgeDao = db.badgeDao(),
            rawDao = db.rawUsageEventDao(),
            fragmentDao = db.manualFragmentDao(),
            milestoneDao = db.milestoneDao()
        )
        RestoreResult.Success(report)
    }.getOrElse {
        RestoreResult.Failure(it.message ?: "恢复失败")
    }

    /** 解密备份字节并解析为待入库条目；口令错误/篡改/格式不符抛异常。查重入库复用导入仓储。 */
    internal fun decryptToParsed(blob: ByteArray, password: String) =
        ImportParser.parse(BackupCipher.decrypt(blob, password).toString(Charsets.UTF_8))
}
