package app.olauncher.helper

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDateTextTest {
    private val date = LocalDate.of(2026, 10, 21)

    @Test fun germanPlain() =
        assertEquals("Mittwoch, 21. Oktober", HomeDateText.format(date, true, false, Locale.UK))

    @Test fun germanCombined() =
        assertEquals("Mittwoch 21. Oktober", HomeDateText.format(date, true, true, Locale.UK))

    @Test fun ukPlain() =
        assertEquals("Wed, 21 Oct", HomeDateText.format(date, false, false, Locale.UK))

    @Test fun ukCombined() =
        assertEquals("Wed 21 Oct", HomeDateText.format(date, false, true, Locale.UK))
}
