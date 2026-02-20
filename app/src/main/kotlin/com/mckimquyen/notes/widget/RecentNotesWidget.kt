package com.mckimquyen.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.mckimquyen.notes.R
import com.mckimquyen.notes.ui.main.MainAct

/**
 * Feature 9 (W4) - Recent Notes Widget
 * Displays a scrollable list of up to 10 recent active notes.
 */
class RecentNotesWidget : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_recent_notes)

            // Setup the intent that points to the RemoteViewsService
            val intent = Intent(context, RecentNotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetListView, intent)
            views.setEmptyView(R.id.widgetListView, R.id.widgetEmptyText)

            // Template PendingIntent for items
            val clickIntentTemplate = Intent(context, MainAct::class.java).apply {
                action = MainAct.INTENT_ACTION_EDIT
            }

            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val clickPendingIntentTemplate = PendingIntent.getActivity(
                context, 0, clickIntentTemplate, flags
            )
            views.setPendingIntentTemplate(R.id.widgetListView, clickPendingIntentTemplate)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, RecentNotesWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView)
            }
        }
    }
}
