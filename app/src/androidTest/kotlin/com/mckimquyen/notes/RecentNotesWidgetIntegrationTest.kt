package com.mckimquyen.notes

import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.widget.RecentNotesWidget
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentNotesWidgetIntegrationTest {

    @Test
    fun testWidgetUpdateAndRemoteViewsSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        // Verify widget layout RemoteViews can be instantiated
        val packageName = context.packageName
        val views = RemoteViews(packageName, R.layout.widget_recent_notes)
        assertNotNull(views)
        
        // Execute the full updateWidget logic in Instrumented context
        RecentNotesWidget.updateWidget(context, appWidgetManager, 999)
        
        // Also verify the slideshow list item layout can be instantiated
        val itemViews = RemoteViews(packageName, R.layout.widget_recent_notes_item)
        assertNotNull(itemViews)
    }

    @Test
    fun testUpdateAllWidgetsExecutesSuccessfully() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Call the static broadcast update trigger to verify it runs without crashing
        RecentNotesWidget.updateAllWidgets(context)
    }
}
