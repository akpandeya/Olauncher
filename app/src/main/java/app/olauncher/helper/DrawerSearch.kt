package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Constants

/** Everything one drawer search needs. Context-free: strings and row providers come in as values and lambdas. */
class DrawerSearchInput(
    val query: CharSequence?,
    val apps: List<AppModel>,
    val flag: Int,
    val bottomSearch: Boolean,
    val drawerRecents: Boolean,
    val allowAutoLaunch: Boolean,
    val germanEnabled: () -> Boolean,
    val recentApps: () -> List<RecentApp>,
    val hiddenApps: () -> Set<String>,
    val recentCaption: String,
    val callRowText: (number: String) -> String,
    val saveWordRowText: (word: String) -> String,
    /** One-line help row for a command, e.g. "Timer: :t 10 tea". */
    val commandHelpRowText: (word: String) -> String = { ":$it" },
    /** Error row text for unreadable command input, e.g. "Can't read '25x'". */
    val cantReadRowText: (token: String) -> String = { "Can't read '$it'" },
    /** Error row text when `:e` has no title. */
    val eventNoTitleRowText: () -> String = { "Can't add an event without a title" },
    /** Preview row text for `:n`, e.g. "Save note: buy milk". */
    val noteRowText: (text: String) -> String = { "Save note: $it" },
    val saveWordsRowText: (count: Int, shown: String) -> String,
    val contactSource: () -> ContactSource?,
    val calendarRows: (String) -> List<AppModel.Result>,
    val currencyRows: (String) -> List<AppModel.Result>,
    val spellRows: (List<String>) -> List<AppModel.Result>,
    val lookUpRows: (String) -> List<AppModel.Result>,
    /** Current local time, injectable for tests. */
    val now: () -> java.time.LocalTime = { java.time.LocalTime.now() },
    /** Current moment with zone, injectable for tests. */
    val nowZoned: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now() },
    /** Stable string for a user, matching [RecentApp.user] and hidden-app keys. */
    val userKey: (android.os.UserHandle) -> String = { it.toString() },
)

class DrawerSearchResult(
    val items: List<AppModel>,
    val scores: List<Int>,
    val searchQuery: String,
    val isBangSearch: Boolean,
    val autoLaunch: Boolean,
)

object DrawerSearch {

    fun run(input: DrawerSearchInput): DrawerSearchResult {
        val charSearch = input.query
        val queryStr = charSearch?.toString().orEmpty()
        val bangSearch = charSearch?.startsWith("!") ?: false
        val shouldAutoLaunch = input.allowAutoLaunch
            && (charSearch?.startsWith(" ")?.not() ?: true)
            && (charSearch?.trimStart()?.startsWith(":")?.not() ?: true)

        if (charSearch.isNullOrBlank()) {
            val items = RecentApps.forEmptySearch(
                appsList = input.apps,
                recentApps = recentsForEmptyQuery(input),
                recentCaption = input.recentCaption,
                bottomSearch = input.bottomSearch,
            )
            return DrawerSearchResult(items, emptyList(), queryStr, bangSearch, shouldAutoLaunch)
        }

        val scoredApps = input.apps.mapNotNull { app ->
            if (app is AppModel.PrivateSpaceHeader || app is AppModel.Result || app is AppModel.RecentHeader) return@mapNotNull null
            val score = AppMatcher.score(app.appLabel, queryStr) ?: return@mapNotNull null
            app to score
        }.sortedWith(
            compareByDescending<Pair<AppModel, Int>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.appLabel }
        )

