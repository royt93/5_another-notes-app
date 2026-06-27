package com.mckimquyen.notes.ui.note.adt

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.notes.databinding.VItemTimelineHeaderBinding
import com.mckimquyen.notes.databinding.VItemTimelineNoteBinding
import com.mckimquyen.notes.model.entity.NoteType

class TimelineHeaderViewHolder(
    private val binding: VItemTimelineHeaderBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TimelineDateHeaderItem) {
        binding.dateTxv.text = item.dateLabel
    }
}

class TimelineNoteViewHolder(
    private val binding: VItemTimelineNoteBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: NoteItem, adapter: NoteAdt) {
        val note = item.note

        // Title
        binding.titleTxv.text = item.title.content
        binding.titleTxv.isVisible = item.title.content.isNotBlank()

        val isLocked = note.isLocked
        binding.lockImv.isVisible = isLocked

        if (isLocked) {
            binding.contentTxv.isVisible = false
            binding.moodTxv.isVisible = false
        } else {
            // Content preview (only for text notes)
            val content = when (item) {
                is NoteItemText -> item.content.content
                is NoteItemList -> if (item.items.isNotEmpty()) item.items.first().content else ""
            }
            binding.contentTxv.text = content
            binding.contentTxv.isVisible = content.isNotBlank()

            // Mood badge
            val moodEmoji = when (note.mood) {
                1 -> "😄"; 2 -> "😐"; 3 -> "😔"; 4 -> "💡"; 5 -> "🔥"; else -> null
            }
            binding.moodTxv.isVisible = moodEmoji != null
            binding.moodTxv.text = moodEmoji
        }

        // Card background color
        val color = note.color
        if (color != 0) {
            binding.cardView.setCardBackgroundColor(color)
        } else {
            binding.cardView.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.cardView,
                    com.google.android.material.R.attr.colorSurface,
                    android.graphics.Color.WHITE
                )
            )
        }

        // Click listeners
        binding.cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, bindingAdapterPosition)
        }
        binding.cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, bindingAdapterPosition)
            true
        }

        // Selection state
        binding.cardView.isChecked = item.checked
    }

    fun unbind() {
        binding.cardView.setOnClickListener(null)
        binding.cardView.setOnLongClickListener(null)
    }
}
