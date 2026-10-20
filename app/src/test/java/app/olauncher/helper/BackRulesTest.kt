package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class BackRulesTest {

    @Test
    fun homeWithoutSearchIsNotConsumed() {
        assertEquals(false, BackRules.shouldConsumeBack(isHome = true, searchOpen = false))
    }

    @Test
    fun homeWithSearchOpenIsConsumed() {
        assertEquals(true, BackRules.shouldConsumeBack(isHome = true, searchOpen = true))
    }

    @Test
    fun otherScreenWithoutSearchIsConsumed() {
        assertEquals(true, BackRules.shouldConsumeBack(isHome = false, searchOpen = false))
    }

    @Test
    fun otherScreenWithSearchOpenIsConsumed() {
        assertEquals(true, BackRules.shouldConsumeBack(isHome = false, searchOpen = true))
    }
}
