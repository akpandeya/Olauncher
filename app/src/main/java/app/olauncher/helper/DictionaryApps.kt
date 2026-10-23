package app.olauncher.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.WordEdit

object DictionaryApps {
    data class App(val packageName: String, val label: String)

    private fun textIntent(action: String) = Intent(action).setType("text/plain")

    @Suppress("DEPRECATION")
    private fun handlers(context: Context, intent: Intent): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(intent, 0).filter {
            val activity = it.activityInfo
            activity != null && activity.exported && activity.enabled && activity.applicationInfo.enabled &&
                activity.packageName != context.packageName &&
                (activity.permission == null ||
                    context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)
        }

    private fun apps(context: Context, handlers: List<ResolveInfo>): List<App> =
        handlers.distinctBy { it.activityInfo.packageName }
            .map { App(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /** PROCESS_TEXT handlers only: every messaging app registers for text/plain SEND. */
    fun textApps(context: Context): List<App> = apps(
        context,
        handlers(context, textIntent(Intent.ACTION_PROCESS_TEXT)),
    )

    fun launchableApps(context: Context): List<App> = apps(
        context, handlers(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)),
    )

    @Suppress("DEPRECATION")
    fun label(context: Context, packageName: String): String = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    fun actionLabel(context: Context, packageName: String): String =
        if (packageName.isBlank()) context.getString(R.string.words_open_wiktionary)
        else context.getString(R.string.dictionary_open_in, label(context, packageName))

    fun open(context: Context, word: String, packageName: String = Prefs(context).germanDictionaryApp) {
        if (packageName.isBlank()) {
            try {
                context.openUrl(WordEdit.wiktionaryUrl(word))
            } catch (_: Exception) {
                context.showToast(R.string.unable_to_open_app)
            }
            return
        }
        val text = word.trim()
        val process = handlers(context, textIntent(Intent.ACTION_PROCESS_TEXT).setPackage(packageName))
        val send = handlers(context, textIntent(Intent.ACTION_SEND).setPackage(packageName))
        val opened = DictionaryHandoff.open(process.isNotEmpty(), send.isNotEmpty()) { action ->
            when (action) {
                DictionaryHandoff.Action.PROCESS_TEXT -> launchText(context, process, textIntent(Intent.ACTION_PROCESS_TEXT).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                    putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                })
                DictionaryHandoff.Action.SEND -> launchText(context, send, textIntent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                })
                DictionaryHandoff.Action.COPY_AND_LAUNCH -> {
                    // Never use an implicit launcher intent: it could open a different app.
                    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launch == null || launch.component?.packageName != packageName) {
                        false
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
                        start(context, launch).also { success ->
                            if (success) context.showToast(context.getString(R.string.dictionary_word_copied, text))
                        }
                    }
                }
            }
        }
        if (!opened) context.showToast(R.string.unable_to_open_app)
    }

    private fun launchText(context: Context, handlers: List<ResolveInfo>, intent: Intent): Boolean =
        handlers.any {
            val activity = it.activityInfo
            start(context, Intent(intent).setComponent(ComponentName(activity.packageName, activity.name)))
        }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
