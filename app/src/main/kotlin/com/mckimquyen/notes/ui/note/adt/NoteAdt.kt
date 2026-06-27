package com.mckimquyen.notes.ui.note.adt

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.VItemHeaderBinding
import com.mckimquyen.notes.databinding.VItemMessageBinding
import com.mckimquyen.notes.databinding.VItemNoteLabelBinding
import com.mckimquyen.notes.databinding.VItemNoteListBinding
import com.mckimquyen.notes.databinding.VItemNoteListItemBinding
import com.mckimquyen.notes.databinding.VItemNoteTextBinding
import com.mckimquyen.notes.databinding.VItemTimelineHeaderBinding
import com.mckimquyen.notes.databinding.VItemTimelineNoteBinding
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.note.SwipeAction

class NoteAdt(
    val context: Context,
    val callback: Callback,
    val prefsManager: PrefsManager,
) : ListAdapter<NoteListItem, RecyclerView.ViewHolder>(NoteListDiffCallback()) {

    /**
     * A pool of view holders for showing items of list notes.
     * When list note items are bound, view holders are obtained from this pool and bound.
     * When list note items are recycled, view holders are added back to the pool.
     * **Should only be accessed on main thread.**
     */
    private val listNoteItemViewHolderPool = ArrayDeque<ListNoteItemViewHolder>()

    /**
     * A pool of view holders for showing label chips
     * When note items are bound, view holders are obtained from this pool and bound.
     * When note items are recycled, view holders are added back to the pool.
     * **Should only be accessed on main thread.**
     */
    private val labelViewHolderPool = ArrayDeque<LabelChipViewHolder>()

    private val itemTouchHelper = ItemTouchHelper(SwipeTouchHelperCallback(callback))

    // Used by view holders with highlighted text.
    val highlightBackgroundColor = ContextCompat.getColor(context, R.color.color_highlight)
    val highlightForegroundColor = ContextCompat.getColor(context, R.color.color_on_highlight)

    /** Current layout mode — used to dispatch NoteItems to the correct view holder type. */
    var layoutMode: NoteListLayoutMode = NoteListLayoutMode.LIST

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.MESSAGE.ordinal -> MessageViewHolder(
                VItemMessageBinding.inflate(inflater, parent, false)
            )

            ViewType.HEADER.ordinal -> HeaderViewHolder(
                VItemHeaderBinding.inflate(inflater, parent, false)
            )

            ViewType.TEXT_NOTE.ordinal -> TextNoteViewHolder(
                VItemNoteTextBinding.inflate(inflater, parent, false)
            )

            ViewType.LIST_NOTE.ordinal -> ListNoteViewHolder(
                VItemNoteListBinding.inflate(inflater, parent, false)
            )

            ViewType.TIMELINE_DATE_HEADER.ordinal -> TimelineHeaderViewHolder(
                VItemTimelineHeaderBinding.inflate(inflater, parent, false)
            )

            ViewType.TIMELINE_NOTE.ordinal -> TimelineNoteViewHolder(
                VItemTimelineNoteBinding.inflate(inflater, parent, false)
            )

            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MessageViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind()
                holder.bind(item as MessageItem, this)
            }
            is HeaderViewHolder -> holder.bind(item as HeaderItem)
            is TextNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemText)
            }

            is ListNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemList)
            }

            is TimelineHeaderViewHolder -> holder.bind(item as TimelineDateHeaderItem)

            is TimelineNoteViewHolder -> holder.bind(item as NoteItem, this)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (layoutMode == NoteListLayoutMode.TIMELINE && item is NoteItem) {
            ViewType.TIMELINE_NOTE.ordinal
        } else {
            item.type.ordinal
        }
    }

    override fun getItemId(position: Int) = getItem(position).id

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // Used to recycle secondary view holders
        when (holder) {
            is NoteViewHolder<*> -> holder.unbind(this)
            is MessageViewHolder -> holder.unbind()
            is TimelineNoteViewHolder -> holder.unbind()
        }
    }

    @SuppressLint("InflateParams")
    fun obtainListNoteItemViewHolder(): ListNoteItemViewHolder =
        if (listNoteItemViewHolderPool.isNotEmpty()) {
            listNoteItemViewHolderPool.removeLast()
        } else {
            ListNoteItemViewHolder(
                VItemNoteListItemBinding.inflate(
                    LayoutInflater.from(context), null, false
                )
            )
        }

    @SuppressLint("InflateParams")
    fun obtainLabelViewHolder(): LabelChipViewHolder =
        if (labelViewHolderPool.isNotEmpty()) {
            labelViewHolderPool.removeLast()
        } else {
            LabelChipViewHolder(
                VItemNoteLabelBinding.inflate(
                    LayoutInflater.from(context), null, false
                )
            )
        }

    fun freeListNoteItemViewHolder(viewHolder: ListNoteItemViewHolder) {
        listNoteItemViewHolderPool += viewHolder
    }

    fun freeLabelViewHolder(viewHolder: LabelChipViewHolder) {
        labelViewHolderPool += viewHolder
    }

    fun updateForListLayoutChange(newMode: NoteListLayoutMode? = null) {
        if (newMode != null) layoutMode = newMode
        // Number of preview lines have changed, must rebind all items
        notifyItemRangeChanged(0, itemCount)
    }

    enum class ViewType {
        MESSAGE,
        HEADER,
        TEXT_NOTE,
        LIST_NOTE,
        TIMELINE_DATE_HEADER,
        TIMELINE_NOTE
    }

    enum class SwipeDirection {
        LEFT, RIGHT
    }

    interface Callback {
        /** Called when a note [item] at [pos] is clicked. */
        fun onNoteItemClicked(item: NoteItem, pos: Int)

        /** Called when a note [item] at [pos] is long-clicked. */
        fun onNoteItemLongClicked(item: NoteItem, pos: Int)

        /** Called when a message [item] at [pos] is dismissed by clicking on close button. */
        fun onMessageItemDismissed(item: MessageItem, pos: Int)

        /** Called when a note's action button is clicked. */
        fun onNoteActionButtonClicked(item: NoteItem, pos: Int)

        /** Returns the action for the given swipe direction. */
        fun getNoteSwipeAction(direction: SwipeDirection): SwipeAction

        /** Called when a [NoteItem] at [pos] is swiped. */
        fun onNoteSwiped(pos: Int, direction: NoteAdt.SwipeDirection)

        /** Whether strikethrough should be added to checked items or not. */
        val strikethroughCheckedItems: Boolean
    }
}
