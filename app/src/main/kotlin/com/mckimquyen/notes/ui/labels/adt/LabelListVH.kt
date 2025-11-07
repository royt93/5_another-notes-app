package com.mckimquyen.notes.ui.labels.adt

import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.VItemLabelBinding

class LabelListVH(val binding: VItemLabelBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: LabelListItem, adapter: LabelAdt) {
        var name = item.label.name
        if (com.mckimquyen.notes.BuildConfig.ENABLE_DEBUG_FEATURES) {
            name += " (${item.label.id})"
        }

        binding.labelTxv.text = name
        binding.hiddenImv.isInvisible = !item.label.hidden

        val view = binding.root

        if (adapter.callback.shouldHighlightCheckedItems) {
            view.isActivated = item.checked
        } else {
            binding.labelImv.setImageResource(
                if (item.checked) {
                    R.drawable.ic_label
                } else {
                    R.drawable.ic_label_outline
                }
            )
        }

        binding.labelImv.setOnClickListener {
            adapter.callback.onLabelItemIconClicked(item, bindingAdapterPosition)
        }
        view.setOnClickListener {
            adapter.callback.onLabelItemClicked(item, bindingAdapterPosition)
        }
        view.setOnLongClickListener {
            adapter.callback.onLabelItemLongClicked(item, bindingAdapterPosition)
            true
        }
    }

    fun unbind() {
        binding.labelImv.setOnClickListener(null)
        binding.root.setOnClickListener(null)
        binding.root.setOnLongClickListener(null)
    }
}
