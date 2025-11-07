package com.mckimquyen.notes.ui.labels.adt

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.mckimquyen.notes.databinding.VItemLabelBinding

class LabelAdt(
    val context: Context,
    val callback: Callback,
) : ListAdapter<LabelListItem, LabelListVH>(LabelListDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelListVH {
        val inflater = LayoutInflater.from(parent.context)
        return LabelListVH(VItemLabelBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: LabelListVH, position: Int) {
        holder.bind(item = getItem(position), adapter = this)
    }

    override fun getItemId(position: Int) = getItem(position).id

    override fun onViewRecycled(holder: LabelListVH) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    interface Callback {
        val shouldHighlightCheckedItems: Boolean

        /** Called when a label [item] at [pos] is clicked. */
        fun onLabelItemClicked(item: LabelListItem, pos: Int)

        /** Called when a label [item] at [pos] is long-clicked. */
        fun onLabelItemLongClicked(item: LabelListItem, pos: Int)

        /** Called when the icon of a label [item] at [pos] is clicked. */
        fun onLabelItemIconClicked(item: LabelListItem, pos: Int)
    }
}
