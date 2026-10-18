package app.olauncher.wellbeing

import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NudgePlumbingTest {

    @Test
    fun `nudge constants match specification`() {
        assertEquals("nudges", Nudges.CHANNEL_ID)
        assertEquals("water", Nudges.WATER)
        assertEquals("walk", Nudges.WALK)
        assertEquals("social", Nudges.SOCIAL)
    }

    @Test
    fun `notification IDs are distinct for different nudge types`() {
        val waterId = Nudges.notificationId(Nudges.WATER)
        val walkId = Nudges.notificationId(Nudges.WALK)
        val socialId = Nudges.notificationId(Nudges.SOCIAL)

        assertNotEquals(waterId, walkId)
        assertNotEquals(waterId, socialId)
        assertNotEquals(walkId, socialId)
    }

    @Test
    fun `nudgeDoneKey formats type in uppercase with prefix`() {
        assertEquals("NUDGE_DONE_WATER", Prefs.nudgeDoneKey("water"))
        assertEquals("NUDGE_DONE_WALK", Prefs.nudgeDoneKey("walk"))
        assertEquals("NUDGE_DONE_SOCIAL", Prefs.nudgeDoneKey("social"))
        assertEquals("NUDGE_DONE_WATER", Prefs.nudgeDoneKey("WATER"))
    }

    @Test
    fun `workName prefixes type with nudge_`() {
        assertEquals("nudge_water", NudgeScheduler.workName(Nudges.WATER))
        assertEquals("nudge_walk", NudgeScheduler.workName(Nudges.WALK))
        assertEquals("nudge_social", NudgeScheduler.workName(Nudges.SOCIAL))
    }

    @Test
    fun `action and extra constants match specification`() {
        assertEquals("app.olauncher.action.NUDGE_DONE", NudgeActionReceiver.ACTION_DONE)
        assertEquals("type", NudgeActionReceiver.EXTRA_TYPE)
    }
}
