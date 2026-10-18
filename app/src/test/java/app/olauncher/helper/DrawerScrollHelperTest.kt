package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerScrollHelperTest {

    @Test
    fun `when bottom search is on, atSearch checks if list cannot scroll down`() {
        // At the bottom of the list (cannot scroll down further): should be at search
        assertTrue(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = true,
                canScrollUp = true,
                canScrollDown = false
            )
        )
        // Mid-list (can scroll in both directions): not at search
        assertFalse(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = true,
                canScrollUp = true,
                canScrollDown = true
            )
        )
        // At the top of the list (cannot scroll up): not at search
        assertFalse(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = true,
                canScrollUp = false,
                canScrollDown = true
            )
        )
    }

    @Test
    fun `when bottom search is on, atFarEnd checks if list cannot scroll up`() {
        // At the top of the list (cannot scroll up further): should be at far end
        assertTrue(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = true,
                canScrollUp = false,
                canScrollDown = true
            )
        )
        // Mid-list (can scroll in both directions): not at far end
        assertFalse(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = true,
                canScrollUp = true,
                canScrollDown = true
            )
        )
        // At the bottom of the list (cannot scroll down): not at far end
        assertFalse(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = true,
                canScrollUp = true,
                canScrollDown = false
            )
        )
    }

    @Test
    fun `when bottom search is off, atSearch checks if list cannot scroll up`() {
        // At the top of the list (cannot scroll up further): should be at search
        assertTrue(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = false,
                canScrollUp = false,
                canScrollDown = true
            )
        )
        // Mid-list (can scroll in both directions): not at search
        assertFalse(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = false,
                canScrollUp = true,
                canScrollDown = true
            )
        )
        // At the bottom of the list (cannot scroll down): not at search
        assertFalse(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = false,
                canScrollUp = true,
                canScrollDown = false
            )
        )
    }

    @Test
    fun `when bottom search is off, atFarEnd checks if list cannot scroll down`() {
        // At the bottom of the list (cannot scroll down further): should be at far end
        assertTrue(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = false,
                canScrollUp = true,
                canScrollDown = false
            )
        )
        // Mid-list (can scroll in both directions): not at far end
        assertFalse(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = false,
                canScrollUp = true,
                canScrollDown = true
            )
        )
        // At the top of the list (cannot scroll up): not at far end
        assertFalse(
            DrawerScrollHelper.isAtFarEnd(
                drawerBottomSearch = false,
                canScrollUp = false,
                canScrollDown = true
            )
        )
    }

    @Test
    fun `non scrollable list is considered at search in both modes`() {
        // When list has 0 or 1 item and cannot scroll in either direction:
        assertTrue(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = true,
                canScrollUp = false,
                canScrollDown = false
            )
        )
        assertTrue(
            DrawerScrollHelper.isAtSearch(
                drawerBottomSearch = false,
                canScrollUp = false,
                canScrollDown = false
            )
        )
    }

    @Test
    fun `imeBottomPadding is zero when the keyboard is hidden`() {
        assertEquals(0, DrawerScrollHelper.imeBottomPadding(true, imeBottom = 0, searchBottomMargin = 63))
    }

    @Test
    fun `imeBottomPadding lifts the bottom search row above the keyboard`() {
        assertEquals(837, DrawerScrollHelper.imeBottomPadding(true, imeBottom = 900, searchBottomMargin = 63))
    }

    @Test
    fun `imeBottomPadding never goes negative`() {
        assertEquals(0, DrawerScrollHelper.imeBottomPadding(true, imeBottom = 40, searchBottomMargin = 63))
        assertEquals(900, DrawerScrollHelper.imeBottomPadding(true, imeBottom = 900, searchBottomMargin = -5))
    }

    @Test
    fun `imeBottomPadding leaves the top search layout alone`() {
        assertEquals(0, DrawerScrollHelper.imeBottomPadding(false, imeBottom = 900, searchBottomMargin = 63))
    }
}
