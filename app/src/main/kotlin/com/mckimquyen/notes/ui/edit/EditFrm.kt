package com.mckimquyen.notes.ui.edit

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.doAfterTextChanged
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.NavGraphMainDirections
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.FEditBinding
import com.mckimquyen.notes.ext.hideKeyboard
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.ext.showKeyboard
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.model.entity.Reminder
import com.mckimquyen.notes.ui.SharedViewModel
import com.mckimquyen.notes.ui.common.ConfirmDlg
import com.mckimquyen.notes.ui.edit.adt.EditAdt
import com.mckimquyen.notes.ui.navGraphViewModel
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.startSharingData
import com.mckimquyen.notes.ui.viewModel
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Provider
import com.google.android.material.R as RMaterial

class EditFrm : Fragment(), Toolbar.OnMenuItemClickListener, ConfirmDlg.Callback {

    @Inject
    lateinit var viewModelFactory: EditVM.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    private val args: EditFrmArgs by navArgs()

    private var _binding: FEditBinding? = null
    private val binding get() = _binding!!

    private val transitionListener = object : TransitionListenerAdapter() {
        override fun onTransitionEnd(transition: Transition) {
            sharedViewModel.sharedElementTransitionFinished()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedElementEnterTransition = MaterialContainerTransform(requireContext(), true).apply {
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong()
        }

        sharedElementReturnTransition = MaterialContainerTransform(requireContext(), false).apply {
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong()
        }

        // Send an event via the sharedViewModel when the transition has finished playing
        (sharedElementReturnTransition as MaterialContainerTransform).addListener(transitionListener)

        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as RApp).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        _binding = FEditBinding.inflate(inflater, container, false)
        val noteId = args.noteId
        ViewCompat.setTransitionName(
            /* view = */ binding.fragmentEditLayout,
            /* transitionName = */ "noteContainer$noteId"
        )
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            viewModel.saveNote()
            viewModel.exit()
        }

        viewModel.start(
            noteId = args.noteId,
            labelId = args.labelId,
            changeReminder = args.changeReminder,
            type = NoteType.fromValue(args.type),
            title = args.title,
            content = args.content
        )

        // Toolbar
        binding.toolbar.apply {
            setOnMenuItemClickListener(this@EditFrm)
            setNavigationOnClickListener {
                view.hideKeyboard()
                viewModel.saveNote()
                viewModel.exit()
            }
            setTitle(
                if (args.noteId == Note.NO_ID) {
                    view.showKeyboard()
                    R.string.edit_add_title
                } else {
                    R.string.edit_change_title
                }
            )
        }

        // Recycler view
        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = EditAdt(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager
        // Feature 1+3: Live word/char count — attach text watcher to EditTexts as they appear
        rcv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val contentEdt = view.findViewById<android.widget.EditText>(R.id.contentEdt)
                val titleEdt = view.findViewById<android.widget.EditText>(R.id.titleEdt)
                contentEdt?.doAfterTextChanged { viewModel.updateLiveStats() }
                titleEdt?.doAfterTextChanged { viewModel.updateLiveStats() }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        rcv.itemAnimator = object : DefaultItemAnimator() {
            override fun animateAppearance(
                viewHolder: RecyclerView.ViewHolder,
                preLayoutInfo: ItemHolderInfo?,
                postLayoutInfo: ItemHolderInfo,
            ): Boolean {
                return if (preLayoutInfo != null && (preLayoutInfo.left != postLayoutInfo.left
                            || preLayoutInfo.top != postLayoutInfo.top)
                ) {
                    // item move, handle normally
                    super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo)
                } else {
                    // do not animate new item appearance
                    // this is mainly to avoid animating the whole list when fragment view is recreated.
                    dispatchAddFinished(viewHolder)
                    false
                }
            }
        }
        rcv.setOnTouchListener { _, event ->
            // Special case to dispatch touch events to underlaying background view to focus content view.
            // This is only done if content view is not taller than RecyclerView to avoid scrolling issues (#63).
            val contentEdt = rcv.findViewById<View>(R.id.contentEdt)
            if (contentEdt != null && contentEdt.height < rcv.height) {
                binding.viewBackground.dispatchTouchEvent(event)
            }
            false
        }
        binding.viewBackground.setOnClickListener {
            // On background click, focus note content if text note.
            viewModel.focusNoteContent()
        }

