package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseGoalTest {
    @Test
    fun dailyGoalIs45Minutes() {
        assertEquals(45L, ExerciseGoal.DAILY_MIN)
    }

    @Test
    fun weeklyGoalIsSevenDailyGoals() {
        assertEquals(315L, ExerciseGoal.WEEKLY_MIN)
    }
}
