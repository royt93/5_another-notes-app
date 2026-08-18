package com.mckimquyen.notes.ui.setting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mckimquyen.notes.ui.AssistedSavedStateViewModelFactory
import com.mckimquyen.notes.ui.Event
import com.mckimquyen.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class ImportPasswordVM @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    companion object {
        private const val KEY_PASSWORD = "password"
    }

    private val _setDialogDataEvent = MutableLiveData<Event<String>>()
    val setDialogDataEvent: LiveData<Event<String>>
        get() = _setDialogDataEvent

    private var password = savedStateHandle[KEY_PASSWORD] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_PASSWORD] = value
        }

    fun onPasswordChanged(password: String) {
        this.password = password
    }

    /**
     * Wipe the plaintext password out of both the field and the SavedStateHandle bundle as
     * soon as the dialog goes away, instead of leaving it there for however long this
     * ViewModel happens to survive before onCleared(). FIX-M25.
     */
    fun clearPassword() {
        password = ""
    }

    fun start() {
        if (KEY_PASSWORD in savedStateHandle) {
            _setDialogDataEvent.send(this.password)
        }
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<ImportPasswordVM> {
        override fun create(savedStateHandle: SavedStateHandle): ImportPasswordVM
    }
}