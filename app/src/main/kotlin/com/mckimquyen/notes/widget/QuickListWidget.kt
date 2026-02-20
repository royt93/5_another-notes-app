package com.mckimquyen.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.mckimquyen.notes.R
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.ui.main.MainAct

/**
 * Feature 7 (W2) - Quick List Widget
 * Taps on the widget open the app directly to a new checklist note,
 * bypassing SplashAct, using INTENT_ACTION_CREATE with type LIST.
 */
class QuickListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_list)

            // Build intent to open MainAct with CREATE action (skip SplashAct)
            val intent = Intent(context, MainAct::class.java).apply {
                action = MainAct.INTENT_ACTION_CREATE
                putExtra(MainAct.EXTRA_NOTE_TYPE, NoteType.LIST.value)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
