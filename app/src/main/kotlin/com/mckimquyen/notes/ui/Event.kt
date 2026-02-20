package com.mckimquyen.notes.ui

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * Taken from: [https://github.com/android/architecture-samples]
 * Changes were made to allow unhandled `null` values.
 */
class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set

    /**
     * Returns the content if not handled, otherwise throws an exception.
     */
    fun requireUnhandledContent(): T {
        check(!hasBeenHandled)
        hasBeenHandled = true
        return content
    }

    /**
     * Returns the content regardless of whether it has been handled.
     * Use this when you need to "peek" at the value without consuming the event,
     * so other observers can still receive it via [EventObserver].
     */
    fun peekContent(): T = content
}

/**
 * An [Observer] for [Event]s, simplifying the pattern of checking if the [Event]'s content has
 * already been handled.
 *
 * [onEventUnhandledContent] is *only* called if the [Event]'s contents has not been handled.
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        if (!value.hasBeenHandled) {
            onEventUnhandledContent(value.requireUnhandledContent())
        }
    }
}

fun <T> LiveData<Event<T>>.observeEvent(owner: LifecycleOwner, observer: (T) -> Unit) {
    this.observe(owner, EventObserver(observer))
}

fun <T> MutableLiveData<Event<T>>.send(value: T) {
    this.value = Event(value)
}

fun MutableLiveData<Event<Unit>>.send() {
    this.send(Unit)
}
