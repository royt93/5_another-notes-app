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

class NoteLockUnitTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val notesRepository = mockk<NotesRepository>(relaxed = true)
    private val labelsRepository = mockk<LabelsRepository>(relaxed = true)
    private val prefsManager = mockk<PrefsManager>(relaxed = true)
    private val reminderAlarmManager = mockk<ReminderAlarmManager>(relaxed = true)
    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)

    @Test
    fun testNoteLockToggling() {
        val viewModel = EditVM(
            notesRepository,
            labelsRepository,
            prefsManager,
            reminderAlarmManager,
            savedStateHandle
        )

        // 1. Lock state is initially not set (or false for default blank note)
        // Since start() is not called yet, it won't be initialized in LiveData, but let's check toggle
        viewModel.toggleLock()
        assertTrue(viewModel.noteLocked.value == true)

        // 2. Toggle lock again -> should be false
        viewModel.toggleLock()
        assertFalse(viewModel.noteLocked.value ?: true)
    }
}
