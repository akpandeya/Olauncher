package app.olauncher.helper

sealed class DrawerCommand {
    data class SaveWords(val items: List<String>) : DrawerCommand()
    data class LookUp(val text: String) : DrawerCommand()

    companion object {
        private const val MAX_ITEM_CHARS = 40
        private const val MAX_ITEM_WORDS = 4
        private const val PUNCTUATION = ".,;:!?\"'„“”‚‘()[]«»"
        private val PUNCTUATION_SET = PUNCTUATION.toSet()
        private val SPLIT_REGEX = Regex("""[,;\r\n]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun parse(query: String, germanEnabled: Boolean): DrawerCommand? {
            if (!germanEnabled) return null
            val q = query.trimStart()
            if (!q.startsWith(":")) return null
            val rest = q.substring(1)
            val word = rest.takeWhile { !it.isWhitespace() }
            val args = rest.substring(word.length)
            return when (word.lowercase()) {
                "sw" -> SaveWords(splitItems(args))
                "w" -> {
                    val text = args.trim()
                    if (text.isEmpty()) null else LookUp(text)
                }
                else -> null
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
