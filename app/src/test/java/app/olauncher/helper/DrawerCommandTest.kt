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
        assertNull(parse(":wx"))
        assertNull(parse(":swim"))
        assertNull(parse(":"))
        assertNull(parse("sw foo"))
    }

    @Test
    fun `german off is always null`() {
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
