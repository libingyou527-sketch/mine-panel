package app.minepanel.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.core.security.BackupCipher
import app.minepanel.data.db.AppDatabase
import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.data.db.entity.Badge
import app.minepanel.data.db.entity.ConfirmedMemory
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.db.entity.ManualFragment
import app.minepanel.data.db.entity.Milestone
import app.minepanel.data.export.ImportConflictStrategy
import app.minepanel.data.export.ImportParser
import app.minepanel.data.export.JsonExporter
import app.minepanel.data.repository.ExportRepository
import app.minepanel.data.repository.ImportRepository
import app.minepanel.data.repository.WipeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.Charset

/**
 * 本机「备份 → 擦除 → 恢复」往返验证（破坏性 instrumented 测试）。
 *
 * 流程：读基线 → 加密备份到 app 外部私有目录 → 擦除前预解密核对 → 物理擦除 → 重建空库 →
 * 凭同一口令解密恢复 → 读回计数与基线逐节比对。不经过 SAF 文件选择与 WorkManager。
 *
 * 注意：[WipeRepository.wipeAll] 会删除业务数据、本地模型文件、DataStore 与 Keystore 包装；
 * 备份包不包含模型文件，恢复后需重新选择 GGUF。本测试只保留业务数据的还原校验。
 *
 * 本用例为破坏性测试，默认 `@Ignore`，常规全量上机不会擦除真机数据；仅在需要复核往返时，
 * 移除下方方法上的 @Ignore，再用 `-Pandroid.testInstrumentationRunnerArguments.class=...` 单跑。
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripInstrumentedTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val backupDir get() = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    private val utf8 = Charset.forName("UTF-8")

    private data class Baseline(
        val memories: Int,
        val tags: Int,
        val scores: Int,
        val badges: Int,
        val fragments: Int,
        val milestones: Int
    )

    private suspend fun readBaseline(): Baseline {
        val db = ServiceLocator.database()
        return Baseline(
            memories = db.confirmedMemoryDao().all().size,
            tags = db.draftMemoryDao().byTypeAndStatuses(
                DraftType.STATUS_TAG.name,
                listOf(DraftStatus.CONFIRMED.name, DraftStatus.MODIFIED.name)
            ).size,
            scores = db.attributeScoreDao().all().size,
            badges = db.badgeDao().all().size,
            fragments = db.manualFragmentDao().all().size,
            milestones = db.milestoneDao().all().size
        )
    }

    /** 写入一套固定时间戳的种子数据，保证往返非空、可逐条核对内容还原。 */
    private suspend fun insertSeed(db: AppDatabase) {
        val now = 1_700_000_000_000L
        db.confirmedMemoryDao().insert(
            ConfirmedMemory(
                originDraftId = null,
                content = "种子记忆：完成首次回顾",
                periodStart = now,
                periodEnd = now,
                createdAt = now
            )
        )
        db.draftMemoryDao().insert(
            DraftMemory(
                modelRunId = null,
                draftType = DraftType.STATUS_TAG.name,
                contentJson = "作息规律",
                status = DraftStatus.CONFIRMED.name,
                userEditedContent = null,
                createdAt = now,
                reviewedAt = now
            )
        )
        db.attributeScoreDao().upsert(
            AttributeScore(
                attributeKey = "FOCUS",
                periodStart = now,
                periodEnd = now,
                score = 72,
                confidence = 0.8,
                evidenceJson = "{}",
                sampleDays = 3,
                updatedAt = now
            )
        )
        db.badgeDao().upsert(
            Badge(badgeKey = "seed_first", unlockedAt = now, conditionSnapshotJson = "{}")
        )
        db.manualFragmentDao().insert(
            ManualFragment(
                content = "种子片段：晚间复盘",
                mood = "平静",
                category = "WORK",
                anchorEpochMs = null,
                createdAt = now,
                updatedAt = now
            )
        )
        db.milestoneDao().insert(
            Milestone(
                title = "种子里程碑",
                label = "习惯",
                note = "首周达成",
                anchorEpochMs = null,
                linkedFragmentId = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Ignore("破坏性擦除真机数据，仅手动复核往返时移除此注解")
    @Test
    fun backupWipeRestoreRoundTrip() = runBlocking {
        val backupFile = File(backupDir, "roundtrip_test.mpbb")
        val fallbackFile = File(backupDir, "roundtrip_fallback.json")
        val password = "roundtrip42"

        // 0. 空库先种入一套基线数据
        insertSeed(ServiceLocator.database())

        // 1. 擦除前基线（当前真库）
        val base = readBaseline()

        // 2. 明文兜底导出（独立于加密备份，额外保险）
        val exportRepo = ExportRepository(ctx)
        val fallbackBundle = exportRepo.buildBundle(includeRawEvents = false)
        fallbackFile.writeText(JsonExporter.toJson(fallbackBundle), utf8)
        assertTrue("兜底文件已写入", fallbackFile.exists() && fallbackFile.length() > 0)

        // 3. 组包 → 加密 → 写备份文件
        val bundle = exportRepo.buildBundle(includeRawEvents = false)
        val payload = JsonExporter.toJson(bundle).toByteArray(utf8)
        val blob = BackupCipher.encrypt(payload, password)
        backupFile.writeBytes(blob)
        assertTrue("备份文件已写入", backupFile.exists() && backupFile.length() > 0)
        assertEquals(base.memories, bundle.confirmedMemories.size)
        assertEquals(base.tags, bundle.statusTags.size)
        assertEquals(base.scores, bundle.attributeScores.size)
        assertEquals(base.badges, bundle.badges.size)
        assertEquals(base.fragments, bundle.manualFragments.size)
        assertEquals(base.milestones, bundle.milestones.size)

        // 4. 擦除前预解密：口令可解开，各节数量与基线一致（擦除前双保险）
        val parsedBeforeWipe = ImportParser.parse(
            BackupCipher.decrypt(backupFile.readBytes(), password).toString(utf8)
        )
        assertEquals(base.memories, parsedBeforeWipe.memories.size)
        assertEquals(base.tags, parsedBeforeWipe.tags.size)
        assertEquals(base.scores, parsedBeforeWipe.scores.size)
        assertEquals(base.badges, parsedBeforeWipe.badges.size)
        assertEquals(base.fragments, parsedBeforeWipe.fragments.size)
        assertEquals(base.milestones, parsedBeforeWipe.milestones.size)

        // 5. 物理擦除全部本地数据（库 / 设置 / 模型 / Keystore 包装）
        WipeRepository(ctx).wipeAll()

        // 6. 重建空库与全新 Keystore 包装
        ServiceLocator.init(ctx)

        // 7. 凭同一口令解密并查重入库到新库（空库无重复，应全部 imported）
        val parsed = ImportParser.parse(
            BackupCipher.decrypt(backupFile.readBytes(), password).toString(utf8)
        )
        val db = ServiceLocator.database()
        val report = ImportRepository(ctx).persist(
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
        assertEquals(base.memories, report.memoriesImported)
        assertEquals(base.tags, report.tagsImported)
        assertEquals(base.scores, report.scoresImported)
        assertEquals(base.badges, report.badgesImported)
        assertEquals(base.fragments, report.fragmentsImported)
        assertEquals(base.milestones, report.milestonesImported)

        // 8. 新库读回计数与基线逐节一致
        val restored = readBaseline()
        assertEquals(base, restored)

        // 9. 抽查关键内容确实还原（非仅计数相等）
        val dbRestored = ServiceLocator.database()
        assertTrue(dbRestored.confirmedMemoryDao().all().any { it.content == "种子记忆：完成首次回顾" })
        assertTrue(dbRestored.manualFragmentDao().all().any { it.content == "种子片段：晚间复盘" && it.mood == "平静" })
        assertTrue(dbRestored.milestoneDao().all().any { it.title == "种子里程碑" && it.label == "习惯" && it.note == "首周达成" })
        assertTrue(dbRestored.badgeDao().all().any { it.badgeKey == "seed_first" })
    }
}
