package app.olauncher.data.store

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupCoverageTest {

    /** The stored API key file (issues #599, 9.6): excluded from every backup. */
    private val AI_KEY_FILE = "no_backup/ai_key.bin"

    @Test
    fun everyUserDataFileIsExported() {
        assertEquals(
            listOf(
                "saved_items.json",
                "word_progress.json",
                "words.json",
                "usage_days.json",
                "intention_history.json",
                "review_events.json",
                "practice_items.json"
            ),
            Backup.EXPORTED_FILES
        )
        assertEquals(
            setOf(
                Backup.FILE_SAVED_ITEMS,
                Backup.FILE_WORD_PROGRESS,
                Backup.FILE_WORDS,
                Backup.FILE_USAGE_DAYS,
                Backup.FILE_INTENTION_HISTORY,
                Backup.FILE_REVIEW_EVENTS,
                Backup.FILE_PRACTICE_ITEMS
            ),
            Backup.EXPORTED_FILES.toSet()
        )
    }

    @Test
    fun backupRulesIncludeEveryExportedFile() {
        val backupRulesDoc = parseXml(resolveFile("src/main/res/xml/backup_rules.xml"))
        val dataExtractionDoc = parseXml(resolveFile("src/main/res/xml/data_extraction_rules.xml"))

        val fullBackupEl = backupRulesDoc.documentElement
        assertEquals("full-backup-content", fullBackupEl.tagName)

        val cloudBackupEl = dataExtractionDoc.getElementsByTagName("cloud-backup").item(0) as? Element
            ?: error("cloud-backup section not found in data_extraction_rules.xml")
        val deviceTransferEl = dataExtractionDoc.getElementsByTagName("device-transfer").item(0) as? Element
            ?: error("device-transfer section not found in data_extraction_rules.xml")

        val sections = mapOf(
            "backup_rules.xml <full-backup-content>" to extractIncludedFiles(fullBackupEl),
            "data_extraction_rules.xml <cloud-backup>" to extractIncludedFiles(cloudBackupEl),
            "data_extraction_rules.xml <device-transfer>" to extractIncludedFiles(deviceTransferEl),
        )

        for ((sectionName, includedFiles) in sections) {
            for (exportedFile in Backup.EXPORTED_FILES) {
                assertTrue(
                    "Section '$sectionName' must include '$exportedFile'",
                    includedFiles.contains(exportedFile)
                )
            }
        }
    }

    @Test
    fun cachesAreNotExported() {
        listOf("weather.json", "rates.json", "held.json", "wiktionary_cache.json").forEach {
            assertFalse(it, it in Backup.EXPORTED_FILES)
        }
    }

    @Test
    fun theAiKeyFileIsExcludedFromEveryBackupSection() {
        val backupRulesDoc = parseXml(resolveFile("src/main/res/xml/backup_rules.xml"))
        val dataExtractionDoc = parseXml(resolveFile("src/main/res/xml/data_extraction_rules.xml"))

        val cloudBackupEl = dataExtractionDoc.getElementsByTagName("cloud-backup").item(0) as? Element
            ?: error("cloud-backup section not found in data_extraction_rules.xml")
        val deviceTransferEl = dataExtractionDoc.getElementsByTagName("device-transfer").item(0) as? Element
            ?: error("device-transfer section not found in data_extraction_rules.xml")

        val sections = mapOf(
            "backup_rules.xml <full-backup-content>" to backupRulesDoc.documentElement,
            "data_extraction_rules.xml <cloud-backup>" to cloudBackupEl,
            "data_extraction_rules.xml <device-transfer>" to deviceTransferEl,
        )

        for ((sectionName, section) in sections) {
            assertTrue(
                "Section '$sectionName' must exclude '$AI_KEY_FILE'",
                extractRootExcludes(section).contains(AI_KEY_FILE),
            )
        }
    }

    @Test
    fun noIncludeSectionMentionsTheAiKey() {
        val backupRulesDoc = parseXml(resolveFile("src/main/res/xml/backup_rules.xml"))
        val dataExtractionDoc = parseXml(resolveFile("src/main/res/xml/data_extraction_rules.xml"))

        listOf(backupRulesDoc.documentElement, dataExtractionDoc.documentElement).forEach { root ->
            val includes = root.getElementsByTagName("include")
            for (i in 0 until includes.length) {
                val path = (includes.item(i) as Element).getAttribute("path")
                assertFalse(
                    "No include may mention the key file, found '$path'",
                    path.contains("ai_key"),
                )
            }
        }
    }

    @Test
    fun theAiKeyFileIsNeverExported() {
        assertTrue(
            Backup.EXPORTED_FILES.toString(),
            Backup.EXPORTED_FILES.none { it.contains("ai_key") },
        )
    }

    @Test
    fun widgetKeysNeverLeaveTheDevice() {
        // Widget instance IDs and heights are meaningless on another device or after a
        // reinstall: restoring them binds dead IDs ("This widget is unavailable").
        listOf("TODAY_WIDGET_IDS", "TODAY_PENDING_WIDGET_ID", "TODAY_WIDGET_HEIGHTS").forEach {
            assertTrue(it, Backup.isVolatileKey(it))
        }
    }

    @Test
    fun restoringKeepsTheDevicesOwnWidgetState() {
        val current = mapOf<String, Any?>(
            "TODAY_WIDGET_IDS" to "2,7",
            "TODAY_WIDGET_HEIGHTS" to "2:496",
            "TODAY_PENDING_WIDGET_ID" to -1,
        )
        val imported = mapOf<String, Any?>(
            "TODAY_WIDGET_IDS" to "3,9",
            "THEME" to "dark",
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals("2,7", merged["TODAY_WIDGET_IDS"])
        assertEquals("2:496", merged["TODAY_WIDGET_HEIGHTS"])
        assertEquals(-1, merged["TODAY_PENDING_WIDGET_ID"])
        assertEquals("dark", merged["THEME"])
    }

    private fun extractIncludedFiles(section: Element): Set<String> {
        val includes = section.getElementsByTagName("include")
        val files = mutableSetOf<String>()
        for (i in 0 until includes.length) {
            val el = includes.item(i) as Element
            if (el.getAttribute("domain") == "file") {
                files.add(el.getAttribute("path"))
            }
        }
        return files
    }

    private fun extractRootExcludes(section: Element): Set<String> {
        val excludes = section.getElementsByTagName("exclude")
        val paths = mutableSetOf<String>()
        for (i in 0 until excludes.length) {
            val el = excludes.item(i) as Element
            if (el.getAttribute("domain") == "root") {
                paths.add(el.getAttribute("path"))
            }
        }
        return paths
    }

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("File $relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
}
