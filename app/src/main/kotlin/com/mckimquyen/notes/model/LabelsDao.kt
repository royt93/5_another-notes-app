package com.mckimquyen.notes.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.LabelRef
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label)

    @Delete
    suspend fun delete(label: Label)

    @Delete
    suspend fun deleteAll(labels: List<Label>)

    /**
     * Used for clearing all data.
     */
    @Query("DELETE FROM labels")
    suspend fun clear()

    /**
     * Get all labels in database
     * Used for exporting data.
     */
    @Query("SELECT * FROM labels")
    suspend fun getAll(): List<Label>

    /**
     * Get all labels in database, sorted by most used first, then by name.
     * Used for viewing labels.
     * Left join so that labels with no references are returned.
     */
    @Query(
        """SELECT labels.* FROM labels LEFT JOIN label_refs ON labelId == id GROUP BY id
                    ORDER BY CASE WHEN labelId IS NULL THEN 0 ELSE COUNT(*) END DESC, name ASC"""
    )
    fun getAllByUsage(): Flow<List<Label>>

    /**
     * Get a label by its ID. Returns `null` if label doesn't exist.
     */
    @Query("SELECT * FROM labels WHERE id == :id")
    suspend fun getById(id: Long): Label?

    /**
     * Get a label by its name, or `null` if none exists. Case-insensitive, so "Work" and
     * "work" are treated as the same label for uniqueness/dedup purposes. FIX-M26.
     * Used to ensure name uniqueness and for searching by label.
     */
    @Query("SELECT * FROM labels WHERE name == :name COLLATE NOCASE")
    suspend fun getLabelByName(name: String): Label?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefs(refs: List<LabelRef>)

    @Delete
    suspend fun deleteRefs(refs: List<LabelRef>)

    /**
     * Get all label references for a note by ID.
     * Used to remove old label references when changing labels on a note.
     */
    @Query("SELECT labelId FROM label_refs WHERE noteId == :noteId")
    suspend fun getLabelIdsForNote(noteId: Long): List<Long>

    /**
     * Returns the number of references to a label ID.
     * Used when deleting a label to show confirmation or not.
     */
    @Query("SELECT COUNT(*) FROM label_refs WHERE labelId == :labelId")
    suspend fun countRefs(labelId: Long): Long
}
