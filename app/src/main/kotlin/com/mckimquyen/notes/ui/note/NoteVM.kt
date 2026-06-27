package com.mckimquyen.notes.ui.note

import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.ShareData
import com.mckimquyen.notes.ui.StatusChange
import com.mckimquyen.notes.ui.note.adt.MessageItem
import com.mckimquyen.notes.ui.note.adt.NoteAdt
import com.mckimquyen.notes.ui.note.adt.NoteItem
import com.mckimquyen.notes.ui.note.adt.NoteListItem
import com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode
import com.mckimquyen.notes.ui.note.adt.TimelineDateHeaderItem
import com.mckimquyen.notes.ui.send
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This view model provides common behavior for home and search view models.
 */
abstract class NoteVM(
    protected val savedStateHandle: SavedStateHandle,
    protected val notesRepository: NotesRepository,
    protected val labelsRepository: LabelsRepository,
    protected val prefs: PrefsManager,
    protected val noteItemFactory: NoteItemFactory,
    protected val reminderAlarmManager: ReminderAlarmManager,
) : ViewModel(), NoteAdt.Callback {

    private val timelineDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /** Inject `TimelineDateHeaderItem` + convert NoteItems to TIMELINE_NOTE ViewType when in TIMELINE mode. */
    private fun buildDisplayedList(items: List<NoteListItem>): List<NoteListItem> {
        if (_listLayoutMode.value != NoteListLayoutMode.TIMELINE) return items
        val result = mutableListOf<NoteListItem>()
        var lastDateLabel: String? = null
        for (item in items) {
            if (item is NoteItem) {
                val dateLabel = timelineDateFormat.format(item.note.addedDate)
                if (dateLabel != lastDateLabel) {
                    val headerId = -(dateLabel.hashCode().toLong() and 0xFFFFFFFFL) - 1000L
                    result += TimelineDateHeaderItem(id = headerId, dateLabel = dateLabel)
                    lastDateLabel = dateLabel
                }
                result += item
            } else {
                result += item
            }
        }
        return result
    }

    protected var listItems: List<NoteListItem> = emptyList()
        set(value) {
            field = value
            _noteItems.value = buildDisplayedList(value)

            _placeholderData.value = if (value.isEmpty()) {
                updatePlaceholder()
            } else {
                null
            }

            // Update selected notes.
            val selectedBefore = selectedNotes.size
            _selectedNotes.clear()
            selectedNoteIds.clear()
            for (item in value) {
                if (item is NoteItem && item.checked) {
                    _selectedNotes += item.note
                    selectedNoteIds += item.note.id
                }
            }

            updateNoteSelection()
            if (selectedNotes.size != selectedBefore) {
                saveNoteSelectionState()
            }
        }

    private val _selectedNotes = mutableSetOf<Note>()
    private val selectedNoteIds = mutableSetOf<Long>()
    protected val selectedNotes: Set<Note> get() = _selectedNotes

    /**
     * Implementation should return the "global" status of the selected notes.
     * This is used to determine what move event will do on the selected notes.
     */
    protected abstract val selectedNoteStatus: NoteStatus?

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems: LiveData<List<NoteListItem>>
        get() = _noteItems

    private val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode: LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _editItemEvent = MutableLiveData<Event<Pair<Long, Int>>>()
    val editItemEvent: LiveData<Event<Pair<Long, Int>>>
        get() = _editItemEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _currentSelection = MutableLiveData<NoteSelection>()
    val currentSelection: LiveData<NoteSelection>
        get() = _currentSelection

    private val _placeholderData = MutableLiveData<PlaceholderData?>(null)
    val placeholderData: LiveData<PlaceholderData?>
        get() = _placeholderData

    private val _showReminderDialogEvent = MutableLiveData<Event<List<Long>>>()
    val showReminderDialogEvent: LiveData<Event<List<Long>>>
        get() = _showReminderDialogEvent

    private val _showLabelsFragmentEvent = MutableLiveData<Event<List<Long>>>()
    val showLabelsFragmentEvent: LiveData<Event<List<Long>>>
        get() = _showLabelsFragmentEvent

    private val _showDeletedForeverConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeletedForeverConfirmEvent

    protected var noteListJob: Job? = null
    private var restoreStateJob: Job? = null

    init {
        // Initialize list layout to saved value.
        _listLayoutMode.value = prefs.listLayoutMode

        if (com.mckimquyen.notes.BuildConfig.ENABLE_DEBUG_FEATURES) {
            noteItemFactory.appendIdToTitle = true
        }
    }

    /**
     * Restore the state of this fragment from [savedStateHandle].
     * Must be called by child to ensure child is fully constructed before restoring state.
     * Notice that state restoration is suspending, so when initializing the child view model,
     * [waitForRestoredState] must be called to wait for state restoration to be complete.
     */
    protected open fun restoreState() {
        restoreStateJob = viewModelScope.launch {
            // Restore saved selected notes
            selectedNoteIds += savedStateHandle.get<List<Long>>(KEY_SELECTED_IDS)
                .orEmpty().toMutableSet()
            _selectedNotes += selectedNoteIds.mapNotNull { notesRepository.getNoteById(it) }
            updateNoteSelection()
            restoreStateJob = null
        }
    }

    protected suspend fun waitForRestoredState() {
        restoreStateJob?.join()
    }

    /**
     * Stop updating list. This is called when the fragment view is destroyed to
     * prevent useless updates when the fragment isn't visible but the view model still exists.
     */
    fun stopUpdatingList() {
        noteListJob?.cancel()
        noteListJob = null
    }

    /**
     * Called when note list is empty to update the placeholder data.
     */
    abstract fun updatePlaceholder(): PlaceholderData

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun togglePin() {
        // If one note in selection isn't pinned, pin all. If all are pinned, unpin all.
        viewModelScope.launch {
            val newPinned = if (selectedNotes.any { it.pinned == PinnedStatus.UNPINNED }) {
                PinnedStatus.PINNED
            } else {
                PinnedStatus.UNPINNED
            }
            val newNotes = selectedNotes.mapNotNull { note ->
                if (note.pinned != PinnedStatus.CANT_PIN && note.pinned != newPinned) {
                    note.copy(pinned = newPinned)
                } else {
                    null
                }
            }
            notesRepository.updateNotes(newNotes)
        }
    }

    fun createReminder() {
        _showReminderDialogEvent.send(selectedNoteIds.toList())
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(selectedNoteIds.toList())
    }

    protected open fun onListLayoutModeChanged() {
        // Rebuild the displayed list since TIMELINE mode injects date headers.
        _noteItems.value = buildDisplayedList(listItems)
    }

    fun toggleListLayoutMode() {
        val mode = when (_listLayoutMode.value!!) {
            NoteListLayoutMode.LIST -> NoteListLayoutMode.GRID
            NoteListLayoutMode.GRID -> NoteListLayoutMode.TIMELINE
            NoteListLayoutMode.TIMELINE -> NoteListLayoutMode.LIST
        }
        _listLayoutMode.value = mode
        prefs.listLayoutMode = mode

        onListLayoutModeChanged()
    }

    fun moveSelectedNotes() {
        changeSelectedNotesStatus(
            if (selectedNoteStatus == NoteStatus.ACTIVE) {
                NoteStatus.ARCHIVED
            } else {
                NoteStatus.ACTIVE
            }
        )
    }

    fun deleteSelectedNotesPre() {
        if (selectedNoteStatus == NoteStatus.DELETED) {
            // Ask user for confirmation before deleting selected notes forever.
            _showDeletedForeverConfirmEvent.send()
        } else {
            // Send to trash
            changeSelectedNotesStatus(NoteStatus.DELETED)
        }
    }

    fun deleteSelectedNotes() {
        // Delete forever
        viewModelScope.launch {
            notesRepository.deleteNotes(selectedNotes.toList())
            clearSelection()
        }
    }

    fun copySelectedNote(untitledName: String, copySuffix: String) {
        if (selectedNotes.size != 1) {
            return
        }

        viewModelScope.launch {
            val note = selectedNotes.first()
            val date = Date()
            val copy = note.copy(
                id = Note.NO_ID,
                title = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix),
                addedDate = date,
                lastModifiedDate = date,
                reminder = null
            )
            val id = notesRepository.insertNote(copy)

            // Set labels for copy
            val labelIds = labelsRepository.getLabelIdsForNote(note.id)
            if (labelIds.isNotEmpty()) {
                labelsRepository.insertLabelRefs(labelIds.map {
                    LabelRef(noteId = id, labelId = it)
                })
            }

            clearSelection()
        }
    }

    fun shareSelectedNote() {
        val note = selectedNotes.firstOrNull() ?: return
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    /** Set the selected state of all notes to [selected]. */
    private fun setAllSelected(selected: Boolean) {
        if (!selected && selectedNotes.isEmpty()) {
            // Already all unselected.
            // (No fast path for all selected since there are multiple view types.)
            return
        }

        changeListItems {
            for ((i, item) in it.withIndex()) {
                if (item is NoteItem && item.checked != selected) {
                    it[i] = item.withChecked(selected)
                }
            }
        }
    }

    protected fun isNoteSelected(note: Note): Boolean {
        return note.id in selectedNoteIds
    }

    /** Update current selection live data to reflect current selection. */
    protected open fun updateNoteSelection() {
        // If no pinnable (active) notes are selected, selection is unpinnable.
        // If at least one unpinned note is selected, selection is unpinned.
        // Otherwise selection is pinned.
        val pinned = when {
            selectedNotes.none { it.status == NoteStatus.ACTIVE } -> PinnedStatus.CANT_PIN
            selectedNotes.any { it.pinned == PinnedStatus.UNPINNED } -> PinnedStatus.UNPINNED
            else -> PinnedStatus.PINNED
        }

        // If any note has a reminder, consider whole selection has a reminder,
        // so the single note reminder can be deleted.
        val hasReminder = selectedNotes.any { it.reminder != null }

        _currentSelection.value =
            NoteSelection(
                count = selectedNotes.size,
                status = selectedNoteStatus,
                pinned = pinned,
                hasReminder = hasReminder
            )
    }

    /** Save [selectedNotes] to [savedStateHandle]. */
    private fun saveNoteSelectionState() {
        savedStateHandle[KEY_SELECTED_IDS] = selectedNoteIds.toList()
    }

    /** Change the status of [notes] to [newStatus]. */
    protected fun changeNotesStatus(notes: Set<Note>, newStatus: NoteStatus) {
        val oldNotes = notes
            .filter { it.status != newStatus }
            .ifEmpty { return }

        viewModelScope.launch {
            val date = Date()
            val newNotes = mutableListOf<Note>()
            for (note in oldNotes) {
                newNotes += note.copy(
                    status = newStatus,
                    pinned = if (newStatus == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN,
                    reminder = note.reminder.takeIf { newStatus != NoteStatus.DELETED },
                    lastModifiedDate = date
                )
                if (newStatus == NoteStatus.DELETED) {
                    if (note.reminder != null) {
                        // Remove reminder alarm for deleted note.
                        reminderAlarmManager.removeAlarm(note.id)
                    }
                }
            }

            // Update the status in database
            notesRepository.updateNotes(newNotes)

            // Show status change message.
            val statusChange = StatusChange(oldNotes, oldNotes.first().status, newStatus)
            _statusChangeEvent.send(statusChange)
        }
    }

    /** Change the status of selected notes to [newStatus], and clear selection. */
    private fun changeSelectedNotesStatus(newStatus: NoteStatus) {
        changeNotesStatus(selectedNotes, newStatus)
        clearSelection()
    }

    override fun onNoteItemClicked(item: NoteItem, pos: Int) {
        if (selectedNotes.isEmpty()) {
            // Edit item
            _editItemEvent.send(Pair(item.note.id, pos))
        } else {
            // Toggle item selection
            toggleItemChecked(item, pos)
        }
    }

    override fun onNoteItemLongClicked(item: NoteItem, pos: Int) {
        toggleItemChecked(item, pos)
    }

    private fun toggleItemChecked(item: NoteItem, pos: Int) {
        // Set the item as checked and update the list.
        // In TIMELINE mode, adapter positions include date header items, so use ID-based lookup.
        changeListItems {
            val rawPos = it.indexOfFirst { listItem -> listItem.id == item.id }
            if (rawPos >= 0) {
                it[rawPos] = item.withChecked(!item.checked)
            } else {
                // Fallback: use adapter position (only valid in non-TIMELINE mode)
                if (pos < it.size) it[pos] = item.withChecked(!item.checked)
            }
        }
    }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        // Do nothing.
    }

    override fun onNoteActionButtonClicked(item: NoteItem, pos: Int) {
        // Do nothing.
    }

    override fun getNoteSwipeAction(direction: NoteAdt.SwipeDirection) = SwipeAction.NONE

    override fun onNoteSwiped(pos: Int, direction: NoteAdt.SwipeDirection) {
        // Do nothing.
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    protected inline fun changeListItems(change: (MutableList<NoteListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    @Keep
    data class NoteSelection(
        val count: Int,
        val status: NoteStatus?,
        val pinned: PinnedStatus,
        val hasReminder: Boolean,
    )

    companion object {
        private const val KEY_SELECTED_IDS = "selected_ids"
    }
}
