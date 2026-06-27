package com.mckimquyen.notes.ui.sort

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.DlgSortBinding
import com.mckimquyen.notes.model.SortDirection
import com.mckimquyen.notes.model.SortField
import com.mckimquyen.notes.model.SortSettings
import com.mckimquyen.notes.ui.SharedViewModel
import com.mckimquyen.notes.ui.navGraphViewModel
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.viewModel
import debugCheck
import javax.inject.Inject
import javax.inject.Provider

class SortDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) {
        sharedViewModelProvider.get()
    }

    @Inject
    lateinit var viewModelProvider: Provider<SortViewModel>
    private val viewModel by viewModel {
        viewModelProvider.get()
    }

    private var _binding: DlgSortBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as RApp?)?.appComponent?.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DlgSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Close button
        binding.btnClose.setOnClickListener {
            dismissAllowingStateLoss()
        }

        // Apply button — reads radio selections and dispatches to shared VM
        binding.btnApply.setOnClickListener {
            val field = when (binding.sortFieldRadioGroup.checkedRadioButtonId) {
                R.id.sortFieldAddedRadio -> SortField.ADDED_DATE
                R.id.sortFieldModifiedRadio -> SortField.MODIFIED_DATE
                R.id.sortFieldTitleRadio -> SortField.TITLE
                else -> SortField.MODIFIED_DATE
            }
            val direction = when (binding.sortDirectionRadioGroup.checkedRadioButtonId) {
                R.id.sortDirectionAscRadio -> SortDirection.ASCENDING
                R.id.sortDirectionDescRadio -> SortDirection.DESCENDING
                else -> SortDirection.DESCENDING
            }
            sharedViewModel.changeSortSettings(SortSettings(field, direction))
            dismissAllowingStateLoss()
        }

        setupViewModelObservers()

        if (savedInstanceState == null) {
            viewModel.start()
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

    private fun setupViewModelObservers() {
        // Using `this` as lifecycle owner, cannot show dialog twice with same instance to avoid double observation.
        debugCheck(!viewModel.sortField.hasObservers()) { "Dialog was shown twice with same instance." }

        viewModel.sortField.observeEvent(this) { field ->
            when (field) {
                SortField.ADDED_DATE -> binding.sortFieldAddedRadio
                SortField.MODIFIED_DATE -> binding.sortFieldModifiedRadio
                SortField.TITLE -> binding.sortFieldTitleRadio
            }.isChecked = true
        }

        viewModel.sortDirection.observeEvent(this) { direction ->
            when (direction) {
                SortDirection.ASCENDING -> binding.sortDirectionAscRadio
                SortDirection.DESCENDING -> binding.sortDirectionDescRadio
            }.isChecked = true
        }
    }
}
