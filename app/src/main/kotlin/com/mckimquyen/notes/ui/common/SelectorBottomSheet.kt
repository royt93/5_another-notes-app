package com.mckimquyen.notes.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
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

        // Wire close button — hidden entirely when isCancelable=false (first-run language)
        binding.btnClose.isVisible = isCancelable
        binding.btnClose.setOnClickListener {
            dismissAllowingStateLoss()
        }

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

    override fun onStart() {
        super.onStart()
        // Safe way to make bottom sheet background transparent without breaking window rendering on custom OS/Samsung devices.
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
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
            val isSelected = position == selectedIndex
            holder.binding.label.text = entries[position]
            if (isSelected) {
                holder.binding.check.isInvisible = false
            } else {
                holder.binding.check.isInvisible = true
            }
            holder.itemView.setOnClickListener {
                if (position == selectedIndex) {
                    onPick(values[position])
                    return@setOnClickListener
                }
                val previous = selectedIndex
                selectedIndex = position
                notifyItemChanged(previous, PAYLOAD_SELECTION)
                notifyItemChanged(position, PAYLOAD_SELECTION)
                onPick(values[position])
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position)
                return
            }
            val isSelected = position == selectedIndex
            if (isSelected) {
                holder.binding.check.apply {
                    alpha = 0f
                    scaleX = 0.5f
                    scaleY = 0.5f
                    isInvisible = false
                    animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                        .start()
                }
            } else {
                holder.binding.check.isInvisible = true
            }
        }

        class VH(val binding: ISelectorBottomSheetBinding) : RecyclerView.ViewHolder(binding.root)

        companion object {
            private const val PAYLOAD_SELECTION = "selection"
        }
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
            cancelable: Boolean = true,
        ) = SelectorBottomSheet().apply {
            isCancelable = cancelable
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
