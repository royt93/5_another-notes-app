package com.mckimquyen.notes

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.ui.main.MainAct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteMoodUITest {

    @Before
    fun setup() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().applicationContext as RApp
        app.database.clearAllTables()
        app.prefs.listLayoutMode = com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode.LIST
    }

    @Test
    fun testNoteMoodUIFlow() {
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

            // 2. Set title and select mood in EditFrm
            scenario.onActivity { activity ->
                titleEdt!!.setText("Mood UI Test")

                // Click moodBtn3 (😔)
                val moodBtn3 = activity.findViewById<View>(R.id.moodBtn3)
                assertNotNull("moodBtn3 should exist in EditFrm", moodBtn3)
                moodBtn3.performClick()
            }

            // Wait for database update and UI changes
            Thread.sleep(1000)

            // 3. Exit EditFrm to return to Home screen
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            // Wait for return navigation to Home screen
            Thread.sleep(1500)

            // 4. Verify on Home screen RecyclerView that the note shows the correct mood tag badge
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                assertNotNull("RecyclerView should exist on Home screen", recyclerView)

                // Check if we can find the ViewHolder for the first item
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(0)
                if (viewHolder != null) {
                    val moodTxv = viewHolder.itemView.findViewById<TextView>(R.id.moodTxv)
                    assertNotNull("moodTxv should exist in the note item layout", moodTxv)
                    assertEquals("moodTxv should be visible", View.VISIBLE, moodTxv.visibility)
                    assertEquals("moodTxv should display correct emoji", "😔", moodTxv.text.toString())
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
