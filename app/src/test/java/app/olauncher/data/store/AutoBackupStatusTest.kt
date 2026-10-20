package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupStatusTest {
    @Test
    fun disabledIsHiddenEvenWithOldFailure() {
        assertEquals(AutoBackupStatus.HIDDEN, AutoBackupStatus.of(false, 5L, true))
    }

    @Test
    fun failureWinsOverLastOk() {
        assertEquals(AutoBackupStatus.FAILED, AutoBackupStatus.of(true, 5L, true))
    }

    @Test
    fun lastOkShownWhenSet() {
        assertEquals(AutoBackupStatus.LAST_OK, AutoBackupStatus.of(true, 5L, false))
    }

    @Test
    fun waitingBeforeFirstRun() {
        assertEquals(AutoBackupStatus.WAITING, AutoBackupStatus.of(true, 0L, false))
    }

    @Test
    fun autoBackupSettingsAreNotExported() {
        listOf(
            "AUTO_BACKUP_ENABLED",
            "AUTO_BACKUP_TREE_URI",
            "AUTO_BACKUP_LAST_OK",
            "AUTO_BACKUP_LAST_FAILED",
        ).forEach { assertTrue(it, Backup.isVolatileKey(it)) }
    }
}
