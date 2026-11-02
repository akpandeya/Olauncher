package app.olauncher.ui.askai

import app.olauncher.R
import app.olauncher.data.store.AiChatMessage
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatThread
import app.olauncher.data.store.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AskAiEntries] — the Context-free entry-point decisions of the
 * Ask-AI surfaces (docs/design/ask-ai-chat.md §Entry points): visibility (gate +
 * valid config, hidden never greyed), the one chip shape ("Ask AI" / "Chat · n"),
 * the one row shape ("Ask AI about this word" + "n turns"), and the thread
 * sheet's rows (newest first, first-question snippet, delivered-turn count).
 */
class AskAiEntriesTest {

    // --- visibility: gate AND valid config ------------------------------------------

    @Test
    fun `visible when the gate is open and the config validates`() {
        assertTrue(AskAiEntries.visible(enabled = true, hasKey = true, model = "glm-5.3-flash", configError = null))
    }

    @Test
    fun `hidden when the feature switch is off`() {
        assertFalse(AskAiEntries.visible(enabled = false, hasKey = true, model = "glm-5.3-flash", configError = null))
    }

    @Test
    fun `hidden without a key`() {
        assertFalse(AskAiEntries.visible(enabled = true, hasKey = false, model = "glm-5.3-flash", configError = null))
    }

    @Test
    fun `hidden without a model`() {
        assertFalse(AskAiEntries.visible(enabled = true, hasKey = true, model = "", configError = null))
    }

    @Test
    fun `hidden when the provider config is invalid`() {
        assertFalse(AskAiEntries.visible(enabled = true, hasKey = true, model = "glm-5.3-flash", configError = "Base URL must start with https://"))
    }

    // --- the one chip shape ----------------------------------------------------------

    @Test
    fun `chip reads Ask AI with no delivered turns`() {
        assertEquals(AskAiEntries.Chip.Fresh, AskAiEntries.chip(0))
        assertEquals(R.string.ask_ai_chip, AskAiEntries.chipLabelRes(AskAiEntries.Chip.Fresh))
        assertEquals(0, AskAiEntries.chipTurns(AskAiEntries.Chip.Fresh))
    }

    @Test
    fun `chip reads Chat dot n with delivered turns`() {
        val chip = AskAiEntries.chip(4)
        assertEquals(AskAiEntries.Chip.Resumed(4), chip)
        assertEquals(R.string.ask_ai_chip_turns, AskAiEntries.chipLabelRes(chip))
        assertEquals(4, AskAiEntries.chipTurns(chip))
    }

    // --- the one row shape -----------------------------------------------------------

    @Test
    fun `word row has no turns suffix without history`() {
        assertEquals(0, AskAiEntries.wordRowTurns(0))
    }

    @Test
    fun `word row appends turns with history`() {
        assertEquals(4, AskAiEntries.wordRowTurns(4))
    }

    // --- the thread sheet ------------------------------------------------------------

    private fun subject(ref: String = "prep-two-way") = AiChatSubject(AiChatSubject.Kind.RULE, ref)

    private fun thread(
        id: String,
        updatedAt: Long,
        messages: List<AiChatMessage> = emptyList(),
    ) = AiChatThread(
        id = id,
        subject = subject(),
        createdAt = 1L,
        updatedAt = updatedAt,
        messages = messages,
    )

    private fun deliveredTurn(count: Int, startTs: Long = 1L): List<AiChatMessage> {
        val messages = mutableListOf<AiChatMessage>()
        for (i in 0 until count) {
            val ts = startTs + i * 10
            messages += AiChatMessage(AiChatMessage.Role.USER, "Frage $i", ts)
            messages += AiChatMessage(AiChatMessage.Role.ASSISTANT, "Antwort $i", ts + 1)
        }
        return messages
    }

    @Test
    fun `sheet rows are newest first with snippet and turn count`() {
        val rows = AskAiEntries.sheetRows(
            listOf(
                thread("old", updatedAt = 100, messages = deliveredTurn(7)),
                thread("new", updatedAt = 200, messages = deliveredTurn(2)),
            ),
        )
        assertEquals(listOf("new", "old"), rows.map { it.threadId })
        assertEquals("Frage 0", rows[0].snippet)
        assertEquals(2, rows[0].turns)
        assertEquals(200, rows[0].updatedAt)
        assertEquals(7, rows[1].turns)
    }

    @Test
    fun `sheet snippet is the first question even when it failed`() {
        val rows = AskAiEntries.sheetRows(
            listOf(
                thread(
                    "t",
                    updatedAt = 1,
                    messages = listOf(
                        AiChatMessage(AiChatMessage.Role.USER, "Wohin oder wo?", 1, failed = true),
                        AiChatMessage(AiChatMessage.Role.USER, "Nochmal: wohin oder wo?", 2),
                        AiChatMessage(AiChatMessage.Role.ASSISTANT, "Wohin.", 3),
                    ),
                ),
            ),
        )
        assertEquals("Wohin oder wo?", rows.single().snippet)
        // The failed question never reached the model: still not a delivered turn.
        assertEquals(1, rows.single().turns)
    }

