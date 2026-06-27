package com.mckimquyen.notes

import android.content.Intent
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.model.NotesDao
import com.mckimquyen.notes.model.NotesDb
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class InjectDataTest {

    @Test
    fun inject50NotesAndLaunch() = runBlocking {
        // MUST use targetContext, otherwise it injects into the .test app sandbox!
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Sử dụng database thật (trên ổ cứng máy), không dùng inMemory!
        val db = Room.databaseBuilder(targetContext, NotesDb::class.java, "notes_db")
            .allowMainThreadQueries()
            .build()
        val notesDao = db.notesDao()

        // Tạo 50 Notes thật
        for (i in 1..50) {
            val note = Note(
                type = NoteType.TEXT,
                title = "UI Smoke Test Note $i",
                content = "This is a real note injected into your physical database to test the UI scroll and search. Number: $i",
                metadata = BlankNoteMetadata,
                addedDate = Date(),
                lastModifiedDate = Date(),
                status = NoteStatus.ACTIVE,
                pinned = PinnedStatus.UNPINNED,
                reminder = null
            )
            notesDao.insert(note)
        }

        db.close()

        // Launch app trực tiếp từ test để user nhìn thấy
        val intent = targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)
    }
}
