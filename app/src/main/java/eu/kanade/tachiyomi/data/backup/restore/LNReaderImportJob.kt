package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class LNReaderImportJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri() ?: return Result.failure()

        setForegroundSafely()

        return try {
            val importer = LNReaderBackupImporter(context, notifier)
            val options = LNReaderBackupImporter.ImportOptions(
                restoreNovels = inputData.getBoolean(KEY_RESTORE_NOVELS, true),
                restoreChapters = inputData.getBoolean(KEY_RESTORE_CHAPTERS, true),
                restoreCategories = inputData.getBoolean(KEY_RESTORE_CATEGORIES, true),
                restoreHistory = inputData.getBoolean(KEY_RESTORE_HISTORY, true),
                restorePlugins = inputData.getBoolean(KEY_RESTORE_PLUGINS, true),
                restoreMissingPlugins = inputData.getBoolean(KEY_RESTORE_MISSING_PLUGINS, false),
            )
            val startTime = System.currentTimeMillis()
            val result = importer.import(uri, options)

            notifier.showRestoreComplete(
                time = System.currentTimeMillis() - startTime,
                errorCount = result.errorCount,
                path = result.logFile.parent,
                file = result.logFile.name,
                sync = false,
            )

            logcat(LogPriority.INFO) {
                "LNReaderImport: Completed - ${result.novelCount} novels, ${result.categoryCount} categories, " +
                    "${result.skippedCount} skipped, ${result.errorCount} errors" +
                    if (result.missingPlugins.isNotEmpty()) ", missing plugins: ${result.missingPlugins.joinToString()}" else ""
            }

            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError("LNReader import failed: ${e.message}")
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(
            context: Context,
            uri: Uri,
            restoreNovels: Boolean = true,
            restoreChapters: Boolean = true,
            restoreCategories: Boolean = true,
            restoreHistory: Boolean = true,
            restorePlugins: Boolean = true,
            restoreMissingPlugins: Boolean = false,
        ) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                KEY_RESTORE_NOVELS to restoreNovels,
                KEY_RESTORE_CHAPTERS to restoreChapters,
                KEY_RESTORE_CATEGORIES to restoreCategories,
                KEY_RESTORE_HISTORY to restoreHistory,
                KEY_RESTORE_PLUGINS to restorePlugins,
                KEY_RESTORE_MISSING_PLUGINS to restoreMissingPlugins,
            )
            val request = OneTimeWorkRequestBuilder<LNReaderImportJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}

private const val TAG = "LNReaderImport"
private const val LOCATION_URI_KEY = "location_uri"
private const val KEY_RESTORE_NOVELS = "restore_novels"
private const val KEY_RESTORE_CHAPTERS = "restore_chapters"
private const val KEY_RESTORE_CATEGORIES = "restore_categories"
private const val KEY_RESTORE_HISTORY = "restore_history"
private const val KEY_RESTORE_PLUGINS = "restore_plugins"
private const val KEY_RESTORE_MISSING_PLUGINS = "restore_missing_plugins"
