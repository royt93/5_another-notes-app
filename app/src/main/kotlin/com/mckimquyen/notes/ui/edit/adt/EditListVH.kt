package com.mckimquyen.notes.ui.edit.adt

import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.isInvisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.VItemEditContentBinding
import com.mckimquyen.notes.databinding.VItemEditDateBinding
import com.mckimquyen.notes.databinding.VItemEditHeaderBinding
import com.mckimquyen.notes.databinding.VItemEditItemAddBinding
import com.mckimquyen.notes.databinding.VItemEditItemBinding
import com.mckimquyen.notes.databinding.VItemEditLabelsBinding
import com.mckimquyen.notes.databinding.VItemEditTitleBinding
import com.mckimquyen.notes.ext.hideKeyboard
import com.mckimquyen.notes.ext.showKeyboard
import com.mckimquyen.notes.ext.strikethroughText
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.Reminder
import com.mckimquyen.notes.ui.edit.BulletTextWatcher
import com.mckimquyen.notes.utils.RelativeDateFormatter
import java.text.DateFormat

/**
 * Interface implemented by any item that can have its focus position changed.
 */
sealed interface EditFocusableViewHolder {
    fun setFocus(pos: Int)
}

class EditDateViewHolder(binding: VItemEditDateBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val dateEdt = binding.dateEdt

    private val dateFormatter = RelativeDateFormatter(dateEdt.resources) { date ->
        DateUtils.formatDateTime(
            dateEdt.context, date, DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        )
    }

    fun bind(item: EditDateItem) {
        dateEdt.text = dateFormatter.format(
            item.date, System.currentTimeMillis(),
            PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS
        )
    }
}

class EditTitleViewHolder(binding: VItemEditTitleBinding, callback: EditAdt.Callback) :
    RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    private val titleEdt = binding.titleEdt
    private var item: EditTitleItem? = null

    private val titleTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(editable: Editable?) {
            if (editable != item?.title?.text) {
                item?.title = AndroidEditableText(editable ?: return)
            }
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    init {
        titleEdt.setOnClickListener {
            callback.onNoteClickedToEdit()
        }
        titleEdt.addTextChangedListener(titleTextWatcher)
        titleEdt.setHorizontallyScrolling(false)
        titleEdt.maxLines = Integer.MAX_VALUE
    }

    fun bind(item: EditTitleItem) {
        this.item = item
        titleEdt.isFocusable = item.editable
        titleEdt.isFocusableInTouchMode = item.editable
        titleEdt.setText(item.title.text)
    }

    override fun setFocus(pos: Int) {
        titleEdt.requestFocus()
        titleEdt.setSelection(pos)
        titleEdt.showKeyboard()
    }

    fun onRecycled() {
        titleEdt.setOnClickListener(null)
        titleEdt.removeTextChangedListener(titleTextWatcher)
    }
}

class EditContentViewHolder(
    binding: VItemEditContentBinding,
    callback: EditAdt.Callback,
) :
    RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    private val contentEdt = binding.contentEdt
    private var item: EditContentItem? = null
    private val bulletTextWatcher = BulletTextWatcher()

    init {
        contentEdt.addTextChangedListener(bulletTextWatcher)
        contentEdt.doAfterTextChanged { editable ->
            if (editable != null && editable != item?.content?.text) {
                item?.content = AndroidEditableText(editable)
            }
        }

        contentEdt.setOnClickListener {
            callback.onNoteClickedToEdit()
        }
        contentEdt.onLinkClickListener = callback::onLinkClickedInNote
    }

    fun onRecycled() {
        contentEdt.removeTextChangedListener(bulletTextWatcher)
        contentEdt.setOnClickListener(null)
        contentEdt.onLinkClickListener = null
    }

    fun bind(item: EditContentItem) {
        this.item = item
        contentEdt.isFocusable = item.editable
        contentEdt.isFocusableInTouchMode = item.editable
        contentEdt.setText(item.content.text)
    }

    override fun setFocus(pos: Int) {
        contentEdt.requestFocus()
        contentEdt.setSelection(pos)
        contentEdt.showKeyboard()
    }
}

