package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMappingTest {

    // The real dump's node tree spans [0,0][1280,2856] (root bounds), 9:16 portrait.
    private val tagesschau = UiAutomatorFixture.loadTagesschau()

    /** First and last word box from the real node tree, as ScreenMapping's callers pass them. */
    private fun anchors(nodes: List<ScreenNode>): List<ScreenBox> {
        val words = ScreenWords.build(nodes).words
        return listOfNotNull(
            words.firstOrNull { it.box != null }?.box,
            words.lastOrNull { it.box != null }?.box,
        )
    }

    @Test
    fun frameMatchingTheNodeTreeVerifies() {
        val screen = tagesschau.first().bounds!!
        val result = ScreenMapping.verify(ScreenMapping.Frame(screen.width, screen.height), screen, anchors(tagesschau))
        val verified = result as ScreenMapping.Verified
        assertEquals(1f, verified.scaleX, 1e-5f)
        assertEquals(1f, verified.scaleY, 1e-5f)
    }

    @Test
    fun slightScaleDeviationWithinToleranceVerifies() {
        // Same aspect, 1.01x smaller frame (e.g. a rounding drift) stays within the 2% band.
        val screen = tagesschau.first().bounds!!
        val frame = ScreenMapping.Frame(1267, 2828)
        val verified = ScreenMapping.verify(frame, screen, anchors(tagesschau)) as ScreenMapping.Verified
        assertEquals(screen.width / frame.width.toFloat(), verified.scaleX, 1e-5f)
        assertEquals(screen.height / frame.height.toFloat(), verified.scaleY, 1e-5f)
    }

    @Test
    fun densityChangeMismatchesOnScale() {
        // The user changed display size/density: same aspect, frame captured 1.25x larger.
        val screen = tagesschau.first().bounds!!
        val frame = ScreenMapping.Frame(1600, 3570)
        val result = ScreenMapping.verify(frame, screen, anchors(tagesschau))
        assertEquals(ScreenMapping.Reason.SCALE, (result as ScreenMapping.Mismatch).reason)
    }

    @Test
    fun aspectMismatchOnTheTagesschauMismatchFixture() {
        // The mismatch fixture's node tree is [0,0][1080,1920] (9:16); a 1080x2400 frame is 9:20.
        val nodes = UiAutomatorFixture.loadTagesschauMismatch()
        val screen = nodes.first().bounds!!
        assertEquals(ScreenBox(0, 0, 1080, 1920), screen)
        val result = ScreenMapping.verify(ScreenMapping.Frame(1080, 2400), screen, anchors(nodes))
        assertEquals(ScreenMapping.Reason.ASPECT, (result as ScreenMapping.Mismatch).reason)
    }

    @Test
    fun anchorOutsideTheFrameMismatches() {
        val screen = tagesschau.first().bounds!!
        val anchors = listOf(ScreenBox(5000, 5000, 5100, 5100))
        val result = ScreenMapping.verify(ScreenMapping.Frame(screen.width, screen.height), screen, anchors)
        assertEquals(ScreenMapping.Reason.NO_ANCHOR, (result as ScreenMapping.Mismatch).reason)
    }

    @Test
    fun anchorPartiallyInsideTheFrameCounts() {
        val screen = tagesschau.first().bounds!!
        val anchors = listOf(ScreenBox(screen.width - 10, 10, screen.width + 10, 30))
        val result = ScreenMapping.verify(ScreenMapping.Frame(screen.width, screen.height), screen, anchors)
        assertTrue(result is ScreenMapping.Mismatch)
    }

    @Test
    fun degenerateSizesMismatchOnAspect() {
        val screen = ScreenBox(0, 0, 100, 20)
        assertEquals(
            ScreenMapping.Reason.ASPECT,
            (ScreenMapping.verify(ScreenMapping.Frame(0, 100), screen, emptyList()) as ScreenMapping.Mismatch).reason,
        )
        // A zero-height screen box has no aspect either.
        assertEquals(
            ScreenMapping.Reason.ASPECT,
            (ScreenMapping.verify(ScreenMapping.Frame(100, 100), ScreenBox(0, 0, 100, 0), emptyList()) as ScreenMapping.Mismatch).reason,
        )
    }

    // --- ScreenNodesCodec: the node snapshot handoff format ---

    @Test
    fun codecRoundTripsRealNodes() {
        val encoded = ScreenNodesCodec.encode(tagesschau)
        assertEquals(tagesschau, ScreenNodesCodec.decode(encoded))
    }

    @Test
    fun codecRoundTripsNullsEmptyStringsAndSpecialCharacters() {
        val nodes = listOf(
            ScreenNode(
                id = 0,
                parentId = -1,
                text = null,
                bounds = null,
                charBoxes = null,
                className = null,
                contentDescription = null,
            ),
            ScreenNode(
                id = 1,
                parentId = 0,
                text = "",
                bounds = ScreenBox(1, 2, 3, 4),
                charBoxes = listOf(ScreenBox(1, 2, 3, 4), ScreenBox(-5, -6, -7, -8)),
                className = "android.widget.TextView",
                contentDescription = "",
            ),
            ScreenNode(
                id = 2,
                parentId = 1,
                text = "tabs\there\nand lines\rand \\ backslashes, comma,; semicolon",
                bounds = ScreenBox(0, 0, 10, 10),
                charBoxes = listOf(ScreenBox(9, 9, 10, 10)),
                className = "\\0 is not a null marker",
                contentDescription = "text\twith\tseparators",
            ),
        )
        assertEquals(nodes, ScreenNodesCodec.decode(ScreenNodesCodec.encode(nodes)))
    }

    @Test
    fun codecDecodesEmptySnapshot() {
        assertEquals(emptyList<ScreenNode>(), ScreenNodesCodec.decode(ScreenNodesCodec.encode(emptyList())))
        assertEquals(emptyList<ScreenNode>(), ScreenNodesCodec.decode(""))
    }

    @Test
    fun codecRejectsMalformedInputInsteadOfPartialSnapshots() {
        val encoded = ScreenNodesCodec.encode(
            listOf(ScreenNode(0, -1, "hallo", ScreenBox(0, 0, 5, 5), null, "TextView", null)),
        )
        assertNull(ScreenNodesCodec.decode("not a snapshot"))
        assertNull(ScreenNodesCodec.decode("$encoded\nbroken\tline"))
        assertNull(ScreenNodesCodec.decode("x\ty\tz")) // too few fields
        assertNull(ScreenNodesCodec.decode(encoded.replaceFirst("0,0,5,5", "not,a,box"))) // not numeric
        assertNull(ScreenNodesCodec.decode(encoded.replaceFirst("0,0,5,5", "0,0"))) // too few box parts
        // A dropped char-box field is malformed too, never silently repaired.
        assertNull(
            ScreenNodesCodec.decode(
                ScreenNodesCodec.encode(
                    listOf(
                        ScreenNode(
                            0, -1, "ab", ScreenBox(0, 0, 5, 5),
                            listOf(ScreenBox(0, 0, 2, 5), ScreenBox(2, 0, 5, 5)), "TextView", null,
                        ),
                    ),
                ).replaceFirst("2,0,5,5", ""),
            ),
        )
    }

    @Test
    fun codecKeepsTextAndBoxesAlignedAfterRoundTrip() {
        val nodes = UiAutomatorFixture.loadTwoColumn()
        val decoded = ScreenNodesCodec.decode(ScreenNodesCodec.encode(nodes))!!
        for ((original, read) in nodes.zip(decoded)) {
            assertEquals(original.text?.length ?: -1, read.text?.length ?: -1)
            if (original.charBoxes != null) assertEquals(original.charBoxes.size, read.charBoxes!!.size)
        }
    }
}
