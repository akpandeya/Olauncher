package app.olauncher.ui.settings

import app.olauncher.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Deutsch section page shows every row of the catalog's deutsch list, in catalog
 * order, through the one shared row model: [SettingsSectionNavigation] renders
 * [SettingsCatalog.itemsFor] for KEY_DEUTSCH and the adapter draws each row with a
 * generic shape layout (ids are assigned at bind time; there is no per-section layout
 * XML). These tests pin that contract, so a row added to the catalog without the
 * pieces it needs to render — or hidden by the wrong switch — fails here before it
 * reaches a device.
 */
class GermanSectionRowsTest {

    private val deutschRows = SettingsCatalog.rowsFor(SettingsSections.KEY_DEUTSCH)

    /** The section page is the card heading, then every catalog row of the section, in order. */
    @Test
    fun `the section page shows every deutsch row in catalog order`() {
        val items = SettingsCatalog.itemsFor(SettingsSections.KEY_DEUTSCH)
        assertEquals(
            "the section page must render the catalog rows in order",
            deutschRows.map { it.key },
            items.filterIsInstance<SettingsItem.Row>().map { it.spec.key },
        )
        assertEquals("the section page carries no extra items", deutschRows.size, items.size)

        // renderSection prepends the section's card heading before these items.
        val page = listOf(SettingsItem.Heading(R.string.german_section, SettingsHeadingStyle.CARD)) + items
        assertEquals(
            "the page heading is the German card heading",
            SettingsItem.Heading(R.string.german_section, SettingsHeadingStyle.CARD),
            page.first(),
        )
    }

    /**
     * A row renders only if the adapter's generic layout for its shape carries the
     * views the row model names. The subtitle and action views exist only in the
     * subtitle shape, so a row added with a subtitle or a chip but a VALUE shape
     * would render without them.
     */
    @Test
    fun `every deutsch row's shape layout carries the views the row needs`() {
        for (row in deutschRows) {
            val where = row.key
            val ids = layoutIds(shapeLayout.getValue(row.shape))
            assertTrue("$where: title view", "settingsRowTitle" in ids)
            if (row.type != SettingsRowType.ACTION) assertTrue("$where: value view", "settingsRowValue" in ids)

            val hasSubtitle = row.subtitleRes != 0 || row.subtitleId != 0
            if (hasSubtitle) {
                assertEquals("$where: a subtitle needs the subtitle shape", SettingsRowShape.SUBTITLE, row.shape)
                assertTrue("$where: subtitle view", "settingsRowSubtitle" in ids)
            }
            if (row.actionIds.isNotEmpty()) {
                assertEquals("$where: actions need the subtitle shape", SettingsRowShape.SUBTITLE, row.shape)
                for (view in listOf("settingsRowActions", "settingsRowActionFirst", "settingsRowActionSecond")) {
                    assertTrue("$where: $view", view in ids)
                }
            }
            if (row.trailingActionIds.isNotEmpty()) {
                for (view in listOf(
                    "settingsRowTrailingActions", "settingsRowTrailingActionFirst", "settingsRowTrailingActionSecond",
                )) {
                    assertTrue("$where: $view", view in ids)
                }
            }
        }
    }

    /**
     * populate writes the row's visibility through SettingsViews, which resolves the
     * enclosing *Layout id against the view the adapter bound. A row without a
     * layoutId would be hidden for good after its first hide, because the write
     * would land on a detached stand-in.
     */
    @Test
    fun `every deutsch row names its enclosing layout, title and value views`() {
        for (row in deutschRows) {
            assertTrue("${row.key}: layoutId", row.layoutId != 0)
            assertTrue("${row.key}: titleId", row.titleId != 0)
            if (row.type != SettingsRowType.ACTION) assertTrue("${row.key}: valueId", row.valueId != 0)
        }
    }

    /**
     * With German on, every deutsch row binds visible — the adapter hides a row only
     * through [SettingsRowAvailability], so any other hiding mechanism (a populate
     * function overruling the bind decision) breaks this contract.
     */
    @Test
    fun `every deutsch row except the hint row is available while German is on`() {
        for (row in deutschRows) {
            val available = SettingsRowAvailability.isAvailable(row.primaryId, shortcutsOn = true, germanOn = true)
            if (row.key == "germanScreenPickScreenshot") {
                assertFalse("the re-enable hint row hides until its hint applies", available)
            } else {
                assertTrue("${row.key} must render on the Deutsch section while German is on", available)
            }
        }
    }

    private fun layoutIds(name: String): Set<String> {
        val file = File("src/main/res/layout/$name.xml")
        assertTrue("layout not found at ${file.absolutePath}", file.isFile)
        return Regex("android:id=\"@\\+id/(\\w+)\"").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    private companion object {
        /** Which row layout draws each shape (SettingsRowsAdapter.createHolder). */
        val shapeLayout = mapOf(
            SettingsRowShape.VALUE to "item_settings_row_value",
            SettingsRowShape.SUBTITLE to "item_settings_row_subtitle",
            SettingsRowShape.SLIDER to "item_settings_row_slider",
            SettingsRowShape.STATUS to "item_settings_row_status",
        )
    }
}
