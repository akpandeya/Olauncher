package app.olauncher.helper

/**
 * What the tap menu of a swipe gesture setting offers, and what each choice does.
 * "Off" is always first, so the gesture can be switched off and on with taps only.
 */
object SwipeSettingMenu {
    enum class Side { LEFT, RIGHT }

    enum class Option { OFF, TODAY_PAGE, CHOOSE_APP }

    /** What a choice does: the new on/off state, whether to show the app picker, and whether the right swipe opens Today. */
    data class Outcome(val enabled: Boolean, val openAppPicker: Boolean, val rightOpensToday: Boolean?)

    fun options(side: Side): List<Option> = when (side) {
        Side.LEFT -> listOf(Option.OFF, Option.CHOOSE_APP)
        Side.RIGHT -> listOf(Option.OFF, Option.TODAY_PAGE, Option.CHOOSE_APP)
    }

    fun outcome(side: Side, option: Option): Outcome {
        require(option in options(side)) { "$option is not offered for $side" }
        return when (option) {
            Option.OFF -> Outcome(enabled = false, openAppPicker = false, rightOpensToday = null)
            Option.TODAY_PAGE -> Outcome(enabled = true, openAppPicker = false, rightOpensToday = true)
            Option.CHOOSE_APP -> Outcome(enabled = true, openAppPicker = true, rightOpensToday = null)
        }
    }
}
