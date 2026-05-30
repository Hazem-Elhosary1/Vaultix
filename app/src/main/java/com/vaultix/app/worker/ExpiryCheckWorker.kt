package com.vaultix.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultix.app.R
import com.vaultix.app.data.local.dao.CardDao
import com.vaultix.app.data.local.dao.IdentityDao
import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import com.vaultix.app.security.SecurePreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that checks for expiring items (Passwords, Cards, IDs).
 * Runs once daily, fully offline.
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val passwordDao: PasswordDao,
    private val cardDao: CardDao,
    private val identityDao: IdentityDao,
    private val cryptoManager: CryptoManager,
    private val securePreferences: SecurePreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "vaultix_expiry_check"
        const val CHANNEL_ID = "vaultix_expiry_alerts"
        private const val NOTIFICATION_ID_BASE = 9000
        private const val DAYS_BEFORE_EXPIRY = 30

        fun schedule(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
                1, TimeUnit.DAYS
            ).setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Security & Expiry Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts for weak passwords, insecure networks, and expiring items"
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        try {
            createNotificationChannel(appContext)

            val now = System.currentTimeMillis()
            val threshold = now + TimeUnit.DAYS.toMillis(DAYS_BEFORE_EXPIRY.toLong())
            val key = KeystoreManager.getOrCreateDatabaseKey()

            // Resolve localized context based on stored app language setting or default system language
            val lang = securePreferences.getPlainString(SecurePreferences.KEY_APP_LANGUAGE) ?: "en"
            val locale = java.util.Locale(lang)
            val config = android.content.res.Configuration(appContext.resources.configuration)
            config.setLocale(locale)
            val localizedContext = appContext.createConfigurationContext(config)

            var notificationId = NOTIFICATION_ID_BASE
            var alertCount = 0

            // 1. Check Passwords
            val expiringPasswords = passwordDao.getExpiringPasswords(threshold)
            for (pwd in expiringPasswords) {
                val title = try { cryptoManager.decrypt(pwd.title, key) } catch (e: Exception) { "Password" }
                val daysLeft = calculateDays(pwd.expiresAt ?: 0, now)
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.password_expiring_soon_title),
                    localizedContext.getString(R.string.password_expiring_soon_msg, title, daysLeft)
                )
                alertCount++
            }

            // 2. Check Cards
            val expiringCards = cardDao.getExpiringCards(threshold)
            for (card in expiringCards) {
                val name = try { cryptoManager.decrypt(card.cardName, key) } catch (e: Exception) { "Card" }
                val daysLeft = calculateDays(card.expiryTimestamp ?: 0, now)
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.card_expiring_soon_title),
                    localizedContext.getString(R.string.card_expiring_soon_msg, name, daysLeft)
                )
                alertCount++
            }

            // 3. Check Identities
            val expiringIdentities = identityDao.getExpiringIdentities(threshold)
            for (id in expiringIdentities) {
                val name = try { cryptoManager.decrypt(id.documentName, key) } catch (e: Exception) { "ID Document" }
                val daysLeft = calculateDays(id.expiryTimestamp ?: 0, now)
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.id_expiring_soon_title),
                    localizedContext.getString(R.string.id_expiring_soon_msg, name, daysLeft)
                )
                alertCount++
            }

            // 4. Check Weak Passwords & Weak Wi-Fi
            val allPasswords = passwordDao.getAllPasswordsList()
            var weakPasswordsCount = 0
            var weakWifiCount = 0

            for (pwd in allPasswords) {
                if (pwd.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive) {
                    val website = try { cryptoManager.decrypt(pwd.website, key) } catch (e: Exception) { "" }
                    if (website == "vaultix://wifi") {
                        if (pwd.appPackageName == "Open" || pwd.appPackageName == "WEP" || pwd.passwordStrength < 3) {
                            weakWifiCount++
                        }
                    } else {
                        if (pwd.passwordStrength < 3) {
                            weakPasswordsCount++
                        }
                    }
                }
            }

            if (weakPasswordsCount > 0) {
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.weak_passwords_title),
                    localizedContext.getString(R.string.weak_passwords_msg, weakPasswordsCount)
                )
                alertCount++
            }

            if (weakWifiCount > 0) {
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.weak_wifi_title),
                    localizedContext.getString(R.string.weak_wifi_msg, weakWifiCount)
                )
                alertCount++
            }

            // General "Stay Safe" positive encouragement if everything is fully secure
            if (alertCount == 0) {
                sendNotification(
                    notificationId++,
                    localizedContext.getString(R.string.vault_secure_title),
                    localizedContext.getString(R.string.vault_secure_msg)
                )
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun calculateDays(expiry: Long, now: Long): Int {
        val diff = expiry - now
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) return
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(id, notification)
    }
}
