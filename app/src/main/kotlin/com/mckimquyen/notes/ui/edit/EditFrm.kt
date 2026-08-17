package com.mckimquyen.notes.ui.edit

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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
import com.mckimquyen.notes.model.entity.NoteHistory
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

    private var wordCountAnimator: ValueAnimator? = null
    private var lastDisplayedWords = 0
    private var lastDisplayedChars = 0
    private var isFocusMode = false
    private var historyList: List<NoteHistory> = emptyList()

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

        // Paired with removeListener in onDestroyView; onCreate cannot register here because onDestroyView runs many times.
        (sharedElementReturnTransition as? MaterialContainerTransform)?.addListener(transitionListener)

        // viewLifecycleOwner, not `this` (the Fragment) — Navigation Component drops this
        // Fragment's view to CREATED (without destroying the Fragment instance) when
        // navigating to Reminder/Labels and back, so onViewCreated() re-runs on the same
        // instance. Registering against `this` piled up one extra callback per round trip. FIX-M03.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (isFocusMode) {
                toggleFocusMode()
                return@addCallback
            }
            if (viewModel.isReadingMode.value == true) {
                viewModel.toggleReadingMode()
                return@addCallback
            }
            if (viewModel.isTimeTraveling) {
                stopTimeTravel(restore = false)
                return@addCallback
            }
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
        // Feature 1+3: Live word/char count — attach text watcher to EditTexts as they appear.
        // R.id.contentEdt is reused by both the content row and every checklist row, and
        // RecyclerView recycles those Views on scroll — without detaching on
        // onChildViewDetachedFromWindow, each attach/detach cycle piled another watcher onto
        // the same View, so a keystroke later fired updateLiveStats() once per accumulated
        // watcher. FIX-H10.
        rcv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val contentEdt = view.findViewById<android.widget.EditText>(R.id.contentEdt)
                val titleEdt = view.findViewById<android.widget.EditText>(R.id.titleEdt)
                contentEdt?.let { attachLiveStatsWatcher(it) }
                titleEdt?.let { attachLiveStatsWatcher(it) }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                val contentEdt = view.findViewById<android.widget.EditText>(R.id.contentEdt)
                val titleEdt = view.findViewById<android.widget.EditText>(R.id.titleEdt)
                contentEdt?.let { detachLiveStatsWatcher(it) }
                titleEdt?.let { detachLiveStatsWatcher(it) }
            }
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

        binding.timeTravelSlider.addOnChangeListener { _, value, _ ->
            val idx = value.toInt()
            if (idx in historyList.indices) {
                val history = historyList[idx]
                viewModel.previewHistoryVersion(history)

                binding.timeTravelTitle.text = getString(R.string.time_travel_title_version_format, idx + 1, historyList.size)

                val timeStr = android.text.format.DateUtils.getRelativeTimeSpanString(
                    history.timestamp,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                binding.timeTravelDate.text = timeStr
            }
        }
        binding.timeTravelCancelBtn.setOnClickListener {
            stopTimeTravel(restore = false)
        }
        binding.timeTravelRestoreBtn.setOnClickListener {
            val idx = binding.timeTravelSlider.value.toInt()
            val history = historyList.getOrNull(idx)
            stopTimeTravel(restore = true, restoredHistory = history)
        }
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
        viewModel.noteLocked.observe(viewLifecycleOwner) { isLocked ->
            binding.toolbar.menu.findItem(R.id.itemLock)?.apply {
                setTitle(if (isLocked) R.string.action_unlock_note else R.string.action_lock_note)
            }
        }

        viewModel.editItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            // Feature 1: Trigger initial word/char count after note data is loaded
            viewModel.updateLiveStats()
        }

        viewModel.focusEvent.observeEvent(viewLifecycleOwner, adapter::setItemFocus)

        viewModel.isReadingMode.observe(viewLifecycleOwner) { isReadingMode ->
            adapter.isReadingMode = isReadingMode
            if (isReadingMode) {
                view?.hideKeyboard()
            }
            androidx.transition.TransitionManager.beginDelayedTransition(binding.fragmentEditLayout)

            val visibility = if (isReadingMode) View.GONE else View.VISIBLE
            binding.colorPickerScroll.visibility = visibility
            binding.moodPickerRow.visibility = visibility
            binding.wordCharCountTxv.visibility = visibility
            
            // Hide charLimitRing in reading mode
            if (isReadingMode) {
                binding.charLimitRing.visibility = View.GONE
            } else {
                val chars = viewModel.wordCharCount.value?.second ?: 0
                binding.charLimitRing.visibility = if (!isFocusMode && chars >= EditVM.CHAR_LIMIT_WARN) View.VISIBLE else View.GONE
            }

            if (!isReadingMode && isFocusMode) {
                binding.colorPickerScroll.visibility = View.GONE
                binding.wordCharCountTxv.visibility = View.GONE
            }

            val menu = binding.toolbar.menu
            menu.findItem(R.id.itemType)?.isVisible = !isReadingMode
            menu.findItem(R.id.itemPin)?.isVisible = !isReadingMode
            menu.findItem(R.id.itemReminder)?.isVisible = !isReadingMode
            menu.findItem(R.id.itemLabels)?.isVisible = !isReadingMode

            val isList = viewModel.noteType.value == NoteType.LIST
            menu.findItem(R.id.itemUncheckAll)?.isVisible = !isReadingMode && isList
            menu.findItem(R.id.itemDeleteChecked)?.isVisible = !isReadingMode && isList
            menu.findItem(R.id.itemFocusMode)?.isVisible = !isReadingMode

            menu.findItem(R.id.itemReadingMode)?.apply {
                setIcon(if (isReadingMode) R.drawable.ic_pencil else R.drawable.ic_eye)
                setTitle(if (isReadingMode) "Edit Note" else "Reading Mode")
            }
        }

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

        viewModel.noteHistory.observe(viewLifecycleOwner) { history ->
            historyList = history
            if (viewModel.isTimeTraveling) {
                if (history.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        R.string.time_travel_no_history,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    stopTimeTravel(restore = false)
                } else {
                    if (history.size == 1) {
                        binding.timeTravelSlider.isEnabled = false
                        binding.timeTravelSlider.valueFrom = 0f
                        binding.timeTravelSlider.valueTo = 1f
                        binding.timeTravelSlider.value = 0f
                        binding.timeTravelTitle.text = getString(R.string.time_travel_title_no_versions)
                    } else {
                        binding.timeTravelSlider.isEnabled = true
                        binding.timeTravelSlider.valueFrom = 0f
                        binding.timeTravelSlider.valueTo = (history.size - 1).toFloat()
                        binding.timeTravelSlider.value = (history.size - 1).toFloat()
                        val idx = history.size - 1
                        binding.timeTravelTitle.text = getString(R.string.time_travel_title_version_format, idx + 1, history.size)
                    }
                    binding.timeTravelLayout.visibility = View.VISIBLE

                    val idx = history.size - 1
                    viewModel.previewHistoryVersion(history[idx])

                    val timeStr = android.text.format.DateUtils.getRelativeTimeSpanString(
                        history[idx].timestamp,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    )
                    binding.timeTravelDate.text = timeStr
                }
            }
        }

        // Feature 1: Update word/char count footer
        viewModel.wordCharCount.observe(viewLifecycleOwner) { (words, chars) ->
            val wordDelta = kotlin.math.abs(words - lastDisplayedWords)
            if (wordDelta > 5 && lastDisplayedWords > 0) {
                if (wordCountAnimator == null) {
                    wordCountAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 200 }
                }
                wordCountAnimator?.cancel()
                wordCountAnimator?.removeAllUpdateListeners()
                val fromW = lastDisplayedWords
                val fromC = lastDisplayedChars
                wordCountAnimator?.addUpdateListener { va ->
                    val f = va.animatedFraction
                    val w = (fromW + (words - fromW) * f).toInt()
                    val c = (fromC + (chars - fromC) * f).toInt()
                    binding.wordCharCountTxv.text = "$w words · $c chars"
                }
                wordCountAnimator?.start()
            } else {
                wordCountAnimator?.cancel()
                binding.wordCharCountTxv.text = "$words words · $chars chars"
            }
            lastDisplayedWords = words
            lastDisplayedChars = chars

            // E-07: Char limit progress ring
            val charLimit = EditVM.CHAR_LIMIT
            val warnThreshold = EditVM.CHAR_LIMIT_WARN
            val pct = (chars.toFloat() / charLimit * 100).toInt().coerceIn(0, 100)
            when {
                chars >= charLimit -> {
                    if (!isFocusMode && viewModel.isReadingMode.value != true) binding.charLimitRing.isVisible = true
                    binding.charLimitRing.setIndicatorColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.charLimitRing,
                            android.R.attr.colorError,
                            0
                        )
                    )
                    binding.charLimitRing.setProgress(100, true)
                    if (binding.charLimitRing.tag != "pulse") {
                        binding.charLimitRing.tag = "pulse"
                        binding.charLimitRing.animate()
                            .scaleX(1.2f).scaleY(1.2f).setDuration(200)
                            .withEndAction {
                                binding.charLimitRing.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                            }.start()
                    }
                }
                chars >= warnThreshold -> {
                    if (!isFocusMode && viewModel.isReadingMode.value != true) binding.charLimitRing.isVisible = true
                    binding.charLimitRing.tag = null
                    binding.charLimitRing.setIndicatorColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.charLimitRing,
                            android.R.attr.colorPrimary,
                            0
                        )
                    )
                    binding.charLimitRing.setProgress(pct, true)
                }
                else -> {
                    binding.charLimitRing.isVisible = false
                    binding.charLimitRing.tag = null
                }
            }
        }

        // Feature 3: Character limit warnings
        viewModel.wordMilestoneEvent.observeEvent(viewLifecycleOwner) { milestone ->
            val label = when {
                milestone >= 1_000 -> "${milestone / 1_000}k"
                else -> "$milestone"
            }
            binding.wordCharCountTxv.animate()
                .scaleX(1.4f).scaleY(1.4f).setDuration(120).setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction {
                    binding.wordCharCountTxv.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }.start()
            com.google.android.material.snackbar.Snackbar.make(
                binding.fragmentEditLayout,
                "🎉 $label words written!",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }

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

        // F-01: Mood picker
        val moodButtons = listOf(
            binding.moodBtn0, binding.moodBtn1, binding.moodBtn2,
            binding.moodBtn3, binding.moodBtn4, binding.moodBtn5
        )
        moodButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { viewModel.setMood(index) }
        }
        viewModel.noteMood.observe(viewLifecycleOwner) { mood ->
            moodButtons.forEachIndexed { index, btn ->
                val selected = index == mood && mood != 0
                btn.alpha = if (selected) 1f else 0.45f
                btn.scaleX = if (selected) 1.25f else 1f
                btn.scaleY = if (selected) 1.25f else 1f
            }
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
        val isReading = viewModel.isReadingMode.value == true

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
        menu.findItem(R.id.itemReminder).isVisible = !isTrash && !isReading
        menu.findItem(R.id.itemExport)?.isVisible = !isTrash
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
        val isReading = viewModel.isReadingMode.value == true
        when (pinned) {
            PinnedStatus.PINNED -> {
                item.isVisible = !isReading
                item.setTitle(R.string.action_unpin)
                item.setIcon(R.drawable.ic_pin_outline)
            }

            PinnedStatus.UNPINNED -> {
                item.isVisible = !isReading
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
        val isReading = viewModel.isReadingMode.value == true

        val typeItem = menu.findItem(R.id.itemType)
        typeItem.isVisible = !isReading
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
        val isReading = viewModel.isReadingMode.value == true
        val isEditableList = !isReading && state.first != NoteStatus.DELETED && state.second == NoteType.LIST
        menu.findItem(R.id.itemUncheckAll).isVisible = isEditableList
        menu.findItem(R.id.itemDeleteChecked).isVisible = isEditableList
    }

    private fun toggleFocusMode() {
        isFocusMode = !isFocusMode
        val alpha = if (isFocusMode) 0f else 1f
        // Toolbar fades to 0.15 (not 0) so the Focus button stays tappable as an exit hint.
        // Back press also exits focus mode (intercepted in onBackPressedDispatcher callback).
        binding.toolbarLayout.animate().alpha(if (isFocusMode) 0.15f else 1f).setDuration(250).start()
        binding.colorPickerScroll.animate().alpha(alpha).setDuration(250)
            .withEndAction { binding.colorPickerScroll.isVisible = !isFocusMode }.start()
        binding.wordCharCountTxv.animate().alpha(alpha).setDuration(250).start()
        if (!isFocusMode) {
            binding.colorPickerScroll.isVisible = true
            binding.charLimitRing.alpha = 1f
        }
    }

    private fun attachLiveStatsWatcher(editText: android.widget.EditText) {
        detachLiveStatsWatcher(editText) // guard against a duplicate attach with no detach in between
        editText.tag = editText.doAfterTextChanged { viewModel.updateLiveStats() }
    }

    private fun detachLiveStatsWatcher(editText: android.widget.EditText) {
        (editText.tag as? TextWatcher)?.let { editText.removeTextChangedListener(it) }
        editText.tag = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wordCountAnimator?.cancel()
        wordCountAnimator = null
        // These ViewPropertyAnimators have withEndAction lambdas that touch `binding` — cancel
        // them before _binding = null below, or a callback firing 200-400ms after this method
        // returns hits `_binding!!` and crashes. FIX-M07.
        binding.charLimitRing.animate().cancel()
        binding.wordCharCountTxv.animate().cancel()
        binding.toolbarLayout.animate().cancel()
        binding.colorPickerScroll.animate().cancel()
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
            R.id.itemFocusMode -> toggleFocusMode()
            R.id.itemReadingMode -> viewModel.toggleReadingMode()
            R.id.itemLock -> viewModel.toggleLock()
            R.id.itemTimeTravel -> startTimeTravel()
            R.id.itemExport -> {
                android.widget.Toast.makeText(requireContext(), R.string.export_menu_clicked, android.widget.Toast.LENGTH_SHORT).show()
                showExportDialog()
            }
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
            // Tooltip + content description: tells the user that the white circle is "no color"
            // and lets the rest be readable to TalkBack.
            btn.contentDescription = if (color == 0) getString(R.string.color_default) else null
            androidx.appcompat.widget.TooltipCompat.setTooltipText(
                btn,
                if (color == 0) getString(R.string.color_default) else null
            )
            btn.setOnClickListener {
                viewModel.setNoteColor(color)
            }
            binding.colorPickerContainer.addView(btn)
        }
    }

    private fun showExportDialog() {
        val options = arrayOf(
            getString(R.string.export_as_pdf),
            getString(R.string.export_as_image)
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportNote(true)
                    1 -> exportNote(false)
                }
            }
            .show()
    }

    private fun exportNote(isPdf: Boolean) {
        android.widget.Toast.makeText(
            requireContext(),
            if (isPdf) R.string.exporting_pdf else R.string.exporting_image,
            android.widget.Toast.LENGTH_SHORT
        ).show()
        val note = viewModel.getNoteForExport()
        val labels = viewModel.getLabelsForExport()
        val cleanTitle = if (note.title.isBlank()) getString(R.string.export_untitled) else note.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val timestamp = System.currentTimeMillis()
        val filename = if (isPdf) "${cleanTitle}_$timestamp.pdf" else "${cleanTitle}_$timestamp.png"

        try {
            val exportDir = java.io.File(requireContext().cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            // Clear previous exports to save space
            exportDir.listFiles()?.forEach { it.delete() }

            val file = java.io.File(exportDir, filename)
            if (isPdf) {
                ExportHelper.exportAsPdf(requireContext(), note, labels, file)
            } else {
                ExportHelper.exportAsImage(requireContext(), note, labels, file)
            }

            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.export_success_format, file.length()),
                android.widget.Toast.LENGTH_LONG
            ).show()

            // Share tệp
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isPdf) "application/pdf" else "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.export_failed_format, e.message),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startTimeTravel() {
        view?.hideKeyboard()

        binding.colorPickerScroll.visibility = View.GONE
        binding.moodPickerRow.visibility = View.GONE
        binding.wordCharCountTxv.visibility = View.GONE
        binding.charLimitRing.visibility = View.GONE

        val menu = binding.toolbar.menu
        menu.findItem(R.id.itemType)?.isVisible = false
        menu.findItem(R.id.itemPin)?.isVisible = false
        menu.findItem(R.id.itemReminder)?.isVisible = false
        menu.findItem(R.id.itemLabels)?.isVisible = false
        menu.findItem(R.id.itemShare)?.isVisible = false
        menu.findItem(R.id.itemDelete)?.isVisible = false

        viewModel.enterTimeTravelMode()
        viewModel.loadNoteHistory()
    }

    private fun stopTimeTravel(restore: Boolean, restoredHistory: NoteHistory? = null) {
        binding.timeTravelLayout.visibility = View.GONE

        val isReadingMode = viewModel.isReadingMode.value == true
        val visibility = if (isReadingMode) View.GONE else View.VISIBLE
        binding.colorPickerScroll.visibility = visibility
        binding.moodPickerRow.visibility = visibility
        binding.wordCharCountTxv.visibility = visibility

        val menu = binding.toolbar.menu
        menu.findItem(R.id.itemType)?.isVisible = !isReadingMode
        menu.findItem(R.id.itemPin)?.isVisible = !isReadingMode
        menu.findItem(R.id.itemReminder)?.isVisible = !isReadingMode
        menu.findItem(R.id.itemLabels)?.isVisible = !isReadingMode
        menu.findItem(R.id.itemShare)?.isVisible = true
        menu.findItem(R.id.itemDelete)?.isVisible = true

        viewModel.exitTimeTravelMode(restore, restoredHistory)
    }

    companion object {
        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
        private const val REMOVE_CHECKED_CONFIRM_DIALOG_TAG = "remove_checked_confirm_dialog"
        private const val OPEN_LINK_DIALOG_TAG = "open_link_confirm_dialog"
        private const val CANT_EDIT_SNACKBAR_DURATION = 5000
    }
}
