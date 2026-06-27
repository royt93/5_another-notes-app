package com.mckimquyen.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.ui.edit.EditVM
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReadingModeUnitTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val notesRepository = mockk<NotesRepository>(relaxed = true)
    private val labelsRepository = mockk<LabelsRepository>(relaxed = true)
    private val prefsManager = mockk<PrefsManager>(relaxed = true)
    private val reminderAlarmManager = mockk<ReminderAlarmManager>(relaxed = true)
    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)

    @Test
    fun testReadingModeToggling() {
        val viewModel = EditVM(
            notesRepository,
            labelsRepository,
            prefsManager,
            reminderAlarmManager,
            savedStateHandle
        )

        // 1. Initially reading mode should be false
        assertFalse(viewModel.isReadingMode.value ?: false)

        // 2. Toggle reading mode -> should be true
        viewModel.toggleReadingMode()
        assertTrue(viewModel.isReadingMode.value ?: false)

        // 3. Toggle reading mode again -> should be false
        viewModel.toggleReadingMode()
        assertFalse(viewModel.isReadingMode.value ?: false)
    }

    @Test
    fun testDragDisabledInReadingMode() {
        val viewModel = EditVM(
            notesRepository,
            labelsRepository,
            prefsManager,
            reminderAlarmManager,
            savedStateHandle
        )

        // Force a state where we can reorder if not in reading mode
        // isNoteDragEnabled checks !isNoteInTrash && !isReadingMode && listItems.count { it is EditItemItem } > 1
        // We can check isNoteDragEnabled is false when isReadingMode is true.
        viewModel.toggleReadingMode()
        assertTrue(viewModel.isReadingMode.value ?: false)
        assertFalse(viewModel.isNoteDragEnabled)
    }
}
