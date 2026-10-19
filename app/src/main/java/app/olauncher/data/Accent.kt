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
    }
}
