package com.mckimquyen.notes.ui.search

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mckimquyen.notes.R
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteWithLabels
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.note.NoteItemFactory
import com.mckimquyen.notes.ui.note.NoteVM
import com.mckimquyen.notes.ui.note.PlaceholderData
import com.mckimquyen.notes.ui.note.adt.HeaderItem
import com.mckimquyen.notes.ui.note.adt.NoteAdt
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.maltaisn.notes.debugCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchVM @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    notesRepository: NotesRepository,
    labelsRepository: LabelsRepository,
    prefs: PrefsManager,
    reminderAlarmManager: ReminderAlarmManager,
    noteItemFactory: NoteItemFactory,
) : NoteVM(
    savedStateHandle = savedStateHandle,
    notesRepository = notesRepository,
    labelsRepository = labelsRepository,
    prefs = prefs,
    noteItemFactory = noteItemFactory,
    reminderAlarmManager = reminderAlarmManager
),
    NoteAdt.Callback {

    companion object {
        val ARCHIVED_HEADER_ITEM = HeaderItem(-1, R.string.note_location_archived)

        private const val SEARCH_DEBOUNCE_DELAY = 100L
    }

    // No need to save this is a saved state handle, SearchView will
    // call query changed listener after it's been recreated.
    private var lastQuery = ""

    init {
        viewModelScope.launch {
            restoreState()
        }
    }

    fun searchNotes(query: String) {
        lastQuery = query
        noteItemFactory.query = query

        // Cancel previous flow collection / debounce
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        val cleanedQuery = SearchQueryCleaner.clean(query)
        noteListJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_DELAY)
            try {
                notesRepository.searchNotes(cleanedQuery).collect { notes ->
                    createListItems(notes)
                }
            } catch (e: SQLiteException) {
                // SearchQueryCleaner may not be perfect, user might have entered
                // something that produces erronous FTS match syntax. Just ignore it.
                debugCheck(false) { "Search query cleaner failed for query '$cleanedQuery'" }
                createListItems(emptyList())
            }
        }
    }

    override val selectedNoteStatus: NoteStatus?
        // If a single note is active in selection, treat all as active.
        // Otherwise all notes are archived. Deleted notes are never shown in search.
        get() = when {
            selectedNotes.isEmpty() -> null
            selectedNotes.any { it.status == NoteStatus.ACTIVE } -> NoteStatus.ACTIVE
            else -> NoteStatus.ARCHIVED
        }

    private fun createListItems(notes: List<NoteWithLabels>) {
        listItems = buildList {
            var addedArchivedHeader = false
            for (noteWithLabels in notes) {
                val note = noteWithLabels.note

                // If this is the first archived note, add a header before it.
                if (!addedArchivedHeader && note.status == NoteStatus.ARCHIVED) {
                    this += ARCHIVED_HEADER_ITEM
                    addedArchivedHeader = true
                }

                val checked = isNoteSelected(note)
                this += noteItemFactory.createItem(note, noteWithLabels.labels, checked)
            }
        }
    }

    override fun updatePlaceholder() = PlaceholderData(
        R.drawable.ic_search, R.string.search_empty_placeholder
    )

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<SearchVM> {
        override fun create(savedStateHandle: SavedStateHandle): SearchVM
    }
}
