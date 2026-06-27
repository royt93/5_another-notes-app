package com.mckimquyen.notes.ui.note.adt

import androidx.annotation.Keep
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.ui.note.Highlighted
import com.mckimquyen.notes.ui.note.adt.NoteAdt.ViewType

sealed interface NoteListItem {
    val id: Long
    val type: ViewType
}

sealed interface NoteItem : NoteListItem {
    val note: Note
    val labels: List<Label>
    var checked: Boolean
    val title: Highlighted
    val showMarkAsDone: Boolean

    fun withChecked(checked: Boolean): NoteItem
}

@Keep
data class NoteItemText(
    override val id: Long,
    override val note: Note,
    override val labels: List<Label>,
    override var checked: Boolean,
    override val title: Highlighted,
    val content: Highlighted,
    override val showMarkAsDone: Boolean,
) : NoteItem {

    override val type: ViewType
        get() = ViewType.TEXT_NOTE

    override fun withChecked(checked: Boolean) = copy(checked = checked)
}

@Keep
data class NoteItemList(
    override val id: Long,
    override val note: Note,
    override val labels: List<Label>,
    override var checked: Boolean,
    override val title: Highlighted,
    val items: List<Highlighted>,
    val itemsChecked: List<Boolean>,
    val overflowCount: Int,
    val onlyCheckedInOverflow: Boolean,
    override val showMarkAsDone: Boolean,
) : NoteItem {

    override val type: ViewType
        get() = ViewType.LIST_NOTE

    override fun withChecked(checked: Boolean) = copy(checked = checked)
}

@Keep
data class HeaderItem(
    override val id: Long,
    @StringRes val title: Int,
) : NoteListItem {

    override val type get() = ViewType.HEADER
}

@Keep
data class MessageItem(
    override val id: Long,
    @StringRes @PluralsRes val message: Int,
    val args: List<Any> = emptyList(),
) : NoteListItem {

    override val type get() = ViewType.MESSAGE
}

@Keep
data class TimelineDateHeaderItem(
    override val id: Long,
    val dateLabel: String,
) : NoteListItem {

    override val type get() = ViewType.TIMELINE_DATE_HEADER
}
