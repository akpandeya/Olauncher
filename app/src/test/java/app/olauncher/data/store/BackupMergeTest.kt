package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupMergeTest {

    @Test
    fun keepsCurrentVolatileValuesAndImportsTheRest() {
        val current = mapOf<String, Any?>(
            "AUTO_BACKUP_ENABLED" to true,
            "AUTO_BACKUP_TREE_URI" to "content://tree/current",
            "THEME" to "dark",
        )
        val imported = mapOf<String, Any?>(
            "AUTO_BACKUP_ENABLED" to false,
            "THEME" to "light",
            "FONT" to 3,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(true, merged["AUTO_BACKUP_ENABLED"])
        assertEquals("content://tree/current", merged["AUTO_BACKUP_TREE_URI"])
        assertEquals("light", merged["THEME"])
        assertEquals(3, merged["FONT"])
    }

    @Test
    fun volatileKeyAbsentOnDeviceIsNotInvented() {
        val merged = Backup.mergeKeepingVolatile(emptyMap<String, Any?>(), mapOf("AUTO_BACKUP_ENABLED" to true, "A" to 1))
        assertFalse(merged.containsKey("AUTO_BACKUP_ENABLED"))
        assertEquals(1, merged["A"])
    }
}
