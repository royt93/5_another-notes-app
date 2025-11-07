package com.mckimquyen.notes.ui.labels

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.transition.MaterialElevationScale
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.FLabelBinding
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.ui.SharedViewModel
import com.mckimquyen.notes.ui.common.ConfirmDlg
import com.mckimquyen.notes.ui.labels.adt.LabelAdt
import com.mckimquyen.notes.ui.navGraphViewModel
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.utils.startSafeActionMode
import com.mckimquyen.notes.ui.viewModel
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import com.google.android.material.R as RMaterial

/**
 * This fragment has two purposes:
 *
 * - Managing labels: add, rename, delete. Supports multiple selection.
 * - Selecting and applying labels: set or change labels on a set of notes.
 *
 * The mode is determined by the argument passed by the navigation component.
 */
class LabelFrm : DialogFragment(), Toolbar.OnMenuItemClickListener,
    ActionMode.Callback, ConfirmDlg.Callback {

    @Inject
    lateinit var viewModelFactory: LabelVM.Factory
    val viewModel by viewModel {
        viewModelFactory.create(it)
    }

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) {
        sharedViewModelProvider.get()
    }

    private val args: LabelFrmArgs by navArgs()

    private var _binding: FLabelBinding? = null
    private val binding get() = _binding!!

    private var actionMode: ActionMode? = null
    private var statusBarAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as RApp).appComponent.inject(this)

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        _binding = FLabelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        viewModel.start(args.noteIds.toList())

        binding.toolbar.apply {
            setOnMenuItemClickListener(this@LabelFrm)
            setNavigationIcon(R.drawable.ic_arrow_start)
            setNavigationOnClickListener {
                findNavController().popBackStack()
            }
            setTitle(
                if (args.noteIds.isEmpty()) {
                    R.string.label_manage
                } else {
                    R.string.label_select
                }
            )
            menu.findItem(R.id.itemConfirm).isVisible = (args.noteIds.isNotEmpty())
        }

        binding.fab.setOnClickListener {
            findNavController().navigateSafe(LabelFrmDirections.actionLabelToLabelEdit())
        }

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = LabelAdt(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        setupViewModelObservers(adapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any running animations to prevent memory leaks
        statusBarAnimator?.cancel()
        statusBarAnimator = null
        _binding = null
    }

    private fun setupViewModelObservers(adapter: LabelAdt) {
        viewModel.labelItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }

        viewModel.placeholderShown.observe(viewLifecycleOwner) { shown ->
            binding.placeholderGroup.isVisible = shown
        }

        viewModel.labelSelection.observe(viewLifecycleOwner) { count ->
            updateActionModeForSelection(count)
            updateItemsForSelection(count)
            updateFabForSelection(count)
        }

        viewModel.showRenameDialogEvent.observeEvent(viewLifecycleOwner) { labelId ->
            findNavController().navigateSafe(LabelFrmDirections.actionLabelToLabelEdit(labelId))
        }

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            showDeleteConfirmDialog()
        }

        viewModel.exitEvent.observeEvent(viewLifecycleOwner) {
            findNavController().popBackStack()
        }

        sharedViewModel.labelAddEventSelect.observeEvent(viewLifecycleOwner) { label ->
            viewModel.selectNewLabel(label)
        }
    }

    private fun updateActionModeForSelection(count: Int) {
        if (count != 0 && actionMode == null) {
            actionMode = binding.toolbar.startSafeActionMode(this)
        } else if (count == 0 && actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }
    }

    private fun updateItemsForSelection(count: Int) {
        actionMode?.let {
            it.title = NUMBER_FORMAT.format(count)

            val menu = it.menu
            menu.findItem(R.id.itemRename).isVisible = count == 1
        }
    }

    private fun updateFabForSelection(count: Int) {
        if (count != 0) {
            if (binding.fab.isOrWillBeShown) {
                binding.fab.hide()
            }
        } else if (binding.fab.isOrWillBeHidden) {
            binding.fab.show()
        }
    }

    private fun showDeleteConfirmDialog() {
        ConfirmDlg.newInstance(
            title = R.string.action_delete_selection,
            message = R.string.label_delete_message,
            btnPositive = R.string.action_delete
        ).show(/* manager = */ childFragmentManager, /* tag = */ DELETE_CONFIRM_DIALOG_TAG)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itemConfirm -> viewModel.setNotesLabels()
            else -> return false
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itemRename -> viewModel.renameSelection()
            R.id.itemDelete -> viewModel.deleteSelectionPre()
            R.id.itemSelectAll -> viewModel.selectAll()
            else -> return false
        }
        return true
    }

    private fun switchStatusBarColor(
        colorFrom: Int,
        colorTo: Int,
        duration: Long,
        endAsTransparent: Boolean = false,
    ) {
        // Cancel any existing animation
        statusBarAnimator?.cancel()

        val anim = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        statusBarAnimator = anim

        anim.duration = duration
        anim.addUpdateListener { animator ->
            requireActivity().window.statusBarColor = animator.animatedValue as Int
        }

        if (endAsTransparent) {
            anim.addListener(onEnd = {
                // Wait 50ms before resetting the status bar color to prevent flickering, when the
                // regular toolbar isn't yet visible again.
                // Use view.postDelayed instead of Executors to avoid thread leaks
                view?.postDelayed({
                    requireActivity().window.statusBarColor = Color.TRANSPARENT
                }, 50)
            })
        }

        anim.start()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_cab_label_selection, menu)
        if (Build.VERSION.SDK_INT >= 23) {
            switchStatusBarColor(
                colorFrom = (binding.toolbarLayout.background as MaterialShapeDrawable).resolvedTintColor,
                colorTo = MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_2).toLong()
            )
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        viewModel.clearSelection()
        if (Build.VERSION.SDK_INT >= 23) {
            switchStatusBarColor(
                colorFrom = MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                colorTo = (binding.toolbarLayout.background as MaterialShapeDrawable).resolvedTintColor,
                duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong(),
                endAsTransparent = true
            )
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (tag == DELETE_CONFIRM_DIALOG_TAG) {
            viewModel.deleteSelection()
        }
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()

        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
    }
}
