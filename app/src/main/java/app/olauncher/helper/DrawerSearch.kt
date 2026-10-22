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
    val saveWordsRowText: (count: Int, shown: String) -> String,
    val contactSource: () -> ContactSource?,
    val calendarRows: (String) -> List<AppModel.Result>,
    val currencyRows: (String) -> List<AppModel.Result>,
    val spellRows: (List<String>) -> List<AppModel.Result>,
    val lookUpRows: (String) -> List<AppModel.Result>,
    /** Stable string for a user, matching [RecentApp.user] and hidden-app keys. */
    /** Current local time, injectable for tests. */
    val now: () -> java.time.LocalTime = { java.time.LocalTime.now() },
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
            is DrawerCommand.Timer -> TimerCommand.parse(command.args)?.let {
                rows.add(AppModel.Result(TimerCommand.previewText(it), AppModel.Result.Action.StartTimer(it.seconds, it.label)))
            }
            is DrawerCommand.Alarm -> AlarmCommand.parse(command.args, input.now())?.let {
                rows.add(AppModel.Result(AlarmCommand.previewText(it), AppModel.Result.Action.SetAlarm(it.hour, it.minute, it.days, it.label)))
            }
            // Handled by their own features.
            is DrawerCommand.Event, is DrawerCommand.Note, null -> Unit
        }
        // Runs on the Filter worker thread; a newer query supersedes this one.
        safeAdd(rows) {
            val source = input.contactSource()
            if (source == null) emptyList() else ContactSearch.search(source, query).map { ContactSearch.toResult(it) }
        }
        safeAdd(rows) { input.calendarRows(query) }
        return rows
    }

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
