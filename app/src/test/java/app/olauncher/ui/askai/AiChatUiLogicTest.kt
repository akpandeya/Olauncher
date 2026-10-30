package app.olauncher.ui.askai

import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatGroundingPayload
import app.olauncher.data.store.AiChatMessage
import app.olauncher.data.store.AiChatState
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatSummary
import app.olauncher.data.store.AiChatThread
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.AiUsage
import app.olauncher.data.store.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiChatUiLogic] — the chat screen's rendering decisions
 * (docs/design/ask-ai-chat.md §Chat screen, §Reliability): row order, compaction
 * marker placement, newest-failed-turn retryability, the header subject and the
 * usage-line formatting. Pure JVM, no Android.
 */
class AiChatUiLogicTest {

    private val ruleSubject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way")
    private val wordSubject = AiChatSubject(AiChatSubject.Kind.WORD, "die straße")

    private fun user(text: String, ts: Long, failed: Boolean = false, kind: AiResult.ErrorKind? = null) =
        AiChatMessage(AiChatMessage.Role.USER, text, ts, failed = failed, errorKind = kind)

    private fun ai(text: String, ts: Long, usage: AiUsage? = null) =
        AiChatMessage(AiChatMessage.Role.ASSISTANT, text, ts, usage = usage)

    private fun state(
        thread: AiChatThread?,
        pendingQuestion: String? = null,
        storeError: Boolean = false,
        omittedUpTo: Int? = null,
        lastUsage: AiUsage? = null,
    ) = AiChatState(thread, pendingQuestion, storeError, omittedUpTo, lastUsage)

    // --- rows ------------------------------------------------------------------------

