package com.mckimquyen.notes.ui.note

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.app.SharedElementCallback
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.google.android.material.transition.MaterialElevationScale
import com.mckimquyen.notes.NavGraphMainDirections
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.FNoteBinding
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.ui.SharedViewModel
import com.mckimquyen.notes.ui.StatusChange
import com.mckimquyen.notes.ui.common.ConfirmDlg
import com.mckimquyen.notes.ui.main.MainAct
import com.mckimquyen.notes.ui.navGraphViewModel
import com.mckimquyen.notes.ui.note.adt.NoteAdt
import com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode
import com.mckimquyen.notes.ui.note.adt.SpringItemAnimator
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.startSharingData
import com.mckimquyen.notes.utils.startSafeActionMode
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Provider
import com.google.android.material.R as RMaterial

/**
 * This fragment provides common code for home and search fragments.
 */
abstract class NoteFrm : Fragment(), ActionMode.Callback, ConfirmDlg.Callback,
    NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    val sharedViewModel: SharedViewModel by navGraphViewModel(R.id.nav_graph_main) {
        sharedViewModelProvider.get()
    }

    @Inject
    lateinit var prefsManager: PrefsManager

    protected abstract val viewModel: NoteVM

    private var _binding: FNoteBinding? = null
    protected val binding get() = _binding!!

    private var actionMode: ActionMode? = null

    protected lateinit var drawerLayout: DrawerLayout

    private val handler = Handler(Looper.getMainLooper())
    private var statusBarAnimator: ValueAnimator? = null

    private var spanCount = 1
    private var hideActionMode = false

    private var layoutManager: androidx.recyclerview.widget.RecyclerView.LayoutManager? = null
    private var currentHomeDestinationChanged: Boolean = false

    private var isSharedElementTransitionPlaying: Boolean = false
    private var rcvOneShotPreDrawListener: OneShotPreDrawListener? = null
    private var createdNote: View? = null
    private var createdNoteId: Long? = null
    private var isPlaceholderShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>?,
                sharedElements: MutableMap<String, View>?,
            ) {
                isSharedElementTransitionPlaying = !sharedElements.isNullOrEmpty()
                super.onMapSharedElements(names, sharedElements)
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        // Drawer
        val activity = requireActivity() as MainAct
        drawerLayout = activity.drawerLayout

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        rcv.itemAnimator = SpringItemAnimator()
        val adapter = NoteAdt(context, viewModel, prefsManager)
        val staggeredLayoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        this.layoutManager = staggeredLayoutManager
        rcv.adapter = adapter
        rcv.layoutManager = staggeredLayoutManager

        // Apply padding to the bottom of the recyclerview, so that the last notes aren't covered by the FAB
        val initialPadding = resources.getDimensionPixelSize(R.dimen.notes_recyclerview_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(rcv) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            rcv.updatePadding(bottom = sysWindow.bottom + initialPadding)
            insets
        }

        val navController = findNavController()
        navController.addOnDestinationChangedListener(this)

        setupViewModelObservers(adapter, staggeredLayoutManager)

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }

        // Handle Shared Element Transitions when returning to this fragment.
        if (isSharedElementTransitionPlaying) {
            rcvOneShotPreDrawListener = OneShotPreDrawListener.add(binding.recyclerView) {
                exitTransition = null
                enterTransition = null
                // Start shared element transition when the recyclerview is ready to be drawn
                startPostponedEnterTransition()
            }

            // Delay shared element transition until all views are laid out
            postponeEnterTransition()
        }
    }

    private fun noteListCommitCallback() {
        // Scroll to top of notes list, when the HomeDestination has changed
        if (currentHomeDestinationChanged) {
            if ((binding.recyclerView.adapter?.itemCount ?: 0) > 0) {
                binding.recyclerView.scrollToPosition(0)
                binding.recyclerView.scrollBy(0, -1)
            }
            currentHomeDestinationChanged = false
        }
    }

    private fun setupNoteItemsObserver(adapter: NoteAdt) {
        viewModel.noteItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items, ::noteListCommitCallback)

            if (isSharedElementTransitionPlaying) {
                // Remove observer to prevent changes to the recyclerview content,
                // while a transition is playing.
                viewModel.noteItems.removeObservers(viewLifecycleOwner)
            }
        }
    }

    private fun setupViewModelObservers(
        adapter: NoteAdt,
        initialLayoutManager: StaggeredGridLayoutManager,
    ) {
        val navController = findNavController()

        setupNoteItemsObserver(adapter)

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            val rcv = binding.recyclerView
            when (mode!!) {
                NoteListLayoutMode.TIMELINE -> {
                    val linearLm = LinearLayoutManager(requireContext())
                    this.layoutManager = linearLm
                    rcv.layoutManager = linearLm
                    spanCount = 1
                }
                else -> {
                    val sgLm = layoutManager as? StaggeredGridLayoutManager
                        ?: StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL).also {
                            this.layoutManager = it
                            rcv.layoutManager = it
                        }
                    sgLm.spanCount = resources.getInteger(
                        when (mode) {
                            NoteListLayoutMode.LIST -> R.integer.note_list_layout_span_count
                            NoteListLayoutMode.GRID -> R.integer.note_grid_layout_span_count
                            else -> R.integer.note_list_layout_span_count
                        }
                    )
                    if (rcv.layoutManager !== sgLm) {
                        this.layoutManager = sgLm
                        rcv.layoutManager = sgLm
                    }
                    spanCount = sgLm.spanCount
                }
            }
            adapter.updateForListLayoutChange(mode)
        }

        viewModel.editItemEvent.observeEvent(viewLifecycleOwner) { (noteId, pos) ->
            val noteItem = adapter.currentList.find { it.id == noteId } as? com.mckimquyen.notes.ui.note.adt.NoteItem
            val note = noteItem?.note

            fun proceedToEdit() {
                exitTransition = Hold().apply {
                    duration = resources.getInteger(RMaterial.integer.material_motion_duration_medium_2).toLong()
                }

                val viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(pos)
                val itemView: View? = viewHolder?.itemView?.findViewById(R.id.cardView)

                val extras = if (itemView != null) {
                    FragmentNavigatorExtras(itemView to "noteContainer$noteId")
                } else {
                    null
                }

                // If the selected note isn't completely in view, move it into view.
                val sgLm = layoutManager as? StaggeredGridLayoutManager
                if (sgLm != null) {
                    val firstVisibleItem = sgLm.findFirstCompletelyVisibleItemPositions(null).minOrNull()
                    val lastVisibleItem = sgLm.findLastCompletelyVisibleItemPositions(null).maxOrNull()
                    if (firstVisibleItem != null && lastVisibleItem != null &&
                        (pos < firstVisibleItem || pos > lastVisibleItem)
                    ) {
                        binding.recyclerView.scrollToPosition(pos)
                    }
                } else {
                    val llm = layoutManager as? LinearLayoutManager
                    if (llm != null) {
                        val firstVisibleItem = llm.findFirstCompletelyVisibleItemPosition()
                        val lastVisibleItem = llm.findLastCompletelyVisibleItemPosition()
                        if (pos < firstVisibleItem || pos > lastVisibleItem) {
                            binding.recyclerView.scrollToPosition(pos)
                        }
                    }
                }
                navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), extras = extras)
            }

            if (note != null && note.isLocked) {
                if (com.mckimquyen.notes.ui.common.BiometricHelper.isBiometricAvailable(requireContext())) {
                    com.mckimquyen.notes.ui.common.BiometricHelper.showBiometricPrompt(
                        fragment = this,
                        title = getString(R.string.lock_biometric_title),
                        subtitle = getString(R.string.lock_biometric_prompt_message),
                        onSuccess = {
                            proceedToEdit()
                        }
                    )
                } else {
                    Snackbar.make(requireView(), R.string.lock_biometric_not_configured_warning, Snackbar.LENGTH_LONG).show()
                }
            } else {
                proceedToEdit()
            }
        }

        viewModel.currentSelection.observe(viewLifecycleOwner) { selection ->
            updateActionModeForSelection(selection)
            updateItemsForSelection(selection)
        }

        viewModel.shareEvent.observeEvent(viewLifecycleOwner) { data ->
            startSharingData(data)
        }

        viewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        }

        viewModel.placeholderData.observe(viewLifecycleOwner) { data ->
            binding.placeholderImv.animate().cancel()
            binding.placeholderTxv.animate().cancel()
            if (data != null) {
                binding.placeholderImv.setImageResource(data.iconId)
                binding.placeholderTxv.setText(data.messageId)
                if (!isPlaceholderShowing) {
                    isPlaceholderShowing = true
                    binding.placeholderGroup.isVisible = true
                    binding.placeholderImv.alpha = 0f
                    binding.placeholderImv.scaleX = 0.6f
                    binding.placeholderImv.scaleY = 0.6f
                    binding.placeholderTxv.alpha = 0f
                    binding.placeholderTxv.translationY = resources.displayMetrics.density * 16f
                    binding.placeholderImv.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                        .start()
                    binding.placeholderTxv.animate()
                        .alpha(1f).translationY(0f)
                        .setStartDelay(120).setDuration(280)
                        .start()
                }
            } else {
                if (isPlaceholderShowing) {
                    isPlaceholderShowing = false
                    binding.placeholderImv.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f)
                        .setDuration(200).withEndAction {
                            if (!isPlaceholderShowing) {
                                binding.placeholderGroup.isVisible = false
                                binding.placeholderImv.scaleX = 1f
                                binding.placeholderImv.scaleY = 1f
                                // Recreate layout manager to prevent weird spacing after placeholder shown.
                                if (layoutManager is LinearLayoutManager && layoutManager !is StaggeredGridLayoutManager) {
                                    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
                                } else {
                                    binding.recyclerView.layoutManager =
                                        StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
                                }
                            }
                        }.start()
                    binding.placeholderTxv.animate().alpha(0f).setDuration(150).start()
                }
            }
            if (data == null && !isPlaceholderShowing) binding.placeholderGroup.isVisible = false
        }

        viewModel.showReminderDialogEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionReminder(noteIds.toLongArray()))
        }

        viewModel.showLabelsFragmentEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionLabel(noteIds.toLongArray()))
        }

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            showDeleteConfirmDialog()
        }

        sharedViewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT)
                .setGestureInsetBottomIgnored(true)
                .show()
        }
        sharedViewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            showMessageForStatusChange(statusChange)
        }
        sharedViewModel.currentHomeDestinationChangeEvent.observeEvent(viewLifecycleOwner) {
            currentHomeDestinationChanged = true
        }
        sharedViewModel.sharedElementTransitionFinishedEvent.observeEvent(viewLifecycleOwner) {
            isSharedElementTransitionPlaying = false
            // Reattach observers
            setupNoteItemsObserver(adapter)

            // Reset the transition names of the fab and the newly created note
            if (createdNote != null && createdNoteId != null) {
                binding.fab.transitionName = "createNoteTransition"
                createdNote?.transitionName = "noteContainer$createdNoteId"
            }
        }
        sharedViewModel.noteCreatedEvent.observeEvent(viewLifecycleOwner) { noteId ->
            rcvOneShotPreDrawListener?.removeListener()
            OneShotPreDrawListener.add(binding.recyclerView) {
                exitTransition = null
                enterTransition = null

                // Change the transition names, so that the shared element transition returns to
                // the newly created note item in the recyclerview instead of to the FAB.
                for (c in binding.recyclerView.children) {
                    if (c.findViewById<View>(R.id.cardView)?.transitionName == "noteContainer$noteId") {
                        binding.fab.transitionName = ""
                        c.transitionName = "createNoteTransition"

                        createdNoteId = noteId
                        createdNote = c
                        break
                    }
                }

                startPostponedEnterTransition()
            }
        }
    }

    private fun updateActionModeForSelection(selection: NoteVM.NoteSelection) {
        if (selection.count != 0 && actionMode == null) {
            actionMode = binding.toolbar.startSafeActionMode(this)
        } else if (selection.count == 0 && actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }
    }

    private fun updateItemsForSelection(selection: NoteVM.NoteSelection) {
        actionMode?.let {
            it.title = NUMBER_FORMAT.format(selection.count)

            // Share and copy are only visible if there is a single note selected.
            val menu = it.menu
            val copyShareVisible = selection.count == 1 && selection.status != NoteStatus.DELETED
            menu.findItem(R.id.itemShare).isVisible = copyShareVisible
            menu.findItem(R.id.itemCopy).isVisible = copyShareVisible

            // Pin item
            val pinItem = menu.findItem(R.id.itemPin)
            when (selection.pinned) {
                PinnedStatus.PINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin_outline)
                    pinItem.setTitle(R.string.action_unpin)
                }

                PinnedStatus.UNPINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin)
                    pinItem.setTitle(R.string.action_pin)
                }

                PinnedStatus.CANT_PIN -> {
                    pinItem.isVisible = false
                }
            }

            // Reminder item
            val reminderItem = menu.findItem(R.id.itemReminder)
            reminderItem.isVisible = (selection.status != NoteStatus.DELETED)
            reminderItem.setTitle(
                if (selection.hasReminder) {
                    R.string.action_reminder_edit
                } else {
                    R.string.action_reminder_add
                }
            )

            // Labels item
            val labelsItem = menu.findItem(R.id.itemLabels)
            labelsItem.isVisible = (selection.status != NoteStatus.DELETED)

            // Update move items depending on status
            val moveItem = menu.findItem(R.id.itemMove)
            val deleteItem = menu.findItem(R.id.itemDelete)
            when (selection.status!!) {
                NoteStatus.ACTIVE -> {
                    moveItem.setIcon(R.drawable.ic_archive)
                    moveItem.setTitle(R.string.action_archive)
                    deleteItem.setTitle(R.string.action_delete)
                }

                NoteStatus.ARCHIVED -> {
                    moveItem.setIcon(R.drawable.ic_unarchive)
                    moveItem.setTitle(R.string.action_unarchive)
                    deleteItem.setTitle(R.string.action_delete)
                }

                NoteStatus.DELETED -> {
                    moveItem.setIcon(R.drawable.ic_restore)
                    moveItem.setTitle(R.string.action_restore)
                    deleteItem.setTitle(R.string.action_delete_forever)
                }
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        ConfirmDlg.newInstance(
            title = R.string.action_delete_selection_forever,
            message = R.string.trash_delete_selected_message,
            btnPositive = R.string.action_delete
        ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
    }

    @SuppressLint("WrongConstant")
    private fun showMessageForStatusChange(statusChange: StatusChange) {
        val messageId = when (statusChange.newStatus) {
            NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.DELETED) {
                R.plurals.edit_message_move_restore
            } else {
                R.plurals.edit_message_move_unarchive
            }

            NoteStatus.ARCHIVED -> R.plurals.edit_move_archive_message
            NoteStatus.DELETED -> R.plurals.edit_message_move_delete
        }
        val count = statusChange.oldNotes.size
        val message = requireContext().resources.getQuantityString(messageId, count, count)

        Snackbar.make(requireView(), message, STATUS_CHANGE_SNACKBAR_DURATION)
            .setAction(R.string.action_undo) {
                sharedViewModel.undoStatusChange()
            }
            .setGestureInsetBottomIgnored(true)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.stopUpdatingList()

        // If navigating to another fragment while there is a selection (action mode shown),
        // action mode will stay shown on top of new fragment. It has to be destroyed to hide it.
        // `actionMode.finish` calls `onDestroyActionMode`, but we don't want to clear the
        // selection, hence the `hideActionMode` flag.
        // When view is recreated, the selection observer will be fired and action mode reshown.
        hideActionMode = (actionMode != null)
        actionMode?.finish()
        _binding = null

        layoutManager = null
        rcvOneShotPreDrawListener = null
        createdNote = null
        createdNoteId = null

        // Clean up handler callbacks
        handler.removeCallbacksAndMessages(null)

        // Cancel any running animations to prevent memory leaks
        statusBarAnimator?.cancel()
        statusBarAnimator = null

        // Safe removal of listener to prevent memory leaks
        try {
            findNavController().removeOnDestinationChangedListener(this)
        } catch (e: IllegalStateException) {
            // Fragment may not be attached to a NavController anymore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Additional safety check to ensure listener is removed
        try {
            findNavController().removeOnDestinationChangedListener(this)
        } catch (e: IllegalStateException) {
            // Fragment may not be attached to a NavController anymore
        }
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itemPin -> viewModel.togglePin()
            R.id.itemReminder -> viewModel.createReminder()
            R.id.itemLabels -> viewModel.changeLabels()
            R.id.itemMove -> viewModel.moveSelectedNotes()
            R.id.itemSelectAll -> viewModel.selectAll()
            R.id.itemShare -> viewModel.shareSelectedNote()
            R.id.itemCopy -> viewModel.copySelectedNote(
                getString(R.string.edit_copy_untitled_name), getString(R.string.edit_copy_suffix)
            )

            R.id.itemDelete -> viewModel.deleteSelectedNotesPre()
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

        val anim = ValueAnimator.ofObject(/* evaluator = */ ArgbEvaluator(), /* ...values = */ colorFrom, colorTo)
        statusBarAnimator = anim

        anim.duration = duration
        anim.addUpdateListener { animator ->
            requireActivity().window.statusBarColor = animator.animatedValue as Int
        }

        if (endAsTransparent) {
            anim.addListener(onEnd = {
                // Wait 50ms before resetting the status bar color to prevent flickering, when the
                // regular toolbar isn't yet visible again.
                // Use Handler instead of Executors to avoid thread leaks
                handler.postDelayed({
                    if (isAdded && !isDetached) {
                        requireActivity().window.statusBarColor = Color.TRANSPARENT
                    }
                }, 50)
            })
        }

        anim.start()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_cab_note_selection, menu)
        if (Build.VERSION.SDK_INT >= 23) {
            switchStatusBarColor(
                toolbarTintColor(),
                MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                resources.getInteger(RMaterial.integer.material_motion_duration_long_2).toLong()
            )
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        if (!hideActionMode) {
            viewModel.clearSelection()

            if (Build.VERSION.SDK_INT >= 23) {
                switchStatusBarColor(
                    MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                    toolbarTintColor(),
                    resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong(),
                    true
                )
            }
        }
        hideActionMode = false
    }

    // toolbarLayout's background is normally a MaterialShapeDrawable (from the
    // MaterialToolbar/AppBarLayout theme), but a customized ROM/theme could swap it for a
    // plain ColorDrawable/GradientDrawable — fall back to the surface color instead of
    // crashing the whole Action Mode transition. FIX-L10.
    private fun toolbarTintColor(): Int {
        return (binding.toolbarLayout.background as? MaterialShapeDrawable)?.resolvedTintColor
            ?: MaterialColors.getColor(requireView(), RMaterial.attr.colorSurface)
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?,
    ) {
        if (destination.id == R.id.fragment_edit) {
            // If notes are selected and action mode is shown, navigating to edit fragment
            // with reminder notification or with share action won't dismiss the action mode.
            // Must do it manually.
            viewModel.clearSelection()
        }

        val noteIdsSize = arguments?.getLongArray("noteIds")?.size
        if (destination.id == R.id.fragment_label && noteIdsSize != null && noteIdsSize > 0) {
            // Change status bar color to match label fragment
            if (Build.VERSION.SDK_INT >= 23) {
                switchStatusBarColor(
                    colorFrom = MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                    colorTo = MaterialColors.getColor(requireView(), RMaterial.attr.colorSurface),
                    duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong() * 2,
                    endAsTransparent = true
                )
            }
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (tag == DELETE_CONFIRM_DIALOG_TAG) {
            viewModel.deleteSelectedNotes()
        }
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()
        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
        private const val STATUS_CHANGE_SNACKBAR_DURATION = 5000
    }
}

