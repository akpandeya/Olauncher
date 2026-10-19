package app.olauncher.data

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import app.olauncher.R

/**
 * Named descriptor for a selectable font family option.
 */
data class FontFamilyResource(
    val id: Int,
    @FontRes val fontRes: Int?,
    @StringRes val labelRes: Int,
) {
    @get:FontRes val fontResourceId: Int? get() = fontRes
    @get:StringRes val stringRes: Int get() = labelRes
}

/**
 * Context-free mapping from font family IDs to their font and label resources.
 */
object FontFamilyResources {

    val SYSTEM = FontFamilyResource(
        id = Constants.FontFamily.SYSTEM,
        fontRes = null,
        labelRes = R.string.system_default,
    )

    val INTER = FontFamilyResource(
        id = Constants.FontFamily.INTER,
        fontRes = R.font.inter,
        labelRes = R.string.font_inter,
    )

    val PLEX = FontFamilyResource(
        id = Constants.FontFamily.PLEX,
        fontRes = R.font.ibm_plex_sans,
        labelRes = R.string.font_plex,
    )

    val ALL: List<FontFamilyResource> = listOf(SYSTEM, INTER, PLEX)

    /**
     * Maps a font family id to its corresponding [FontFamilyResource].
     * Unknown IDs map to [SYSTEM].
     */
    fun forId(id: Int): FontFamilyResource = when (id) {
        Constants.FontFamily.INTER -> INTER
        Constants.FontFamily.PLEX -> PLEX
        else -> SYSTEM
    }

    fun all(): List<FontFamilyResource> = ALL
}
