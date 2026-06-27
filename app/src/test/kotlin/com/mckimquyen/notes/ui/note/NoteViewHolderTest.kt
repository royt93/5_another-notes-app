package com.mckimquyen.notes.ui.note

import android.content.Context
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.VItemNoteTextBinding
import com.mckimquyen.notes.databinding.VItemNoteListBinding
import com.mckimquyen.notes.databinding.VItemNoteListItemBinding
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.entity.*
import com.mckimquyen.notes.ui.note.adt.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteViewHolderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.AppTheme_DayNight)
    }
    private val mockCallback = mockk<NoteAdt.Callback>(relaxed = true)
    private val mockPrefs = mockk<PrefsManager>(relaxed = true)

    @Test
    fun testTextNoteViewHolderLockedHidesContent() {
        val mockAdapter = mockk<NoteAdt>(relaxed = true) {
            every { context } returns this@NoteViewHolderTest.context
            every { prefsManager } returns mockPrefs
            every { callback } returns mockCallback
        }
        every { mockPrefs.getMaximumPreviewLines(NoteType.TEXT) } returns 5

        val inflater = LayoutInflater.from(context)
        val binding = VItemNoteTextBinding.inflate(inflater)
        val viewHolder = TextNoteViewHolder(binding)

        // 1. Locked Note
        val lockedNote = Note(
            id = 1,
            type = NoteType.TEXT,
            title = "Secret Title",
            content = "Sensitve Content",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null,
            isLocked = true // Locked
        )
        val itemTextLocked = NoteItemText(
            id = 1,
            note = lockedNote,
            labels = emptyList(),
            checked = false,
            title = Highlighted("Secret Title"),
            content = Highlighted("Sensitve Content"),
            showMarkAsDone = false
        )

        viewHolder.bind(mockAdapter, itemTextLocked)
        assertTrue(binding.lockImv.visibility == android.view.View.VISIBLE)
        assertFalse(binding.contentTxv.visibility == android.view.View.VISIBLE)

        // 2. Unlocked Note
        val unlockedNote = lockedNote.copy(isLocked = false)
        val itemTextUnlocked = itemTextLocked.copy(note = unlockedNote)

        viewHolder.bind(mockAdapter, itemTextUnlocked)
        assertFalse(binding.lockImv.visibility == android.view.View.VISIBLE)
        assertTrue(binding.contentTxv.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun testListNoteViewHolderLockedHidesItems() {
        val mockAdapter = mockk<NoteAdt>(relaxed = true) {
            every { context } returns this@NoteViewHolderTest.context
            every { prefsManager } returns mockPrefs
            every { callback } returns mockCallback
            every { obtainListNoteItemViewHolder() } answers {
                val itemBinding = VItemNoteListItemBinding.inflate(LayoutInflater.from(this@NoteViewHolderTest.context))
                ListNoteItemViewHolder(itemBinding)
            }
        }

        val inflater = LayoutInflater.from(context)
        val binding = VItemNoteListBinding.inflate(inflater)
        val viewHolder = ListNoteViewHolder(binding)

        // 1. Locked List Note
        val lockedNote = Note(
            id = 2,
            type = NoteType.LIST,
            title = "Secret List",
            content = "item1\nitem2",
            metadata = ListNoteMetadata(listOf(false, true)),
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null,
            isLocked = true // Locked
        )
        val itemListLocked = NoteItemList(
            id = 2,
            note = lockedNote,
            labels = emptyList(),
            checked = false,
            title = Highlighted("Secret List"),
            items = listOf(Highlighted("item1"), Highlighted("item2")),
            itemsChecked = listOf(false, true),
            overflowCount = 0,
            onlyCheckedInOverflow = false,
            showMarkAsDone = false
        )

        viewHolder.bind(mockAdapter, itemListLocked)
        assertTrue(binding.lockImv.visibility == android.view.View.VISIBLE)
        assertFalse(binding.itemsLayout.visibility == android.view.View.VISIBLE)

        // 2. Unlocked List Note
        val unlockedNote = lockedNote.copy(isLocked = false)
        val itemListUnlocked = itemListLocked.copy(note = unlockedNote)

        viewHolder.bind(mockAdapter, itemListUnlocked)
        assertFalse(binding.lockImv.visibility == android.view.View.VISIBLE)
        assertTrue(binding.itemsLayout.visibility == android.view.View.VISIBLE)
    }
}
