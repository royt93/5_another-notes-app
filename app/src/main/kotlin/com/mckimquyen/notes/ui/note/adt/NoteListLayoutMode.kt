package com.mckimquyen.notes.ui.note.adt

import com.mckimquyen.notes.model.ValueEnum
import com.mckimquyen.notes.model.findValueEnum

/**
 * A note list layout mode.
 */
enum class NoteListLayoutMode(override val value: Int) : ValueEnum<Int> {
    LIST(0),
    GRID(1),
    TIMELINE(2);

    companion object {
        fun fromValue(value: Int): NoteListLayoutMode = findValueEnum(value)
    }
}
