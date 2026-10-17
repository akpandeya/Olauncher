package app.olauncher.ui

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutIconRendererTest {

    @Test
    fun `default icon resource matches slot specification`() {
        assertEquals(R.drawable.ic_shortcut_search, ShortcutIconRenderer.getDefaultIconRes(1))
        assertEquals(R.drawable.ic_shortcut_gallery, ShortcutIconRenderer.getDefaultIconRes(2))
        assertEquals(R.drawable.ic_shortcut_camera, ShortcutIconRenderer.getDefaultIconRes(3))
        assertEquals(R.drawable.ic_shortcut_messages, ShortcutIconRenderer.getDefaultIconRes(4))
        assertEquals(R.drawable.ic_shortcut_phone, ShortcutIconRenderer.getDefaultIconRes(5))
        assertEquals(R.drawable.ic_shortcut_more, ShortcutIconRenderer.getDefaultIconRes(6))
    }

    @Test
    fun `default icon resource falls back to more for unknown slots`() {
        assertEquals(R.drawable.ic_shortcut_more, ShortcutIconRenderer.getDefaultIconRes(0))
        assertEquals(R.drawable.ic_shortcut_more, ShortcutIconRenderer.getDefaultIconRes(7))
        assertEquals(R.drawable.ic_shortcut_more, ShortcutIconRenderer.getDefaultIconRes(8))
    }

    @Test
    fun `default label resource matches slot specification`() {
        assertEquals(R.string.shortcut_search, ShortcutIconRenderer.getDefaultLabelRes(1))
        assertEquals(R.string.shortcut_gallery, ShortcutIconRenderer.getDefaultLabelRes(2))
        assertEquals(R.string.shortcut_camera, ShortcutIconRenderer.getDefaultLabelRes(3))
        assertEquals(R.string.shortcut_messages, ShortcutIconRenderer.getDefaultLabelRes(4))
        assertEquals(R.string.shortcut_phone, ShortcutIconRenderer.getDefaultLabelRes(5))
        assertEquals(R.string.shortcut_more, ShortcutIconRenderer.getDefaultLabelRes(6))
    }

    @Test
    fun `default label resource falls back to more for unknown slots`() {
        assertEquals(R.string.shortcut_more, ShortcutIconRenderer.getDefaultLabelRes(0))
        assertEquals(R.string.shortcut_more, ShortcutIconRenderer.getDefaultLabelRes(7))
        assertEquals(R.string.shortcut_more, ShortcutIconRenderer.getDefaultLabelRes(8))
    }
}
