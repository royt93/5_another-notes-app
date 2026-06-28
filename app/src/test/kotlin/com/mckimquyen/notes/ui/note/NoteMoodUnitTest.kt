package com.mckimquyen.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.ui.edit.EditVM
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoteMoodUnitTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val notesRepository = mockk<NotesRepository>(relaxed = true)
    private val labelsRepository = mockk<LabelsRepository>(relaxed = true)
    private val prefsManager = mockk<PrefsManager>(relaxed = true)
    private val reminderAlarmManager = mockk<ReminderAlarmManager>(relaxed = true)
    private val savedStateHandle = mockk<SavedStateHandle>(relaxed = true)

    @Test
    fun testNoteMoodSetting() {
        val viewModel = EditVM(
            notesRepository,
            labelsRepository,
            prefsManager,
            reminderAlarmManager,
            savedStateHandle
        )

        // 1. Initial mood setting
        viewModel.setMood(3) // 😔 sad
        assertEquals(3, viewModel.noteMood.value)

        // 2. Changing mood setting
        viewModel.setMood(5) // 🔥 excited
        assertEquals(5, viewModel.noteMood.value)
    }
}
