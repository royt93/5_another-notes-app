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
}
