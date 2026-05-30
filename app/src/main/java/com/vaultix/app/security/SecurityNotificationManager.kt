package com.vaultix.app.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vaultix.app.R

object SecurityNotificationManager {
    var hasAddedWeakItemThisSession: Boolean = false

    fun triggerWeakItemNotification(context: Context, lang: String) {
        if (!hasAddedWeakItemThisSession) return
        hasAddedWeakItemThisSession = false // Reset

        // Create configuration for translation based on app language
        val locale = java.util.Locale(lang)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)

        val channelId = "vaultix_expiry_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Security & Expiry Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        val title = localizedContext.getString(R.string.weak_passwords_title)
        val message = if (lang == "ar") {
            "لقد قمت بإضافة كلمة مرور ضعيفة أو شبكة غير آمنة مؤخراً. يرجى مراجعتها وتأمين حساباتك لتبقى في أمان!"
        } else {
            "You added a weak password or insecure network recently. Please review it to stay safe and secure!"
        }

        val intent = android.content.Intent(context, com.vaultix.app.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(9099, notification)
    }
}
