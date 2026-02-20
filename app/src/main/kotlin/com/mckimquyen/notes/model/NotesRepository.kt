package com.mckimquyen.notes.model

import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteWithLabels
import kotlinx.coroutines.flow.Flow

interface NotesRepository {

    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun updateNotes(notes: List<Note>)
    suspend fun deleteNote(note: Note)
    suspend fun deleteNotes(notes: List<Note>)
    suspend fun getNoteById(id: Long): Note?
    suspend fun getNoteByIdWithLabels(id: Long): NoteWithLabels?
    suspend fun getLastCreatedNote(): Note?
    suspend fun getActiveNotesCount(): Int
    suspend fun getRecentNotes(limit: Int = 10): List<NoteWithLabels>

    fun getNotesByStatus(status: NoteStatus): Flow<List<NoteWithLabels>>
    fun getNotesByLabel(labelId: Long): Flow<List<NoteWithLabels>>
    fun getNotesWithReminder(): Flow<List<NoteWithLabels>>
    fun searchNotes(query: String): Flow<List<NoteWithLabels>>

    suspend fun emptyTrash()
    suspend fun deleteOldNotesInTrash()

    suspend fun clearAllData()
}