    @Test
    fun `rows alternate user tiles and assistant panels in transcript order`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            messages = listOf(user("Q1", 1), ai("A1", 2), user("Q2", 3), ai("A2", 4)),
        )
        val rows = AiChatUiLogic.rows(state(thread))
        assertEquals(
            listOf(
                AiChatUiLogic.Row.UserTile("Q1", failed = false),
                AiChatUiLogic.Row.AssistantPanel("A1"),
                AiChatUiLogic.Row.UserTile("Q2", failed = false),
                AiChatUiLogic.Row.AssistantPanel("A2"),
            ),
            rows,
        )
    }

    @Test
    fun `a failed turn renders a dimmed tile, the kind's error line and retry on the newest`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            messages = listOf(
                user("Q1", 1), ai("A1", 2),
                user("Q2", 3, failed = true, kind = AiResult.ErrorKind.RATE_LIMIT),
            ),
        )
        val rows = AiChatUiLogic.rows(state(thread))
        assertEquals(AiChatUiLogic.Row.UserTile("Q2", failed = true), rows[2])
        assertEquals(
            AiChatUiLogic.Row.ErrorLine(kind = AiResult.ErrorKind.RATE_LIMIT, retryable = true, question = "Q2"),
            rows[3],
        )
    }

    @Test
    fun `an older failed turn is not retryable once a newer turn exists`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            messages = listOf(
                user("Q1", 1, failed = true, kind = AiResult.ErrorKind.NETWORK),
                user("Q2", 3, failed = true, kind = AiResult.ErrorKind.NETWORK),
            ),
        )
        val rows = AiChatUiLogic.rows(state(thread))
        // Newest failed turn (last message) retries; the older one asks again.
        assertEquals(false, (rows[1] as AiChatUiLogic.Row.ErrorLine).retryable)
        assertEquals(true, (rows[3] as AiChatUiLogic.Row.ErrorLine).retryable)
    }

    @Test
    fun `thinking and the storage-failure line sit at the end while waiting`() {
        val thread = AiChatThread("t", ruleSubject, 1, 2, messages = listOf(user("Q1", 1)))
        val rows = AiChatUiLogic.rows(
            state(thread, pendingQuestion = "Q2", storeError = true)
        )
        assertEquals(listOf(AiChatUiLogic.Row.UserTile("Q1", false), AiChatUiLogic.Row.Thinking, AiChatUiLogic.Row.StoreError), rows)
    }

    @Test
    fun `a fresh session shows only waiting and storage lines`() {
        assertEquals(
            listOf(AiChatUiLogic.Row.Thinking),
            AiChatUiLogic.rows(state(thread = null, pendingQuestion = "Q")),
        )
        assertEquals(
            listOf(AiChatUiLogic.Row.StoreError),
            AiChatUiLogic.rows(AiChatState(null, null, storeError = true, omittedUpTo = null, lastUsage = null)),
        )
    }

    // --- compaction markers ------------------------------------------------------------

    @Test
    fun `the summary marker sits where its turns were and carries summary plus originals`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            summaries = listOf(AiChatSummary("Earlier: wohin/wo.", coversUpTo = 1, createdAt = 5)),
            messages = listOf(user("Q1", 1), ai("A1", 2), user("Q2", 3), ai("A2", 4)),
        )
        val rows = AiChatUiLogic.rows(state(thread))
        assertEquals(
            listOf(
                AiChatUiLogic.Row.UserTile("Q1", false),
                AiChatUiLogic.Row.AssistantPanel("A1"),
                AiChatUiLogic.Row.CompactionMarker(
                    summarised = true,
                    summaryText = "Earlier: wohin/wo.",
                    originals = listOf(AiChatUiLogic.OriginalTurn("Q1", "A1")),
                ),
                AiChatUiLogic.Row.UserTile("Q2", false),
                AiChatUiLogic.Row.AssistantPanel("A2"),
            ),
            rows,
        )
    }

    @Test
    fun `the truncation fallback marker sits after the omitted prefix`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            messages = listOf(user("Q1", 1), ai("A1", 2), user("Q2", 3), ai("A2", 4), user("Q3", 5), ai("A3", 6)),
        )
        val rows = AiChatUiLogic.rows(state(thread, omittedUpTo = 3))
        // Omitted marker after message index 3, summarised = false, no summary text.
        val marker = rows.filterIsInstance<AiChatUiLogic.Row.CompactionMarker>().single()
        assertEquals(false, marker.summarised)
        assertNull(marker.summaryText)
        assertEquals(
            listOf(AiChatUiLogic.OriginalTurn("Q1", "A1"), AiChatUiLogic.OriginalTurn("Q2", "A2")),
            marker.originals,
        )
        assertEquals(4, rows.indexOf(marker))
    }

    @Test
    fun `failed turns are never marker originals`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            summaries = listOf(AiChatSummary("s", coversUpTo = 2, createdAt = 5)),
            messages = listOf(user("Q0", 0, failed = true), user("Q1", 1), ai("A1", 2), user("Q2", 3), ai("A2", 4)),
        )
        val rows = AiChatUiLogic.rows(state(thread))
        val marker = rows.filterIsInstance<AiChatUiLogic.Row.CompactionMarker>().single()
        assertEquals(listOf(AiChatUiLogic.OriginalTurn("Q1", "A1")), marker.originals)
    }

    @Test
    fun `the omitted marker still marks the gap when a summary exists at another index`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            summaries = listOf(AiChatSummary("s", coversUpTo = 1, createdAt = 5)),
            messages = listOf(
                user("Q1", 1), ai("A1", 2), user("Q2", 3), ai("A2", 4), user("Q3", 5), ai("A3", 6),
            ),
        )
        val rows = AiChatUiLogic.rows(state(thread, omittedUpTo = 3))
        val markers = rows.filterIsInstance<AiChatUiLogic.Row.CompactionMarker>()
        assertEquals(2, markers.size)
        assertEquals(true, markers[0].summarised)
        assertEquals(false, markers[1].summarised)
        assertNull(markers[1].summaryText)
        // After index 3's turn; the summary marker before it shifted the position.
        assertEquals(5, rows.indexOf(markers[1]))
    }

    // --- header -------------------------------------------------------------------------

    @Test
    fun `a provisional session reads New conversation regardless of the fallback subject`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            groundings = listOf(wordGrounding()),
            messages = listOf(user("Q1", 1)),
        )
        assertEquals(
            AiChatUiLogic.Header.NewConversation,
            AiChatUiLogic.header(state(thread), provisional = true),
        )
    }

    @Test
    fun `a threadless fresh session reads the opening subject's header`() {
        assertEquals(
            AiChatUiLogic.Header.Rule("Two-way prepositions"),
            AiChatUiLogic.header(
                state(thread = null),
                provisional = false,
                openingSubject = ruleSubject,
            ),
        )
        assertEquals(
            AiChatUiLogic.Header.Word("die Straße"),
            AiChatUiLogic.header(
                state(thread = null),
                provisional = false,
                openingSubject = wordSubject,
                openingGroundings = listOf(wordGrounding()),
            ),
        )
        // Without an opening subject (a resume whose thread failed to load)
        // "New conversation" still applies.
        assertEquals(
            AiChatUiLogic.Header.NewConversation,
            AiChatUiLogic.header(state(thread = null), provisional = false),
        )
    }

    @Test
    fun `a word subject reads the citation text as written from the grounding`() {
        val thread = AiChatThread(
            "t", wordSubject, 1, 2,
            groundings = listOf(wordGrounding(), missGrounding()),
            messages = listOf(user("Q1", 1), ai("A1", 2)),
        )
        assertEquals(AiChatUiLogic.Header.Word("die Straße"), AiChatUiLogic.header(state(thread), provisional = false))
    }

    @Test
    fun `a rule subject reads the help content's title`() {
        val thread = AiChatThread("t", ruleSubject, 1, 2, messages = listOf(user("Q1", 1), ai("A1", 2)))
        val header = AiChatUiLogic.header(state(thread), provisional = false)
        assertTrue(header is AiChatUiLogic.Header.Rule)
        assertEquals("Two-way prepositions", (header as AiChatUiLogic.Header.Rule).title)
    }

    @Test
    fun `a topic subject reads the drill type's label resource`() {
        val subject = AiChatSubject(AiChatSubject.Kind.TOPIC, DrillType.PREPOSITIONS.id)
        val thread = AiChatThread("t", subject, 1, 2, messages = listOf(user("Q1", 1), ai("A1", 2)))
        assertEquals(
            AiChatUiLogic.Header.Topic(app.olauncher.R.string.drill_type_prepositions),
            AiChatUiLogic.header(state(thread), provisional = false),
        )
    }

    // --- usage line ---------------------------------------------------------------------

    @Test
    fun `usage reports the latest request's input tokens and delivered turns`() {
        val thread = AiChatThread(
            "t", ruleSubject, 1, 2,
            messages = listOf(user("Q1", 1), ai("A1", 2), user("Q2", 3, failed = true), user("Q3", 4)),
        )
        val s = state(thread, lastUsage = AiUsage(inputTokens = 1847, outputTokens = 210))
        assertEquals(1847, AiChatUiLogic.usageTokens(s))
        assertEquals(1, AiChatUiLogic.deliveredTurns(s))
        assertNull(AiChatUiLogic.usageTokens(state(thread, lastUsage = null)))
    }

    @Test
    fun `token counts format as 1_8k above a thousand and plain below`() {
        assertEquals("1.8k", AiChatUiLogic.formatTokenCount(1847))
        assertEquals("1.0k", AiChatUiLogic.formatTokenCount(1000))
        assertEquals("823", AiChatUiLogic.formatTokenCount(823))
        assertEquals("0", AiChatUiLogic.formatTokenCount(0))
    }

    // --- fixtures -----------------------------------------------------------------------

    private fun wordGrounding() = AiChatGrounding(
        "g-word",
        AiChatGroundingPayload.Word(
            text = "die Straße",
            meaning = "the street",
            context = "Die Straße ist breit.",
        ),
    )

    private fun missGrounding() = AiChatGrounding(
        "g-miss",
        AiChatGroundingPayload.Miss(
            prompt = "___ Straße",
            picked = "der",
            answer = "die",
            ruleId = "noun-e",
            chips = listOf("feminine"),
        ),
    )
}
