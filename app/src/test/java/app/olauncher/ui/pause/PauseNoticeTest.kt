package app.olauncher.ui.pause

import app.olauncher.quiet.QuietRelease
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PauseNoticeTest {

    private val zoneUtc: ZoneId = ZoneId.of("UTC")

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = zoneUtc,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun formatNotice(
        quietOn: Boolean,
        nowMillis: Long,
        zone: ZoneId,
        releaseTimesRaw: String?,
    ): String {
        return if (quietOn) {
            val times = QuietRelease.parseTimes(releaseTimesRaw)
            val formattedTime = QuietRelease.nextReleaseFormatted(nowMillis, zone, times)
            "Notifications from this app are held until $formattedTime"
        } else {
            "Opens from notifications aren't paused"
        }
    }

    @Test
    fun `notice shows default text when quiet is off`() {
        val now = epochMillis(2026, 10, 19, 10, 0, zoneUtc)
        val notice = formatNotice(quietOn = false, nowMillis = now, zone = zoneUtc, releaseTimesRaw = "750,1110")
        assertEquals("Opens from notifications aren't paused", notice)
    }

    @Test
    fun `notice shows held until 12 30 when quiet is on in morning`() {
        val morning = epochMillis(2026, 10, 19, 10, 0, zoneUtc)
        val notice = formatNotice(quietOn = true, nowMillis = morning, zone = zoneUtc, releaseTimesRaw = "750,1110")
        assertEquals("Notifications from this app are held until 12:30", notice)
    }

    @Test
    fun `notice shows held until 18 30 when quiet is on in afternoon`() {
        val afternoon = epochMillis(2026, 10, 19, 14, 0, zoneUtc)
        val notice = formatNotice(quietOn = true, nowMillis = afternoon, zone = zoneUtc, releaseTimesRaw = "750,1110")
        assertEquals("Notifications from this app are held until 18:30", notice)
    }

    @Test
    fun `notice shows rolls to tomorrow 12 30 when quiet is on late at night`() {
        val night = epochMillis(2026, 10, 19, 21, 0, zoneUtc)
        val notice = formatNotice(quietOn = true, nowMillis = night, zone = zoneUtc, releaseTimesRaw = "750,1110")
        assertEquals("Notifications from this app are held until 12:30", notice)
    }
}