        // Dynamically adjust the padding on the bottom of the RecyclerView and Color Picker.
        // This enables edge-to-edge functionality and also handles resizing
        // when the keyboard is opened / closed.
        val initialRcvPadding = resources.getDimensionPixelSize(R.dimen.edit_recyclerview_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(rcv) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            
            // Recycler view should scroll above the color picker + keyboard
            val colorPickerHeight = resources.displayMetrics.density * 60
            rcv.updatePadding(bottom = sysWindow.bottom + initialRcvPadding + colorPickerHeight.toInt())
            
            // Color picker should rest above the keyboard / nav bar
            binding.colorPickerScroll.updatePadding(bottom = sysWindow.bottom)
            
            insets
        }

        setupViewModelObservers(adapter)

        // Delay the shared element transition until the recyclerView is ready to be drawn
        OneShotPreDrawListener.add(binding.recyclerView) {
            // Start shared element transition
            startPostponedEnterTransition()
        }
        postponeEnterTransition()
        
        setupColorPicker()
    }

    @SuppressLint("WrongConstant")
    private fun setupViewModelObservers(adapter: EditAdt) {
        val navController = findNavController()

        // Each observer must take care not to undo the work of another observer
        // in case an attribute of a menu item is dependant on more than one criterion.
        viewModel.noteStatus.observe(viewLifecycleOwner, ::updateItemsForNoteStatus)
        viewModel.notePinned.observe(viewLifecycleOwner, ::updateItemsForPinnedStatus)
        viewModel.noteReminder.observe(viewLifecycleOwner, ::updateItemsForStatusAndReminder)
        viewModel.noteType.observe(viewLifecycleOwner, ::updateItemsForNoteType)
        viewModel.noteType.asFlow().combine(viewModel.noteStatus.asFlow()) { type, status -> status to type }
            .asLiveData().observe(viewLifecycleOwner, ::updateItemsForStatusAndType)

        viewModel.editItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            // Feature 1: Trigger initial word/char count after note data is loaded
            viewModel.updateLiveStats()
        }

        viewModel.focusEvent.observeEvent(viewLifecycleOwner, adapter::setItemFocus)

        viewModel.noteCreateEvent.observeEvent(viewLifecycleOwner) { noteId ->
            sharedViewModel.noteCreated(noteId)
        }

        val restoreNoteSnackbar by lazy {
            Snackbar.make(
                requireView(), R.string.edit_in_trash_message,
                CANT_EDIT_SNACKBAR_DURATION
            )
                .setGestureInsetBottomIgnored(true)
                .setAction(R.string.action_restore) { viewModel.restoreNoteAndEdit() }
        }
        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { message ->
            when (message) {
                EditMessage.BLANK_NOTE_DISCARDED -> sharedViewModel.onBlankNoteDiscarded()
                EditMessage.RESTORED_NOTE -> Snackbar.make(
                    requireView(), resources.getQuantityText(
                        R.plurals.edit_message_move_restore, 1
                    ), Snackbar.LENGTH_SHORT
                )
                    .setGestureInsetBottomIgnored(true)
                    .show()

                EditMessage.CANT_EDIT_IN_TRASH -> restoreNoteSnackbar.show()
            }
        }

        viewModel.statusChangeEvent.observeEvent(
            viewLifecycleOwner,
            sharedViewModel::onStatusChange
        )

        viewModel.shareEvent.observeEvent(viewLifecycleOwner, ::startSharingData)

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            ConfirmDlg.newInstance(
                title = R.string.action_delete_forever,
                message = R.string.trash_delete_message,
                btnPositive = R.string.action_delete
            ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
        }

        viewModel.showRemoveCheckedConfirmEvent.observeEvent(viewLifecycleOwner) {
            ConfirmDlg.newInstance(
                title = R.string.edit_convert_keep_checked,
                btnPositive = R.string.action_delete,
                btnNegative = R.string.action_keep
            ).show(childFragmentManager, REMOVE_CHECKED_CONFIRM_DIALOG_TAG)
        }

        viewModel.showReminderDialogEvent.observeEvent(viewLifecycleOwner) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionReminder(longArrayOf(noteId)))
        }

        viewModel.showLabelsFragmentEvent.observeEvent(viewLifecycleOwner) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionLabel(longArrayOf(noteId)))
        }

        viewModel.showLinkDialogEvent.observeEvent(viewLifecycleOwner) { linkText ->
            ConfirmDlg.newInstance(
                btnPositive = R.string.action_open,
                btnNegative = R.string.action_cancel,
                messageStr = linkText,
            ).show(childFragmentManager, OPEN_LINK_DIALOG_TAG)
        }

        viewModel.openLinkEvent.observeEvent(viewLifecycleOwner) { url ->
            val uri = Uri.parse(url)
            val context = requireContext()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // do nothing
            }
        }

        sharedViewModel.reminderChangeEvent.observeEvent(viewLifecycleOwner) { reminder ->
            viewModel.onReminderChange(reminder)
        }

        viewModel.exitEvent.observeEvent(viewLifecycleOwner) {
            navController.popBackStack()
        }

        // Feature 1: Update word/char count footer
        viewModel.wordCharCount.observe(viewLifecycleOwner) { (words, chars) ->
            binding.wordCharCountTxv.text = "$words words · $chars chars"
        }

        // Feature 3: Character limit warnings
        viewModel.charLimitWarningEvent.observeEvent(viewLifecycleOwner) { charCount ->
            val limit = EditVM.CHAR_LIMIT
            val isAtLimit = charCount >= limit
            val message = if (isAtLimit) {
                "Đã đạt giới hạn $limit ký tự"
            } else {
                "Gần đạt giới hạn ($charCount/$limit ký tự)"
            }
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
                .setTextColor(
                    if (isAtLimit) {
                        requireContext().getColor(android.R.color.holo_red_light)
                    } else {
                        requireContext().getColor(android.R.color.holo_orange_light)
                    }
                )
                .setGestureInsetBottomIgnored(true)
                .show()
        }

        // Feature 10: Color Notes Observer
        viewModel.noteColor.observe(viewLifecycleOwner) { color ->
            val isDarkTheme = resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            val finalColor = if (color != 0 && isDarkTheme) {
                 androidx.core.graphics.ColorUtils.setAlphaComponent(color, 76)
            } else if (color != 0) {
                 color
            } else {
                 com.google.android.material.color.MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorSurface, Color.WHITE)
            }
            
            binding.viewBackground.setBackgroundColor(finalColor)
            binding.colorPickerScroll.setBackgroundColor(finalColor)
            binding.toolbarLayout.setBackgroundColor(finalColor)
        }
    }

    private fun updateItemsForNoteStatus(status: NoteStatus) {
        val menu = binding.toolbar.menu

        val moveItem = menu.findItem(R.id.itemMove)
        when (status) {
            NoteStatus.ACTIVE -> {
                moveItem.setIcon(R.drawable.ic_archive)
                moveItem.setTitle(R.string.action_archive)
            }

            NoteStatus.ARCHIVED -> {
                moveItem.setIcon(R.drawable.ic_unarchive)
                moveItem.setTitle(R.string.action_unarchive)
            }

            NoteStatus.DELETED -> {
                moveItem.setIcon(R.drawable.ic_restore)
                moveItem.setTitle(R.string.action_restore)
            }
        }

        val isTrash = status == NoteStatus.DELETED
        menu.findItem(R.id.itemShare).isVisible = !isTrash
        menu.findItem(R.id.itemCopy).isVisible = !isTrash
        menu.findItem(R.id.itemReminder).isVisible = !isTrash
        menu.findItem(R.id.itemDelete).setTitle(
            if (isTrash) {
                R.string.action_delete_forever
            } else {
                R.string.action_delete
            }
        )
    }

    private fun updateItemsForPinnedStatus(pinned: PinnedStatus) {
        val item = binding.toolbar.menu.findItem(R.id.itemPin)
        when (pinned) {
            PinnedStatus.PINNED -> {
                item.isVisible = true
                item.setTitle(R.string.action_unpin)
                item.setIcon(R.drawable.ic_pin_outline)
            }

            PinnedStatus.UNPINNED -> {
                item.isVisible = true
                item.setTitle(R.string.action_pin)
                item.setIcon(R.drawable.ic_pin)
            }

            PinnedStatus.CANT_PIN -> {
                item.isVisible = false
            }
        }
    }

    private fun updateItemsForStatusAndReminder(reminder: Reminder?) {
        binding.toolbar.menu.findItem(R.id.itemReminder).setTitle(
            if (reminder != null) {
                R.string.action_reminder_edit
            } else {
                R.string.action_reminder_add
            }
        )
    }

    private fun updateItemsForNoteType(type: NoteType) {
        val menu = binding.toolbar.menu

        val typeItem = menu.findItem(R.id.itemType)
        when (type) {
            NoteType.TEXT -> {
                typeItem.setIcon(R.drawable.ic_checkbox)
                typeItem.setTitle(R.string.action_convert_to_list)
            }

            NoteType.LIST -> {
                typeItem.setIcon(R.drawable.ic_text)
                typeItem.setTitle(R.string.action_convert_to_text)
            }
        }
    }

    private fun updateItemsForStatusAndType(state: Pair<NoteStatus, NoteType>) {
        val menu = binding.toolbar.menu
        val isEditableList = state.first != NoteStatus.DELETED && state.second == NoteType.LIST
        menu.findItem(R.id.itemUncheckAll).isVisible = isEditableList
        menu.findItem(R.id.itemDeleteChecked).isVisible = isEditableList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Fix MEDIUM-2: Remove TouchListener to release the lambda that captures binding.viewBackground
        binding.recyclerView.setOnTouchListener(null)
        // Remove transition listener to prevent memory leaks
        (sharedElementReturnTransition as? MaterialContainerTransform)?.removeListener(transitionListener)
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveNote()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itemType -> viewModel.toggleNoteType()
            R.id.itemMove -> viewModel.moveNoteAndExit()
            R.id.itemPin -> viewModel.togglePin()
            R.id.itemReminder -> viewModel.changeReminder()
            R.id.itemLabels -> viewModel.changeLabels()
            R.id.itemShare -> viewModel.shareNote()
            R.id.itemUncheckAll -> {
                viewModel.uncheckAllItems()
            }

            R.id.itemDeleteChecked -> viewModel.deleteCheckedItems()
            R.id.itemCopy -> viewModel.copyNote(
                getString(R.string.edit_copy_untitled_name),
                getString(R.string.edit_copy_suffix)
            )

            R.id.itemDelete -> viewModel.deleteNote()
            else -> return false
        }
        return true
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        when (tag) {
            DELETE_CONFIRM_DIALOG_TAG -> viewModel.deleteNoteForeverAndExit()
            REMOVE_CHECKED_CONFIRM_DIALOG_TAG -> viewModel.convertToText(false)
            OPEN_LINK_DIALOG_TAG -> viewModel.openClickedLink()
        }
    }

    override fun onDialogNegativeButtonClicked(tag: String?) {
        if (tag == REMOVE_CHECKED_CONFIRM_DIALOG_TAG) {
            viewModel.convertToText(true)
        }
    }

    private fun setupColorPicker() {
        val colors = listOf(
            0, // Default transparent
            Color.parseColor("#F28B82"), // Red
            Color.parseColor("#FBBC04"), // Orange
            Color.parseColor("#FFF475"), // Yellow
            Color.parseColor("#CCFF90"), // Green
            Color.parseColor("#A7FFEB"), // Teal
            Color.parseColor("#CBF0F8"), // Blue
            Color.parseColor("#AECBFA"), // Dark Blue
            Color.parseColor("#D7AEFB"), // Purple
            Color.parseColor("#FDCFE8"), // Pink
            Color.parseColor("#E6C9A8"), // Brown
            Color.parseColor("#E8EAED")  // Gray
        )

        binding.colorPickerContainer.removeAllViews()
        for (color in colors) {
            val btn = ImageButton(requireContext())
            val size = resources.displayMetrics.density * 40
            val margin = resources.displayMetrics.density * 6
            val params = LinearLayout.LayoutParams(size.toInt(), size.toInt())
            params.setMargins(margin.toInt(), margin.toInt(), margin.toInt(), margin.toInt())
            btn.layoutParams = params
            btn.setBackgroundResource(R.drawable.shape_color_circle)
            
            // Apply color to the circle background programmatically since shape_color_circle is an oval
            val bg = btn.background.mutate() as android.graphics.drawable.GradientDrawable
            bg.setColor(if (color == 0) Color.WHITE else color)
            
            btn.elevation = 4f
            btn.setOnClickListener {
                viewModel.setNoteColor(color)
            }
            binding.colorPickerContainer.addView(btn)
        }
    }

    companion object {
        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
        private const val REMOVE_CHECKED_CONFIRM_DIALOG_TAG = "remove_checked_confirm_dialog"
        private const val OPEN_LINK_DIALOG_TAG = "open_link_confirm_dialog"
        private const val CANT_EDIT_SNACKBAR_DURATION = 5000
    }
}
