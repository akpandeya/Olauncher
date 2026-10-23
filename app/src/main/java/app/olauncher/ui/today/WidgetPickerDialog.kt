package app.olauncher.ui.today

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog

/**
 * In-app widget picker: a dialog listing the installed widget providers with
 * previews, replacing the legacy system picker screen ([AppWidgetManager.ACTION_APPWIDGET_PICK]).
 *
 * Providers are enumerated off the main thread (label lookup and preview
 * loading are I/O); the dialog opens as soon as the labels are ready and each
 * row's preview fills its slot when its load finishes. A pick hands the
 * provider (and the profile it came from) back to the caller; closing the
 * dialog cancels.
 */
internal object WidgetPickerDialog {

    /** One pickable widget: its provider, the profile it was found on and its label. */
    data class Entry(val info: AppWidgetProviderInfo, val profile: UserHandle?, val label: CharSequence)

    fun show(context: Context, isAlive: () -> Boolean, onPick: (Entry) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val entries = providers(context)
            main.post {
                // The enumeration takes a beat; the page may be gone by the time it lands.
                if (!isAlive()) return@post
                if (entries.isEmpty()) showEmpty(context) else showEntries(context, entries, onPick)
            }
        }.apply { isDaemon = true }.start()
    }

    /** Providers across every profile visible to this launcher, labelled and sorted. */
    private fun providers(context: Context): List<Entry> {
        val manager = AppWidgetManager.getInstance(context)
        val paired = try {
            context.getSystemService(LauncherApps::class.java)?.profiles
                ?.flatMap { profile ->
                    try {
                        manager.getInstalledProvidersForProfile(profile).map { it to profile }
                    } catch (_: SecurityException) {
                        emptyList()
                    }
                }.orEmpty()
                .ifEmpty { fallbackProviders(manager) }
        } catch (_: SecurityException) {
            fallbackProviders(manager)
        }
        return paired
            .distinctBy { it.first.provider.flattenToString() to it.second }
            .mapNotNull { (info, profile) ->
                val label = try {
                    info.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }?.ifBlank { null } ?: return@mapNotNull null
                Entry(info, profile, label)
            }
            .sortedBy { it.label.toString().lowercase() }
    }

    /** Used when profile-scoped enumeration is refused; returns what this app may see. */
    @Suppress("DEPRECATION") // getInstalledProviders: every profile this launcher can access, no replacement for it.
    private fun fallbackProviders(manager: AppWidgetManager) =
        try {
            manager.getInstalledProviders().map { it to null as UserHandle? }
        } catch (_: SecurityException) {
            emptyList()
        }

    private fun showEmpty(context: Context) {
        context.createDialog(R.string.today_add_widget, action = 0, messageText = context.getString(R.string.today_widget_none))
            .showRespectingStatusBar()
    }

    private fun showEntries(context: Context, entries: List<Entry>, onPick: (Entry) -> Unit) {
        val inflater = LayoutInflater.from(context)
        val holder = arrayOfNulls<OlDialog>(1)
        val dialog = context.createDialog(R.string.today_add_widget, action = 0, message = R.string.today_widget_explanation) { _ ->
            val density = context.resources.displayMetrics.density
            ScrollView(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (8 * density).toInt() }
                clipToPadding = false
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    // The dialog closes before the pick runs: the bind or configure
                    // step that follows must not reveal this dialog still open behind it.
                    // holder, because the rows are built before the dialog is assigned.
                    // dismiss() fades the blur out asynchronously, leaving rows tappable
                    // during the animation — the latch keeps a double tap from picking twice.
                    var picked = false
                    entries.forEach { entry ->
                        addView(row(inflater, context, entry) {
                            if (picked) return@row
                            picked = true
                            holder[0]?.dismiss()
                            onPick(entry)
                        })
                    }
                })
            }
        }
        holder[0] = dialog
        dialog.showRespectingStatusBar()
    }

    /** One pickable row: a preview slot on the start side, then the widget's label. */
    private fun row(inflater: LayoutInflater, context: Context, entry: Entry, onPick: (Entry) -> Unit): View {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setPadding(
                (16 * density).toInt(), (8 * density).toInt(),
                (16 * density).toInt(), (8 * density).toInt(),
            )
            minimumHeight = (64 * density).toInt()
            setOnClickListener {                Haptics.perform(it, Haptics.Kind.MENU)
                onPick(entry)
            }
        }
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (96 * density).toInt(), (56 * density).toInt(),
            ).apply { marginEnd = (12 * density).toInt() }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(preview)
        row.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TextSmall)
            text = entry.label
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        loadPreview(entry.info, preview)
        return row
    }

    /** Loads the provider's preview off the main thread; the provider icon stands in when there is none. */
    private fun loadPreview(info: AppWidgetProviderInfo, into: ImageView) {
        val context = into.context
        val densityDpi = context.resources.displayMetrics.densityDpi
        Thread {
            val drawable = try {
                info.loadPreviewImage(context, densityDpi)
            } catch (_: Exception) {
                null
            } ?: try {
                info.loadIcon(context, densityDpi)
            } catch (_: Exception) {
                null
            }
            if (drawable != null) into.post { into.setImageDrawable(drawable) }
        }.apply { isDaemon = true }.start()
    }
}
