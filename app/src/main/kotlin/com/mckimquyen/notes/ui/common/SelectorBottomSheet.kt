package com.mckimquyen.notes.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.notes.databinding.DlgSelectorBottomSheetBinding
import com.mckimquyen.notes.databinding.ISelectorBottomSheetBinding

/**
 * Cupertino-style single-choice bottom sheet that replaces the default ListPreference dialog.
 * Returns the picked value via FragmentResult under [requestKey]; the bundle key is [RESULT_KEY].
 *
 * Usage:
 *   childFragmentManager.setFragmentResultListener("selector_app_language", this) { _, bundle ->
 *       val newValue = bundle.getString(SelectorBottomSheet.RESULT_KEY).orEmpty()
 *       // apply value...
 *   }
 *   SelectorBottomSheet.newInstance("selector_app_language", title, entries, values, current)
 *       .show(childFragmentManager, "selector")
 */
class SelectorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DlgSelectorBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val requestKey: String get() = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()
    private val title: String get() = requireArguments().getString(ARG_TITLE).orEmpty()
    private val entries: Array<String>
        get() = requireArguments().getStringArray(ARG_ENTRIES) ?: emptyArray()
    private val values: Array<String>
        get() = requireArguments().getStringArray(ARG_VALUES) ?: emptyArray()
    private val initialValue: String
        get() = requireArguments().getString(ARG_SELECTED).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DlgSelectorBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = title
        binding.items.layoutManager = LinearLayoutManager(requireContext())
        binding.items.adapter = Adapter(entries, values, initialValue) { picked ->
            // Animate selection then dismiss — feels more polished than instant dismiss.
            view.postDelayed({
                setFragmentResult(requestKey, bundleOf(RESULT_KEY to picked))
                dismissAllowingStateLoss()
            }, ANIMATION_DELAY_MS)
        }
        // Auto-scroll to current selection so the user lands on it.
        val currentIndex = values.indexOf(initialValue).coerceAtLeast(0)
        binding.items.post {
            (binding.items.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(currentIndex, ITEM_SCROLL_OFFSET_PX)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class Adapter(
        private val entries: Array<String>,
        private val values: Array<String>,
        initialValue: String,
        private val onPick: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private var selectedIndex: Int = values.indexOf(initialValue).coerceAtLeast(0)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ISelectorBottomSheetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.label.text = entries[position]
            holder.binding.check.isInvisible = position != selectedIndex
            holder.itemView.setOnClickListener {
                if (position == selectedIndex) {
                    // Tap on already-selected: dismiss anyway, treating it as confirm.
                    onPick(values[position])
                    return@setOnClickListener
                }
                val previous = selectedIndex
                selectedIndex = position
                notifyItemChanged(previous)
                notifyItemChanged(position)
                onPick(values[position])
            }
        }

        class VH(val binding: ISelectorBottomSheetBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val RESULT_KEY = "value"

        private const val ARG_REQUEST_KEY = "requestKey"
        private const val ARG_TITLE = "title"
        private const val ARG_ENTRIES = "entries"
        private const val ARG_VALUES = "values"
        private const val ARG_SELECTED = "selected"

        private const val ANIMATION_DELAY_MS = 180L
        private const val ITEM_SCROLL_OFFSET_PX = 0

        fun newInstance(
            requestKey: String,
            title: String,
            entries: Array<String>,
            values: Array<String>,
            selectedValue: String,
        ) = SelectorBottomSheet().apply {
            arguments = bundleOf(
                ARG_REQUEST_KEY to requestKey,
                ARG_TITLE to title,
                ARG_ENTRIES to entries,
                ARG_VALUES to values,
                ARG_SELECTED to selectedValue,
            )
        }
    }
}
