package com.mckimquyen.notes.ui.note.adt

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

class NoteListDiffCallback : DiffUtil.ItemCallback<NoteListItem>() {

    override fun areItemsTheSame(old: NoteListItem, new: NoteListItem) = old.id == new.id

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(old: NoteListItem, new: NoteListItem): Boolean {
        if (new.type != old.type) {
            // Should never happen since items of different types can't have the same ID.
            return false
        }

        return when (new) {
            is MessageItem -> {
                old as MessageItem
                new.message == old.message
            }

            is HeaderItem -> {
                old as HeaderItem
                new.title == old.title
            }

            is TimelineDateHeaderItem -> {
                old as TimelineDateHeaderItem
                new.dateLabel == old.dateLabel
            }

            is NoteItem -> {
                // Only check the attributes that have an influence on the
                // visual representation of the note item.
                old as NoteItem
                val oldNote = old.note
                val newNote = new.note
                new.checked == old.checked &&
                        newNote.type == oldNote.type &&
                        newNote.status == oldNote.status &&
                        newNote.pinned == oldNote.pinned &&
                        newNote.title == oldNote.title &&
                        newNote.content == oldNote.content &&
                        newNote.metadata == oldNote.metadata &&
                        newNote.reminder == oldNote.reminder &&
                        newNote.color == oldNote.color &&
                        new.labels == old.labels &&
                        new.showMarkAsDone == old.showMarkAsDone &&
                        // At this point only content highlights can differ
                        when (new) {
                            is NoteItemText -> {
                                old as NoteItemText
                                new.content == old.content
                            }

                            is NoteItemList -> {
                                old as NoteItemList
                                new.items == old.items
                            }
                        }
            }
        }
    }
}
