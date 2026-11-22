package app.minepanel.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test

class StorageInvalidScreenTest {

    @Test
    fun nullBackupShowsNoBackupNotice() {
        val notice = backupNotice(null)
        assertTrue(notice.startsWith("未找到本地备份"))
    }

    @Test
    fun nonPositiveBackupTreatedAsNoBackup() {
        assertTrue(backupNotice(0).startsWith("未找到本地备份"))
        assertTrue(backupNotice(-1L).startsWith("未找到本地备份"))
    }

    @Test
    fun validBackupReferencesReimport() {
        val notice = backupNotice(1795327614632L)
        assertTrue(notice.startsWith("上次本地备份"))
        assertTrue(notice.contains("重新导入"))
    }

    @Test
    fun formatBackupTimeUsesDateMinutePattern() {
        val formatted = formatBackupTime(1795327614632L)
        assertTrue(matchesDateMinute(formatted))
    }

    private fun matchesDateMinute(value: String): Boolean =
        Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(value)
}
