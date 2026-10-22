package app.olauncher.helper

sealed class DrawerCommand {
    data class SaveWords(val items: List<String>) : DrawerCommand()
    data class LookUp(val text: String) : DrawerCommand()
    /** `:t`, `:a`, `:e`, `:n`: [args] is the raw text after the command word; each is handled by its own feature. */
    data class Timer(val args: String) : DrawerCommand()
    data class Alarm(val args: String) : DrawerCommand()
    data class Event(val args: String) : DrawerCommand()
    data class Note(val args: String) : DrawerCommand()
    /** `:` alone or an unknown `:xyz`: show [commands], the commands that are enabled. */
    data class Help(val commands: List<CommandInfo>) : DrawerCommand()

    /** One command for the help list. [word] is the text after `:`; tapping its row fills `:word `. */
    data class CommandInfo(val word: String, val germanOnly: Boolean = false) {
        val prefix: String get() = ":$word "
    }

    companion object {
        private const val MAX_ITEM_CHARS = 40
        private const val MAX_ITEM_WORDS = 4
        private const val PUNCTUATION = ".,;:!?\"'„“”‚‘()[]«»"
        private val PUNCTUATION_SET = PUNCTUATION.toSet()
        private val SPLIT_REGEX = Regex("""[,;\r\n]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        private val ALL_COMMANDS = listOf(
            CommandInfo("t"),
            CommandInfo("a"),
            CommandInfo("e"),
            CommandInfo("n"),
            CommandInfo("sw", germanOnly = true),
            CommandInfo("w", germanOnly = true),
        )

        /** Commands usable right now; `:sw` and `:w` need the German switch. */
        fun available(germanEnabled: Boolean): List<CommandInfo> =
            ALL_COMMANDS.filter { germanEnabled || !it.germanOnly }

        fun parse(query: String, germanEnabled: Boolean): DrawerCommand? {
            val q = query.trimStart()
            if (!q.startsWith(":")) return null
            val rest = q.substring(1)
            val word = rest.takeWhile { !it.isWhitespace() }
            val args = rest.substring(word.length)
            return when (word.lowercase()) {
                "sw" -> if (germanEnabled) SaveWords(splitItems(args)) else null
                "w" -> {
                    val text = args.trim()
                    when {
                        !germanEnabled -> null
                        text.isEmpty() -> null
                        else -> LookUp(text)
                    }
                }
                "t" -> Timer(args.trim())
                "a" -> Alarm(args.trim())
                "e" -> Event(args.trim())
                "n" -> Note(args.trim())
                else -> Help(available(germanEnabled))
            }
        }

        private fun splitItems(args: String): List<String> {
            val seen = HashSet<String>()
            val result = ArrayList<String>()
            for (part in args.split(SPLIT_REGEX)) {
                val item = part.trim { it.isWhitespace() || it in PUNCTUATION_SET }
                if (item.isEmpty() || item.length > MAX_ITEM_CHARS) continue
                if (item.split(WHITESPACE_REGEX).size > MAX_ITEM_WORDS) continue
                if (seen.add(item.lowercase())) result.add(item)
            }
            return result
        }
    }
}
