package com.mckimquyen.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentNotesWidgetTest {

    @Test
    fun testRecentNotesWidgetUpdatesWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Mock AppWidgetManager
        val appWidgetManager = mockk<AppWidgetManager>(relaxed = true)
        val appWidgetId = 2

        // Execute updateWidget, which inflates widget_recent_notes.xml layout
        // and binds the AdapterViewFlipper (R.id.widgetListView).
        RecentNotesWidget.updateWidget(context, appWidgetManager, appWidgetId)
        
        // Assert: reached here without any crash, meaning layout structure and setup are valid
        assert(true)
    }
}
