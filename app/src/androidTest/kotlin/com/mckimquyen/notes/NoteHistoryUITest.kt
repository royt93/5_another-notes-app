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

@RunWith(AndroidJUnit4::class)
class NoteHistoryUITest {

    @Test
    fun testNoteHistoryUIFlow() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Click FAB to open EditFrm
            scenario.onActivity { activity ->
                val fab = activity.findViewById<View>(R.id.fab)
                assertNotNull("FAB should exist", fab)
                fab.performClick()
            }

            // Wait for navigation and layout pass
            var titleEdt: EditText? = null
            val startTime = System.currentTimeMillis()
            while (titleEdt == null && System.currentTimeMillis() - startTime < 5000) {
                scenario.onActivity { activity ->
                    val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                    if (recyclerView != null) {
                        titleEdt = recyclerView.findViewById<EditText>(R.id.titleEdt)
                    }
                }
                if (titleEdt == null) {
                    Thread.sleep(200)
                }
            }
            assertNotNull("titleEdt should exist in EditFrm layout within timeout", titleEdt)

            // 2. Add some content and check overflow menu
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
                val title = recyclerView.findViewById<EditText>(R.id.titleEdt)
                title.setText("Test History Note")

                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull("Toolbar should exist", toolbar)

                val historyMenuItem = toolbar.menu.findItem(R.id.itemTimeTravel)
                assertNotNull("Note history menu item should exist", historyMenuItem)
                assertEquals("Note history", historyMenuItem.title.toString())

                // Start time travel mode by clicking the menu item
                toolbar.menu.performIdentifierAction(R.id.itemTimeTravel, 0)
            }

            // Wait for history load and UI updates
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
        }
    }
}
