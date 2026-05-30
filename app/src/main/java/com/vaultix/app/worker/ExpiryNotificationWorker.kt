package com.vaultix.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultix.app.R
import com.vaultix.app.data.local.dao.CardDao
import com.vaultix.app.data.local.dao.IdentityDao
import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val passwordDao: PasswordDao,
    private val cardDao: CardDao,
    private val identityDao: IdentityDao,
    private val cryptoManager: CryptoManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "vaultix_expiry_alerts"
        const val NOTIFICATION_ID = 1001
        private const val THRESHOLD_DAYS = 30
        private const val DAY_IN_MS = 24 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val threshold = now + (THRESHOLD_DAYS * DAY_IN_MS)
            
            val expiringPasswords = passwordDao.getExpiringPasswords(threshold)
            val expiringCards = cardDao.getExpiringCards(threshold)
            val expiringIdentities = identityDao.getExpiringIdentities(threshold)
            
            val totalCount = expiringPasswords.size + expiringCards.size + expiringIdentities.size
            
            if (totalCount > 0) {
                showNotification(totalCount)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vaultix Expiry Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for expiring items in your vault"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val contentText = if (count == 1) {
            "One item in your vault is expiring soon."
        } else {
            "$count items in your vault are expiring soon."
        }
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vaultix Security Alert")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