    @Test
    fun `sheet rows are empty without threads`() {
        assertTrue(AskAiEntries.sheetRows(emptyList()).isEmpty())
    }

    @Test
    fun `sheet title shows the count only with two or more conversations`() {
        assertFalse(AskAiEntries.sheetTitleShowsCount(0))
        assertFalse(AskAiEntries.sheetTitleShowsCount(1))
        assertTrue(AskAiEntries.sheetTitleShowsCount(2))
    }

    @Test
    fun `the older-threads hint shows only with two or more threads`() {
        assertFalse(AskAiEntries.showOlderThreadsHint(0))
        assertFalse(AskAiEntries.showOlderThreadsHint(1))
        assertTrue(AskAiEntries.showOlderThreadsHint(2))
        assertTrue(AskAiEntries.showOlderThreadsHint(5))
    }

    // --- all-conversations list (#793) ----------------------------------------------

    private fun wordSubject(ref: String = "brücke") = AiChatSubject(AiChatSubject.Kind.WORD, ref)
    private fun ruleSubject(ref: String = "prep-two-way") = AiChatSubject(AiChatSubject.Kind.RULE, ref)
    private fun topicSubject(ref: String = "conjugation") = AiChatSubject(AiChatSubject.Kind.TOPIC, ref)

    private fun threadOf(
        id: String,
        subject: AiChatSubject,
        updatedAt: Long,
        messages: List<AiChatMessage> = emptyList(),
    ) = AiChatThread(
        id = id,
        subject = subject,
        createdAt = 1L,
        updatedAt = updatedAt,
        messages = messages,
    )

    @Test
    fun `conversation rows are newest first across word, rule and topic subjects`() {
        val rows = AskAiEntries.conversationRows(
            listOf(
                threadOf("oldest", topicSubject(), updatedAt = 100, messages = deliveredTurn(3)),
                threadOf("newest", wordSubject(), updatedAt = 300, messages = deliveredTurn(2)),
                threadOf("middle", ruleSubject(), updatedAt = 200, messages = deliveredTurn(5)),
            ),
        )
        assertEquals(listOf("newest", "middle", "oldest"), rows.map { it.threadId })
        assertEquals(listOf(wordSubject(), ruleSubject(), topicSubject()), rows.map { it.subject })
        assertEquals(listOf(2, 5, 3), rows.map { it.turns })
        assertEquals(listOf(300L, 200L, 100L), rows.map { it.updatedAt })
    }

    @Test
    fun `conversation preview is the last message`() {
        val rows = AskAiEntries.conversationRows(
            listOf(
                threadOf(
                    "t",
                    ruleSubject(),
                    updatedAt = 1,
                    messages = deliveredTurn(2) + listOf(
                        AiChatMessage(AiChatMessage.Role.USER, "Nochmal: wohin oder wo?", 30),
                    ),
                ),
            ),
        )
        assertEquals("Nochmal: wohin oder wo?", rows.single().preview)
        assertFalse(rows.single().previewFailed)
        // The unanswered question is not a delivered turn yet.
        assertEquals(2, rows.single().turns)
    }

    @Test
    fun `conversation preview flags a failed newest send with its error kind`() {
        val rows = AskAiEntries.conversationRows(
            listOf(
                threadOf(
                    "t",
                    ruleSubject(),
                    updatedAt = 1,
                    messages = deliveredTurn(1) + listOf(
                        AiChatMessage(
                            AiChatMessage.Role.USER,
                            "Wohin?",
                            20,
                            failed = true,
                            errorKind = AiResult.ErrorKind.NETWORK,
                        ),
                    ),
                ),
            ),
        )
        assertEquals("Wohin?", rows.single().preview)
        assertTrue(rows.single().previewFailed)
        assertEquals(AiResult.ErrorKind.NETWORK, rows.single().previewErrorKind)
        // The failed question never reached the model: still not a delivered turn.
        assertEquals(1, rows.single().turns)
    }

    @Test
    fun `conversation rows are empty without threads`() {
        assertTrue(AskAiEntries.conversationRows(emptyList()).isEmpty())
    }

    @Test
    fun `new conversation subject is the newest thread's subject, null without threads`() {
        assertEquals(
            wordSubject(),
            AskAiEntries.newConversationSubject(
                listOf(
                    threadOf("old", ruleSubject(), updatedAt = 100),
                    threadOf("new", wordSubject(), updatedAt = 200),
                ),
            ),
        )
        assertNull(AskAiEntries.newConversationSubject(emptyList()))
    }

    @Test
    fun `conversation kind labels map one resource per subject kind`() {
        assertEquals(R.string.ask_ai_conversations_kind_word, AskAiEntries.conversationKindLabelRes(AiChatSubject.Kind.WORD))
        assertEquals(R.string.ask_ai_conversations_kind_rule, AskAiEntries.conversationKindLabelRes(AiChatSubject.Kind.RULE))
        assertEquals(R.string.ask_ai_conversations_kind_topic, AskAiEntries.conversationKindLabelRes(AiChatSubject.Kind.TOPIC))
    }
}
