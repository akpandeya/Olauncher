package app.olauncher.ui

import app.olauncher.R
import app.olauncher.data.ShortcutIconStyle.Treatment
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

    @Test
    fun `default icons keep their 12dp padding`() {
        assertEquals(12, ShortcutIconRenderer.paddingDp(isAppIcon = false, treatment = null))
    }

    @Test
    fun `full app icons fill a 32dp box in the 48dp view`() {
        assertEquals(8, ShortcutIconRenderer.paddingDp(isAppIcon = true, treatment = Treatment.ORIGINAL))
        assertEquals(8, ShortcutIconRenderer.paddingDp(isAppIcon = true, treatment = Treatment.FALLBACK_GRAYSCALE))
        assertEquals(8, ShortcutIconRenderer.paddingDp(isAppIcon = true, treatment = Treatment.FALLBACK_GRAYSCALE_ALPHA))
    }

    @Test
    fun `monochrome layer is drawn at adaptive layer scale`() {
        // 32dp mask box x 1.5 = 48dp layer, which fills the 48dp view
        assertEquals(0, ShortcutIconRenderer.paddingDp(isAppIcon = true, treatment = Treatment.MONOCHROME_LAYER))
    }

    @Test
    fun `layer padding matches how AdaptiveIconDrawable sizes its layers`() {
        assertEquals(0, ShortcutIconRenderer.layerPaddingDp(viewSizeDp = 48, maskBoxDp = 32))
        assertEquals(6, ShortcutIconRenderer.layerPaddingDp(viewSizeDp = 48, maskBoxDp = 24))
        assertEquals(0, ShortcutIconRenderer.layerPaddingDp(viewSizeDp = 48, maskBoxDp = 40))
    }

    @Test
    fun `monochrome glyph is about the size of a default glyph`() {
        // Default: 24dp box. App: a 108-unit layer drawn at 48dp; themed glyphs sit in roughly
        // the central 48 units, so they show at about 21dp instead of about 14dp before.
        val layerDp = ShortcutIconRenderer.VIEW_SIZE_DP -
            2 * ShortcutIconRenderer.paddingDp(isAppIcon = true, treatment = Treatment.MONOCHROME_LAYER)
        val defaultBoxDp = ShortcutIconRenderer.VIEW_SIZE_DP -
            2 * ShortcutIconRenderer.paddingDp(isAppIcon = false, treatment = null)
        val glyphDp = layerDp * 48f / 108f
        assertEquals(defaultBoxDp.toFloat(), glyphDp, 4f)
    }
}
