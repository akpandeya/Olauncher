package app.olauncher.ui.words

enum class ConjugationCellLook {
    NEUTRAL,
    RIGHT,
    WRONG;

    companion object {
        fun of(
            answered: Boolean,
            choice: String,
            answer: String,
            picked: String?,
        ): ConjugationCellLook = when {
            answered && choice == answer -> RIGHT
            answered && choice == picked -> WRONG
            else -> NEUTRAL
        }
    }
}
