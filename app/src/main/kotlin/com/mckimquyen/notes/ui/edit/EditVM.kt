package com.mckimquyen.notes.ui.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.model.entity.ListNoteMetadata
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteMetadata
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.model.entity.Reminder
import com.mckimquyen.notes.model.entity.NoteHistory
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.ShareData
import com.mckimquyen.notes.ui.StatusChange
import com.mckimquyen.notes.ui.edit.adt.EditAdt
import com.mckimquyen.notes.ui.edit.adt.EditCheckedHeaderItem
import com.mckimquyen.notes.ui.edit.adt.EditChipsItem
import com.mckimquyen.notes.ui.edit.adt.EditContentItem
import com.mckimquyen.notes.ui.edit.adt.EditDateItem
import com.mckimquyen.notes.ui.edit.adt.EditItemAddItem
import com.mckimquyen.notes.ui.edit.adt.EditItemItem
import com.mckimquyen.notes.ui.edit.adt.EditListItem
import com.mckimquyen.notes.ui.edit.adt.EditTitleItem
import com.mckimquyen.notes.ui.edit.adt.EditableText
import com.mckimquyen.notes.ui.note.ShownDateField
import com.mckimquyen.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Date

/**
 * View model for the edit note screen.
 */
class EditVM @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefs: PrefsManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(), EditAdt.Callback {

    /**
     * Whether the current note is a new note.
     * This is important to remember as to not recreate as new blank note
     * when [start] is called a second time.
     */
    private var isNewNote = false

    /**
     * Note being edited by user. This note data is not up-to-date with the UI.
     * - Call [updateNote] to update it to reflect UI state.
     * - Call [saveNote] to update it from UI and update database.
     */
    private var note = BLANK_NOTE

    /**
     * List of labels on note. Always reflects the UI.
     */
    private var labels = emptyList<Label>()

    /**
     * Status of the note being edited. This is separate from [note] so that
     * note status can be updated from this in [updateNote].
     */
    private var status = note.status

    /**
     * Whether the note being edited is pinned or not.
     */
    private var pinned = note.pinned

    /**
     * The reminder set on the note, or `null` if none is set.
     */
    private var reminder: Reminder? = null

    /**
     * The color of the note, 0 if default.
     */
    private var color: Int = note.color

    /** Mood tag: 0 = none, 1–5 = emoji moods. */
    private var mood: Int = note.mood

    /** Whether the note is locked with biometric authentication. */
    private var isLocked: Boolean = note.isLocked

    /**
     * URL of last clicked span, if any.
     */
    private var linkUrl: String? = null

    private var historySnapshotJob: Job? = null
    private var lastSnapshotTitle: String? = null
    private var lastSnapshotContent: String? = null

    /**
     * The currently displayed list items created in [recreateListItems].
     *
     * While this list is mutable, any in place changes should be reported to the adapter! This is used in the case
     * of moving items, where the view model updates the list but the adapter already knows of the move.
     *
     * Note the in the case of list items, a specific item has no identity. Its position and its content
     * can change at any time, so it can be associated with a stable ID. This is problematic for item diff callback
     * and animations, which would rely on ID. Instead, the diff callback was made to rely on identity so that
     * add/remove animations take place correctly. The recycler view was also set up to not animate item appearance,
     * so that if the list is completely recreated, no animation will occur despite all items identity being lost.
     *
     * To allow restoring list note items original positions when checking and unchecking (if move to bottom is set),
     * each list note item carries an `actualPos` field which is the item actual position in the list note.
     * This field must be kept up-to-date after all changes to the list!
     */
    private val listItems: MutableList<EditListItem> = mutableListOf()

    private val _noteType = MutableLiveData<NoteType>()
    val noteType: LiveData<NoteType>
        get() = _noteType

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _notePinned = MutableLiveData<PinnedStatus>()
    val notePinned: LiveData<PinnedStatus>
        get() = _notePinned

    private val _noteReminder = MutableLiveData<Reminder?>()
    val noteReminder: LiveData<Reminder?>
        get() = _noteReminder

    private val _noteColor = MutableLiveData<Int>()
    val noteColor: LiveData<Int>
        get() = _noteColor

    private val _noteMood = MutableLiveData<Int>()
    val noteMood: LiveData<Int>
        get() = _noteMood

    private val _noteLocked = MutableLiveData<Boolean>()
    val noteLocked: LiveData<Boolean>
        get() = _noteLocked

    private val _isReadingMode = MutableLiveData<Boolean>(false)
    val isReadingMode: LiveData<Boolean>
        get() = _isReadingMode

    private val _editItems = MutableLiveData<MutableList<EditListItem>>()
    val editItems: LiveData<out List<EditListItem>>
        get() = _editItems

    private val _noteCreateEvent = MutableLiveData<Event<Long>>()
    val noteCreateEvent: LiveData<Event<Long>>
        get() = _noteCreateEvent

    private val _focusEvent = MutableLiveData<Event<FocusChange>>()
    val focusEvent: LiveData<Event<FocusChange>>
        get() = _focusEvent

