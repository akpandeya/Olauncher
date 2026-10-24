package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.helper.DrawerCommand.LookUp
import app.olauncher.helper.DrawerCommand.SaveWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerCommandTest {

    private fun parse(q: String) = DrawerCommand.parse(q, germanEnabled = true)

    private fun app(label: String) = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = "pkg.$label",
        activityClassName = null,
        isNew = false,
        user = AppModel.defaultUser,
        firstInstallTime = 0L,
        isArchived = false
    )

    @Test
    fun `s splits items like sw and works with german off`() {
        for (german in listOf(false, true)) {
            assertEquals(DrawerCommand.ShopItems(listOf("milk", "eggs", "bread")), DrawerCommand.parse(":s milk, eggs;bread\nmilk", german))
        }
        assertEquals(DrawerCommand.ShopItems(emptyList()), parse(":s"))
        assertEquals(DrawerCommand.ShopItems(emptyList()), parse(":s , ;"))
        assertEquals(DrawerCommand.SaveWords(listOf("milk", "eggs")), parse(":sw milk, eggs"))
        assertEquals(DrawerCommand.splitItems(" a, b"), listOf("a", "b"))
    }

    @Test
    fun `sw single word`() = assertEquals(SaveWords(listOf("Fernweh")), parse(":sw Fernweh"))

    @Test
    fun `SW uppercase with separators`() =
        assertEquals(SaveWords(listOf("Fernweh", "Heimweh", "Sehnsucht")), parse(":SW  Fernweh, Heimweh;Sehnsucht"))

    @Test
    fun `sw newline separated`() =
        assertEquals(SaveWords(listOf("a", "b")), parse(":sw a\nb"))

    @Test
    fun `sw phrase stays whole`() = assertEquals(SaveWords(listOf("sich freuen auf")), parse(":sw sich freuen auf"))

    @Test
    fun `sw drops case-insensitive duplicates`() = assertEquals(SaveWords(listOf("Haus")), parse(":sw Haus, haus"))

    @Test
    fun `sw without items is empty`() {
        assertEquals(SaveWords(emptyList()), parse(":sw"))
        assertEquals(SaveWords(emptyList()), parse(":sw ,"))
    }

    @Test
    fun `sw drops long items`() {
        assertEquals(SaveWords(listOf("ok")), parse(":sw one two three four five, ${"x".repeat(41)}, ok"))
    }

    @Test
    fun `w looks up`() = assertEquals(LookUp("ging"), parse(":w ging"))

    @Test
    fun `non commands are null`() {
        assertNull(parse("sw foo"))
        assertNull(parse("t 10"))
        assertNull(parse(":w"))
    }

    private val allWords = listOf("t", "a", "e", "n", "p", "s", "sw", "w")

    @Test
    fun `colon alone lists enabled commands`() {
        assertEquals(allWords, (parse(":") as DrawerCommand.Help).commands.map { it.word })
        val off = DrawerCommand.parse(":", germanEnabled = false) as DrawerCommand.Help
        assertEquals(listOf("t", "a", "e", "n", "p", "s"), off.commands.map { it.word })
        assertEquals(off, DrawerCommand.parse("  :", germanEnabled = false))
    }

    @Test
    fun `unknown command lists commands`() {
        assertEquals(DrawerCommand.Help(DrawerCommand.available(true)), parse(":xyz"))
        assertEquals(DrawerCommand.Help(DrawerCommand.available(true)), parse(":swim"))
        assertEquals(DrawerCommand.Help(DrawerCommand.available(false)), DrawerCommand.parse(":wx", germanEnabled = false))
    }

    @Test
    fun `help row prefix fills command word`() {
        assertEquals(listOf(":t ", ":a ", ":e ", ":n ", ":p ", ":s ", ":sw ", ":w "), DrawerCommand.available(true).map { it.prefix })
    }

    @Test
    fun `t a e n work with german off`() {
        for (german in listOf(false, true)) {
            assertEquals(DrawerCommand.Timer("10"), DrawerCommand.parse(":t 10", german))
            assertEquals(DrawerCommand.Alarm("7:30"), DrawerCommand.parse(":a 7:30", german))
            assertEquals(DrawerCommand.Event("dentist"), DrawerCommand.parse(":E dentist", german))
            assertEquals(DrawerCommand.Note("buy milk"), DrawerCommand.parse(":n buy milk", german))
        }
    }

    @Test
    fun `german off gates only sw and w`() {
        assertNull(DrawerCommand.parse(":sw Fernweh", germanEnabled = false))
        assertNull(DrawerCommand.parse(":w ging", germanEnabled = false))
    }

    @Test
    fun `colon query never auto-launches`() {
        assertFalse(AutoLaunchPolicy.canAutoLaunch(query = ":sw spotify", firstItem = app("Spotify")))
        assertFalse(AutoLaunchPolicy.canAutoLaunch(query = ":w spotify", firstItem = app("Spotify")))
    }

    @Test
    fun `plain query still auto-launches`() {
        assertTrue(AutoLaunchPolicy.canAutoLaunch(query = "spotify", firstItem = app("Spotify")))
    }
}
