package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLauncherCheckTest {

    private val own = "app.thumbline.debug"

    @Test
    fun `role result wins over the resolved package`() {
        assertEquals(true, DefaultLauncherCheck.isDefault(true, "android", own))
        assertEquals(true, DefaultLauncherCheck.isDefault(true, "com.google.android.apps.nexuslauncher", own))
        assertEquals(false, DefaultLauncherCheck.isDefault(false, own, own))
    }

    @Test
    fun `without the role, the resolved package decides`() {
        assertEquals(true, DefaultLauncherCheck.isDefault(null, own, own))
        assertEquals(false, DefaultLauncherCheck.isDefault(null, "com.google.android.apps.nexuslauncher", own))
    }

    @Test
    fun `chooser or missing result is inconclusive`() {
        assertNull(DefaultLauncherCheck.isDefault(null, "android", own))
        assertNull(DefaultLauncherCheck.isDefault(null, null, own))
        assertNull(DefaultLauncherCheck.isDefault(null, "", own))
    }

    @Test
    fun `bottom alignment applies unless known not default`() {
        assertTrue(DefaultLauncherCheck.useBottomAlignment(true, true))
        assertTrue(DefaultLauncherCheck.useBottomAlignment(true, null))
        assertFalse(DefaultLauncherCheck.useBottomAlignment(true, false))
        assertFalse(DefaultLauncherCheck.useBottomAlignment(false, true))
    }

    @Test
    fun `change default row shows unless Thumbline is known to be the default`() {
        assertFalse(DefaultLauncherCheck.showChangeDefault(true))
        assertTrue(DefaultLauncherCheck.showChangeDefault(false))
        assertTrue(DefaultLauncherCheck.showChangeDefault(null))
    }
}
