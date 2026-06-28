package com.mckimquyen.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.ui.note.adt.NoteListLayoutMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoteVMTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val notesRepository = mockk<NotesRepository>(relaxed = true)
    private val labelsRepository = mockk<LabelsRepository>(relaxed = true)
    private val prefs = mockk<PrefsManager>(relaxed = true)
    private val noteItemFactory = mockk<NoteItemFactory>(relaxed = true)
    private val reminderAlarmManager = mockk<ReminderAlarmManager>(relaxed = true)
    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)

    private class TestNoteVM(
        savedStateHandle: SavedStateHandle,
        notesRepository: NotesRepository,
        labelsRepository: LabelsRepository,
        prefs: PrefsManager,
        noteItemFactory: NoteItemFactory,
        reminderAlarmManager: ReminderAlarmManager
    ) : NoteVM(savedStateHandle, notesRepository, labelsRepository, prefs, noteItemFactory, reminderAlarmManager) {
        override val selectedNoteStatus: NoteStatus? = null
        override fun updatePlaceholder() = PlaceholderData(0, 0)

        fun setListItemsTest(items: List<com.mckimquyen.notes.ui.note.adt.NoteListItem>) {
            listItems = items
        }
    }

    @Test
    fun testLayoutModeToggling() {
        every { prefs.listLayoutMode } returns NoteListLayoutMode.LIST

        val viewModel = TestNoteVM(
            savedStateHandle,
            notesRepository,
            labelsRepository,
            prefs,
            noteItemFactory,
            reminderAlarmManager
        )

        // 1. Initial layout mode is LIST
        assertEquals(NoteListLayoutMode.LIST, viewModel.listLayoutMode.value)

        // 2. Toggle once -> GRID
        viewModel.toggleListLayoutMode()
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.value)

        // 3. Toggle again -> TIMELINE
        viewModel.toggleListLayoutMode()
        assertEquals(NoteListLayoutMode.TIMELINE, viewModel.listLayoutMode.value)

        // 4. Toggle again -> LIST
        viewModel.toggleListLayoutMode()
        assertEquals(NoteListLayoutMode.LIST, viewModel.listLayoutMode.value)
    }
}
