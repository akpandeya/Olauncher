package app.olauncher.data.store

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        if (!prefs.autoBackupEnabled) return@withContext Result.success()
        try {
            runBackup(applicationContext, prefs)
            prefs.autoBackupLastOk = System.currentTimeMillis()
            prefs.autoBackupLastFailed = false
        } catch (e: Exception) {
            prefs.autoBackupLastFailed = true
        }
        Result.success()
    }

    private fun runBackup(context: Context, prefs: Prefs) {
        val treeString = prefs.autoBackupTreeUri
        if (treeString.isEmpty()) throw IOException("No backup folder")
        val tree = Uri.parse(treeString)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        if (!hasPermission) throw IOException("Folder permission revoked")

        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val existing = listChildren(context, tree, parent)
        val todayName = Backup.defaultBackupFileName(LocalDate.now())

        existing[todayName]?.let { DocumentsContract.deleteDocument(context.contentResolver, it) }
        val target = DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            "application/json",
            todayName,
        ) ?: throw IOException("Cannot create backup file")
        Backup.export(context, target)

        val names = (existing.keys + todayName).toList()
        for (name in AutoBackupRetention.namesToDelete(names)) {
            val doc = existing[name] ?: continue
            try {
                DocumentsContract.deleteDocument(context.contentResolver, doc)
            } catch (_: Exception) {
                // Old file stays; the next run tries again.
            }
        }
    }

    private fun listChildren(context: Context, tree: Uri, parent: Uri): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getDocumentId(parent),
        )
        val result = mutableMapOf<String, Uri>()
        val cursor = context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        ) ?: throw IOException("Backup folder is gone")
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                if (AutoBackupRetention.backupDate(name) == null) continue
                result[name] = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
            }
        }
        return result
    }

    companion object {
        const val WORK_NAME_PERIODIC = "auto_backup"
        const val WORK_NAME_NOW = "auto_backup_now"

        fun schedulePeriodic(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(AutoBackupRetention.millisUntilNextRun(now), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(WORK_NAME_NOW)
        }
    }
}
