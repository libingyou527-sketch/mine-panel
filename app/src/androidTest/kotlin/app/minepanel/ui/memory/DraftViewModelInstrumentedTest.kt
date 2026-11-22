package app.minepanel.ui.memory

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.minepanel.core.model.DraftStatus
import app.minepanel.core.model.DraftType
import app.minepanel.data.db.AppDatabase
import app.minepanel.data.db.entity.DraftMemory
import app.minepanel.data.prefs.SettingsAccess
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.data.repository.DraftRepository
import app.minepanel.data.repository.ManualFragmentRepository
import app.minepanel.data.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 内存库贯通 DAO → 仓储 → ViewModel 的 instrumented 测试；不触碰真库、Keystore、DataStore 与 WorkManager。 */
@RunWith(AndroidJUnit4::class)
class DraftViewModelInstrumentedTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var fakeSettings: FakeSettingsAccess
    private lateinit var vm: DraftViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.inMemory(app)
        fakeSettings = FakeSettingsAccess()
        vm = DraftViewModel(
            app,
            DraftRepository(
                draftDao = db.draftMemoryDao(),
                confirmedDao = db.confirmedMemoryDao(),
                runLogDao = db.modelRunLogDao()
            ),
            ManualFragmentRepository(db.manualFragmentDao()),
            MilestoneRepository(db.milestoneDao()),
            fakeSettings
        )
        await { !vm.state.value.loading }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // —— 里程碑 ——

    @Test
    fun addMilestoneAppearsInStateWithNormalizedLabelAndNote() {
        vm.addMilestone("  坚持满一周  ", " 习惯 ", " 首周达成 ")

        await { vm.state.value.milestones.any { it.title == "坚持满一周" } }
        val milestone = vm.state.value.milestones.first { it.title == "坚持满一周" }
        assertEquals("习惯", milestone.label)
        assertEquals("首周达成", milestone.note)
    }

    @Test
    fun duplicateMilestoneTitleIsSkipped() {
        vm.addMilestone("重复里程碑", null, null)
        await { vm.state.value.milestones.size == 1 }

        vm.addMilestone("重复里程碑", "另一个标签", "另一条说明")
        Thread.sleep(150)

        assertEquals(1, vm.state.value.milestones.size)
        assertNull(vm.state.value.milestones.first().label)
    }

    @Test
    fun deleteMilestoneRemovesItFromState() {
        vm.addMilestone("待删除里程碑", null, null)
        await { vm.state.value.milestones.isNotEmpty() }
        val id = vm.state.value.milestones.first().id

        vm.deleteMilestone(id)
        await { vm.state.value.milestones.isEmpty() }
    }

    // —— 手写片段 ——

    @Test
    fun addFragmentAppearsAndDuplicateContentIsSkipped() {
        vm.addFragment("片段甲", null, null)
        await { vm.state.value.fragments.any { it.content == "片段甲" } }

        vm.addFragment("片段甲", "心情", null)
        Thread.sleep(150)

        assertEquals(1, vm.state.value.fragments.size)
        assertNull(vm.state.value.fragments.first().mood)
    }

    @Test
    fun deleteFragmentRemovesItFromState() {
        vm.addFragment("待删除片段", null, null)
        await { vm.state.value.fragments.isNotEmpty() }
        val id = vm.state.value.fragments.first().id

        vm.deleteFragment(id)
        await { vm.state.value.fragments.isEmpty() }
    }

    // —— 草案状态机 ——

    @Test
    fun confirmMovesPendingDraftIntoConfirmedMemories() {
        val id = insertPendingDraft("待确认原文")

        await { vm.state.value.pending.any { it.id == id } }
        vm.confirm(id)

        await { vm.state.value.pending.none { it.id == id } }
        assertTrue(vm.state.value.confirmed.any { it.content == "待确认原文" })
    }

    @Test
    fun rejectDraftDoesNotWriteConfirmedMemory() {
        val id = insertPendingDraft("被丢弃草案")

        await { vm.state.value.pending.any { it.id == id } }
        vm.reject(id)

        await { vm.state.value.pending.none { it.id == id } }
        assertTrue(vm.state.value.confirmed.isEmpty())
    }

    @Test
    fun modifyConfirmsDraftWithEditedText() {
        val id = insertPendingDraft("原始草案")

        await { vm.state.value.pending.any { it.id == id } }
        vm.modify(id, "修改后内容")

        await { vm.state.value.pending.none { it.id == id } }
        assertTrue(vm.state.value.confirmed.any { it.content == "修改后内容" })
        assertFalse(vm.state.value.confirmed.any { it.content == "原始草案" })
    }

    // —— 自动确认开关 ——

    @Test
    fun autoConfirmDefaultsOnAndToggleWritesThroughToSettings() {
        assertTrue(vm.state.value.autoConfirmEnabled)

        vm.toggleAutoConfirm(false)
        await { !vm.state.value.autoConfirmEnabled }
        assertFalse(fakeSettings.flow.value.autoConfirmEnabled)

        vm.toggleAutoConfirm(true)
        await { vm.state.value.autoConfirmEnabled }

        assertTrue(fakeSettings.flow.value.autoConfirmEnabled)
    }

    private fun insertPendingDraft(content: String): Long {
        val now = System.currentTimeMillis()
        val id = runBlocking {
            db.draftMemoryDao().insert(
                DraftMemory(
                    modelRunId = null,
                    draftType = DraftType.MEMORY.name,
                    contentJson = content,
                    status = DraftStatus.PENDING.name,
                    userEditedContent = null,
                    createdAt = now,
                    reviewedAt = null
                )
            )
        }
        vm.refresh()
        return id
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("condition timed out", condition())
    }

    private class FakeSettingsAccess : SettingsAccess {
        val flow = MutableStateFlow(SettingsSnapshot())
        override val settings: Flow<SettingsSnapshot> = flow.asStateFlow()

        override suspend fun setAutoConfirmEnabled(enabled: Boolean) {
            flow.value = flow.value.copy(autoConfirmEnabled = enabled)
        }
    }
}
