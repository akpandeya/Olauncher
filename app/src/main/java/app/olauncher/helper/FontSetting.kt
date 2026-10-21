package app.olauncher.helper

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

/** Applies the Inter/Plex/bold font setting to [theme] as theme overlays. Call before super.onCreate. */
fun Prefs.applyFontOverlay(theme: Resources.Theme) {
    when (fontFamily) {
        Constants.FontFamily.INTER ->
            theme.applyStyle(if (boldFont) R.style.FontInterBoldOverlay else R.style.FontInterOverlay, true)
        Constants.FontFamily.PLEX ->
            theme.applyStyle(if (boldFont) R.style.FontPlexBoldOverlay else R.style.FontPlexOverlay, true)
        else -> if (boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
    }
}

/** The typeface the font setting resolves to (the mainFontFamily theme attribute). */
fun Context.mainTypeface(): Typeface {
    val value = TypedValue()
    if (!theme.resolveAttribute(R.attr.mainFontFamily, value, true)) return Typeface.DEFAULT
    if (value.resourceId != 0) {
        ResourcesCompat.getFont(this, value.resourceId)?.let { return it }
    }
    return Typeface.create(value.string?.toString() ?: "sans-serif-light", Typeface.NORMAL)
}
