package app.olauncher.helper

import app.olauncher.helper.DictionaryHandoff.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryHandoffTest {
    @Test fun `process text wins when both handlers accept text`() {
        assertAttempts(true, true, Action.PROCESS_TEXT, listOf(Action.PROCESS_TEXT))
    }

    @Test fun `send is used when process text is unsupported`() {
        assertAttempts(false, true, Action.SEND, listOf(Action.SEND))
    }

    @Test fun `failed process text falls through to send`() {
        assertAttempts(true, true, Action.SEND, listOf(Action.PROCESS_TEXT, Action.SEND))
    }

    @Test fun `failed send without process text copies and launches`() {
        assertAttempts(false, true, Action.COPY_AND_LAUNCH, listOf(Action.SEND, Action.COPY_AND_LAUNCH))
    }

    @Test fun `both failed handlers fall through to clipboard and app launch`() {
        assertAttempts(true, true, Action.COPY_AND_LAUNCH, Action.entries)
    }

    @Test fun `app with neither handler copies and launches directly`() {
        assertAttempts(false, false, Action.COPY_AND_LAUNCH, listOf(Action.COPY_AND_LAUNCH))
    }

    @Test fun `process text failure without send copies and launches`() {
        assertAttempts(true, false, Action.COPY_AND_LAUNCH, listOf(Action.PROCESS_TEXT, Action.COPY_AND_LAUNCH))
    }

    @Test fun `unavailable app reports failure after ordered attempts`() {
        val attempts = mutableListOf<Action>()
        assertFalse(DictionaryHandoff.open(true, true) { attempts.add(it); false })
        assertEquals(Action.entries, attempts)
    }

    private fun assertAttempts(
        process: Boolean,
        send: Boolean,
        succeeds: Action,
        expected: List<Action>,
    ) {
        val attempts = mutableListOf<Action>()
        assertTrue(DictionaryHandoff.open(process, send) { attempts.add(it); it == succeeds })
        assertEquals(expected, attempts)
    }
}
