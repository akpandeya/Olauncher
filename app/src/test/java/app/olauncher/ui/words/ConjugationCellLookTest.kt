package app.olauncher.ui.words

import org.junit.Assert.assertEquals
import org.junit.Test

class ConjugationCellLookTest {

    @Test
    fun `unanswered question keeps all choices neutral`() {
        val answer = "mache"
        val choices = listOf("mache", "machst", "macht", "machen")
        for (choice in choices) {
            assertEquals(
                "Choice $choice must be NEUTRAL when unanswered",
                ConjugationCellLook.NEUTRAL,
                ConjugationCellLook.of(answered = false, choice = choice, answer = answer, picked = null),
            )
            assertEquals(
                "Choice $choice must be NEUTRAL when unanswered regardless of picked",
                ConjugationCellLook.NEUTRAL,
                ConjugationCellLook.of(answered = false, choice = choice, answer = answer, picked = "machst"),
            )
        }
    }

    @Test
    fun `right pick marks answer RIGHT and other choices NEUTRAL`() {
        val answer = "mache"
        val picked = "mache"

        assertEquals(
            "Answer cell must be RIGHT",
            ConjugationCellLook.RIGHT,
            ConjugationCellLook.of(answered = true, choice = "mache", answer = answer, picked = picked),
        )

        val others = listOf("machst", "macht", "machen")
        for (other in others) {
            assertEquals(
                "Unpicked choice $other must be NEUTRAL",
                ConjugationCellLook.NEUTRAL,
                ConjugationCellLook.of(answered = true, choice = other, answer = answer, picked = picked),
            )
        }
    }

    @Test
    fun `wrong pick marks picked cell WRONG answer cell RIGHT and others NEUTRAL`() {
        val answer = "mache"
        val picked = "machst"

        assertEquals(
            "Picked wrong cell must be WRONG",
            ConjugationCellLook.WRONG,
            ConjugationCellLook.of(answered = true, choice = picked, answer = answer, picked = picked),
        )
        assertEquals(
            "Answer cell must be RIGHT",
            ConjugationCellLook.RIGHT,
            ConjugationCellLook.of(answered = true, choice = answer, answer = answer, picked = picked),
        )

        val others = listOf("macht", "machen")
        for (other in others) {
            assertEquals(
                "Other choice $other must be NEUTRAL",
                ConjugationCellLook.NEUTRAL,
                ConjugationCellLook.of(answered = true, choice = other, answer = answer, picked = picked),
            )
        }
    }

    @Test
    fun `null pick when answered marks answer cell RIGHT and others NEUTRAL`() {
        val answer = "mache"
        val picked: String? = null

        assertEquals(
            "Answer cell must be RIGHT even if picked is null",
            ConjugationCellLook.RIGHT,
            ConjugationCellLook.of(answered = true, choice = answer, answer = answer, picked = picked),
        )

        val others = listOf("machst", "macht", "machen")
        for (other in others) {
            assertEquals(
                "Other choice $other must be NEUTRAL when picked is null",
                ConjugationCellLook.NEUTRAL,
                ConjugationCellLook.of(answered = true, choice = other, answer = answer, picked = picked),
            )
        }
    }
}
