package app.olauncher.helper

object GermanTime {

    fun words(hour: Int, minute: Int): String {
        val h24 = ((hour % 24) + 24) % 24
        val m = ((minute % 60) + 60) % 60

        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val nextH12 = if (h12 == 12) 1 else h12 + 1
        val nextH24 = (h24 + 1) % 24

        return when (m) {
            0 -> {
                when {
                    h24 == 0 -> "Mitternacht"
                    h24 == 12 -> "zwölf Uhr mittags"
                    h12 == 1 -> "ein Uhr"
                    else -> "${hourWord(h12)} Uhr"
                }
            }
            1, 2 -> {
                if (h24 == 0) "kurz nach Mitternacht"
                else "kurz nach ${hourWord(h12)}"
            }
            58, 59 -> {
                if (nextH24 == 0) "kurz vor Mitternacht"
                else "kurz vor ${hourWord(nextH12)}"
            }
            28, 29 -> "kurz vor ${markPhrase(30, h12, nextH12)}"
            30 -> markPhrase(30, h12, nextH12)
            31, 32 -> "kurz nach ${markPhrase(30, h12, nextH12)}"
            else -> {
                val rem = m % 5
                val nearestMark = if (rem >= 3) m + (5 - rem) else m - rem
                val phrase = markPhrase(nearestMark, h12, nextH12)
                if (m < nearestMark) {
                    "gleich $phrase"
                } else {
                    phrase
                }
            }
        }
    }

    private fun hourWord(h: Int): String = when (h) {
        1 -> "eins"
        2 -> "zwei"
        3 -> "drei"
        4 -> "vier"
        5 -> "fünf"
        6 -> "sechs"
        7 -> "sieben"
        8 -> "acht"
        9 -> "neun"
        10 -> "zehn"
        11 -> "elf"
        12 -> "zwölf"
        else -> ""
    }

    private fun markPhrase(mark: Int, h: Int, nextH: Int): String {
        val cur = hourWord(h)
        val next = hourWord(nextH)
        return when (mark) {
            5 -> "fünf nach $cur"
            10 -> "zehn nach $cur"
            15 -> "Viertel nach $cur"
            20 -> "zwanzig nach $cur"
            25 -> "fünf vor halb $next"
            30 -> "halb $next"
            35 -> "fünf nach halb $next"
            40 -> "zwanzig vor $next"
            45 -> "Viertel vor $next"
            50 -> "zehn vor $next"
            55 -> "fünf vor $next"
            else -> ""
        }
    }
}
