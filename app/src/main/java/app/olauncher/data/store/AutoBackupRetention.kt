package app.olauncher.data.store

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

object AutoBackupRetention {
    const val KEEP_COUNT = 7
    val RUN_TIME: LocalTime = LocalTime.of(3, 0)

    private val prefix = Backup.defaultBackupFileName(LocalDate.of(2000, 1, 1))
        .removeSuffix("2000-01-01.json")
    private const val SUFFIX = ".json"
    private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun backupDate(name: String): LocalDate? {
        if (!name.startsWith(prefix) || !name.endsWith(SUFFIX)) return null
        val datePart = name.substring(prefix.length, name.length - SUFFIX.length)
        if (!datePattern.matches(datePart)) return null
        return try {
            LocalDate.parse(datePart)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun namesToDelete(names: List<String>, keep: Int = KEEP_COUNT): List<String> {
        return names
            .mapNotNull { name -> backupDate(name)?.let { name to it } }
            .sortedByDescending { it.second }
            .drop(keep)
            .map { it.first }
    }

    fun millisUntilNextRun(now: ZonedDateTime): Long {
        val zone = now.zone
        var next = now.toLocalDate().atTime(RUN_TIME).atZone(zone)
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(RUN_TIME).atZone(zone)
        }
        return Duration.between(now, next).toMillis()
    }
}