        val resultRows = if (input.flag == Constants.FLAG_LAUNCH_APP) resultRows(input, queryStr) else emptyList()
        val items = DrawerOrdering.forSearchPosition(resultRows + scoredApps.map { it.first }, input.bottomSearch)
        return DrawerSearchResult(items, scoredApps.map { it.second }, queryStr, bangSearch, shouldAutoLaunch)
    }

    private fun recentsForEmptyQuery(input: DrawerSearchInput): List<AppModel> {
        if (input.flag != Constants.FLAG_LAUNCH_APP || !input.drawerRecents) return emptyList()
        val hidden = input.hiddenApps()
        val apps = input.apps.filterIsInstance<AppModel.App>()
        return input.recentApps().mapNotNull { entry ->
            apps.firstOrNull {
                it.appPackage == entry.packageName &&
                    input.userKey(it.user) == entry.user &&
                    !it.isArchived &&
                    it.appLabel.isNotBlank() &&
                    !hidden.contains(it.appPackage + "|" + input.userKey(it.user))
            }?.copy(isRecent = true)
        }.take(3)
    }

    private fun resultRows(input: DrawerSearchInput, query: String): List<AppModel.Result> {
        val rows = mutableListOf<AppModel.Result>()
        val calc = if (DialMatch.showCalcRow(query)) Calc.evaluate(query) else null
        if (calc != null) rows.add(copyRow(calc))
        val unit = if (calc == null) UnitConvert.evaluate(query) else null
        if (unit != null) rows.add(copyRow(unit))
        safeAdd(rows) { input.currencyRows(query) }
        val dialNumber = if (DialMatch.showDialRow(query)) DialMatch.number(query) else null
        if (dialNumber != null) {
            rows.add(AppModel.Result(input.callRowText(dialNumber), AppModel.Result.Action.Dial(dialNumber)))
        }
        val command = try {
            DrawerCommand.parse(query, input.germanEnabled())
        } catch (_: Exception) {
            null
        }
        when (command) {
            is DrawerCommand.SaveWords -> if (command.items.isNotEmpty()) {
                safeAdd(rows) { input.spellRows(command.items) }
                rows.add(saveWordsRow(input, command.items))
            }
            is DrawerCommand.LookUp -> safeAdd(rows) { input.lookUpRows(command.text) }
            is DrawerCommand.Help -> command.commands.forEach {
                rows.add(AppModel.Result(input.commandHelpRowText(it.word), AppModel.Result.Action.FillQuery(it.prefix)))
            }
            is DrawerCommand.Timer -> if (command.args.isNotBlank()) {
                val timer = TimerCommand.parse(command.args)
                rows.add(
                    if (timer != null) AppModel.Result(TimerCommand.previewText(timer), AppModel.Result.Action.StartTimer(timer.seconds, timer.label))
                    else errorRow(input.cantReadRowText(firstWord(command.args)))
                )
            }
            is DrawerCommand.Alarm -> if (command.args.isNotBlank()) {
                val alarm = AlarmCommand.parse(command.args, input.now())
                rows.add(
                    if (alarm != null) AppModel.Result(AlarmCommand.previewText(alarm), AppModel.Result.Action.SetAlarm(alarm.hour, alarm.minute, alarm.days, alarm.label))
                    else errorRow(input.cantReadRowText(firstWord(command.args)))
                )
            }
            is DrawerCommand.Event -> if (command.args.isNotBlank()) {
                rows.add(
                    when (val parsed = EventCommand.parse(command.args, input.nowZoned())) {
                        is EventCommand.Parsed.Ok -> parsed.event.let {
                            AppModel.Result(EventCommand.previewText(it), AppModel.Result.Action.AddEvent(it.title, it.place, it.beginMillis, it.endMillis, it.allDay))
                        }
                        is EventCommand.Parsed.Error -> errorRow(
                            parsed.token?.let(input.cantReadRowText) ?: input.eventNoTitleRowText()
                        )
                    }
                )
            }
            // Handled by their own features.
            is DrawerCommand.Note -> when (val parsed = Notes.parse(command.args, input.nowZoned())) {
                is Notes.Parsed.Error -> rows.add(errorRow(input.cantReadRowText(parsed.token)))
                is Notes.Parsed.Ok -> if (parsed.text.isNotEmpty()) {
                    val label = parsed.showFrom?.let { Notes.dateLabel(it) }
                    val rowText = input.noteRowText(parsed.text) + label?.let { " · $it" }.orEmpty()
                    rows.add(AppModel.Result(rowText, AppModel.Result.Action.SaveNote(parsed.text, parsed.showFrom)))
                }
            }
            null -> Unit
        }
        // Runs on the Filter worker thread; a newer query supersedes this one.
        safeAdd(rows) {
            val source = input.contactSource()
            if (source == null) emptyList() else ContactSearch.search(source, query).map { ContactSearch.toResult(it) }
        }
        safeAdd(rows) { input.calendarRows(query) }
        return rows
    }

    private fun errorRow(text: String) = AppModel.Result(text, AppModel.Result.Action.Error)

    private fun firstWord(args: String) = args.trim().split(Regex("""\s+""")).first()

    private fun copyRow(value: String) =
        AppModel.Result(text = "= $value", action = AppModel.Result.Action.Copy(value))

    /** A throwing provider (revoked permission, provider failure) adds no rows. */
    private inline fun safeAdd(rows: MutableList<AppModel.Result>, block: () -> List<AppModel.Result>) {
        try {
            rows.addAll(block())
        } catch (_: Exception) {
            // No rows on failure.
        }
    }

    private fun saveWordsRow(input: DrawerSearchInput, items: List<String>): AppModel.Result {
        val text = if (items.size == 1) {
            input.saveWordRowText(items[0])
        } else {
            val shown = items.take(2).joinToString(", ") + if (items.size > 2) ", …" else ""
            input.saveWordsRowText(items.size, shown)
        }
        return AppModel.Result(text = text, action = AppModel.Result.Action.SaveWords(items))
    }
}
