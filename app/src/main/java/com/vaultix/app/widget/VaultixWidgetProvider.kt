package com.vaultix.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vaultix.app.MainActivity
import com.vaultix.app.R

class VaultixWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Intent to launch Main Activity (Default)
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_logo_layout, mainPendingIntent)

        // Intent to launch Add Password Screen
        val passwordIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.vaultix.app.ACTION_ADD_PASSWORD"
        }
        val passwordPendingIntent = PendingIntent.getActivity(
            context, 1, passwordIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_add_pwd, passwordPendingIntent)

        // Intent to launch Add Note Screen
        val noteIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.vaultix.app.ACTION_ADD_NOTE"
        }
        val notePendingIntent = PendingIntent.getActivity(
            context, 2, noteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_add_note, notePendingIntent)

        // Intent to launch Password Generator Screen
        val generatorIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.vaultix.app.ACTION_GENERATE_PASSWORD"
        }
        val generatorPendingIntent = PendingIntent.getActivity(
            context, 3, generatorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_generate, generatorPendingIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
