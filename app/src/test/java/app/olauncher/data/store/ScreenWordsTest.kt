package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWordsTest {

    // Real dump (tagesschau.de, Chrome, Android 16 emulator).
    private val headline = "Nach der Elternzeit Wenn die Rückkehr in den Job schwierig wird"
    private val headlineLink = "Nach der Elternzeit: Wenn die Rückkehr in den Job schwierig wird"
    private val lead =
        "Viele Mütter und Väter wollen nach der Elternzeit wieder arbeiten, häufig in Teilzeit. " +
            "Doch nicht immer finden sie mit ihren Arbeitgebern eine Lösung. Betroffene berichten " +
            "von Benachteiligungen, Fachleute fordern mehr Schutz."
    private val julie =
        "Julie K. ist 22 Jahre alt und Mutter eines kleinen Sohnes. Vor seiner Geburt hat sie eine " +
            "Ausbildung zur Tiermedizinischen Fachangestellten in einer Tierklinik begonnen. Nach " +
            "der Elternzeit will sie ihre Ausbildung unbedingt fortsetzen, allerdings in Teilzeit. " +
            "Denn in der Klinik wird auch nachts und am Wochenende gearbeitet."
    private val hatNode =
        "hat den Betreiber der Tierklinik, AniCura, mit Julies Schilderungen konfrontiert. Das " +
            "Unternehmen erklärt, eine Teilzeitausbildung sei grundsätzlich möglich. Im konkreten " +
            "Fall habe man verschiedene Optionen geprüft und sich mehrfach mit der zuständigen " +
            "Tierärztekammer beraten."
    private val breadcrumb = "tagesschau.de/inland/gesellschaft/elternzeit-rueckkehr-job-100.html"

    private fun node(
        text: String? = null,
        className: String? = "android.widget.TextView",
        contentDescription: String? = null,
    ) = ScreenNode(1, -1, text, ScreenBox(0, 0, 100, 20), null, className, contentDescription)

    private fun positioned(nodes: List<ScreenNode>): List<ScreenNode> = nodes.filter { n ->
        val b = n.bounds
        b != null && b.width > 0 && b.height > 0
    }

    @Test
    fun noiseTableDropsNewsAppClutter() {
        val noise = listOf(
            "content description only" to node(contentDescription = "Zurück"),
            "empty text" to node(text = ""),
            "media badge" to node(text = "Video | 14 Min."),
            "media badge audio" to node(text = "Audio | 05 Min."),
            "standalone duration" to node(text = "14 Min."),
            "credit" to node(text = "picture alliance/dpa"),
            "foto credit" to node(text = "Foto: dpa"),
            "clock time" to node(text = "09:12 Uhr"),
            "bare clock time" to node(text = "20:15"),
            "date" to node(text = "22.10.2026"),
            "date and time" to node(text = "22.10.2026 09:12 Uhr"),
            "bullet timestamp" to node(text = "07.07.2026 • 09:14 Uhr"),
            "stand timestamp" to node(text = "Stand: 24.10.2026 • 16:26 Uhr"),
            "byline" to node(text = "Von Esra Özer, NDR"),
            "player badge" to node(text = "Player: video"),
            "player badge audio" to node(text = "Player: audio"),
            "copyright" to node(text = "© ARD-aktuell / tagesschau.de"),
            "share label" to node(text = "Teilen Teilen"),
            "share label facebook" to node(text = "Facebook"),
            "share label whatsapp" to node(text = "WhatsApp"),
            "share label mail" to node(text = "Mail"),
            "share label drucken" to node(text = "Drucken"),
            "tag chip" to node(text = "Elternzeit"),
            "tag chip arbeit" to node(text = "Arbeit"),
            "tag chip teilzeit" to node(text = "Teilzeit"),
            "comment count" to node(text = "(80)"),
            "image alt text" to node(text = "Mutter hilft Kind beim Anziehen", className = "android.widget.ImageView"),
        )
        for ((label, candidate) in noise) assertTrue(label, ScreenWords.isNoise(candidate))

        val keep = listOf(
            "kicker and headline" to node(text = headline),
            "headline link" to node(text = headlineLink),
            "lead" to node(text = lead),
            "julie prose" to node(text = julie),
            "name not swallowed by byline" to node(text = "Julie K. ist 22 Jahre alt und Mutter eines kleinen Sohnes."),
            "von with lowercase next word" to node(text = "Von der Kita bis zur Schulbetreuung fehlt oft ein passender Platz."),
            "nav chip" to node(text = "Wirtschaft"),
            "nav chip arbeitsmarkt" to node(text = "Arbeitsmarkt"),
            "breadcrumb" to node(text = breadcrumb),
            "teaser headline with percent" to node(text = "Equal Pay Day:Weiterhin im Schnitt 16% weniger Gehalt für Frauen"),
        )
        for ((label, candidate) in keep) assertFalse(label, ScreenWords.isNoise(candidate))
    }

    @Test
    fun realDumpDropsNoiseAndKeepsArticleText() {
        val result = ScreenWords.build(UiAutomatorFixture.loadTagesschau())
        val texts = result.nodes.mapNotNull { it.text }

        // Short labels must be matched exactly — they occur inside prose otherwise.
        val exactNoise = listOf(
            "Player: video", "14 Min", "Teilen Teilen", "Facebook", "WhatsApp", "Mail",
            "Drucken", "Elternzeit", "Arbeit", "Teilzeit", "(80)",
        )
        for (clutter in exactNoise) assertTrue("dropped: $clutter", texts.none { it == clutter })

        val substringNoise = listOf(
            "Stand: 24.10.2026 • 16:26 Uhr", "Von Esra Özer, NDR",
            "© ARD-aktuell / tagesschau.de", "07.07.2026 • 09:14 Uhr",
        )
        for (clutter in substringNoise) assertTrue("dropped: $clutter", texts.none { it.contains(clutter) })

        val kept = listOf(
            "kicker and headline" to headline,
            "headline link" to headlineLink,
            "lead" to lead,
            "julie prose" to julie,
            "fragment" to hatNode,
            "nav chip" to "Wirtschaft",
            "nav chip arbeitsmarkt" to "Arbeitsmarkt",
            "breadcrumb" to breadcrumb,
        )
        for ((label, prose) in kept) assertTrue(label, texts.any { it.contains(prose) })

        // Positioned kept nodes stay in screen order; zero-height off-screen nodes trail.
        assertEquals(
            listOf(breadcrumb, "Wirtschaft", "Arbeitsmarkt", headlineLink, "Panorama 3: Karriereknick durch Elternzeit", headline, lead, julie),
            texts.take(8),
        )
        val zeroHeightProse = "Nach ihren Angaben hatte sie bereits Monate vorher"
        val zeroIdx = texts.indexOfFirst { it.startsWith(zeroHeightProse) }
        val julieIdx = texts.indexOfFirst { it == julie }
        assertTrue(julieIdx in 0 until zeroIdx)
    }

    @Test
    fun realDumpReadingOrderIsTopToBottom() {
        // readingOrder sees the nodes build() hands it after noise filtering — text-bearing
        // nodes only (text-less containers are noise). The "Stand:" line is noise too, yet
        // the order itself must still sequence it correctly.
        val ordered = ScreenWords.readingOrder(positioned(UiAutomatorFixture.loadTagesschau()).filter { it.text != null })
            .mapNotNull { it.text }

        fun indexOf(prefix: String) = ordered.indexOfFirst { it.startsWith(prefix) }
        // Content column, top to bottom.
        val kicker = indexOf("Nach der Elternzeit Wenn die Rückkehr")
        val stand = indexOf("Stand: 24.10.2026")
        val lead = indexOf("Viele Mütter und Väter")
        val byline = indexOf("Von Esra Özer, NDR")
        assertTrue(kicker in 0 until stand)
        assertTrue(stand < lead)
        assertTrue(lead < byline)

        // Horizontally adjacent nav chips stay left-to-right on their row.
        val wirtschaft = indexOf("Wirtschaft")
        val arbeitsmarkt = indexOf("Arbeitsmarkt")
        assertTrue(wirtschaft in 0 until arbeitsmarkt)
        assertTrue(arbeitsmarkt < indexOf("Nach der Elternzeit: Wenn die Rückkehr"))
    }

    @Test
    fun realDumpZeroHeightNodesSortUnpositionedWithoutBoxes() {
        // Chrome parks off-screen content at zero-height bounds. Those nodes survive the
        // noise filter and sort as unpositioned behind every positioned node — but their
        // geometry is invisible on screen, so no word box is guessed from it.
        val result = ScreenWords.build(UiAutomatorFixture.loadTagesschau())
        val texts = result.nodes.mapNotNull { it.text }
        val zeroNode = result.nodes.first { it.text!!.startsWith("Nach ihren Angaben hatte sie bereits") }
        val zeroIdx = texts.indexOf(zeroNode.text)
        val julieIdx = texts.indexOf(julie)
        assertTrue(julieIdx in 0 until zeroIdx) // positioned content first

        assertEquals(0, zeroNode.bounds!!.height)
        assertEquals(zeroNode.text!!.length, zeroNode.charBoxes!!.size)
        assertEquals(2784, zeroNode.charBoxes!!.first().top)
        assertEquals(2784, zeroNode.charBoxes!!.first().bottom)
        val word = result.words.first { it.nodeId == zeroNode.id }
        assertNull(word.box)
    }

    @Test
    fun realDumpFragmentedRichTextKeepsNodeParagraphs() {
        val result = ScreenWords.build(UiAutomatorFixture.loadTagesschau())
        // "Der " + "NDR" + " hat den Betreiber…" are three sibling nodes forming one sentence.
        // Documented limitation: paragraph boundaries come from nodes, so paragraphOf returns
        // each fragment's own text, never the stitched sentence.
        val derNode = result.nodes.first { it.text == "Der " }
        val ndrNode = result.nodes.first { it.text == "NDR" }
        val hatNode = result.nodes.first { it.text!!.trim().startsWith("hat den Betreiber der Tierklinik") }
        assertNotEquals(derNode.id, ndrNode.id)
        assertNotEquals(ndrNode.id, hatNode.id)

        val der = result.words.first { it.nodeId == derNode.id }
        val ndr = result.words.first { it.nodeId == ndrNode.id }
        val hat = result.words.first { it.nodeId == hatNode.id && it.text == "Betreiber" }
        assertEquals(listOf("Der"), result.words.filter { it.nodeId == derNode.id }.map { it.text })
        assertEquals("Der ", result.paragraphOf(der))
        assertEquals("NDR", result.paragraphOf(ndr))
        assertEquals(hatNode.text, result.paragraphOf(hat))
    }

    @Test
    fun twoColumnReadingOrderKeepsColumnsTogether() {
        val result = ScreenWords.build(UiAutomatorFixture.loadTwoColumn())
        val orderedTexts = result.nodes.mapNotNull { it.text }

        // Row-major everywhere except the two-column facts box, which reads column-major:
        // first column top to bottom, then second column.
        val expected = listOf(
            "Nach der Elternzeit",
            "Wenn die Rückkehr in den Job schwierig wird",
            "Viele Mütter und Väter wollen nach der Elternzeit wieder arbeiten, häufig in Teilzeit. Von der Kita bis zur Schulbetreuung fehlt oft ein passender Platz.",
            "Dauer: bis zu drei Jahre",
            "Ort: Betrieb und Berufsschule",
            "Einstieg: auch ohne Abschluss",
            "Vergütung: meist nach Tarif",
            "Bewerbung: online und unkompliziert",
            "Start: jederzeit möglich",
            "Beratung und Weiterbildungen können den Wiedereinstieg erleichtern.",
        )
        assertEquals(expected, orderedTexts)

        // Spelled out: every word of the left column comes before every word of the right column.
        val leftIds = result.nodes.filter { it.text!!.startsWith("Dauer:") || it.text!!.startsWith("Ort:") || it.text!!.startsWith("Einstieg:") }.map { it.id }.toSet()
        val rightIds = result.nodes.filter { it.text!!.startsWith("Vergütung:") || it.text!!.startsWith("Bewerbung:") || it.text!!.startsWith("Start:") }.map { it.id }.toSet()
        val lastLeftIndex = result.words.indexOfLast { it.nodeId in leftIds }
        val firstRightIndex = result.words.indexOfFirst { it.nodeId in rightIds }
        assertTrue(lastLeftIndex in 0 until firstRightIndex)
    }

    @Test
    fun fixtureCharBoxesSplitNodeBoundsEvenlyPerCharacter() {
        val nodes = UiAutomatorFixture.loadTagesschau()
        val headlineNode = nodes.first { it.text == headline }
        val bounds = headlineNode.bounds!!
        val charWidth = bounds.width / headline.length

        assertEquals(headline.length, headlineNode.charBoxes!!.size)
        // uiautomator has no per-character data: box 0 is the first charWidth slice of the bounds.
        assertEquals(ScreenBox(bounds.left, bounds.top, bounds.left + charWidth, bounds.bottom), headlineNode.charBoxes!![0])

        val words = ScreenWords.build(nodes)
        val nach = words.words.first { it.text == "Nach" && it.nodeId == headlineNode.id }
        assertEquals(ScreenBox(60, 1382, 60 + 4 * charWidth, 1694), nach.box)
        val rueckkehr = words.words.first { it.text == "Rückkehr" && it.nodeId == headlineNode.id }
        assertEquals(ScreenBox(60 + 29 * charWidth, 1382, 60 + 37 * charWidth, 1694), rueckkehr.box)
    }

    @Test
    fun zeroAreaHiddenNodesYieldNoWordGeometry() {
        // Probe rows 17/20/26 (tagesschau capture): hidden WebView chrome nodes parked at
        // 0,0,0,0 with 0,0,0,0 character boxes. Their words must carry no box — otherwise
        // a tap at the frame's top-left corner selects invisible words.
        fun hidden(id: Int, text: String) = ScreenNode(
            id, id - 1, text, ScreenBox(0, 0, 0, 0),
            List(text.length) { ScreenBox(0, 0, 0, 0) },
            "android.widget.TextView", null,
        )
        val nodes = listOf(
            hidden(16, "Hauptnavigation"),
            hidden(19, "Nebennavigation"),
            hidden(25, "Fußzeile"),
        )
        val words = ScreenWords.build(nodes).words
        assertEquals(listOf("Hauptnavigation", "Nebennavigation", "Fußzeile"), words.map { it.text })
        assertTrue(words.all { it.box == null })
    }

    @Test
    fun degenerateWordBoxesAreDroppedEvenOnPositionedNodes() {
        // A positioned node whose character boxes all collapsed to zero area: the union
        // per word is zero-area, so it counts as no geometry either.
        val text = "Hallo Welt"
        val node = ScreenNode(
            0, -1, text, ScreenBox(0, 0, 500, 40),
            List(text.length) { ScreenBox(0, 0, 0, 0) },
            "android.widget.TextView", null,
        )
        val words = ScreenWords.build(listOf(node)).words
        assertEquals(listOf("Hallo", "Welt"), words.map { it.text })
        assertTrue(words.all { it.box == null })
    }

    @Test
    fun staleCharBoxesOnHiddenNodeYieldNoGeometry() {
        // A hidden node parked at 0,0,0,0 whose character boxes are stale but non-zero:
        // the bounds guard is the only thing that catches this — the union guard alone
        // would accept the boxes and hit-test invisible words at their stale place.
        val text = "Hallo Welt"
        val node = ScreenNode(
            0, -1, text, ScreenBox(0, 0, 0, 0),
            List(text.length) { ScreenBox(10, 10, 20, 30) },
            "android.widget.TextView", null,
        )
        val words = ScreenWords.build(listOf(node)).words
        assertEquals(listOf("Hallo", "Welt"), words.map { it.text })
        assertTrue(words.all { it.box == null })
    }

    @Test
    fun nodeWithoutCharBoxesGetsNoGeometry() {
        val noBoxes = ScreenNode(0, -1, "Hallo Welt", ScreenBox(0, 0, 100, 20), null, "android.widget.TextView", null)
        val words = ScreenWords.build(listOf(noBoxes)).words
        assertEquals(listOf("Hallo", "Welt"), words.map { it.text })
        assertTrue(words.all { it.box == null })

        // A box count that does not match the text length would misplace words — no geometry.
        val mismatched = ScreenNode(0, -1, "Hallo Welt", ScreenBox(0, 0, 100, 20), listOf(ScreenBox(0, 0, 10, 10)), "android.widget.TextView", null)
        val mismatchedWords = ScreenWords.build(listOf(mismatched)).words
        assertEquals(listOf("Hallo", "Welt"), mismatchedWords.map { it.text })
        assertTrue(mismatchedWords.all { it.box == null })
    }

    @Test
    fun paragraphOfReturnsContainingNodeText() {
        val result = ScreenWords.build(UiAutomatorFixture.loadTagesschau())

        val headlineNode = result.nodes.first { it.text == headline }
        val rueckkehr = result.words.first { it.nodeId == headlineNode.id && it.text == "Rückkehr" }
        assertEquals(headline, result.paragraphOf(rueckkehr))
        // The word keeps its place in the paragraph, so ContextSentence can cut the sentence.
        assertEquals(
            headline,
            ContextSentence.around(result.paragraphOf(rueckkehr), rueckkehr.charRange.first, rueckkehr.charRange.last + 1),
        )

        // Fragmented rich text: each sibling fragment keeps its own node-sized paragraph.
        val ndrNode = result.nodes.first { it.text == "NDR" }
        val ndr = result.words.first { it.nodeId == ndrNode.id }
        assertEquals("NDR", result.paragraphOf(ndr))

        val twoColumn = ScreenWords.build(UiAutomatorFixture.loadTwoColumn())
        val dauer = twoColumn.words.first { it.text == "Dauer:" }
        assertEquals("Dauer: bis zu drei Jahre", twoColumn.paragraphOf(dauer))

        assertNull(result.nodes.find { it.id == 999 })
        assertEquals("", result.paragraphOf(ScreenWord("x", null, 999, 0 until 1)))
    }
}
