package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialNudgePolicyTest {

    @Test
    fun `below the threshold returns null`() {
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 0,
                threshold = 15,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 14,
                threshold = 15,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 29,
                threshold = 30,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
    }

    @Test
    fun `crossing threshold returns bucket 0`() {
        val resultExact = SocialNudgePolicy.shouldNudge(
            minutesToday = 15,
            threshold = 15,
            repeat = 15,
            lastBucket = null,
            inForegroundRecently = true,
        )
        assertEquals(0, resultExact)

        val resultOver = SocialNudgePolicy.shouldNudge(
            minutesToday = 16,
            threshold = 15,
            repeat = 15,
            lastBucket = null,
            inForegroundRecently = true,
        )
        assertEquals(0, resultOver)

        val resultWithNegativeLastBucket = SocialNudgePolicy.shouldNudge(
            minutesToday = 16,
            threshold = 15,
            repeat = 15,
            lastBucket = -1,
            inForegroundRecently = true,
        )
        assertEquals(0, resultWithNegativeLastBucket)
    }

    @Test
    fun `plus repeat returns bucket 1`() {
        val result = SocialNudgePolicy.shouldNudge(
            minutesToday = 30,
            threshold = 15,
            repeat = 15,
            lastBucket = 0,
            inForegroundRecently = true,
        )
        assertEquals(1, result)

        val resultOver = SocialNudgePolicy.shouldNudge(
            minutesToday = 35,
            threshold = 15,
            repeat = 15,
            lastBucket = 0,
            inForegroundRecently = true,
        )
        assertEquals(1, resultOver)
    }

    @Test
    fun `plus 2x repeat returns bucket 2`() {
        val result = SocialNudgePolicy.shouldNudge(
            minutesToday = 45,
            threshold = 15,
            repeat = 15,
            lastBucket = 1,
            inForegroundRecently = true,
        )
        assertEquals(2, result)
    }

    @Test
    fun `same bucket returns null`() {
        // At threshold with bucket 0 already recorded
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 15,
                threshold = 15,
                repeat = 15,
                lastBucket = 0,
                inForegroundRecently = true,
            )
        )
        // Within same bucket interval [15, 30)
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 20,
                threshold = 15,
                repeat = 15,
                lastBucket = 0,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 29,
                threshold = 15,
                repeat = 15,
                lastBucket = 0,
                inForegroundRecently = true,
            )
        )
        // At repeat interval with bucket 1 already recorded
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 30,
                threshold = 15,
                repeat = 15,
                lastBucket = 1,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 38,
                threshold = 15,
                repeat = 15,
                lastBucket = 1,
                inForegroundRecently = true,
            )
        )
    }

    @Test
    fun `not in the foreground returns null`() {
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 16,
                threshold = 15,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = false,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 35,
                threshold = 15,
                repeat = 15,
                lastBucket = 0,
                inForegroundRecently = false,
            )
        )
    }

    @Test
    fun `zero or negative threshold returns null`() {
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 60,
                threshold = 0,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 60,
                threshold = -15,
                repeat = 15,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
    }

    @Test
    fun `zero or negative repeat returns null`() {
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 60,
                threshold = 15,
                repeat = 0,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
        assertNull(
            SocialNudgePolicy.shouldNudge(
                minutesToday = 60,
                threshold = 15,
                repeat = -5,
                lastBucket = null,
                inForegroundRecently = true,
            )
        )
    }

    @Test
    fun `threshold 30 behaves correctly across progression`() {
        val threshold = 30
        val repeat = 15

        // Below 30 -> null
        assertNull(SocialNudgePolicy.shouldNudge(25, threshold, repeat, null, true))

        // Reaching 30 -> bucket 0
        assertEquals(0, SocialNudgePolicy.shouldNudge(30, threshold, repeat, null, true))

        // Staying at 35 -> null (same bucket 0)
        assertNull(SocialNudgePolicy.shouldNudge(35, threshold, repeat, 0, true))

        // Reaching 45 -> bucket 1
        assertEquals(1, SocialNudgePolicy.shouldNudge(45, threshold, repeat, 0, true))

        // Staying at 55 -> null (same bucket 1)
        assertNull(SocialNudgePolicy.shouldNudge(55, threshold, repeat, 1, true))

        // Reaching 60 -> bucket 2
        assertEquals(2, SocialNudgePolicy.shouldNudge(60, threshold, repeat, 1, true))
    }
}
