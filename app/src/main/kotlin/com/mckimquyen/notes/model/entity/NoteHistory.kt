package com.mckimquyen.notes.model.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_history",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
@Keep
data class NoteHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "noteId", index = true)
    val noteId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "metadata")
    val metadata: NoteMetadata,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