    private val _messageEvent = MutableLiveData<Event<EditMessage>>()
    val messageEvent: LiveData<Event<EditMessage>>
        get() = _messageEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _showDeleteConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeleteConfirmEvent

    private val _showRemoveCheckedConfirmEvent = MutableLiveData<Event<Unit>>()
    val showRemoveCheckedConfirmEvent: LiveData<Event<Unit>>
        get() = _showRemoveCheckedConfirmEvent

    private val _showReminderDialogEvent = MutableLiveData<Event<Long>>()
    val showReminderDialogEvent: LiveData<Event<Long>>
        get() = _showReminderDialogEvent

    private val _showLabelsFragmentEvent = MutableLiveData<Event<Long>>()
    val showLabelsFragmentEvent: LiveData<Event<Long>>
        get() = _showLabelsFragmentEvent

    private val _showLinkDialogEvent = MutableLiveData<Event<String>>()
    val showLinkDialogEvent: LiveData<Event<String>>
        get() = _showLinkDialogEvent

    private val _openLinkEvent = MutableLiveData<Event<String>>()
    val openLinkEvent: LiveData<Event<String>>
        get() = _openLinkEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    // Feature 1: Word count & char count for footer display
    private val _wordCharCount = MutableLiveData<Pair<Int, Int>>(Pair(0, 0))
    val wordCharCount: LiveData<Pair<Int, Int>>
        get() = _wordCharCount

    // Feature 3: Character limit warning (9000 = 90%, 10000 = 100%)
    private val _charLimitWarningEvent = MutableLiveData<Event<Int>>()
    val charLimitWarningEvent: LiveData<Event<Int>>
        get() = _charLimitWarningEvent
    private var lastCharWarnThreshold = 0

    private val _noteHistory = MutableLiveData<List<NoteHistory>>()
    val noteHistory: LiveData<List<NoteHistory>>
        get() = _noteHistory

    private val _historyUpdated = MutableLiveData<Boolean>(false)
    val historyUpdated: LiveData<Boolean>
        get() = _historyUpdated

    private val _restoreEvent = MutableLiveData<Event<Pair<String, String>>>()
    val restoreEvent: LiveData<Event<Pair<String, String>>>
        get() = _restoreEvent

    // F-04: Word count milestones
    private val _wordMilestoneEvent = MutableLiveData<Event<Int>>()
    val wordMilestoneEvent: LiveData<Event<Int>>
        get() = _wordMilestoneEvent
    private var reachedWordMilestones = mutableSetOf<Int>()
    // True on first updateLiveStats call — used to pre-populate milestones for existing notes
    // without firing celebration events (opening a 600-word note must not trigger celebrations).
    private var isFirstStatsUpdate = true

    /**
     * Whether to show date item.
     */
    private val shouldShowDate: Boolean
        get() = if (isNewNote) false else prefs.shownDateField != ShownDateField.NONE

    /**
     * Whether note is currently in trash (deleted) or not.
     */
    private val isNoteInTrash: Boolean
        get() = status == NoteStatus.DELETED

    private var updateNoteJob: Job? = null
    private var restoreNoteJob: Job? = null

    init {
        if (KEY_NOTE_ID in savedStateHandle) {
            restoreNoteJob = viewModelScope.launch {
                isNewNote = savedStateHandle[KEY_IS_NEW_NOTE] ?: false

                val note = notesRepository.getNoteById(savedStateHandle[KEY_NOTE_ID] ?: Note.NO_ID)
                if (note != null) {
                    this@EditVM.note = note
                }
                restoreNoteJob = null
            }
        }
    }

