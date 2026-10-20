package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoBackupRetentionTest {

    private fun name(day: Int) = Backup.defaultBackupFileName(LocalDate.of(2027, 1, day))

    @Test
    fun keepsNewest7ByDate() {
        val names = (1..10).map { name(it) }.shuffled()
        val deleted = AutoBackupRetention.namesToDelete(names)
        assertEquals(setOf(name(1), name(2), name(3)), deleted.toSet())
        assertEquals(3, deleted.size)
    }

    @Test
    fun ignoresNonMatchingNames() {
        val names = (1..9).map { name(it) } +
            listOf("notes.json", "thumbline-backup-foo.json", "thumbline-backup-2027-13-45.json", "thumbline-backup-2027-01-01.json.tmp")
        val deleted = AutoBackupRetention.namesToDelete(names)
        assertEquals(setOf(name(1), name(2)), deleted.toSet())
    }

    @Test
    fun fewerThanSevenDeletesNothing() {
        assertTrue(AutoBackupRetention.namesToDelete((1..6).map { name(it) }).isEmpty())
        assertTrue(AutoBackupRetention.namesToDelete(emptyList()).isEmpty())
    }

    @Test
    fun beforeThreeGoesToTodayThree() {
        val now = ZonedDateTime.of(2027, 6, 10, 1, 30, 0, 0, ZoneId.of("UTC"))
        assertEquals(Duration.ofMinutes(90).toMillis(), AutoBackupRetention.millisUntilNextRun(now))
    }

    @Test
    fun exactlyThreeGoesToTomorrow() {
        val now = ZonedDateTime.of(2027, 6, 10, 3, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(Duration.ofHours(24).toMillis(), AutoBackupRetention.millisUntilNextRun(now))
    }

    @Test
    fun afterThreeGoesToTomorrow() {
        val now = ZonedDateTime.of(2027, 6, 10, 15, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(Duration.ofHours(12).toMillis(), AutoBackupRetention.millisUntilNextRun(now))
    }

    @Test
    fun springForwardDayIsShorter() {
        // Europe/Berlin 2027-03-28: 02:00 -> 03:00, so 23-hour day.
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2027, 3, 27, 12, 0, 0, 0, zone)
        // 12:00 Sat to 03:00 Sun CEST: 12h to midnight + 2h (00:00 -> 03:00 is 2h elapsed).
        assertEquals(Duration.ofHours(14).toMillis(), AutoBackupRetention.millisUntilNextRun(now))
        val sunday = ZonedDateTime.of(2027, 3, 28, 3, 0, 0, 0, zone)
        val toMonday = AutoBackupRetention.millisUntilNextRun(sunday)
        assertEquals(Duration.ofHours(24).toMillis(), toMonday)
    }

    @Test
    fun fallBackDayIsLonger() {
        // Europe/Berlin 2027-10-31: 03:00 -> 02:00, so 25-hour day.
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2027, 10, 30, 12, 0, 0, 0, zone)
        // 12h to midnight, then 00:00 -> 03:00 on a 25h day is 4h elapsed.
        assertEquals(Duration.ofHours(16).toMillis(), AutoBackupRetention.millisUntilNextRun(now))
    }
}
