package com.mckimquyen.notes.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LabelEditVM @AssistedInject constructor(
    private val labelsRepository: LabelsRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _setLabelEvent = MutableLiveData<Event<Label>>()
    val setLabelEvent: LiveData<Event<Label>>
        get() = _setLabelEvent

    private val _labelAddEvent = MutableLiveData<Event<Label>>()
    val labelAddEvent: LiveData<Event<Label>>
        get() = _labelAddEvent

    private val _labelError = MutableLiveData(Error.NONE)
    val nameError: LiveData<Error>
        get() = _labelError

    private var labelId = Label.NO_ID

    private var labelName = savedStateHandle[KEY_NAME] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_NAME] = value
        }

    private var hidden = savedStateHandle[KEY_HIDDEN] ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_HIDDEN] = value
        }

    fun start(labelId: Long) {
        this.labelId = labelId
        if (KEY_NAME in savedStateHandle) {
            _setLabelEvent.send(Label(labelId, labelName, hidden))
            updateError()
        } else {
            viewModelScope.launch {
                val label = labelsRepository.getLabelById(labelId)
                if (label != null) {
                    // Edit label, set name initially
                    labelName = label.name
                    hidden = label.hidden
                    _setLabelEvent.send(label)
                } else {
                    labelName = ""
                    hidden = false
                    _setLabelEvent.send(Label(Label.NO_ID, "", false))
                }
                updateError()
            }
        }
    }

    fun onNameChanged(name: String) {
        labelName = name.trim().replace("""\s+""".toRegex(), " ")
        updateError()
    }

    fun onHiddenChanged(hidden: Boolean) {
        this.hidden = hidden
    }

    fun addLabel() {
        var label = Label(labelId, labelName, hidden)
        viewModelScope.launch {
            if (labelId == Label.NO_ID) {
                val id = labelsRepository.insertLabel(label)
                label = label.copy(id = id)
            } else {
                // Must use update, using insert will remove the label references despite being update on conflict.
                labelsRepository.updateLabel(label)
            }
            _labelAddEvent.send(label)
        }
    }

    // Cancelled and replaced on every call — without this, a fast typist could fire several
    // overlapping getLabelByName() lookups whose completion order isn't guaranteed, letting a
    // stale (now-outdated) result win and leave a duplicate name marked as valid. FIX-M08.
    private var updateErrorJob: Job? = null

    private fun updateError() {
        updateErrorJob?.cancel()
        updateErrorJob = viewModelScope.launch {
            // Label name must not be empty and must not exist.
            // Ignore name clash if label is the one being edited.
            _labelError.value = if (labelName.isEmpty()) {
                Error.BLANK
            } else {
                val existingLabel = labelsRepository.getLabelByName(labelName)
                if (existingLabel != null && existingLabel.id != labelId) {
                    Error.DUPLICATE
                } else {
                    Error.NONE
                }
            }
        }
    }

    enum class Error {
        NONE,
        DUPLICATE,
        BLANK
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<LabelEditVM> {
        override fun create(savedStateHandle: SavedStateHandle): LabelEditVM
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_HIDDEN = "hidden"
    }
}