    /**
     * Initialize the view model to edit a note with the ID [noteId].
     * The view model can only be started once to edit a note.
     * Subsequent calls with different arguments will do nothing and previous note will be edited.
     *
     * @param noteId Can be [Note.NO_ID] to create a new note with [type], [title] and [content].
     * @param labelId Can be different from [Label.NO_ID] to initially set a label on a new note.
     * @param changeReminder Whether to start editing note by first changing the reminder.
     */
    fun start(
        noteId: Long = Note.NO_ID,
        labelId: Long = Label.NO_ID,
        changeReminder: Boolean = false,
        type: NoteType = NoteType.TEXT,
        title: String = "",
        content: String = "",
    ) {
        viewModelScope.launch {
            // If fragment was very briefly destroyed then recreated, it's possible that this job is launched
            // before the job to save the note on fragment destruction is called.
            updateNoteJob?.join()
            // Also make sure note is restored after recreation before this is called.
            restoreNoteJob?.join()

            val isFirstStart = (note == BLANK_NOTE)

            // Try to get note by ID with its labels.
            val noteWithLabels = notesRepository.getNoteByIdWithLabels(
                if (isFirstStart) {
                    // first start, use provided note ID
                    noteId
                } else {
                    // start() was already called, fragment view was probably recreated
                    // use the note ID of the note being edited previously
                    note.id
                }
            )

            var note = noteWithLabels?.note
            var labels = noteWithLabels?.labels

            if (note == null) {
                // Note doesn't exist, create new blank note of the corresponding type.
                // This is the expected path for creating a new note (by passing Note.NO_ID)
                val date = Date()
                note = BLANK_NOTE.copy(addedDate = date, lastModifiedDate = date, title = title, content = content)
                if (type == NoteType.LIST) {
                    note = note.asListNote()
                }

                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)

                // If a label was passed to be initially set, use it.
                // Otherwise no labels will be set.
                val label = labelsRepository.getLabelById(labelId)
                labels = listOfNotNull(label)
                if (label != null) {
                    labelsRepository.insertLabelRefs(listOf(LabelRef(id, labelId)))
                }

                _noteCreateEvent.send(id)

                isNewNote = true
                savedStateHandle[KEY_IS_NEW_NOTE] = true
            }

            this@EditVM.note = note
            this@EditVM.labels = labels!!
            status = note.status
            pinned = note.pinned
            reminder = note.reminder
            color = note.color
            mood = note.mood
            isLocked = note.isLocked

            _noteType.value = note.type
            _noteStatus.value = status
            _notePinned.value = pinned
            _noteReminder.value = reminder
            _noteColor.value = color
            _noteMood.value = mood
            _noteLocked.value = isLocked

            savedStateHandle[KEY_NOTE_ID] = note.id

            recreateListItems()

            if (isFirstStart && isNewNote) {
                // Focus on title
                focusItemAt(findItemPos<EditTitleItem>(), 0, false)

                if (changeReminder) {
                    changeReminder()
                }
            }
        }
    }

    /**
     * Update note and save it in database if it was changed.
     * This updates last modified date.
     */
    fun saveNote() {
        if (isTimeTraveling) {
            // Restore draft values so that history preview doesn't overwrite active changes if screen exits/backgrounds
            note = note.copy(
                title = draftTitle,
                content = draftContent,
                metadata = draftMetadata,
                type = draftType
            )
        } else {
            updateNote()
        }

        historySnapshotJob?.cancel()

        // NonCancellable to avoid save being cancelled if called right before fragment destruction
        updateNoteJob = viewModelScope.launch(NonCancellable) {
            // Compare previously saved note from database with new one.
            // It is possible that note will be null here, if:
            // - Back arrow is clicked, saving note.
            // - Exit is called subsequently, deleting blank note.
            // - onStop calls saveNote again, but note was deleted.
            val oldNote = notesRepository.getNoteById(note.id) ?: return@launch
            if (oldNote != note) {
                // Note was changed.
                // To know whether last modified date should be changed, compare note
                // with a copy that has the original values for fields we don't care about.
                val noteForComparison = note.copy(
                    pinned = if (note.status == oldNote.status) oldNote.pinned else note.pinned
                )
                if (oldNote != noteForComparison) {
                    note = note.copy(lastModifiedDate = Date())
                }

                notesRepository.updateNote(note)
                updateNoteJob = null
            }
            if (!isTimeTraveling) {
                saveHistorySnapshot(note.title, note.content)
            }
        }
    }

    /**
     * Send exit event. If note is blank, it's discarded.
     */
    fun exit() {
        viewModelScope.launch {
            updateNoteJob?.join()
            if (note.isBlank) {
                // Delete blank note
                deleteNoteInternal()
                _messageEvent.send(EditMessage.BLANK_NOTE_DISCARDED)
            }
            _exitEvent.send()
        }
    }

    fun toggleNoteType() {
        updateNote()

        // Convert note type
        note = when (note.type) {
            NoteType.TEXT -> note.asListNote()
            NoteType.LIST -> {
                if ((note.metadata as ListNoteMetadata).checked.any { it }) {
                    _showRemoveCheckedConfirmEvent.send()
                    return
                } else {
                    note.asTextNote(true)
                }
            }
        }
        _noteType.value = note.type

        // Update list items
        recreateListItems()

        // Go to first focusable item
        when (note.type) {
            NoteType.TEXT -> {
                val contentPos = listItems.indexOfLast { it is EditContentItem }
                focusItemAt(
                    pos = contentPos,
                    textPos = (listItems[contentPos] as EditContentItem).content.text.length,
                    itemExists = false
                )
            }

            NoteType.LIST -> {
                val lastItemPos = listItems.indexOfLast { it is EditItemItem }
                focusItemAt(
                    pos = lastItemPos,
                    textPos = (listItems[lastItemPos] as EditItemItem).content.text.length,
                    itemExists = false
                )
            }
        }
    }

    fun togglePin() {
        pinned = when (pinned) {
            PinnedStatus.PINNED -> PinnedStatus.UNPINNED
            PinnedStatus.UNPINNED -> PinnedStatus.PINNED
            PinnedStatus.CANT_PIN -> error("Can't pin")
        }
        _notePinned.value = pinned
    }

    fun toggleLock() {
        isLocked = !isLocked
        _noteLocked.value = isLocked
        saveNote()
    }

    fun changeReminder() {
        _showReminderDialogEvent.send(note.id)
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(note.id)
    }

    fun onReminderChange(reminder: Reminder?) {
        this.reminder = reminder
        _noteReminder.value = reminder

        // Update reminder chip
        updateNote()
        recreateListItems()
    }

    fun setNoteColor(color: Int) {
        if (this.color != color) {
            this.color = color
            _noteColor.value = color
            updateNote()
            saveNote()
        }
    }

    fun setMood(mood: Int) {
        if (this.mood != mood) {
            this.mood = mood
            _noteMood.value = mood
            updateNote()
            saveNote()
        }
    }

    fun convertToText(keepCheckedItems: Boolean) {
        note = note.asTextNote(keepCheckedItems)
        _noteType.value = NoteType.TEXT

        // Update list items (updateNote previously called in toggleNoteType)
        recreateListItems()
    }

    fun moveNoteAndExit() {
        changeNoteStatusAndExit(
            if (status == NoteStatus.ACTIVE) {
                NoteStatus.ARCHIVED
            } else {
                NoteStatus.ACTIVE
            }
        )
    }

    fun restoreNoteAndEdit() {
        note = note.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED)

        status = note.status
        pinned = note.pinned
        _noteStatus.value = status
        _notePinned.value = pinned

        // Recreate list items so that they are editable.
        recreateListItems()

        _messageEvent.send(EditMessage.RESTORED_NOTE)
    }

    fun copyNote(untitledName: String, copySuffix: String) {
        saveNote()

        viewModelScope.launch {
            val newTitle = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix)

            if (!note.isBlank) {
                // If note is blank, don't make a copy, just change the title.
                // Copied blank note should be discarded anyway.
                val date = Date()
                val copy = note.copy(
                    id = Note.NO_ID,
                    title = newTitle,
                    addedDate = date,
                    lastModifiedDate = date,
                    reminder = null
                )
                val id = notesRepository.insertNote(copy)
                note = copy.copy(id = id)

                // Set labels for copy
                if (labels.isNotEmpty()) {
                    labelsRepository.insertLabelRefs(createLabelRefs(id))
                }
            }

            // Update title item
            findItem<EditTitleItem>().title.replaceAll(newTitle)
            focusItemAt(pos = findItemPos<EditTitleItem>(), textPos = newTitle.length, itemExists = true)
        }
    }

    fun shareNote() {
        updateNote()
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    fun deleteNote() {
        if (isNoteInTrash) {
            // Delete forever, ask for confirmation.
            _showDeleteConfirmEvent.send()
        } else {
            // Send to trash
            changeNoteStatusAndExit(NoteStatus.DELETED)
        }
    }

    fun deleteNoteForeverAndExit() {
        viewModelScope.launch {
            deleteNoteInternal()
        }
        exit()
    }

    fun uncheckAllItems() {
        // REVERTED (device smoke test caught a worse regression from the in-place-mutate
        // attempt below — see doc/task/todo/FIX.md FIX-M17 postmortem):
        //
        //     for (item in listItems) {
        //         if (item is EditItemItem && item.checked) item.checked = false
        //     }
        //
        // EditDiffCallback.areItemsTheSame() is IDENTITY-only (old === new). Mutating in
        // place means the object AsyncListDiffer holds as "the current list" and the object
        // in the newly submitted list are the exact same instance — there is no before/after
        // snapshot for DiffUtil to diff, so it reports zero changes and RecyclerView never
        // rebinds. On device this looked like nothing happened at all: checkboxes stayed
        // visually checked. The underlying ViewModel state WAS correct (confirmed by leaving
        // the note and reopening it), only the RecyclerView never re-rendered. That's a worse
        // outcome than the original bug this was meant to fix — the original .copy() causes
        // DiffUtil to treat the row as a brand-new item (remove+insert instead of an in-place
        // update), which does flicker and drops row focus, but at least the checklist visibly
        // and reliably updates. Fixing this properly needs either a stable id-based
        // areItemsTheSame() (so DiffUtil can match "same logical row, different content") or
        // an explicit notifyItemChanged() call from the adapter side — out of scope for a
        // one-line P1 fix. Reverted to .copy(), the correctness-preserving option.
        // FIX-M17.
        for ((i, item) in listItems.withIndex()) {
            if (item is EditItemItem && item.checked) {
                listItems[i] = item.copy(checked = false)
            }
        }
        moveCheckedItemsToBottom()
    }

    fun deleteCheckedItems() {
        listItems.removeAll { it is EditItemItem && it.checked }

        // Update actual pos of items by shifting down
        val itemsByActualPos = listItems.asSequence()
            .filterIsInstance<EditItemItem>()
            .sortedBy { it.actualPos }
        var lastActualPos = -1
        for (item in itemsByActualPos) {
            if (item.actualPos != lastActualPos + 1) {
                item.actualPos = lastActualPos + 1
            }
            lastActualPos = item.actualPos
        }

        moveCheckedItemsToBottom()
    }

    fun focusNoteContent() {
        if (note.type == NoteType.TEXT) {
            val contentItemPos = findItemPos<EditContentItem>()
            val contentItem = listItems[contentItemPos] as EditContentItem
            focusItemAt(pos = contentItemPos, textPos = contentItem.content.text.length, itemExists = true)
        }
    }

    fun openClickedLink() {
        _openLinkEvent.send(linkUrl ?: return)
        linkUrl = null
    }

    private fun changeNoteStatusAndExit(newStatus: NoteStatus) {
        updateNote()

        if (!note.isBlank) {
            // If note is blank, it will be discarded on exit anyway, so don't change it.
            val oldNote = note
            status = newStatus

            pinned = if (status == NoteStatus.ACTIVE) {
                PinnedStatus.UNPINNED
            } else {
                PinnedStatus.CANT_PIN
            }

            if (newStatus == NoteStatus.DELETED) {
                // Remove reminder for deleted note
                if (reminder != null) {
                    reminder = null
                    reminderAlarmManager.removeAlarm(note.id)
                }
            }

            saveNote()

            // Show status change message.
            val statusChange = StatusChange(listOf(element = oldNote), oldNote.status, newStatus)
            _statusChangeEvent.send(statusChange)
        }

        exit()
    }

    /**
     * Update [note] to reflect UI changes, like text changes.
     * Note is not updated in database and last modified date isn't changed.
     */
    private fun updateNote() {
        if (listItems.isEmpty()) {
            // updateNote seems to be called before list items are created due to
            // live data events being called in a non-deterministic order? Return to avoid a crash.
            return
        }

        // Create note
        val title = findItem<EditTitleItem>().title.text.toString()
        val content: String
        val metadata: NoteMetadata
        when (note.type) {
            NoteType.TEXT -> {
                content = findItem<EditContentItem>().content.text.toString()
                metadata = BlankNoteMetadata
            }

            NoteType.LIST -> {
                // Add items in the correct actual order
                val items = MutableList(listItems.count { it is EditItemItem }) { TEMP_ITEM }
                for (item in listItems) {
                    if (item is EditItemItem) {
                        items[item.actualPos] = item
                    }
                }
                content = items.joinToString("\n") { it.content.text }
                metadata = ListNoteMetadata(items.map { it.checked })
            }
        }
        note = note.copy(
            title = title, content = content,
            metadata = metadata, status = status, pinned = pinned, reminder = reminder,
            color = color, mood = mood, isLocked = isLocked
        )

        // Feature 1+3: Update word/char count and char-limit warning
        updateLiveStats(title, content)
    }

    /**
     * Feature 1+3: Compute word/char count and emit char-limit warnings.
     * Reads text directly from [listItems] so it can be called live on each
     * keystroke without mutating [note] or touching the database.
     */
    fun updateLiveStats() {
        if (listItems.isEmpty()) return
        val title = runCatching { findItem<EditTitleItem>().title.text.toString() }.getOrDefault("")
        val content = when (note.type) {
            NoteType.TEXT -> runCatching { findItem<EditContentItem>().content.text.toString() }.getOrDefault("")
            NoteType.LIST -> listItems.filterIsInstance<EditItemItem>().joinToString("\n") { it.content.text }
        }
        updateLiveStats(title, content)
    }

    private fun updateLiveStats(title: String, content: String) {
        val fullText = "$title $content".trim()
        val chars = fullText.length
        val words = if (fullText.isEmpty()) 0 else fullText.split(Regex("\\s+")).count { it.isNotEmpty() }
        _wordCharCount.value = Pair(words, chars)

        val contentChars = content.length
        when {
            contentChars >= CHAR_LIMIT && lastCharWarnThreshold < CHAR_LIMIT -> {
                lastCharWarnThreshold = CHAR_LIMIT
                _charLimitWarningEvent.send(contentChars)
            }
            contentChars >= CHAR_LIMIT_WARN && lastCharWarnThreshold < CHAR_LIMIT_WARN -> {
                lastCharWarnThreshold = CHAR_LIMIT_WARN
                _charLimitWarningEvent.send(contentChars)
            }
            contentChars < CHAR_LIMIT_WARN -> lastCharWarnThreshold = 0
        }

        // F-04: Word milestone celebration
        if (isFirstStatsUpdate) {
            // Pre-populate milestones silently so opening an existing long note does not celebrate.
            WORD_MILESTONES.filter { words >= it }.forEach { reachedWordMilestones.add(it) }
            isFirstStatsUpdate = false
        } else {
            for (milestone in WORD_MILESTONES) {
                if (words >= milestone && !reachedWordMilestones.contains(milestone)) {
                    reachedWordMilestones.add(milestone)
                    _wordMilestoneEvent.send(milestone)
                } else if (words < milestone) {
                    reachedWordMilestones.remove(milestone)
                }
            }
        }

        scheduleHistorySnapshot(title, content)
    }

    private suspend fun deleteNoteInternal() {
        notesRepository.deleteNote(note)
        reminderAlarmManager.removeAlarm(note.id)
    }

    /**
     * Create label refs for a note ID from [labels].
     */
    private fun createLabelRefs(noteId: Long) = labels.map { LabelRef(noteId, it.id) }

    private fun scheduleHistorySnapshot(title: String, content: String) {
        if (note.id == Note.NO_ID) return

        historySnapshotJob?.cancel()
        historySnapshotJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10000)
            saveHistorySnapshot(title, content)
        }
    }

    private suspend fun saveHistorySnapshot(title: String, content: String) {
        val noteId = note.id
        if (noteId == Note.NO_ID) return

        updateNote()

        if (lastSnapshotTitle == null && lastSnapshotContent == null) {
            val history = notesRepository.getHistoryForNote(noteId)
            if (history.isNotEmpty()) {
                lastSnapshotTitle = history.first().title
                lastSnapshotContent = history.first().content
            }
        }

        val lastTitle = lastSnapshotTitle ?: ""
        val lastContent = lastSnapshotContent ?: ""

        val titleDiff = Math.abs(title.length - lastTitle.length)
        val contentDiff = Math.abs(content.length - lastContent.length)

        if ((title != lastTitle || content != lastContent) && (titleDiff + contentDiff > 10 || lastSnapshotTitle == null)) {
            val snapshot = NoteHistory(
                noteId = noteId,
                title = title,
                content = content,
                metadata = note.metadata,
                timestamp = System.currentTimeMillis()
            )
            try {
                notesRepository.insertNoteHistory(snapshot)
                notesRepository.pruneHistory(noteId, 30)
            } catch (e: Exception) {
                android.util.Log.w("roy93~", "Failed to insert note history (likely note was deleted or DB cleared): noteId=$noteId", e)
            }

            lastSnapshotTitle = title
            lastSnapshotContent = content
            _historyUpdated.value = true
        }
    }

    fun loadNoteHistory() {
        if (note.id == Note.NO_ID) return
        viewModelScope.launch {
            _noteHistory.value = notesRepository.getHistoryForNote(note.id)
        }
    }

    var isTimeTraveling: Boolean = false
        private set

    private var draftTitle: String = ""
    private var draftContent: String = ""
    private var draftMetadata: NoteMetadata = BlankNoteMetadata
    private var draftType: NoteType = NoteType.TEXT

    fun enterTimeTravelMode() {
        updateNote()
        draftTitle = note.title
        draftContent = note.content
        draftMetadata = note.metadata
        draftType = note.type
        isTimeTraveling = true
    }

    fun previewHistoryVersion(history: NoteHistory) {
        historySnapshotJob?.cancel()
        listItems.clear()

        if (shouldShowDate) {
            listItems += EditDateItem(note.addedDate.time)
        }

        listItems += EditTitleItem(DefaultEditableText(history.title), false)

        val historyType = if (history.metadata is ListNoteMetadata) NoteType.LIST else NoteType.TEXT
        val tempNote = note.copy(
            title = history.title,
            content = history.content,
            metadata = history.metadata,
            type = historyType
        )
        when (historyType) {
            NoteType.TEXT -> {
                listItems += EditContentItem(DefaultEditableText(tempNote.content), false)
            }
            NoteType.LIST -> {
                val noteItems = tempNote.listItems
                if (prefs.moveCheckedToBottom) {
                    for ((i, item) in noteItems.withIndex()) {
                        if (!item.checked) {
                            listItems += EditItemItem(DefaultEditableText(item.content), false, false, i)
                        }
                    }
                    val checkCount = noteItems.count { it.checked }
                    if (checkCount > 0) {
                        listItems += EditCheckedHeaderItem(checkCount)
                        for ((i, item) in noteItems.withIndex()) {
                            if (item.checked) {
                                listItems += EditItemItem(DefaultEditableText(item.content), true, false, i)
                            }
                        }
                    }
                } else {
                    for ((i, item) in noteItems.withIndex()) {
                        listItems += EditItemItem(DefaultEditableText(item.content), item.checked, false, i)
                    }
                }
            }
        }
        updateListItems()
    }

    fun exitTimeTravelMode(restore: Boolean, restoredHistory: NoteHistory? = null) {
        isTimeTraveling = false
        if (restore && restoredHistory != null) {
            note = note.copy(
                title = restoredHistory.title,
                content = restoredHistory.content,
                metadata = restoredHistory.metadata,
                type = restoredHistory.metadata.let { if (it is ListNoteMetadata) NoteType.LIST else NoteType.TEXT }
            )
            lastSnapshotTitle = restoredHistory.title
            lastSnapshotContent = restoredHistory.content
        } else {
            note = note.copy(
                title = draftTitle,
                content = draftContent,
                metadata = draftMetadata,
                type = draftType
            )
        }
        recreateListItems()
        updateListItems()
    }

    /**
     * Update list items to match content of [note].
     * It's important to make sure [updateNote] was called beforehand so that [note] matches UI content!
     */
    private fun recreateListItems() {
        listItems.clear()
        val canEdit = !isNoteInTrash

        // Date item
        if (shouldShowDate) {
            listItems += EditDateItem(
                when (prefs.shownDateField) {
                    ShownDateField.ADDED -> note.addedDate.time
                    ShownDateField.MODIFIED -> note.lastModifiedDate.time
                    else -> 0L  // never happens
                }
            )
        }

        // Title item
        listItems += EditTitleItem(DefaultEditableText(note.title), canEdit)

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                listItems += EditContentItem(DefaultEditableText(note.content), canEdit)
            }

            NoteType.LIST -> {
                val noteItems = note.listItems
                if (prefs.moveCheckedToBottom) {
                    // Unchecked list items
                    for ((i, item) in noteItems.withIndex()) {
                        if (!item.checked) {
                            listItems += EditItemItem(
                                content = DefaultEditableText(item.content),
                                checked = false,
                                editable = canEdit,
                                actualPos = i
                            )
                        }
                    }

                    // Item add item
                    if (canEdit) {
                        listItems += EditItemAddItem
                    }

                    // Checked list items
                    val checkCount = noteItems.count { it.checked }
                    if (checkCount > 0) {
                        listItems += EditCheckedHeaderItem(checkCount)
                        for ((i, item) in noteItems.withIndex()) {
                            if (item.checked) {
                                listItems += EditItemItem(DefaultEditableText(item.content), true, canEdit, i)
                            }
                        }
                    }
                } else {
                    // List items
                    for ((i, item) in noteItems.withIndex()) {
                        listItems += EditItemItem(DefaultEditableText(item.content), item.checked, canEdit, i)
                    }

                    // Item add item
                    if (canEdit) {
                        listItems += EditItemAddItem
                    }
                }
            }
        }

        val chips = mutableListOf<Any>()
        if (reminder != null) {
            chips += reminder!!
        }
        chips.addAll(labels)
        if (chips.isNotEmpty()) {
            listItems += EditChipsItem(chips)
        }

        updateListItems()
    }

    private fun updateListItems() {
        val items = if (isReadingMode.value == true) {
            listItems.filter { it != EditItemAddItem }.toMutableList()
        } else {
            listItems.toMutableList()
        }
        _editItems.value = items
    }

    override fun onNoteItemChanged(pos: Int, isPaste: Boolean) {
        val item = listItems[pos] as EditItemItem
        if ('\n' in item.content.text) {
            // User inserted line breaks in list items, split it into multiple items.
            // If this happens in the checked group when moving checked to the bottom, new items will be checked.
            val lines = item.content.text.split('\n')
            item.content.replaceAll(lines.first())
            for (listItem in listItems) {
                if (listItem is EditItemItem && listItem.actualPos > item.actualPos) {
                    listItem.actualPos += lines.size - 1
                }
            }
            for (i in 1 until lines.size) {
                listItems.add(
                    pos + i, EditItemItem(
                        DefaultEditableText(text = lines[i]),
                        // Preserve the original item's checked state regardless of the
                        // unrelated "move checked to bottom" setting — pasting into a
                        // checked item used to silently uncheck the split-off items
                        // whenever that setting was off. FIX-M18.
                        checked = item.checked, editable = true, item.actualPos + i
                    )
                )
            }

            moveCheckedItemsToBottom() // just to update checked count
            updateListItems()

            // If text was pasted, set focus at the end of last items pasted.
            // If a single linebreak was inserted, focus on the new item.
            focusItemAt(pos + lines.size - 1, if (isPaste) lines.last().length else 0, false)
        }
        // Feature 1+3: Update live word/char count for list-type notes
        updateLiveStats()
    }

    override fun onNoteItemCheckChanged(pos: Int, checked: Boolean) {
        val item = listItems[pos] as EditItemItem
        if (item.checked != checked) {
            item.checked = checked
            moveCheckedItemsToBottom()
        }
    }

    override fun onNoteItemBackspacePressed(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Previous item is also a note list item. Merge the two items content,
            // and delete the current item.
            val prevText = prevItem.content
            val prevLength = prevText.text.length
            prevText.append((listItems[pos] as EditItemItem).content.text)
            deleteListItemAt(pos)

            // Set focus on merge boundary.
            focusItemAt(pos - 1, prevLength, true)
        }
    }

    override fun onNoteItemDeleteClicked(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Set focus at the end of previous item.
            focusItemAt(pos - 1, prevItem.content.text.length, true)
        } else {
            val nextItem = listItems.getOrNull(pos + 1)
            if (nextItem is EditItemItem) {
                // Set focus at the end of next item.
                focusItemAt(pos = pos + 1, textPos = nextItem.content.text.length, itemExists = true)
            }
        }

        deleteListItemAt(pos)
    }

    override fun onNoteItemAddClicked(pos: Int) {
        // pos is the position of EditItemAdd item, which is also the position to insert the new item.
        // The new item is added last, so the actual pos is the maximum plus one.
        val actualPos = listItems.maxOf { (it as? EditItemItem)?.actualPos ?: -1 } + 1
        listItems.add(
            index = pos,
            element = EditItemItem(DefaultEditableText(), checked = false, editable = true, actualPos)
        )
        updateListItems()
        focusItemAt(pos, 0, false)
    }

    override fun onNoteLabelClicked() {
        changeLabels()
    }

    override fun onNoteReminderClicked() {
        changeReminder()
    }

    override fun onNoteClickedToEdit() {
        if (isNoteInTrash) {
            // Cannot edit note in trash! Show message suggesting user to restore the note.
            // This is not just for show. Editing note would change its last modified date
            // which would mess up the auto-delete interval in trash.
            _messageEvent.send(EditMessage.CANT_EDIT_IN_TRASH)
        }
    }

    override fun onLinkClickedInNote(linkText: String, linkUrl: String) {
        this.linkUrl = linkUrl
        _showLinkDialogEvent.send(linkText)
    }

    override val isNoteDragEnabled: Boolean
        get() = !isNoteInTrash && !(isReadingMode.value ?: false) && listItems.count { it is EditItemItem } > 1

    override fun onNoteItemSwapped(from: Int, to: Int) {
        // Swap items actual positions in list note
        val fromItem = listItems[from] as EditItemItem
        val toItem = listItems[to] as EditItemItem
        val actualPosTemp = fromItem.actualPos
        fromItem.actualPos = toItem.actualPos
        toItem.actualPos = actualPosTemp

        // Don't update live data, adapter was notified of the change already.
        // However the live data value must be updated!
        Collections.swap(listItems, from, to)
        Collections.swap(_editItems.value!!, from, to)
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    override val moveCheckedToBottom: Boolean
        get() = prefs.moveCheckedToBottom

    private fun focusItemAt(pos: Int, textPos: Int, itemExists: Boolean) {
        _focusEvent.send(FocusChange(itemPos = pos, pos = textPos, itemExists = itemExists))
    }

    private fun deleteListItemAt(pos: Int) {
        val listItem = listItems[pos] as EditItemItem
        listItems.removeAt(pos)
        // Shift the actual pos of all items after this one
        for (item in listItems) {
            if (item is EditItemItem && item.actualPos > listItem.actualPos) {
                item.actualPos--
            }
        }
        // Update checked/unchecked sections in cast this was the only checked item
        moveCheckedItemsToBottom()
    }

    /**
     * If configured so, move checked items to a separate section at the bottom,
     * and update the checked header count. If no items are checked, the section is removed.
     * Always calls [updateListItems].
     */
    private fun moveCheckedItemsToBottom() {
        if (prefs.moveCheckedToBottom) {
            // Remove the whole checked group
            val checkedItems = listItems.asSequence().filterIsInstance<EditItemItem>()
                .filter { it.checked }.toMutableList()
            listItems.removeAll(checkedItems)
            listItems.removeAll { it is EditCheckedHeaderItem }
            listItems.remove(EditItemAddItem)

            // Sort unchecked items by actual pos
            var lastUncheckedPos = listItems.indexOfLast { it is EditItemItem }
            if (lastUncheckedPos != -1) {
                lastUncheckedPos++
                val firstUncheckedPos = listItems.indexOfFirst { it is EditItemItem }
                listItems.subList(fromIndex = firstUncheckedPos, toIndex = lastUncheckedPos)
                    .sortBy { (it as EditItemItem).actualPos }
            } else {
                lastUncheckedPos = findItemPos<EditTitleItem>() + 1
            }

            // Re-add the checked group if any checked items, items sorted by actual pos
            var pos = lastUncheckedPos
            listItems.add(pos, EditItemAddItem)
            pos++
            if (checkedItems.isNotEmpty()) {
                listItems.add(index = pos, element = EditCheckedHeaderItem(checkedItems.size))
                pos++
                checkedItems.sortBy { it.actualPos }
                for (item in checkedItems) {
                    listItems.add(index = pos, element = item)
                    pos++
                }
            }
        }
        updateListItems()
    }

    private inline fun <reified T : EditListItem> findItem(): T {
        return (listItems.find { it is T } ?: error("List item not found")) as T
    }

    private inline fun <reified T : EditListItem> findItemPos(): Int {
        return listItems.indexOfFirst { it is T }
    }

    fun toggleReadingMode() {
        _isReadingMode.value = !(_isReadingMode.value ?: false)
        updateListItems()
    }

    fun getNoteForExport(): Note {
        updateNote()
        return note
    }

    fun getLabelsForExport(): List<Label> {
        return labels
    }

    data class FocusChange(val itemPos: Int, val pos: Int, val itemExists: Boolean)

    /**
     * The default class used for editable item text, backed by StringBuilder.
     * When items are bound by the adapter, this is changed to AndroidEditableText instead.
     * The default implementation is only used temporarily (before item is bound) and for testing.
     */
    class DefaultEditableText(text: CharSequence = "") : EditableText {
        override val text = StringBuilder(text)

        override fun append(text: CharSequence) {
            this.text.append(text)
        }

        override fun replaceAll(text: CharSequence) {
            this.text.replace(0, this.text.length, text.toString())
        }

        override fun equals(other: Any?) = (other is DefaultEditableText &&
                other.text.toString() == text.toString())

        override fun hashCode() = text.hashCode()

        override fun toString() = text.toString()
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<EditVM> {
        override fun create(savedStateHandle: SavedStateHandle): EditVM
    }

    companion object {
        // Feature 3: Char limit constants
        const val CHAR_LIMIT = 100_000
        const val CHAR_LIMIT_WARN = 90_000
        val WORD_MILESTONES = listOf(100, 500, 1_000, 5_000)

        private val BLANK_NOTE = Note(
            id = Note.NO_ID,
            type = NoteType.TEXT,
            title = "",
            content = "",
            metadata = BlankNoteMetadata,
            addedDate = Date(0),
            lastModifiedDate = Date(0),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )

        private const val KEY_NOTE_ID = "noteId"
        private const val KEY_IS_NEW_NOTE = "isNewNote"

        private val TEMP_ITEM = EditItemItem(
            content = DefaultEditableText(),
            checked = false,
            editable = false,
            actualPos = 0
        )
    }
}
