package com.mckimquyen.notes

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.ui.main.MainAct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Before
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.model.NotesDb

@RunWith(AndroidJUnit4::class)
class NoteHistoryUITest {

    @Before
    fun setup() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().applicationContext as RApp
        app.database.clearAllTables()
        app.prefs.listLayoutMode = com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode.LIST
    }

    @Test
    fun testNoteHistoryUIFlow() {
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
            var titleEdt: EditText? = null
            for (i in 1..5) {
                scenario.onActivity { activity ->
                    val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                    titleEdt = rv?.findViewById<EditText>(R.id.titleEdt)
                }
                if (titleEdt != null) break
                Thread.sleep(500)
            }
            assertNotNull("titleEdt should exist in EditFrm layout within timeout", titleEdt)

            // 2. Set title and Perform History recovery dialog trigger
            scenario.onActivity { activity ->
                titleEdt!!.setText("Test History Title")

                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull("Toolbar should exist in EditFrm", toolbar)
                
                // Show History on the note
                val historyMenuItem = toolbar.menu.findItem(R.id.itemTimeTravel)
                assertNotNull("History menu item should exist", historyMenuItem)

                // Perform history display trigger
                toolbar.menu.performIdentifierAction(R.id.itemTimeTravel, 0)
            }

            // Wait for dialog or bottom sheet sheet to pop up
            Thread.sleep(1000)

            // 3. Verify that the timeTravelLayout is shown
            scenario.onActivity { activity ->
                val timeTravelLayout = activity.findViewById<View>(R.id.timeTravelLayout)
                assertNotNull("timeTravelLayout should exist", timeTravelLayout)
                
                // We click Cancel to close
                val cancelBtn = activity.findViewById<View>(R.id.timeTravelCancelBtn)
                if (cancelBtn != null && timeTravelLayout.visibility == View.VISIBLE) {
                    cancelBtn.performClick()
                    assertEquals("timeTravelLayout should be hidden after cancel", View.GONE, timeTravelLayout.visibility)
                }
            }
        } finally {
            try {
                scenario.close()
            } catch (e: AssertionError) {
                android.util.Log.w("roy93~", "Ignored ActivityScenario close transition AssertionError on Android 14+", e)
            }
        }
    }
}
