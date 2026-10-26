package app.olauncher.data.store

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-item drill maturity (#639): only items answered RIGHT count toward typed
 * graduation; old or malformed data decodes unchanged to an empty map.
 */
class DrillItemHistoryTest {

    private val id1 = DrillItemId(DrillType.PREPOSITIONS, "prep:mit:1")
    private val id2 = DrillItemId(DrillType.ARTICLES, "art:buch")

    @Test
    fun updatedCountsOnlyRightAnswers() {
        val results = mapOf(
            id1 to DrillResult.RIGHT,
            id2 to DrillResult.WRONG,
        )
        val next = DrillItemHistory.updated(emptyMap(), results)
        assertEquals(1, next[id1.encode()])
        assertTrue(id2.encode() !in next)
    }

    @Test
    fun almostAnswersDoNotCountTowardGraduation() {
        val next = DrillItemHistory.updated(emptyMap(), mapOf(id1 to DrillResult.ALMOST))
        assertTrue(next.isEmpty())
    }

    @Test
    fun repeatedRightAnswersAccumulate() {
        var counts = emptyMap<String, Int>()
        repeat(3) {
            counts = DrillItemHistory.updated(counts, mapOf(id1 to DrillResult.RIGHT))
        }
        assertEquals(3, counts[id1.encode()])
    }

    @Test
    fun countsAreCapped() {
        val counts = mapOf(id1.encode() to DrillItemHistory.MAX_COUNT)
        val next = DrillItemHistory.updated(counts, mapOf(id1 to DrillResult.RIGHT))
        assertEquals(DrillItemHistory.MAX_COUNT, next[id1.encode()])
    }

    @Test
    fun maturityIsZeroForUnknownItems() {
        assertEquals(0, DrillItemHistory.maturity(emptyMap(), id1))
        assertEquals(0, DrillItemHistory.maturity(mapOf(id2.encode() to 2), id1))
        assertEquals(2, DrillItemHistory.maturity(mapOf(id2.encode() to 2), id2))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val counts = mapOf(id1.encode() to 1, id2.encode() to DrillItemHistory.TYPED_MATURITY)
        assertEquals(counts, DrillItemHistory.decode(DrillItemHistory.encode(counts)))
    }

    @Test
    fun decodeReturnsEmptyOnMalformedInput() {
        assertEquals(emptyMap<String, Int>(), DrillItemHistory.decode(null))
        assertEquals(emptyMap<String, Int>(), DrillItemHistory.decode(""))
        assertEquals(emptyMap<String, Int>(), DrillItemHistory.decode("not json"))
        assertEquals(emptyMap<String, Int>(), DrillItemHistory.decode(JSONObject().put("x", "y").toString()))
    }

    @Test
    fun decodeDropsUnknownItemIdsAndInsaneCounts() {
        val raw = JSONObject()
            .put(id1.encode(), 2)
            .put("unknown_type:1", 5)
            .put(id2.encode(), 0)
            .put(id2.encode(), -3)
            .toString()
        // "unknown_type:1" is not a DrillType, 0 and -3 are not stored counts.
        assertEquals(mapOf(id1.encode() to 2), DrillItemHistory.decode(raw))
    }

    @Test
    fun updatedReturnsTheSameMapWhenNothingCounts() {
        val counts = mapOf(id1.encode() to 1)
        assertTrue(DrillItemHistory.updated(counts, mapOf(id2 to DrillResult.WRONG)).equals(counts))
    }
}
