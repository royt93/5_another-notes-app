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
import org.junit.After
import org.junit.Assert.assertEquals
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
}
