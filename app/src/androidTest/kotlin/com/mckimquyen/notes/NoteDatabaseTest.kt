package com.mckimquyen.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.NotesDao
import com.mckimquyen.notes.model.NotesDb
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class NoteDatabaseTest {

    private lateinit var notesDao: NotesDao
    private lateinit var db: NotesDb

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            NotesDb::class.java
        ).allowMainThreadQueries().build()
        notesDao = db.notesDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadNote() = runBlocking {
        val note = Note(
            type = NoteType.TEXT,
            title = "Test DB Integration",
            content = "This is a test note for Room integration testing",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        
        // Insert note and get the generated ID
        val id = notesDao.insert(note)
        
        // Read the note by ID
        val loaded = notesDao.getById(id)
        
        // Verify values
        assertEquals(note.title, loaded?.title)
        assertEquals(note.content, loaded?.content)
    }

    @Test
    fun insertAndReadLockedNote() = runBlocking {
        val note = Note(
            type = NoteType.TEXT,
            title = "Test Locked Note",
            content = "This is a locked note",
            metadata = com.mckimquyen.notes.model.entity.BlankNoteMetadata,
            addedDate = java.util.Date(),
            lastModifiedDate = java.util.Date(),
            status = com.mckimquyen.notes.model.entity.NoteStatus.ACTIVE,
            pinned = com.mckimquyen.notes.model.entity.PinnedStatus.UNPINNED,
            reminder = null,
            isLocked = true
        )
        
        val id = notesDao.insert(note)
        val loaded = notesDao.getById(id)
        
        assertEquals(true, loaded?.isLocked)
    }

    @Test
    fun insertAndReadNoteHistory() = runBlocking {
        val note = Note(
            type = NoteType.TEXT,
            title = "Origin Note",
            content = "Origin Content",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        val noteId = notesDao.insert(note)

        val historyDao = db.noteHistoryDao()

        val history1 = com.mckimquyen.notes.model.entity.NoteHistory(
            noteId = noteId,
            title = "Version 1",
            content = "Content 1",
            metadata = BlankNoteMetadata,
            timestamp = 1000L
        )
        val history2 = com.mckimquyen.notes.model.entity.NoteHistory(
            noteId = noteId,
            title = "Version 2",
            content = "Content 2",
            metadata = BlankNoteMetadata,
            timestamp = 2000L
        )

        historyDao.insert(history1)
        historyDao.insert(history2)

        val historyList = historyDao.getHistoryForNote(noteId)
        assertEquals(2, historyList.size)
        assertEquals("Version 2", historyList[0].title) // Sorted by timestamp DESC
        assertEquals("Version 1", historyList[1].title)

        // Test pruning: keep only 1
        historyDao.pruneHistory(noteId, 1)
        val prunedList = historyDao.getHistoryForNote(noteId)
        assertEquals(1, prunedList.size)
        assertEquals("Version 2", prunedList[0].title) // Only the latest remains

        // Test clearing
        historyDao.clearHistoryForNote(noteId)
        val clearedList = historyDao.getHistoryForNote(noteId)
        assertEquals(0, clearedList.size)
    }

    @Test
    fun testInsertAndReadChecklistNote() = runBlocking {
        val checklistMetadata = com.mckimquyen.notes.model.entity.ListNoteMetadata(listOf(true, false, true))
        val note = Note(
            type = NoteType.LIST,
            title = "Checklist Note",
            content = "Item 1\nItem 2\nItem 3",
            metadata = checklistMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )

        val id = notesDao.insert(note)
        val loaded = notesDao.getById(id)

        assertNotNull(loaded)
        assertEquals(NoteType.LIST, loaded?.type)
        assertEquals("Checklist Note", loaded?.title)
        assertTrue(loaded?.metadata is com.mckimquyen.notes.model.entity.ListNoteMetadata)
        val loadedMetadata = loaded?.metadata as com.mckimquyen.notes.model.entity.ListNoteMetadata
        assertEquals(3, loadedMetadata.checked.size)
        assertEquals(true, loadedMetadata.checked[0])
        assertEquals(false, loadedMetadata.checked[1])
        assertEquals(true, loadedMetadata.checked[2])
    }

    @Test
    fun testCascadeDeleteLabel() = runBlocking {
        val note = Note(
            type = NoteType.TEXT,
            title = "Note for Label Ref",
            content = "Note Content",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        val noteId = notesDao.insert(note)

        val labelsDao = db.labelsDao()
        val label = com.mckimquyen.notes.model.entity.Label(name = "Personal")
        val labelId = labelsDao.insert(label)

        // Insert reference
        labelsDao.insertRefs(listOf(com.mckimquyen.notes.model.entity.LabelRef(noteId = noteId, labelId = labelId)))

        // Verify reference count is 1
        assertEquals(1L, labelsDao.countRefs(labelId))

        // Delete label and verify cascade delete on LabelRef
        val labelToDelete = labelsDao.getById(labelId)
        assertNotNull(labelToDelete)
        labelsDao.delete(labelToDelete!!)

        // Reference count should be 0 because LabelRef is cascade deleted
        assertEquals(0L, labelsDao.countRefs(labelId))
    }

    @Test
    fun testVietnameseUnicodeFTSSearch() = runBlocking {
        val note1 = Note(
            type = NoteType.TEXT,
            title = "Học lập trình Android",
            content = "Tiếng Việt có dấu và các ký tự đặc biệt.",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        val note2 = Note(
            type = NoteType.TEXT,
            title = "English Note",
            content = "This is a simple english text.",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        notesDao.insert(note1)
        notesDao.insert(note2)

        val query = com.mckimquyen.notes.ui.search.SearchQueryCleaner.clean("tiếng việt")
        
        val sortSettings = com.mckimquyen.notes.model.SortSettings(
            com.mckimquyen.notes.model.SortField.MODIFIED_DATE,
            com.mckimquyen.notes.model.SortDirection.DESCENDING
        )
        
        val flow = notesDao.search(query, sortSettings)
        val results = flow.first()
        
        assertEquals(1, results.size)
        assertEquals("Học lập trình Android", results[0].note.title)
    }
}
