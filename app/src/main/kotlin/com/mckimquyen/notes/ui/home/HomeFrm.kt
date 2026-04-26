package com.mckimquyen.notes.ui.home

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.mckimquyen.notes.NavGraphMainDirections
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.model.entity.NoteStatus
import com.roy.sdkadbmob.AdManager
import com.mckimquyen.notes.ui.common.ConfirmDlg
import com.mckimquyen.notes.ui.navigation.HomeDestination
import com.mckimquyen.notes.ui.note.NoteFrm
import com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.viewModel
import javax.inject.Inject
import com.google.android.material.R as RMaterial

/**
 * Start screen fragment displaying a list of notes for different note status,
 * by label, or with a reminder.
 */
class HomeFrm : NoteFrm(), Toolbar.OnMenuItemClickListener {

    @Inject
    lateinit var viewModelFactory: HomeVM.Factory
    override val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SDK auto-tracks currentActivity via ActivityLifecycleCallbacks; no need to set manually.
        AdManager.loadInterstitial(requireContext())
        val context = requireContext()
        (context.applicationContext as RApp?)?.appComponent?.inject(this)
    }

    override fun onResume() {
        super.onResume()

        // Hide the crown action when the user is already Premium — frees up toolbar space.
        binding.toolbar.menu.findItem(R.id.itemVip)?.isVisible =
            !com.roy.sdkadbmob.AdManager.isVipByKeyActive()

        val context = requireContext()

        var batteryRestricted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Detect battery restriction as it affects reminder alarms.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager?.isBackgroundRestricted == true) {
                batteryRestricted = true
            }
        }

        var notificationRestricted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED
            ) {
                notificationRestricted = true
            }
        }

        viewModel.updateRestrictions(battery = batteryRestricted, notifications = notificationRestricted)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        binding.toolbar.apply {
            inflateMenu(R.menu.menu_toolbar_home)
            setOnMenuItemClickListener(this@HomeFrm)
            setNavigationIcon(R.drawable.ic_menu)
            setNavigationContentDescription(R.string.content_descrp_open_drawer)
            setNavigationOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            // Hide or show build type and flavor specific items
            menu.findItem(R.id.itemExtraAction).isVisible = com.mckimquyen.notes.BuildConfig.ENABLE_DEBUG_FEATURES
        }

        // Floating action button
        binding.fab.transitionName = "createNoteTransition"
        binding.fab.setOnClickListener {
            AdManager.showInterstitial(requireActivity()) { success ->
                if (success) {
                    Log.d("roy93~", "Ad đã hiển thị và đóng thành công")
                } else {
                    Log.d("roy93~", "Ad không hiển thị được hoặc có lỗi")
                }
                viewModel.createNote()
            }
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT)
                .setGestureInsetBottomIgnored(true)
                .show()
        }

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            updateListLayoutItemForMode(mode ?: return@observe)
        }

        viewModel.currentSelection.observe(viewLifecycleOwner) { selection ->
            if (selection.count != 0) {
                // Lock drawer when user just selected a first note.
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }



        viewModel.fabShown.observe(viewLifecycleOwner) { shown ->
            if (shown) {
                binding.fab.show()
            } else {
                binding.fab.hide()
            }
        }

        viewModel.createNoteEvent.observeEvent(viewLifecycleOwner) { settings ->
            exitTransition = Hold().apply {
                duration = resources.getInteger(RMaterial.integer.material_motion_duration_medium_2).toLong()
            }

            val extras = FragmentNavigatorExtras(
                binding.fab to "noteContainer0"
            )

            findNavController().navigateSafe(
                NavGraphMainDirections.actionEditNote(
                    labelId = settings.labelId, changeReminder = settings.initialReminder
                ), extras = extras
            )
        }

        viewModel.showEmptyTrashDialogEvent.observeEvent(viewLifecycleOwner) {
            showEmptyTrashConfirmDialog()
        }

        sharedViewModel.currentHomeDestination.observe(viewLifecycleOwner) { destination ->
            viewModel.setDestination(destination)
            updateToolbarForDestination(destination)
        }

        // Feature 4: Use plain observe + peekContent() so we can read the event
        // even after NoteFrm's EventObserver has already consumed it (hasBeenHandled=true).
        // Guard with a local reference to prevent re-firing on lifecycle resume.
        var lastAdEvent: Any? = null
        viewModel.statusChangeEvent.observe(viewLifecycleOwner) { event ->
            if (event === lastAdEvent) return@observe  // already handled this exact event
            lastAdEvent = event
            val statusChange = event.peekContent()
            Log.d("roy93~", "[Ad-Delete] statusChangeEvent: newStatus=${statusChange.newStatus}, count=${statusChange.oldNotes.size}")
            if (statusChange.newStatus == NoteStatus.DELETED) {
                Log.d("roy93~", "[Ad-Delete] Note DELETED — calling showInterstitial")
                AdManager.showInterstitial(requireActivity()) { success ->
                    Log.d("roy93~", "[Ad-Delete] showInterstitial result: success=$success")
                }
            }
        }

        sharedViewModel.sortChangeEvent.observeEvent(viewLifecycleOwner, viewModel::changeSort)
    }

    private fun updateToolbarForDestination(destination: HomeDestination) {
        // Show "Empty recycle bin" toolbar option
        binding.toolbar.menu.findItem(R.id.itemEmptyTrash).isVisible =
            destination == HomeDestination.Status(NoteStatus.DELETED)

        // Update toolbar title
        binding.toolbar.title = when (destination) {
            is HomeDestination.Status -> when (destination.status) {
                NoteStatus.ACTIVE -> getString(R.string.note_location_active)
                NoteStatus.ARCHIVED -> getString(R.string.note_location_archived)
                NoteStatus.DELETED -> getString(R.string.note_location_deleted)
            }

            is HomeDestination.Labels -> destination.label.name
            is HomeDestination.Reminders -> getString(R.string.note_reminders)
        }
    }

    private fun updateListLayoutItemForMode(mode: NoteListLayoutMode) {
        val layoutItem = binding.toolbar.menu.findItem(R.id.itemLayout)
        when (mode) {
            NoteListLayoutMode.LIST -> {
                layoutItem.setIcon(R.drawable.ic_view_grid)
                layoutItem.setTitle(R.string.action_layout_grid)
            }

            NoteListLayoutMode.GRID -> {
                layoutItem.setIcon(R.drawable.ic_view_list)
                layoutItem.setTitle(R.string.action_layout_list)
            }
        }
    }

    private fun showEmptyTrashConfirmDialog() {
        ConfirmDlg.newInstance(
            title = R.string.action_empty_trash,
            message = R.string.trash_empty_message,
            btnPositive = R.string.action_empty_trash_short
        ).show(childFragmentManager, EMPTY_TRASH_DIALOG_TAG)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itemVip -> findNavController().navigateSafe(HomeFrmDirections.actionHomeToVip())
            R.id.itemSearch -> findNavController().navigateSafe(HomeFrmDirections.actionHomeToSearch())
            R.id.itemLayout -> viewModel.toggleListLayoutMode()
            R.id.itemSort -> findNavController().navigateSafe(HomeFrmDirections.actionHomeToSort())
            R.id.itemEmptyTrash -> viewModel.emptyTrashPre()
            R.id.itemExtraAction -> viewModel.doExtraAction()
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        super.onDestroyActionMode(mode)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        super.onDialogPositiveButtonClicked(tag)
        if (tag == EMPTY_TRASH_DIALOG_TAG) {
            viewModel.emptyTrash()
        }
    }

    companion object {
        private const val EMPTY_TRASH_DIALOG_TAG = "empty_trash_dialog"
    }
}
