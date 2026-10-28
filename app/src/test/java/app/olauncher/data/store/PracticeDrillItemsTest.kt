package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adapter from AI-generated practice items (Epic 9 #561) to planner drill items
 * (#621): prompt, answer, choices and hint carry over, the practice type becomes the
 * drill type, and the key is prefixed so AI ids stay out of the built-in id space.
 */
class PracticeDrillItemsTest {

    private fun practiceItem(
        id: Long = 12,
        wordId: Long = 7,
        type: DrillType = DrillType.CLOZE,
    ) = PracticeItem(
        id = id,
        wordId = wordId,
        type = type,
        prompt = "Ich gehe ___ Park.",
        answer = "durch den",
        choices = listOf("durch den", "zu dem", "gegen den"),
        hint = "Wo gehe ich?",
    )

    @Test
    fun mapsPromptAnswerChoicesHintAndKeepsThePracticeType() {
        val drillItem = PracticeDrillItems.toDrillItem(practiceItem(type = DrillType.CLOZE))
        assertEquals(DrillType.CLOZE, drillItem.id.type)
        assertEquals("Ich gehe ___ Park.", drillItem.prompt)
        assertEquals("durch den", drillItem.answer)
        assertEquals(listOf("durch den", "zu dem", "gegen den"), drillItem.choices)
        assertEquals("Wo gehe ich?", drillItem.hint)
    }

    @Test
    fun aiKeysArePrefixedAndUniquePerStoredItem() {
        val first = PracticeDrillItems.toDrillItem(practiceItem(id = 12, wordId = 7))
        val second = PracticeDrillItems.toDrillItem(practiceItem(id = 13, wordId = 7))
        val otherWord = PracticeDrillItems.toDrillItem(practiceItem(id = 12, wordId = 8))
        assertTrue(first.id.key.startsWith(PracticeDrillItems.KEY_PREFIX))
        assertTrue(PracticeDrillItems.isAiItem(first.id))
        assertEquals(3, setOf(first.id, second.id, otherWord.id).size)
    }

    @Test
    fun anEmptyPoolMapsToNothing() {
        assertEquals(emptyList<DrillItem>(), PracticeDrillItems.toDrillItems(emptyList()))
    }
}
