package com.mckimquyen.notes

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.ui.main.MainAct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Before
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.model.NotesDb

@RunWith(AndroidJUnit4::class)
class ReadingModeUITest {

    @Before
    fun setup() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().applicationContext as RApp
        app.database.clearAllTables()
        app.prefs.listLayoutMode = com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode.LIST
    }

    @Test
    fun testReadingModeUIFlow() {
        val scenario = ActivityScenario.launch(MainAct::class.java)
        try {
            // Wait for activity to load and settle
            Thread.sleep(1500)

            // 1. Click FAB to open EditFrm
            scenario.onActivity { activity ->
                val fab = activity.findViewById<View>(R.id.fab)
                assertNotNull("FAB should exist", fab)
                fab.performClick()
            }

            // Wait for navigation and recycler view layout binding
            var colorPicker: View? = null
            var moodPicker: View? = null
            for (i in 1..15) {
                scenario.onActivity { activity ->
                    colorPicker = activity.findViewById<View>(R.id.colorPickerScroll)
                    moodPicker = activity.findViewById<View>(R.id.moodPickerRow)
                }
                if (colorPicker != null && moodPicker != null) break
                Thread.sleep(500)
            }
            assertNotNull("colorPickerScroll should exist within timeout", colorPicker)
            assertNotNull("moodPickerRow should exist within timeout", moodPicker)

            // 2. Verify initial visibility in Edit Mode and toggle reading mode
            scenario.onActivity { activity ->
                assertEquals("Color picker should be visible in edit mode", View.VISIBLE, colorPicker!!.visibility)
                assertEquals("Mood picker should be visible in edit mode", View.VISIBLE, moodPicker!!.visibility)

                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull("Toolbar should exist", toolbar)
                
                // Toggle to Reading Mode
                toolbar.menu.performIdentifierAction(R.id.itemReadingMode, 0)
            }

            // Wait on test thread for transition to complete
            Thread.sleep(1500)

            // 3. Verify elements are hidden in Reading Mode, then toggle back
            scenario.onActivity { activity ->
                val colorPicker = activity.findViewById<View>(R.id.colorPickerScroll)
                val moodPicker = activity.findViewById<View>(R.id.moodPickerRow)
                assertNotNull(colorPicker)
                assertNotNull(moodPicker)
                assertEquals("Color picker should be hidden in reading mode", View.GONE, colorPicker.visibility)
                assertEquals("Mood picker should be hidden in reading mode", View.GONE, moodPicker.visibility)

                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull(toolbar)
                
                // Toggle back to Edit Mode
                toolbar.menu.performIdentifierAction(R.id.itemReadingMode, 0)
            }

            // Wait on test thread for transition to complete
            Thread.sleep(1500)

            // 4. Verify elements are visible again
            scenario.onActivity { activity ->
                val colorPicker = activity.findViewById<View>(R.id.colorPickerScroll)
                val moodPicker = activity.findViewById<View>(R.id.moodPickerRow)
                assertNotNull(colorPicker)
                assertNotNull(moodPicker)
                assertEquals("Color picker should be visible again in edit mode", View.VISIBLE, colorPicker.visibility)
                assertEquals("Mood picker should be visible again in edit mode", View.VISIBLE, moodPicker.visibility)
                
                // Exit EditFrm to return to Home screen and allow clean activity destruction
                activity.onBackPressedDispatcher.onBackPressed()
            }
            // Allow transitions to finish before scenario closes to prevent Android 14 pause state lifecycle issues
            Thread.sleep(1500)
        } finally {
            try {
                scenario.close()
            } catch (e: AssertionError) {
                android.util.Log.w("roy93~", "Ignored ActivityScenario close transition AssertionError on Android 14+", e)
            }
        }
    }
}
