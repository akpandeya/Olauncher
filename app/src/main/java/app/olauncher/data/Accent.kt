package app.olauncher.data

enum class Accent(val id: Int) {
    OFF(0),
    SYSTEM(1),
    SAGE(2),
    SAND(3),
    SKY(4),
    ROSE(5);

    companion object {
        fun fromId(id: Int): Accent = entries.firstOrNull { it.id == id } ?: OFF

        /** OFF gives [fallback]; any other accent gives its own value from [valueOf]. */
        fun <T> resolve(accent: Accent, fallback: T, valueOf: (Accent) -> T): T =
            if (accent == OFF) fallback else valueOf(accent)
    }
}
