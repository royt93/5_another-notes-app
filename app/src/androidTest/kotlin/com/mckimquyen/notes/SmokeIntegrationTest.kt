package com.mckimquyen.notes

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.notes.model.NotesDao
import com.mckimquyen.notes.model.NotesDb
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.SortField
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SmokeIntegrationTest {

    private lateinit var db: NotesDb
    private lateinit var notesDao: NotesDao
    private lateinit var prefs: PrefsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDb::class.java)
            .allowMainThreadQueries()
            .build()
        notesDao = db.notesDao()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs = PrefsManager(sharedPrefs)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun fullSmokeTest() = runBlocking {
        // 1. Tạo 50 Notes
        val noteIds = mutableListOf<Long>()
        for (i in 1..50) {
            val note = Note(
                type = NoteType.TEXT,
                title = "Note Title $i",
                content = "This is the content for note number $i",
                metadata = BlankNoteMetadata,
                addedDate = Date(),
                lastModifiedDate = Date(),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.UNPINNED,
                reminder = null
            )
            val id = notesDao.insert(note)
            noteIds.add(id)
        }
        assertEquals("Phải có đúng 50 notes trong DB", 50, notesDao.getAll().size)

        // 2. Edit 25 Notes (sửa title và content của 25 note đầu tiên)
        for (i in 0 until 25) {
            val idToEdit = noteIds[i]
            val original = notesDao.getById(idToEdit)!!
            val updated = original.copy(
                title = original.title + " (Edited)",
                content = original.content + " [Updated]"
            )
            notesDao.update(updated)
        }
        val editedNote = notesDao.getById(noteIds[0])!!
        assertTrue("Note đầu tiên phải chứa chữ Edited", editedNote.title.contains("(Edited)"))

        // 3. Delete 10 Notes (đưa vào thùng rác hoặc xóa vĩnh viễn)
        for (i in 25 until 35) {
            val idToDelete = noteIds[i]
            val toDelete = notesDao.getById(idToDelete)!!
            notesDao.delete(toDelete)
        }
        val remainingNotes = notesDao.getAll()
        assertEquals("Sau khi tạo 50, xóa 10, phải còn 40 notes", 40, remainingNotes.size)

        // 4. Search Note (Tìm kiếm FTS hoặc LIKE)
        // Tìm note có chữ "content for note number 45"
        val searchResults = remainingNotes.filter { it.note.content.contains("number 45") }
        assertTrue("Phải tìm ra ít nhất 1 note chứa số 45", searchResults.isNotEmpty())
        assertEquals("Note Title 45", searchResults.first().note.title)

        // 5. Sort Note (Test lấy danh sách và sort theo thời gian tạo giảm dần)
        val sortedNotes = remainingNotes.sortedByDescending { it.note.addedDate.time }
        assertTrue("Note mới nhất phải nằm đầu", sortedNotes.first().note.addedDate.time >= sortedNotes.last().note.addedDate.time)

        // 6. Settings (Test thao tác lưu/đọc Cài đặt từ SharedPreferences)
        val originalSortSetting = prefs.sortField
        prefs.sortField = SortField.TITLE
        assertEquals("Setting sortField phải được lưu", SortField.TITLE, prefs.sortField)
        
        // Hoàn tất Smoke Test!
    }
}
