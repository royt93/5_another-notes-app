package com.mckimquyen.notes.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.notes.model.entity.NoteHistory

@Dao
interface NoteHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: NoteHistory): Long

    @Query("SELECT * FROM note_history WHERE noteId = :noteId ORDER BY timestamp DESC")
    suspend fun getHistoryForNote(noteId: Long): List<NoteHistory>

    @Query("DELETE FROM note_history WHERE noteId = :noteId AND id NOT IN (SELECT id FROM (SELECT id FROM note_history WHERE noteId = :noteId ORDER BY timestamp DESC LIMIT :limit))")
    suspend fun pruneHistory(noteId: Long, limit: Int)

    @Query("DELETE FROM note_history WHERE noteId = :noteId")
    suspend fun clearHistoryForNote(noteId: Long)
}
