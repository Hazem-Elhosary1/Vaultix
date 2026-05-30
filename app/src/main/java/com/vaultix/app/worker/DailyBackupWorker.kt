package com.vaultix.app.worker

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Creates a daily encrypted local backup snapshot in internal storage.
 */
class DailyBackupWorker(
    appContext: Context,
    workerParams: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "vaultix_daily_backup_history"

        fun schedule(context: Context) {
            val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<DailyBackupWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupEntryPoint {
        fun backupManager(): com.vaultix.app.util.BackupManager
        fun securePreferences(): com.vaultix.app.security.SecurePreferences
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                BackupEntryPoint::class.java
            )

            val securePreferences = entryPoint.securePreferences()
            val backupManager = entryPoint.backupManager()

            // 1. Check if setup is complete
            val setupComplete = securePreferences.getBoolean(
                com.vaultix.app.security.SecurePreferences.KEY_IS_SETUP_COMPLETE
            )
            if (!setupComplete) return@withContext androidx.work.ListenableWorker.Result.success()

            // 2. Check if backup is enabled
            val isEnabled = securePreferences.getBoolean(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_ENABLED, true
            )
            if (!isEnabled) return@withContext androidx.work.ListenableWorker.Result.success()

            // 3. Frequency Logic
            val frequency = securePreferences.getString(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_FREQUENCY
            ) ?: "DAILY"
            
            if (frequency == "NEVER") return@withContext androidx.work.ListenableWorker.Result.success()

            val lastBackup = securePreferences.getLong(
                com.vaultix.app.security.SecurePreferences.KEY_LAST_BACKUP_TIME, 0L
            )
            val currentTime = System.currentTimeMillis()
            val diffMillis = currentTime - lastBackup

            val shouldBackup = when (frequency) {
                "DAILY" -> diffMillis >= TimeUnit.DAYS.toMillis(1)
                "WEEKLY" -> diffMillis >= TimeUnit.DAYS.toMillis(7)
                "MONTHLY" -> diffMillis >= TimeUnit.DAYS.toMillis(30)
                else -> diffMillis >= TimeUnit.DAYS.toMillis(1)
            }

            if (!shouldBackup && lastBackup != 0L) {
                return@withContext androidx.work.ListenableWorker.Result.success()
            }

            // 4. Perform Backup
            val result = backupManager.createHistoryBackup()
            if (result.isSuccess) {
                securePreferences.putLong(
                    com.vaultix.app.security.SecurePreferences.KEY_LAST_BACKUP_TIME,
                    currentTime
                )
                
                // 5. Cleanup (Keep last 10 backups by default)
                val maxHistory = securePreferences.getInt(
                    com.vaultix.app.security.SecurePreferences.KEY_MAX_BACKUP_HISTORY, 10
                )
                backupManager.cleanOldBackups(maxHistory)

                androidx.work.ListenableWorker.Result.success()
            } else {
                androidx.work.ListenableWorker.Result.retry()
            }
        } catch (_: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}