class EditItemViewHolder(binding: VItemEditItemBinding, callback: EditAdt.Callback) :
    RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    val dragImv = binding.dragImv
    private val itemCheck = binding.itemChk
    private val itemEdt = binding.contentEdt
    private val deleteImv = binding.deleteImv

    private var item: EditItemItem? = null

    val isChecked: Boolean
        get() = itemCheck.isChecked

    // Store listener references for cleanup
    private val checkChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        itemEdt.clearFocus()
        itemEdt.hideKeyboard()
        itemEdt.strikethroughText = isChecked && callback.strikethroughCheckedItems
        itemEdt.isActivated = !isChecked // Controls text color selector.
        dragImv.isInvisible = isChecked && callback.moveCheckedToBottom

        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            callback.onNoteItemCheckChanged(pos, isChecked)
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (itemEdt.text != item?.content?.text) {
                item?.content = AndroidEditableText(itemEdt.text!!)
            }

            // This is used to detect when user enters line breaks into the input, so the
            // item can be split into multiple items. When user enters a single line break,
            // selection is set at the beginning of new item. On paste, i.e. when more than one
            // character is entered, selection is set at the end of last new item.
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onNoteItemChanged(pos, count > 1)
            }
        }
    }

    private val focusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        // Only show delete icon for currently focused item.
        deleteImv.isInvisible = !hasFocus
    }

    private val keyListener = View.OnKeyListener { _, _, event ->
        val isCursorAtStart =
            itemEdt.selectionStart == 0 && itemEdt.selectionStart == itemEdt.selectionEnd
        if (isCursorAtStart && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
            // If user presses backspace at the start of an item, current item
            // will be merged with previous.
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onNoteItemBackspacePressed(pos)
            }
        }
        false
    }

    private val itemClickListener = View.OnClickListener {
        callback.onNoteClickedToEdit()
    }

    private val deleteClickListener = View.OnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            callback.onNoteItemDeleteClicked(pos)
        }
    }

    init {
        itemCheck.setOnCheckedChangeListener(checkChangeListener)
        itemEdt.addTextChangedListener(textWatcher)
        itemEdt.onFocusChangeListener = focusChangeListener
        itemEdt.setOnKeyListener(keyListener)
        itemEdt.setOnClickListener(itemClickListener)
        itemEdt.onLinkClickListener = callback::onLinkClickedInNote
        deleteImv.setOnClickListener(deleteClickListener)
    }

    fun bind(item: EditItemItem) {
        this.item = item

        itemEdt.isFocusable = item.editable
        itemEdt.isFocusableInTouchMode = item.editable
        itemEdt.setText(item.content.text)
        itemEdt.isActivated = !item.checked

        itemCheck.isChecked = item.checked
        itemCheck.isEnabled = item.editable
    }

    override fun setFocus(pos: Int) {
        itemEdt.requestFocus()
        itemEdt.setSelection(pos)
        itemEdt.showKeyboard()
    }

    fun clearFocus() {
        itemEdt.clearFocus()
    }

    fun onRecycled() {
        // Clean up all listeners to prevent memory leaks
        itemCheck.setOnCheckedChangeListener(null)
        itemEdt.removeTextChangedListener(textWatcher)
        itemEdt.onFocusChangeListener = null
        itemEdt.setOnKeyListener(null)
        itemEdt.setOnClickListener(null)
        itemEdt.onLinkClickListener = null
        deleteImv.setOnClickListener(null)
    }
}

class EditItemAddViewHolder(binding: VItemEditItemAddBinding, callback: EditAdt.Callback) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        itemView.setOnClickListener {
            callback.onNoteItemAddClicked(bindingAdapterPosition)
        }
    }

    fun onRecycled() {
        itemView.setOnClickListener(null)
    }
}

class EditHeaderViewHolder(binding: VItemEditHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val titleTxv = binding.titleTxv

    fun bind(item: EditCheckedHeaderItem) {
        titleTxv.text = titleTxv.context.resources.getQuantityString(
            R.plurals.edit_checked_items, item.count, item.count
        )
    }
}

class EditItemLabelsViewHolder(binding: VItemEditLabelsBinding, callback: EditAdt.Callback) :
    RecyclerView.ViewHolder(binding.root) {

    private val chipGroup = binding.chipGroup
    private val labelClickListener = View.OnClickListener {
        callback.onNoteLabelClicked()
    }
    private val reminderClickListener = View.OnClickListener {
        callback.onNoteReminderClicked()
    }

    private val reminderDateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }

    fun bind(item: EditChipsItem) {
        val layoutInflater = LayoutInflater.from(chipGroup.context)
        // Clear listeners before removing views to prevent memory leaks
        for (i in 0 until chipGroup.childCount) {
            chipGroup.getChildAt(i)?.setOnClickListener(null)
        }
        chipGroup.removeAllViews()
        for (chip in item.chips) {
            when (chip) {
                is Label -> {
                    val view = layoutInflater.inflate(
                        R.layout.v_edit_chip_label,
                        chipGroup,
                        false
                    ) as Chip
                    chipGroup.addView(view)
                    view.text = chip.name
                    view.setOnClickListener(labelClickListener)
                }

                is Reminder -> {
                    val view = layoutInflater.inflate(
                        R.layout.v_edit_chip_reminder,
                        chipGroup,
                        false
                    ) as Chip
                    chipGroup.addView(view)
                    view.text = reminderDateFormatter.format(
                        chip.next.time,
                        System.currentTimeMillis(), PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS
                    )
                    view.strikethroughText = chip.done
                    view.isActivated = !chip.done
                    view.setChipIconResource(if (chip.recurrence != null) R.drawable.ic_repeat else R.drawable.ic_alarm)
                    view.setOnClickListener(reminderClickListener)
                }

                else -> error("Unknown chip type")
            }
        }
    }

    fun onRecycled() {
        // Clear all child view listeners
        for (i in 0 until chipGroup.childCount) {
            chipGroup.getChildAt(i)?.setOnClickListener(null)
        }
        chipGroup.removeAllViews()
    }
}

// Wrapper around Editable to allow transparent access to text content from ViewModel.
// Editable items have a EditableText field which is set by a text watcher added to the
// EditText and called when text is set when item is bound.
// Note that the Editable instance can change during the EditText lifetime.
private class AndroidEditableText(override val text: Editable) : EditableText {

    override fun append(text: CharSequence) {
        this.text.append(text)
    }

    override fun replaceAll(text: CharSequence) {
        this.text.replace(0, this.text.length, text)
    }
}
