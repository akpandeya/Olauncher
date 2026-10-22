package app.olauncher.helper

import android.content.Context
import app.olauncher.R
import app.olauncher.data.HomeSetProfiles
import app.olauncher.data.Prefs
import app.olauncher.data.ProfileChoice

/** Name shown for a profile id, or null for an id nobody knows. */
fun Prefs.profileNameOf(context: Context, id: String): String? =
    profileChoices(context, includeSets = true).firstOrNull { it.id == id }?.name

/**
 * The profiles the user can switch to. The Morning, Day and Evening profiles (the old Home sets)
 * are offered only while a schedule is in use; the others are always offered.
 */
fun Prefs.profileChoices(context: Context, includeSets: Boolean = profileScheduleActive): List<ProfileChoice> {
    val sets = if (includeSets) listOf(
        ProfileChoice(HomeSetProfiles.MORNING_ID, context.getString(R.string.profile_name_morning)),
        ProfileChoice(HomeSetProfiles.DAY_ID, context.getString(R.string.profile_name_day)),
        ProfileChoice(HomeSetProfiles.EVENING_ID, context.getString(R.string.profile_name_evening)),
    ) else emptyList()
    return sets + profiles().map { ProfileChoice(it.id, it.name) }
}
