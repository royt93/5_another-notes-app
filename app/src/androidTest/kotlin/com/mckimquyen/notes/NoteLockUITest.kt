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
class NoteLockUITest {

    @Before
    fun setup() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().applicationContext as RApp
        app.database.clearAllTables()
        app.prefs.listLayoutMode = com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode.LIST
    }

    @Test
    fun testNoteLockUIFlow() {
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
            for (i in 1..15) {
                scenario.onActivity { activity ->
                    val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                    titleEdt = rv?.findViewById<EditText>(R.id.titleEdt)
                }
                if (titleEdt != null) break
                Thread.sleep(500)
            }
            assertNotNull("titleEdt should exist in EditFrm layout within timeout", titleEdt)

            // 2. Set title and Perform Lock in EditFrm
            scenario.onActivity { activity ->
                titleEdt!!.setText("Test Lock Title")

                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull("Toolbar should exist in EditFrm", toolbar)
                
                // Toggle Lock on the note
                val lockMenuItem = toolbar.menu.findItem(R.id.itemLock)
                assertNotNull("Lock menu item should exist", lockMenuItem)
                assertEquals(activity.getString(R.string.action_lock_note), lockMenuItem.title.toString())

                // Perform the lock action
                toolbar.menu.performIdentifierAction(R.id.itemLock, 0)
            }

            // Wait for database update and UI changes
            Thread.sleep(1000)

            // 3. Verify that the Lock menu item title is now "Unlock note"
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull(toolbar)
                val lockMenuItem = toolbar.menu.findItem(R.id.itemLock)
                assertEquals(activity.getString(R.string.action_unlock_note), lockMenuItem.title.toString())

                // Exit EditFrm
                activity.onBackPressedDispatcher.onBackPressed()
            }

            // Wait for return navigation to Home screen
            Thread.sleep(1500)

            // 4. Verify on Home screen RecyclerView that the locked note shows lockImv and hides content
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                assertNotNull("RecyclerView should exist on Home screen", recyclerView)
                
                // Check if we can find the ViewHolder for the first item
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(0)
                if (viewHolder != null) {
                    val lockImv = viewHolder.itemView.findViewById<View>(R.id.lockImv)
                    assertNotNull("lockImv should exist in the note item layout", lockImv)
                    assertEquals("lockImv should be visible for locked note", View.VISIBLE, lockImv.visibility)
                    
                    // Verify content text is hidden
                    val contentTxv = viewHolder.itemView.findViewById<View>(R.id.contentTxv)
                    if (contentTxv != null) {
                        assertEquals("contentTxv should be hidden for locked note", View.GONE, contentTxv.visibility)
                    }
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
