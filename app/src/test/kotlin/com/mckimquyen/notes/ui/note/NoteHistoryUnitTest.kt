package com.mckimquyen.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.BlankNoteMetadata
import com.mckimquyen.notes.model.entity.NoteHistory
import com.mckimquyen.notes.ui.edit.EditVM
import com.mckimquyen.notes.ui.edit.adt.EditTitleItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteHistoryUnitTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val notesRepository = mockk<NotesRepository>(relaxed = true)
    private val labelsRepository = mockk<LabelsRepository>(relaxed = true)
    private val prefsManager = mockk<PrefsManager>(relaxed = true)
    private val reminderAlarmManager = mockk<ReminderAlarmManager>(relaxed = true)
    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)

    @Test
    fun testTimeTravelModes() {
        val viewModel = EditVM(
            notesRepository,
            labelsRepository,
            prefsManager,
            reminderAlarmManager,
            savedStateHandle
        )

        // Mock the setup of listItems (normally populated during VM start)
        viewModel.enterTimeTravelMode()

        val history = NoteHistory(
            id = 1,
            noteId = 123L,
            title = "Historic Title",
            content = "Historic Content",
            metadata = BlankNoteMetadata,
            timestamp = 1000L
        )

        // Preview version
        viewModel.previewHistoryVersion(history)

        // Check that the items in editItems reflect the historic title and content and are not editable
        val titleItem = viewModel.editItems.value?.find { it is EditTitleItem } as? EditTitleItem
        assertEquals("Historic Title", titleItem?.title?.text.toString())
        assertEquals(false, titleItem?.editable)

        // Exit with restore = false -> should go back to the draft (which was empty blank note)
        viewModel.exitTimeTravelMode(restore = false)
        val revertedTitleItem = viewModel.editItems.value?.find { it is EditTitleItem } as? EditTitleItem
        assertEquals("", revertedTitleItem?.title?.text.toString())
        assertEquals(true, revertedTitleItem?.editable)

        // Exit with restore = true -> should keep the restored history values
        viewModel.exitTimeTravelMode(restore = true, restoredHistory = history)
        val restoredTitleItem = viewModel.editItems.value?.find { it is EditTitleItem } as? EditTitleItem
        assertEquals("Historic Title", restoredTitleItem?.title?.text.toString())
        assertEquals(true, restoredTitleItem?.editable)
    }
}
