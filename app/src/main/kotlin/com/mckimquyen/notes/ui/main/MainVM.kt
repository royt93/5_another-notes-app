package com.mckimquyen.notes.ui.main

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.Keep
import androidx.core.view.contains
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import com.mckimquyen.notes.NavGraphMainDirections
import com.mckimquyen.notes.R
import com.mckimquyen.notes.model.JsonManager
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.ReminderAlarmManager
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.home.HomeFrmDirections
import com.mckimquyen.notes.ui.navigation.HomeDestination
import com.mckimquyen.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import kotlin.time.Duration.Companion.hours

class MainVM @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    @Assisted savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _editNoteEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
        get() = _editNoteEvent

    private val _autoExportEvent = MutableLiveData<Event<String>>()
    val autoExportEvent: LiveData<Event<String>>
        get() = _autoExportEvent

    private val _createNoteEvent = MutableLiveData<Event<NewNoteData>>()
    val createNoteEvent: LiveData<Event<NewNoteData>>
        get() = _createNoteEvent

    // This mutex is used to signal that the process of deleting old blank notes is finished and
    // new notes can safely be created. Otherwise newly created notes might be instantly deleted,
    // depending on the timing of the different coroutines.
    private val _deletionFinishedMutex = Mutex(locked = true)

    private val _navDirectionsEvent = MutableLiveData<Event<NavDirections>>()
    val navDirectionsEvent: LiveData<Event<NavDirections>>
        get() = _navDirectionsEvent

    private val _currentHomeDestination = savedStateHandle.getLiveData<HomeDestination>(
        KEY_HOME_DESTINATION, HomeDestination.Status(NoteStatus.ACTIVE)
    )
    val currentHomeDestination: LiveData<HomeDestination>
        get() = _currentHomeDestination

    private val _drawerCloseEvent = MutableLiveData<Event<Unit>>()
    val drawerCloseEvent: LiveData<Event<Unit>>
        get() = _drawerCloseEvent

    private val _clearLabelsEvent = MutableLiveData<Event<Unit>>()
    val clearLabelsEvent: LiveData<Event<Unit>>
        get() = _clearLabelsEvent

    private val _labelsAddEvent = MutableLiveData<Event<List<Label>?>>()
    val labelsAddEvent: LiveData<Event<List<Label>?>>
        get() = _labelsAddEvent

    private val _manageLabelsVisibility = MutableLiveData<Boolean>()
    val manageLabelsVisibility: LiveData<Boolean>
        get() = _manageLabelsVisibility

    private var labelsJob: Job? = null

    init {
        viewModelScope.launch {
            if (prefsManager.shouldAutoExport && prefsManager.autoExportUri == PrefsManager.AUTO_EXPORT_NO_URI) {
                // Auto export was enabled, but setup was not completed, disable it.
                prefsManager.disableAutoExport()
            }

            // Update all alarms for recurring reminders in case the previous alarm wasn't triggered.
            // This shouldn't technically happen, but there have been cases where recurring reminders failed.
            reminderAlarmManager.updateAllAlarms()

            // Check if last added note is blank, in which case delete it.
            val lastCreatedNote = notesRepository.getLastCreatedNote()
            if (lastCreatedNote?.isBlank == true) {
                notesRepository.deleteNote(lastCreatedNote)
            }
            _deletionFinishedMutex.unlock()

            // Periodically remove old notes in trash, and auto export if needed.
            while (true) {
                notesRepository.deleteOldNotesInTrash()

                if (prefsManager.shouldAutoExport &&
                    System.currentTimeMillis() - prefsManager.lastAutoExportTime >
                    PrefsManager.AUTO_EXPORT_DELAY.inWholeMilliseconds
                ) {
                    _autoExportEvent.send(prefsManager.autoExportUri)
                }

                delay(PERIODIC_TASK_INTERVAL.inWholeMilliseconds)
            }
        }
    }

    fun onStart() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
        }
    }

    fun startPopulatingDrawerWithLabels() {
        labelsJob?.cancel()
        // Coroutine to populate drawer with labels
        labelsJob = viewModelScope.launch {
            var oldLabelsList: List<Label> = listOf()
            labelsRepository.getAllLabelsByUsage().collect { labelsList ->
                if (oldLabelsList != labelsList) {
                    oldLabelsList = labelsList

                    // Check if the currently shown label still exists.
                    // If the label has been deleted, navigate to the main notes view
                    if (_currentHomeDestination.value is HomeDestination.Labels) {
                        if ((_currentHomeDestination.value as HomeDestination.Labels).label !in labelsList) {
                            _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ACTIVE)
                        }
                    }

                    // Update the labels in the navigation drawer
                    _clearLabelsEvent.send()
                    _labelsAddEvent.send(labelsList)
                    _manageLabelsVisibility.value = labelsList.isNotEmpty()
                }
            }
        }
    }

    fun selectLabel(label: Label) {
        _currentHomeDestination.value = HomeDestination.Labels(label)
    }

    fun navigationItemSelected(
        item: MenuItem,
        labelsMenu: Menu,
    ) {
        _drawerCloseEvent.send()

        when (item.itemId) {
            R.id.drawerItemNotes -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ACTIVE)
            }

            R.id.drawerItemReminders -> {
                _currentHomeDestination.value = HomeDestination.Reminders
            }

            R.id.drawerItemCreateLabel -> {
                _navDirectionsEvent.send(HomeFrmDirections.actionHomeToLabelEdit())
            }

            R.id.drawerItemEditLabels -> {
                _navDirectionsEvent.send(NavGraphMainDirections.actionLabel(longArrayOf()))
            }

            R.id.drawerItemArchived -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ARCHIVED)
            }

            R.id.drawerItemDeleted -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.DELETED)
            }

            R.id.drawerItemSettings -> {
                _navDirectionsEvent.send(HomeFrmDirections.actionHomeToSettings())
            }

            R.id.drawerItemWidgetGuide -> {
                _navDirectionsEvent.send(HomeFrmDirections.actionHomeToWidgetGuide())
            }

            R.id.drawerItemVip -> {
                _navDirectionsEvent.send(HomeFrmDirections.actionHomeToVip())
            }
        }

        // Navigate to label, if it has been selected
        if (labelsMenu.contains(item)) {
            viewModelScope.launch {
                val label = labelsRepository.getLabelByName(item.title as String)
                if (label != null) {
                    selectLabel(label)
                }
            }
        }
    }

    fun createNote(newNoteData: NewNoteData) {
        viewModelScope.launch {
            // Wait until older notes have been checked / deleted
            _deletionFinishedMutex.withLock {
                _createNoteEvent.send(newNoteData)
            }
        }
    }

    fun editNote(id: Long) {
        viewModelScope.launch {
            // If note doesn't exist, EditFrm would be opened to create a new note without this check.
            if (notesRepository.getNoteById(id) != null) {
                _editNoteEvent.send(id)
            }
        }
    }

    fun autoExport(output: OutputStream?) {
        if (output != null) {
            viewModelScope.launch(Dispatchers.IO) {
                prefsManager.autoExportFailed = try {
                    val jsonData = jsonManager.exportJsonData()
                    output.use {
                        output.write(jsonData.toByteArray())
                    }
                    prefsManager.lastAutoExportTime = System.currentTimeMillis()
                    false
                } catch (e: Exception) {
                    true
                }
            }
        } else {
            prefsManager.autoExportFailed = true
        }
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<MainVM> {
        override fun create(savedStateHandle: SavedStateHandle): MainVM
    }

    @Keep
    data class NewNoteData(
        val type: NoteType,
        val title: String = "",
        val content: String = "",
    )

    companion object {
        private const val KEY_HOME_DESTINATION = "destination"
        private val PERIODIC_TASK_INTERVAL = 1.hours
    }
}
