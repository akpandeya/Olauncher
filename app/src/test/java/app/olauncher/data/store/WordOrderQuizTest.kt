package app.olauncher.data.store

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordOrderQuizTest {

    private val validItem1 = WordOrderItem(
        id = DrillItemId(DrillType.WORD_ORDER, "1"),
        tiles = listOf("Ich", "gehe", "gerne", "ins", "Kino"),
        accepted = listOf(
            listOf("Ich", "gehe", "gerne", "ins", "Kino"),
            listOf("Ich", "gehe", "ins", "Kino", "gerne"),
        ),
        hint = "Position 2: Verb",
    )

    private val validItem2 = WordOrderItem(
        id = DrillItemId(DrillType.WORD_ORDER, "2"),
        tiles = listOf("Wir", "kaufen", "morgen", "ein", "Auto"),
        accepted = listOf(
            listOf("Wir", "kaufen", "morgen", "ein", "Auto"),
        ),
    )

    private val validItem3 = WordOrderItem(
        id = DrillItemId(DrillType.WORD_ORDER, "3"),
        tiles = listOf("Er", "liest", "ein", "Buch"),
        accepted = listOf(
            listOf("Er", "liest", "ein", "Buch"),
        ),
    )

    // --- WordOrderItem validation tests ---

    @Test
    fun validateAcceptsValidItem() {
        val check = validItem1.validate()
        assertTrue(check.isValid)
        assertTrue(check.problems.isEmpty())
    }

    @Test
    fun validateAcceptsThreeTiles() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Das", "ist", "gut"),
            accepted = listOf(listOf("Das", "ist", "gut")),
        )
        val check = item.validate()
        assertTrue(check.isValid)
        assertTrue(check.problems.isEmpty())
    }

    @Test
    fun validateRejectsFewerThanThreeTiles() {
        val twoTiles = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe"),
            accepted = listOf(listOf("Ich", "gehe")),
        )
        val checkTwo = twoTiles.validate()
        assertFalse(checkTwo.isValid)
        assertTrue(checkTwo.problems.any { "at least 3" in it })

        val zeroTiles = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = emptyList(),
            accepted = listOf(emptyList()),
        )
        val checkZero = zeroTiles.validate()
        assertFalse(checkZero.isValid)
    }

    @Test
    fun validateRejectsEmptyAcceptedList() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe", "heute"),
            accepted = emptyList(),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "empty" in it })
    }

    @Test
    fun validateRejectsAcceptedOrderNotPermutationFewerWords() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe", "heute", "ins", "Kino"),
            accepted = listOf(listOf("Ich", "gehe", "ins", "Kino")),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "permutation" in it })
    }

    @Test
    fun validateRejectsAcceptedOrderNotPermutationExtraWords() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe", "heute"),
            accepted = listOf(listOf("Ich", "gehe", "heute", "nicht")),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "permutation" in it })
    }

    @Test
    fun validateRejectsAcceptedOrderNotPermutationDifferentWords() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe", "heute"),
            accepted = listOf(listOf("Du", "gehst", "heute")),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "permutation" in it })
    }

    @Test
    fun validateRejectsAcceptedOrderWithDuplicateWordsNotMatchingTiles() {
        val item = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "test"),
            tiles = listOf("Ich", "gehe", "heute"),
            accepted = listOf(listOf("Ich", "Ich", "heute")),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "permutation" in it })
    }

    @Test
    fun canonicalAnswerReturnsFirstAcceptedOrderJoinedWithSpace() {
        assertEquals("Ich gehe gerne ins Kino", validItem1.canonicalAnswer)
        assertEquals("Wir kaufen morgen ein Auto", validItem2.canonicalAnswer)
    }

    // --- WordOrderQuiz rules & state tests ---

    @Test
    fun quizInitializesWithCorrectTotalAndShuffledTiles() {
        val quiz = WordOrderQuiz(listOf(validItem1, validItem2), Random(42))
        assertEquals(2, quiz.total)
        assertFalse(quiz.isFinished)
        assertFalse(quiz.isAnswered)

        val q = quiz.current
        assertNotNull(q)
        assertEquals(validItem1.id, q!!.id)
        assertEquals(validItem1.hint, q.hint)
        assertEquals(validItem1.tiles.toSet(), q.initialTiles.map { it.text }.toSet())
        assertEquals(5, q.availableTiles.size)
        assertTrue(q.answerTiles.isEmpty())
    }

    @Test
    fun emptyQuizIsFinishedImmediately() {
        val quiz = WordOrderQuiz(emptyList())
        assertEquals(0, quiz.total)
        assertTrue(quiz.isFinished)
        assertNull(quiz.current)
    }

    @Test
    fun tileTappingAppendsToAnswerAndSendsBackToAvailable() {
        val quiz = WordOrderQuiz(listOf(validItem1))
        val q = quiz.current!!

        val firstAvailable = q.availableTiles[0]
        val secondAvailable = q.availableTiles[1]

        // Select first tile
        assertTrue(quiz.selectTile(firstAvailable))
        assertEquals(listOf(firstAvailable), q.answerTiles)
        assertEquals(4, q.availableTiles.size)
        assertFalse(firstAvailable in q.availableTiles)

        // Select second tile
        assertTrue(quiz.selectTile(secondAvailable))
        assertEquals(listOf(firstAvailable, secondAvailable), q.answerTiles)
        assertEquals(3, q.availableTiles.size)

        // Unselect first tile: should remove it from answer and return it to available
        assertTrue(quiz.unselectTile(firstAvailable))
        assertEquals(listOf(secondAvailable), q.answerTiles)
        assertEquals(4, q.availableTiles.size)
        assertTrue(firstAvailable in q.availableTiles)
    }

    @Test
    fun duplicateWordStringsAreTrackedIndependentlyByTileId() {
        val itemWithDuplicates = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "dup"),
            tiles = listOf("Er", "sieht", "ein", "Buch", "und", "ein", "Auto"),
            accepted = listOf(listOf("Er", "sieht", "ein", "Buch", "und", "ein", "Auto")),
        )
        val quiz = WordOrderQuiz(listOf(itemWithDuplicates))
        val q = quiz.current!!

        val einTiles = q.initialTiles.filter { it.text == "ein" }
        assertEquals(2, einTiles.size)
        val ein1 = einTiles[0]
        val ein2 = einTiles[1]

        quiz.selectTile(ein1)
        assertEquals(listOf(ein1), q.answerTiles)
        assertTrue(ein2 in q.availableTiles)
        assertFalse(ein1 in q.availableTiles)

        quiz.selectTile(ein2)
        assertEquals(listOf(ein1, ein2), q.answerTiles)
        assertFalse(ein2 in q.availableTiles)
    }

    @Test
    fun anyAcceptedOrderCountsAsRight() {
        // Test first accepted permutation
        val quiz1 = WordOrderQuiz(listOf(validItem1))
        val ans1 = quiz1.answer(listOf("Ich", "gehe", "gerne", "ins", "Kino"))
        assertNotNull(ans1)
        assertTrue(ans1!!.correct)

        // Test second accepted permutation
        val quiz2 = WordOrderQuiz(listOf(validItem1))
        val ans2 = quiz2.answer(listOf("Ich", "gehe", "ins", "Kino", "gerne"))
        assertNotNull(ans2)
        assertTrue(ans2!!.correct)
    }

    @Test
    fun rightAnswerFirstTryRecordsTrueInFirstAttempts() {
        val quiz = WordOrderQuiz(listOf(validItem1, validItem2))
        val ans = quiz.answer(listOf("Ich", "gehe", "gerne", "ins", "Kino"))
        assertNotNull(ans)
        assertTrue(ans!!.correct)
        assertEquals(mapOf(validItem1.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun checkUsesSelectedTilesToGrade() {
        val quiz = WordOrderQuiz(listOf(validItem1))
        val q = quiz.current!!

        // Arrange in wrong order
        for (tile in q.initialTiles) {
            quiz.selectTile(tile)
        }
        val ans = quiz.check()
        assertNotNull(ans)
        assertTrue(quiz.isAnswered)
        assertEquals(ans, quiz.lastAnswer)
    }

    @Test
    fun wrongAnswerPutsItemAtTheEndOfTheQueue() {
        val quiz = WordOrderQuiz(listOf(validItem1, validItem2, validItem3))

        // Answer validItem1 wrongly
        val wrongAns = quiz.answer(listOf("Kino", "ins", "heute", "gehe", "Ich"))
        assertNotNull(wrongAns)
        assertFalse(wrongAns!!.correct)
        assertEquals(mapOf(validItem1.id to false), quiz.firstAttempts())
        assertEquals(0, quiz.firstTryScore)

        quiz.advance()

        // Next should be validItem2
        assertEquals(validItem2.id, quiz.current!!.id)
        val ans2 = quiz.answer(listOf("Wir", "kaufen", "morgen", "ein", "Auto"))
        assertTrue(ans2!!.correct)
        quiz.advance()

        // Next should be validItem3
        assertEquals(validItem3.id, quiz.current!!.id)
        val ans3 = quiz.answer(listOf("Er", "liest", "ein", "Buch"))
        assertTrue(ans3!!.correct)
        quiz.advance()

        // Now validItem1 must have come back to the end!
        assertEquals(validItem1.id, quiz.current!!.id)
        // Retry validItem1 and answer correctly
        val ans1Retry = quiz.answer(listOf("Ich", "gehe", "gerne", "ins", "Kino"))
        assertTrue(ans1Retry!!.correct)
        quiz.advance()

        // All items cleared, quiz is finished: NO TYPED ROUND
        assertTrue(quiz.isFinished)
        assertNull(quiz.current)
    }

    @Test
    fun retriesNeverChangeFirstAttempts() {
        val quiz = WordOrderQuiz(listOf(validItem1, validItem2))

        // validItem1 wrong first try
        quiz.answer(listOf("wrong", "order", "here", "test", "bad"))
        quiz.advance()
        assertEquals(mapOf(validItem1.id to false), quiz.firstAttempts())
        assertEquals(0, quiz.firstTryScore)

        // validItem2 right first try
        quiz.answer(listOf("Wir", "kaufen", "morgen", "ein", "Auto"))
        quiz.advance()
        assertEquals(mapOf(validItem1.id to false, validItem2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)

        // validItem1 comes back for retry and is answered correctly:
        // firstAttempts must STAY FALSE!
        assertEquals(validItem1.id, quiz.current!!.id)
        quiz.answer(listOf("Ich", "gehe", "gerne", "ins", "Kino"))
        quiz.advance()

        assertEquals(mapOf(validItem1.id to false, validItem2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
        assertTrue(quiz.isFinished)
    }

    @Test
    fun selectAndUnselectAreDisabledOnceAnswered() {
        val quiz = WordOrderQuiz(listOf(validItem1))
        val q = quiz.current!!
        val tile = q.availableTiles.first()
        quiz.selectTile(tile)

        quiz.check()
        assertTrue(quiz.isAnswered)

        val anotherTile = q.availableTiles.first()
        assertFalse(quiz.selectTile(anotherTile))
        assertFalse(quiz.unselectTile(tile))
    }

    // --- Integration with DrillReviewFeed and DrillScores ---

    @Test
    fun firstAttemptsFeedThroughDrillReviewFeedForSavedWords() {
        val savedWord1 = Word(id = 101L, text = "Kino", dueDay = 0L, addedAt = 0L)
        val savedWord2 = Word(id = 202L, text = "Auto", dueDay = 0L, addedAt = 0L)

        val item1 = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "101:sentence"),
            tiles = listOf("Ich", "gehe", "heute", "ins", "Kino"),
            accepted = listOf(listOf("Ich", "gehe", "heute", "ins", "Kino")),
        )
        val item2 = WordOrderItem(
            id = DrillItemId(DrillType.WORD_ORDER, "202:sentence"),
            tiles = listOf("Wir", "kaufen", "morgen", "ein", "Auto"),
            accepted = listOf(listOf("Wir", "kaufen", "morgen", "ein", "Auto")),
        )

        val quiz = WordOrderQuiz(listOf(item1, item2))

        // item1 answered correctly on first try
        quiz.answer(listOf("Ich", "gehe", "heute", "ins", "Kino"))
        quiz.advance()

        // item2 answered incorrectly on first try
        quiz.answer(listOf("Auto", "ein", "morgen", "kaufen", "Wir"))
        quiz.advance()

        val grades = DrillReviewFeed.grades(
            firstAttempts = quiz.firstAttempts(),
            words = listOf(savedWord1, savedWord2),
            today = 19000L,
        )

        assertEquals(2, grades.size)
        val grade1 = grades.first { it.word.id == 101L }
        val grade2 = grades.first { it.word.id == 202L }

        assertEquals(ReviewEvent.Result.KNEW, grade1.result)
        assertEquals("drill:word_order", grade1.source)
        assertEquals(ReviewEvent.Result.AGAIN, grade2.result)
        assertEquals("drill:word_order", grade2.source)
    }

    @Test
    fun firstTryScoreRecordsInDrillScores() {
        val existing = emptyMap<String, DrillScores.Score>()
        val updated = DrillScores.recordRound(
            existing = existing,
            typeId = DrillType.WORD_ORDER.id,
            asked = 5,
            firstTryRight = 4,
        )

        val score = updated[DrillType.WORD_ORDER.id]
        assertNotNull(score)
        assertEquals(5, score!!.asked)
        assertEquals(4, score.firstTryRight)
        assertEquals(80, score.percentage)
    }

    @Test
    fun itemsHelperProducesSampleItemsAndLinksSavedWords() {
        val itemsDefault = WordOrderQuiz.items()
        assertTrue("Expected at least 40 items, found ${itemsDefault.size}", itemsDefault.size >= 40)

        val savedWord = Word(id = 777L, text = "Kino", dueDay = 0L, addedAt = 0L)
        val itemsWithWords = WordOrderQuiz.items(listOf(savedWord))
        val matchingItem = itemsWithWords.first { it.tiles.contains("Kino") }
        assertTrue(matchingItem.id.encode().startsWith("word_order:777:"))
    }

    @Test
    fun roundItemsLimitsToTenShuffledItems() {
        val roundItems = WordOrderQuiz.roundItems()
        assertEquals(10, roundItems.size)
        assertEquals(10, roundItems.distinctBy { it.id }.size)

        // All items should be valid
        for (item in roundItems) {
            assertTrue(item.validate().isValid)
        }
    }
}
