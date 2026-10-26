package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrillLastRoundTest {

    @Test
    fun encodeThenDecodeKeepsTheRound() {
        val round = DrillLastRound(typeId = "prepositions", right = 7, total = 10)
        assertEquals(round, DrillLastRound.decode(DrillLastRound.encode(round)))
    }

    @Test
    fun blankRawDecodesToNull() {
        assertNull(DrillLastRound.decode(null))
        assertNull(DrillLastRound.decode(""))
        assertNull(DrillLastRound.decode("   "))
    }

    @Test
    fun malformedJsonDecodesToNull() {
        assertNull(DrillLastRound.decode("not json"))
        assertNull(DrillLastRound.decode("{\"type\":}"))
    }

    @Test
    fun unknownTypeDecodesToNull() {
        assertNull(DrillLastRound.from("no_such_type", right = 7, total = 10))
        assertNull(DrillLastRound.decode("{\"type\":\"no_such_type\",\"right\":7,\"total\":10}"))
    }

    @Test
    fun blankTypeDecodesToNull() {
        assertNull(DrillLastRound.from("", right = 7, total = 10))
        assertNull(DrillLastRound.from(null, right = 7, total = 10))
    }

    @Test
    fun impossibleCountsDecodeToNull() {
        assertNull(DrillLastRound.from("prepositions", right = 11, total = 10))
        assertNull(DrillLastRound.from("prepositions", right = -1, total = 10))
        assertNull(DrillLastRound.from("prepositions", right = 0, total = 0))
        assertNull(DrillLastRound.from("prepositions", right = 0, total = -3))
    }

    @Test
    fun zeroRightAndMissingCountsAreRejectedByDecoder() {
        // A JSON object without right/total must not decode as 0-of-0.
        assertNull(DrillLastRound.decode("{\"type\":\"prepositions\"}"))
    }

    @Test
    fun edgeCountsDecode() {
        assertEquals(
            DrillLastRound("prepositions", right = 0, total = 10),
            DrillLastRound.decode("{\"type\":\"prepositions\",\"right\":0,\"total\":10}"),
        )
        assertEquals(
            DrillLastRound("prepositions", right = 10, total = 10),
            DrillLastRound.decode("{\"type\":\"prepositions\",\"right\":10,\"total\":10}"),
        )
    }
}
