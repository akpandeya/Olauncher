package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class AppListBuilderTest {

    private val own = "app.thumbline.debug"
    private val hidden = setOf("com.secret|UserHandle{10}", "com.old|UserHandle{0}")

    private fun include(
        pkg: String,
        user: String = "UserHandle{0}",
        regular: Boolean = true,
        hiddenToo: Boolean = false,
        hiddenApps: Set<String> = hidden,
    ) = AppListBuilder.shouldInclude(pkg, user, own, hiddenApps, regular, hiddenToo)

    @Test
    fun displayLabel_usesRenameWhenSet() {
        assertEquals("Mine", AppListBuilder.displayLabel("Mine", "Original"))
    }

    @Test
    fun displayLabel_fallsBackWhenRenameBlank() {
        assertEquals("Original", AppListBuilder.displayLabel("", "Original"))
        assertEquals("Original", AppListBuilder.displayLabel("   ", "Original"))
    }

    @Test
    fun hiddenKey_joinsPackageAndUser() {
        assertEquals("com.x|UserHandle{11}", AppListBuilder.hiddenKey("com.x", "UserHandle{11}"))
    }

    @Test
    fun shouldInclude_neverListsOwnPackage() {
        assertFalse(include(own))
        assertFalse(include(own, regular = true, hiddenToo = true))
    }

    @Test
    fun shouldInclude_regularAppOnlyWhenRegularRequested() {
        assertTrue(include("com.mail"))
        assertFalse(include("com.mail", regular = false, hiddenToo = true))
    }

    @Test
    fun shouldInclude_hiddenAppOnlyWhenHiddenRequested() {
        assertFalse(include("com.secret", user = "UserHandle{10}"))
        assertTrue(include("com.secret", user = "UserHandle{10}", regular = false, hiddenToo = true))
    }

    @Test
    fun shouldInclude_hiddenMatchIsPerUser() {
        // com.secret is hidden for user 10 only; for user 0 it is a regular app.
        assertTrue(include("com.secret", user = "UserHandle{0}"))
        assertFalse(include("com.secret", user = "UserHandle{0}", regular = false, hiddenToo = true))
    }

    @Test
    fun shouldInclude_emptyHiddenSetMeansAllRegular() {
        assertTrue(include("com.secret", user = "UserHandle{10}", hiddenApps = emptySet()))
    }

    @Test
    fun shouldIncludePrivate_skipsOnlyOwnPackage() {
        assertFalse(AppListBuilder.shouldIncludePrivate(own, own))
        assertTrue(AppListBuilder.shouldIncludePrivate("com.vault", own))
    }

    @Test
    fun sortByLabel_ordersAlphabeticallyIgnoringCase() {
        val collator = Collator.getInstance(Locale.ENGLISH)
        val list = mutableListOf("zebra", "Mango", "apple", "Banana")
        AppListBuilder.sortByLabel(list, collator) { it }
        assertEquals(listOf("apple", "Banana", "Mango", "zebra"), list)
    }

    @Test
    fun sortByLabel_usesGivenLabelSelector() {
        val collator = Collator.getInstance(Locale.ENGLISH)
        val list = mutableListOf("c" to "Charlie", "a" to "Zulu", "b" to "alpha")
        AppListBuilder.sortByLabel(list, collator) { it.second }
        assertEquals(listOf("b", "c", "a"), list.map { it.first })
    }

    @Test
    fun sortByLabel_isStableForEqualLabels() {
        val collator = Collator.getInstance(Locale.ENGLISH)
        val list = mutableListOf(1 to "Same", 2 to "Same", 0 to "Aaa")
        AppListBuilder.sortByLabel(list, collator) { it.second }
        assertEquals(listOf(0, 1, 2), list.map { it.first })
    }
}
