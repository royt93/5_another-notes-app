package com.mckimquyen.notes

import android.view.View
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.model.NotesDao
import com.mckimquyen.notes.model.NotesDb
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.ui.main.MainAct
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class AnimationTransitionUITest {

    private lateinit var db: NotesDb
    private lateinit var notesDao: NotesDao

    @Before
    fun setup() {
        // Skip on Android 14+ due to old Espresso reflection constraints on InputManager
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT < 34)

        runBlocking {
        // Setup database and inject a test note to verify card click transition and UI state
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.databaseBuilder(targetContext, NotesDb::class.java, "notes_db")
            .addMigrations(*NotesDb.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        notesDao = db.notesDao()

        // Clean tables to start with a fresh deterministic database state
        db.clearAllTables()

        // Insert one active note card to click on for transition tests
        val note = Note(
            type = NoteType.TEXT,
            title = "Test Active Note",
            content = "Click this note card to verify transition animations.",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        notesDao.insert(note)
        }
    }

    @After
    fun teardown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun testHomeToEditFabMorphingTransition() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Wait for Home screen to load
            Thread.sleep(1500)

            // 2. Click FAB to open EditFrm
            onView(withId(R.id.fab)).perform(click())

            // 3. Sleep 1500ms to let the morphing transition run and complete
            Thread.sleep(1500)

            // 4. Verify we are in the Edit screen by checking if titleEdt is visible
            onView(withId(R.id.titleEdt)).check(matches(isDisplayed()))

            // 5. Press Back to return to Home (trigger return morphing)
            pressBack()

            // 6. Sleep 1500ms for return morphing
            Thread.sleep(1500)

            // 7. Verify we are back on Home screen and FAB is visible
            onView(withId(R.id.fab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNoteCardToEditMorphingTransition() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Wait for Home screen with the injected note card to load
            Thread.sleep(1500)

            // 2. Click on the note card containing the title text "Test Active Note"
            onView(withText("Test Active Note")).perform(click())

            // 3. Sleep 1500ms to let the card morphing transition complete
            Thread.sleep(1500)

            // 4. Verify we are in the Edit screen
            onView(withId(R.id.titleEdt)).check(matches(isDisplayed()))

            // 5. Press Back to return to Home
            pressBack()

            // 6. Sleep 1500ms for return morphing
            Thread.sleep(1500)

            // 7. Verify we are back on Home screen with the note list
            onView(withText("Test Active Note")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testSearchTransition_MaterialElevationScale() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Wait for Home screen to load
            Thread.sleep(1500)

            // 2. Click Search menu item on the toolbar
            onView(withId(R.id.itemSearch)).perform(click())

            // 3. Sleep 1500ms to let MaterialElevationScale search entrance transition run
            Thread.sleep(1500)

            // 4. Verify Search screen is open (checking for R.id.itemSearchEdt)
            onView(withId(R.id.itemSearchEdt)).check(matches(isDisplayed()))

            // 5. Press Back to return to Home
            pressBack()

            // 6. Sleep 1500ms for exit transition
            Thread.sleep(1500)

            // 7. Verify we are back on Home screen
            onView(withId(R.id.fab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testVipSharedAxisTransition() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Wait for Home screen to load
            Thread.sleep(1500)

            // 2. Click VIP menu item on the toolbar
            onView(withId(R.id.itemVip)).perform(click())

            // 3. Sleep 1500ms to let SharedAxis(X) entrance transition complete
            Thread.sleep(1500)

            // 4. Verify VIP screen is open by checking if R.id.heroContainer is displayed
            onView(withId(R.id.heroContainer)).check(matches(isDisplayed()))

            // 5. Press Back to return to Home
            pressBack()

            // 6. Sleep 1500ms for backward transition
            Thread.sleep(1500)

            // 7. Verify we are back on Home screen
            onView(withId(R.id.fab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testSettingsTransition_MaterialElevationScale() {
        ActivityScenario.launch(MainAct::class.java).use { scenario ->
            // 1. Wait for Home screen to load
            Thread.sleep(1500)

            // 2. Open Navigation Drawer by swiping right from the edge
            onView(withId(R.id.drawerLayout)).perform(swipeRight())
            Thread.sleep(1000)

            // 3. Click Settings drawer item
            onView(withId(R.id.drawerItemSettings)).perform(click())

            // 4. Sleep 1500ms for entrance transition
            Thread.sleep(1500)

            // 5. Verify Settings screen is open by checking settings toolbar or preference item
            onView(withText(R.string.action_settings)).check(matches(isDisplayed()))

            // 6. Press Back to return to Home
            pressBack()

            // 7. Sleep 1500ms for exit transition
            Thread.sleep(1500)

            // 8. Verify we are back on Home screen
            onView(withId(R.id.fab)).check(matches(isDisplayed()))
        }
    }
}
