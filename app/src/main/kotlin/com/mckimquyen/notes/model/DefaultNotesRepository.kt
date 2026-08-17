package com.mckimquyen.notes.model

import android.content.Context
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteHistory
import com.mckimquyen.notes.widget.NoteCountWidget
import com.mckimquyen.notes.widget.RecentNotesWidget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultNotesRepository @Inject constructor(
    private val context: Context,
    private val notesDao: NotesDao,
    private val noteHistoryDao: NoteHistoryDao,
    private val prefs: PrefsManager,
) : NotesRepository {

    // Data modification methods are wrapped in non-cancellable context
    // so that calling them in onPause for example won't cancel the transaction on the
    // subsequent onDestroy call, which cancels the coroutine scope.

    override suspend fun insertNote(note: Note): Long = withContext(NonCancellable) {
        val id = notesDao.insert(note)
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
        id
    }

    override suspend fun updateNote(note: Note) = withContext(NonCancellable) {
        notesDao.update(note)
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun updateNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.updateAll(notes)
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun deleteNote(note: Note) = withContext(NonCancellable) {
        notesDao.delete(note)
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun deleteNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.deleteAll(notes)
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun getNoteById(id: Long) = notesDao.getById(id)

    override suspend fun getNoteByIdWithLabels(id: Long) = notesDao.getByIdWithLabels(id)

    override suspend fun getLastCreatedNote() = notesDao.getLastCreatedNote()

    override suspend fun getActiveNotesCount() = notesDao.getActiveNotesCount()

    override suspend fun getRecentNotes(limit: Int) = notesDao.getRecentNotes(limit)

    override fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status, prefs.sortSettings)

    override fun getNotesByLabel(labelId: Long) = notesDao.getByLabel(labelId, prefs.sortSettings)

    override fun getNotesWithReminder() = notesDao.getAllWithReminder()

    override fun searchNotes(query: String) = notesDao.search(query, prefs.sortSettings)

    override suspend fun emptyTrash() {
        withContext(NonCancellable) {
            notesDao.deleteNotesByStatusAndDate(NoteStatus.DELETED, Long.MAX_VALUE)
            NoteCountWidget.updateAllWidgets(context)
            RecentNotesWidget.updateAllWidgets(context)
        }
    }

    override suspend fun deleteOldNotesInTrash() = withContext(NonCancellable) {
        val delay = PrefsManager.TRASH_AUTO_DELETE_DELAY.inWholeMilliseconds
        val minDate = System.currentTimeMillis() - delay
        notesDao.deleteNotesByStatusAndDate(NoteStatus.DELETED, minDate)
        // Usually called from background sync/alarm, safe to update widgets
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun clearAllData() = withContext(NonCancellable) {
        notesDao.clear()
        NoteCountWidget.updateAllWidgets(context)
        RecentNotesWidget.updateAllWidgets(context)
    }

    override suspend fun insertNoteHistory(history: NoteHistory): Long = withContext(NonCancellable) {
        noteHistoryDao.insert(history)
    }

    override suspend fun getHistoryForNote(noteId: Long): List<NoteHistory> = noteHistoryDao.getHistoryForNote(noteId)

    override suspend fun pruneHistory(noteId: Long, limit: Int) = withContext(NonCancellable) {
        noteHistoryDao.pruneHistory(noteId, limit)
    }

    override suspend fun clearHistoryForNote(noteId: Long) = withContext(NonCancellable) {
        noteHistoryDao.clearHistoryForNote(noteId)
    }
}
