package app.olauncher.wellbeing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindDownTest {
    private val start = 21 * 60 + 30
    private val end = 6 * 60

    @Test fun beforeStartIsInactive() = assertFalse(WindDown.isActive(21 * 60 + 29, start, end))
    @Test fun atStartIsActive() = assertTrue(WindDown.isActive(start, start, end))
    @Test fun midnightIsActive() = assertTrue(WindDown.isActive(0, start, end))
    @Test fun lateNightIsActive() = assertTrue(WindDown.isActive(23 * 60 + 59, start, end))
    @Test fun justBeforeEndIsActive() = assertTrue(WindDown.isActive(5 * 60 + 59, start, end))
    @Test fun atEndIsInactive() = assertFalse(WindDown.isActive(end, start, end))
    @Test fun middayIsInactive() = assertFalse(WindDown.isActive(12 * 60, start, end))

    @Test fun sameDayWindow() {
        assertFalse(WindDown.isActive(59, 60, 120))
        assertTrue(WindDown.isActive(60, 60, 120))
        assertTrue(WindDown.isActive(119, 60, 120))
        assertFalse(WindDown.isActive(120, 60, 120))
    }

    @Test fun equalStartAndEndIsNeverActive() {
        assertFalse(WindDown.isActive(0, 600, 600))
        assertFalse(WindDown.isActive(600, 600, 600))
    }

    @Test fun nowIsNormalized() {
        assertTrue(WindDown.isActive(1440 + 23 * 60, start, end))
        assertFalse(WindDown.isActive(-60 * 12, start, end))
    }
}

class WindDownPolicyTest {
    @Test fun windDownPausesAnyApp() {
        assertTrue(DistractingApps.shouldPauseLaunch("a.b", false, emptySet(), windDownActive = true))
    }

    @Test fun windDownRespectsAppSnooze() {
        assertFalse(
            DistractingApps.shouldPauseLaunch(
                "a.b", false, emptySet(), appSnoozes = mapOf("a.b" to 2000L), now = 1000L, windDownActive = true,
            )
        )
    }

    @Test fun windDownRespectsBypass() {
        assertFalse(DistractingApps.shouldPauseLaunch("a.b", false, emptySet(), bypassPackage = "a.b", windDownActive = true))
    }

    @Test fun commitLockRules() {
        assertTrue(CommitLock.needsWait(true, FrictionChange.WindDownToggle(false)))
        assertFalse(CommitLock.needsWait(true, FrictionChange.WindDownToggle(true)))
        assertFalse(CommitLock.needsWait(true, FrictionChange.WindDownTimes))
        assertTrue(CommitLock.isTightening(FrictionChange.WindDownTimes))
    }
}
