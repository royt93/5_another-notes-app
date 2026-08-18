package com.mckimquyen.notes.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.labels.adt.LabelAdt
import com.mckimquyen.notes.ui.labels.adt.LabelListItem
import com.mckimquyen.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LabelVM @AssistedInject constructor(
    private val labelsRepository: LabelsRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(), LabelAdt.Callback {

    private val _labelItems = MutableLiveData<List<LabelListItem>>()
    val labelItems: LiveData<List<LabelListItem>>
        get() = _labelItems

    // current number of selected labels (only when managing labels)
    private val _labelSelection = MutableLiveData<Int>()
    val labelSelection: LiveData<Int>
        get() = _labelSelection

    private val _placeholderShown = MutableLiveData<Boolean>()
    val placeholderShown: LiveData<Boolean>
        get() = _placeholderShown

    private val _showDeleteConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeleteConfirmEvent

    private val _showRenameDialogEvent = MutableLiveData<Event<Long>>()
    val showRenameDialogEvent: LiveData<Event<Long>>
        get() = _showRenameDialogEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    private var listItems = mutableListOf<LabelListItem>()
        set(value) {
            field = value
            _labelItems.value = value

            // Update selected notes.
            val selectedBefore = selectedLabels.size
            selectedLabels.clear()
            selectedLabelIds.clear()
            for (item in value) {
                if (item.checked) {
                    selectedLabels += item.label
                    selectedLabelIds += item.label.id
                }
            }

            // Update placeholder visibility
            _placeholderShown.value = value.isEmpty()

            if (managingLabels) {
                _labelSelection.value = selectedLabels.size
            }
            if (selectedLabels.size != selectedBefore) {
                saveLabelSelectionState()
            }
        }

    /**
     * IDs of notes to set labels on.
     * Empty if managing labels
     */
    private var noteIds = emptyList<Long>()

    private val managingLabels: Boolean
        get() = noteIds.isEmpty()

    private val selectedLabelIds = mutableSetOf<Long>()
    private val selectedLabels = mutableSetOf<Label>()

    // Label id -> name captured at the moment its rename dialog was opened via
    // renameSelection(), so the next label-list emission can tell a completed rename (name
    // actually changed) apart from an unrelated emission that happens to land while a rename
    // dialog was open and then cancelled — a plain boolean flag treated ANY next emission as
    // "rename just happened" and silently cleared the current selection, even when the user
    // backed out of the dialog without saving. FIX-M09. A map (not a single id/name pair)
    // because the rename dialog isn't modal to this ViewModel's coroutine: the DB write for
    // one rename and this Flow's next re-collect are both async, so a second renameSelection()
    // call for a different label can land before the first one's completion is observed —
    // reviewer-caught gap in the original FIX-M09 single-field version.
    private val pendingRenames = mutableMapOf<Long, String>()

    private var labelsListJob: Job? = null
    private var restoreStateJob: Job? = null

    init {
        restoreStateJob = viewModelScope.launch {
            // Restore state
            noteIds = savedStateHandle.get<List<Long>>(KEY_NOTE_IDS).orEmpty()
            selectedLabelIds += savedStateHandle.get<List<Long>>(KEY_SELECTED_IDS).orEmpty()
            selectedLabels += selectedLabelIds.mapNotNull { labelsRepository.getLabelById(it) }
            restoreStateJob = null
        }
    }

    /**
     * Initializes view model to set labels on notes by ID.
     * If ID list is empty, view model will be set up to manage labels instead.
     */
    fun start(ids: List<Long>) {
        labelsListJob?.cancel()
        labelsListJob = viewModelScope.launch {
            if (noteIds.isEmpty() && ids.isNotEmpty()) {
                // First view model start.
                // Initially, set selected notes to the subset of labels shared by all notes.
                noteIds = ids
                selectedLabelIds.clear()
                selectedLabelIds += labelsRepository.getLabelIdsForNote(noteIds.first())
                for (noteId in noteIds.listIterator(1)) {
                    selectedLabelIds.retainAll(labelsRepository.getLabelIdsForNote(noteId).toSet())
                }

                savedStateHandle[KEY_NOTE_IDS] = noteIds
                saveLabelSelectionState()
            }

            // Initialize label list
            labelsRepository.getAllLabelsByUsage().collect { labels ->
                // Since state restoration is suspending, ensure that state is properly restored
                // (e.g. selection) before setting list items, or selection may be lost.
                restoreStateJob?.join()

                if (pendingRenames.isNotEmpty()) {
                    val completed = pendingRenames.filter { (id, originalName) ->
                        labels.find { it.id == id }?.name?.let { it != originalName } ?: false
                    }.keys
                    if (completed.isNotEmpty()) {
                        // At least one pending rename's name actually changed since its
                        // dialog was opened — deselect now that it completed. Others still
                        // pending (dialog cancelled, or not yet completed) are left alone:
                        // the selection stays intact through unrelated list updates instead
                        // of being cleared out from under the user.
                        completed.forEach { pendingRenames.remove(it) }
                        selectedLabelIds.clear()
                        selectedLabels.clear()
                    }
                }
                listItems = labels.mapTo(mutableListOf()) { label ->
                    LabelListItem(id = label.id, label = label, checked = label.id in selectedLabelIds)
                }
            }
        }
    }

    fun setNotesLabels() {
        viewModelScope.launch {
            for (noteId in noteIds) {
                // Find difference between old labels and new labels
                val labelsToRemove = labelsRepository.getLabelIdsForNote(noteId).toMutableSet()
                val labelsToAdd = selectedLabelIds.toMutableSet()
                val unchangedLabels = labelsToAdd intersect labelsToRemove
                labelsToRemove.removeAll(unchangedLabels)
                labelsToAdd.removeAll(unchangedLabels)
                labelsRepository.deleteLabelRefs(labelsToRemove.map {
                    LabelRef(noteId, it)
                })
                labelsRepository.insertLabelRefs(labelsToAdd.map {
                    LabelRef(noteId, it)
                })
            }
            _exitEvent.send()
        }
    }

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun renameSelection() {
        val label = selectedLabels.singleOrNull() ?: return  // renaming multiple or no labels, abort
        pendingRenames[label.id] = label.name
        _showRenameDialogEvent.send(label.id)
    }

    fun deleteSelectionPre() {
        viewModelScope.launch {
            var used = false
            for (label in selectedLabels) {
                if (labelsRepository.countLabelRefs(label.id) > 0) {
                    used = true
                    break
                }
            }

            if (used) {
                _showDeleteConfirmEvent.send()
            } else {
                // None of the labels are used, delete without confirmation.
                deleteSelection()
            }
        }
    }

    fun deleteSelection() {
        // Delete labels (called after confirmation)
        viewModelScope.launch {
            labelsRepository.deleteLabels(selectedLabels.toList())
            clearSelection()
        }
    }

    /** Set the selected state of all notes to [selected]. */
    private fun setAllSelected(selected: Boolean) {
        val allSelected = selected && selectedLabels.size == listItems.size
        val allUnselected = !selected && selectedLabels.isEmpty()
        if (allSelected || allUnselected) {
            // No changes needed.
            return
        }

        changeListItems { items ->
            for ((i, item) in items.withIndex()) {
                if (item.checked != selected) {
                    items[i] = item.copy(checked = selected)
                }
            }
        }
    }

    fun selectNewLabel(label: Label) {
        if (!managingLabels) {
            // If selecting labels and a new label was just added select it automatically.
            // The user most likely added the label with the intention of using it.
            val itemPos = listItems.indexOfFirst { it.id == label.id }
            if (itemPos != -1) {
                // Note that when this is called the list may or may not have been updated with the new label.
                toggleItemChecked(itemPos)
            } else {
                // List wasn't updated yet, select it then.
                selectedLabelIds += label.id
            }
        }
    }

    override val shouldHighlightCheckedItems: Boolean
        // When managing labels, items are highlighted if checked.
        // When selecting labels for a note, only the left icon is changed.
        get() = managingLabels

    override fun onLabelItemClicked(item: LabelListItem, pos: Int) {
        if (!managingLabels || selectedLabels.isNotEmpty()) {
            toggleItemChecked(pos)
        } else {
            _showRenameDialogEvent.send(item.label.id)
        }
    }

    override fun onLabelItemLongClicked(item: LabelListItem, pos: Int) {
        if (managingLabels) {
            toggleItemChecked(pos)
        }
    }

    override fun onLabelItemIconClicked(item: LabelListItem, pos: Int) {
        toggleItemChecked(pos)
    }

    private fun toggleItemChecked(pos: Int) {
        // Set the item as checked and update the list.
        changeListItems { items ->
            items[pos] = items[pos].copy(checked = !items[pos].checked)
        }
    }

    private inline fun changeListItems(change: (MutableList<LabelListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    /** Save [selectedLabels] to [savedStateHandle]. */
    private fun saveLabelSelectionState() {
        savedStateHandle[KEY_SELECTED_IDS] = selectedLabelIds.toList()
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<LabelVM> {
        override fun create(savedStateHandle: SavedStateHandle): LabelVM
    }

    companion object {
        private const val KEY_NOTE_IDS = "note_ids"
        private const val KEY_SELECTED_IDS = "selected_ids"
    }
}
