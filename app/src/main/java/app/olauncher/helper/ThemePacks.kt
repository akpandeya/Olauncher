package app.olauncher.helper

import android.content.Context
import android.content.res.Resources
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.ThemePack
import app.olauncher.data.ThemePackCodec
import app.olauncher.data.ThemePackException
import app.olauncher.data.Prefs
import java.util.Locale

/**
 * The bundled theme packs: JSON files under `assets/packs/`, each applied as a theme
 * overlay style (see docs/design/themes/PACK_FORMAT.md). The style for a pack is applied
 * after the font and accent overlays, so the pack wins.
 */
object ThemePacks {

    private const val ASSET_DIR = "packs"
    private const val FILE_EXTENSION = ".json"

    /** Every bundled pack that parses, sorted by name (case-insensitive). */
    fun list(context: Context): List<ThemePack> {
        val fileNames = try {
            context.assets.list(ASSET_DIR)?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        return fileNames
            .filter { it.endsWith(FILE_EXTENSION) }
            .mapNotNull { load(context, it.removeSuffix(FILE_EXTENSION)) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** The pack with [id] (file name without `.json`), or null when it is missing or invalid. */
    fun load(context: Context, id: String): ThemePack? {
        if (id.isEmpty()) return null
        val raw = try {
            context.assets.open("$ASSET_DIR/$id$FILE_EXTENSION").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return null
        }
        return try {
            ThemePackCodec.decode(raw, "$id$FILE_EXTENSION")
        } catch (e: ThemePackException) {
            null
        }
    }

    /** The overlay style of pack [id]; 0 when the id is empty or unknown. */
    fun styleResForId(id: String): Int = when (id) {
        "christmas-bold-dark" -> R.style.Pack_ChristmasBoldDark
        "christmas-bold-light" -> R.style.Pack_ChristmasBoldLight
        "christmas-dark" -> R.style.Pack_ChristmasDark
        "christmas-light" -> R.style.Pack_ChristmasLight
        "diwali-bold-dark" -> R.style.Pack_DiwaliBoldDark
        "diwali-bold-light" -> R.style.Pack_DiwaliBoldLight
        "diwali-dark" -> R.style.Pack_DiwaliDark
        "diwali-light" -> R.style.Pack_DiwaliLight
        "halloween-bold-dark" -> R.style.Pack_HalloweenBoldDark
        "halloween-bold-light" -> R.style.Pack_HalloweenBoldLight
        "halloween-dark" -> R.style.Pack_HalloweenDark
        "halloween-light" -> R.style.Pack_HalloweenLight
        "midnight-bold-dark" -> R.style.Pack_MidnightBoldDark
        "midnight-bold-light" -> R.style.Pack_MidnightBoldLight
        "midnight-dark" -> R.style.Pack_MidnightDark
        "midnight-light" -> R.style.Pack_MidnightLight
        "rose-bold-dark" -> R.style.Pack_RoseBoldDark
        "rose-bold-light" -> R.style.Pack_RoseBoldLight
        "rose-dark" -> R.style.Pack_RoseDark
        "rose-light" -> R.style.Pack_RoseLight
        "sage-bold-dark" -> R.style.Pack_SageBoldDark
        "sage-bold-light" -> R.style.Pack_SageBoldLight
        "sage-dark" -> R.style.Pack_SageDark
        "sage-light" -> R.style.Pack_SageLight
        "thumbline-bold-dark" -> R.style.Pack_ThumblineBoldDark
        "thumbline-bold-light" -> R.style.Pack_ThumblineBoldLight
        "thumbline-dark" -> R.style.Pack_ThumblineDark
        "thumbline-light" -> R.style.Pack_ThumblineLight
        else -> 0
    }

    /** Maps a pack's font name to the font-family setting id. */
    fun packFontToId(font: String): Int = when (font) {
        "Inter" -> Constants.FontFamily.INTER
        "IBM Plex Sans" -> Constants.FontFamily.PLEX
        "Nunito" -> Constants.FontFamily.NUNITO
        else -> Constants.FontFamily.SYSTEM
    }
}

/**
 * Applies the selected theme pack to [theme] as a theme overlay. Called after the font
 * and accent overlays, so the pack's colours win; an empty or unknown id adds nothing.
 */
fun Prefs.applyThemePackOverlay(theme: Resources.Theme) {
    if (themePackId.isEmpty()) return
    val style = ThemePacks.styleResForId(themePackId)
    if (style != 0) theme.applyStyle(style, true)
}
