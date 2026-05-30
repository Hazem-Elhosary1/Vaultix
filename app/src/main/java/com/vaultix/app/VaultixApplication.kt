package com.vaultix.app

import android.app.Application
import com.vaultix.app.worker.ExpiryCheckWorker
import com.vaultix.app.worker.DailyBackupWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize SQLCipher
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)

        // Create notification channel for expiry alerts
        ExpiryCheckWorker.createNotificationChannel(this)

        // Schedule daily expiry check (cards + IDs)
        ExpiryCheckWorker.schedule(this)

        // Schedule daily encrypted backup history snapshots
        DailyBackupWorker.schedule(this)
    }
}
