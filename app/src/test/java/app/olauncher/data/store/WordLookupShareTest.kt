package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordLookupShareTest {
    @Test fun `single word`() = assertEquals("Fernweh", WordLookupShare.parse("Fernweh"))
    @Test fun `quotes and spaces stripped`() = assertEquals("Fernweh", WordLookupShare.parse("  „Fernweh“ \n"))
    @Test fun `three words ok`() = assertEquals("sich freuen auf", WordLookupShare.parse("sich freuen auf"))
    @Test fun `four words rejected`() = assertNull(WordLookupShare.parse("ich freue mich sehr"))
    @Test fun `blank rejected`() = assertNull(WordLookupShare.parse("   "))
    @Test fun `url rejected`() = assertNull(WordLookupShare.parse("https://example.com/x"))
    @Test fun `punctuation only rejected`() = assertNull(WordLookupShare.parse("!!! ???"))
    @Test fun `long sentence rejected`() =
        assertNull(WordLookupShare.parse("Das ist ein langer Satz mit vielen Wörtern darin."))
}
