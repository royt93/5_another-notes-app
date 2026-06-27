package com.mckimquyen.notes.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.notes.R
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.entity.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentNotesRemoteViewsFactoryTest {

    @Test
    fun testFactoryFetchesCorrectNumberOfNotesAndBindsViews() = runBlocking {
        val mockContext = ApplicationProvider.getApplicationContext<Context>()
        val mockRepository = mockk<NotesRepository>()
        
        // Mock list of 2 notes
        val notesList = listOf(
            NoteWithLabels(
                note = Note(
                    id = 1,
                    type = NoteType.TEXT,
                    title = "Note Title 1",
                    content = "Content 1",
                    metadata = BlankNoteMetadata,
                    addedDate = Date(),
                    lastModifiedDate = Date(),
                    status = NoteStatus.ACTIVE,
                    pinned = PinnedStatus.UNPINNED,
                    reminder = null
                ),
                labels = emptyList()
            ),
            NoteWithLabels(
                note = Note(
                    id = 2,
                    type = NoteType.TEXT,
                    title = "",
                    content = "",
                    metadata = BlankNoteMetadata,
                    addedDate = Date(),
                    lastModifiedDate = Date(),
                    status = NoteStatus.ACTIVE,
                    pinned = PinnedStatus.UNPINNED,
                    reminder = null
                ),
                labels = emptyList()
            )
        )

        // Setup mock repository behavior for getRecentNotes(5)
        coEvery { mockRepository.getRecentNotes(5) } returns notesList

        val factory = RecentNotesRemoteViewsFactory(mockContext)
        factory.repository = mockRepository // Manually inject mocked repository

        // Act: trigger database load
        factory.onDataSetChanged()

        // Assert: count is correct
        assertEquals(2, factory.count)

        // Act: bind view at index 0 (populated note)
        val remoteViews1 = factory.getViewAt(0)
        assertNotNull(remoteViews1)

        // Act: bind view at index 1 (empty title/content note)
        val remoteViews2 = factory.getViewAt(1)
        assertNotNull(remoteViews2)
    }
}
