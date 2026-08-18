package com.mckimquyen.notes.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.entity.NoteWithLabels
import com.mckimquyen.notes.receiver.AlarmReceiver
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class RecentNotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RecentNotesRemoteViewsFactory(applicationContext)
    }
}

class RecentNotesRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    @Inject
    lateinit var repository: NotesRepository

    private var recentNotes = emptyList<NoteWithLabels>()

    override fun onCreate() {
        (context.applicationContext as RApp).appComponent.inject(this)
    }

    override fun onDataSetChanged() {
        // This is called synchronously to fetch data.
        runBlocking {
            recentNotes = repository.getRecentNotes()
        }
    }

    override fun onDestroy() {
        recentNotes = emptyList()
    }

    override fun getCount(): Int = recentNotes.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= count) return RemoteViews(context.packageName, R.layout.widget_recent_notes_item)

        val noteItem = recentNotes[position]
        val note = noteItem.note

        val views = RemoteViews(context.packageName, R.layout.widget_recent_notes_item)

        // Set text
        // Hide the real title too, not just the content — a locked note's title is otherwise
        // shown in the clear on the home screen, a surface with a bigger exposure than in-app
        // (no device unlock or app auth needed to see it). FIX-M13.
        val isLocked = note.isLocked
        val titleText = if (isLocked) {
            "🔒 Locked Note"
        } else {
            note.title.ifEmpty { "Untitled Note" }
        }
        val contentText = if (isLocked) {
            "Locked"
        } else {
            note.content.ifEmpty { "..." }
        }
        views.setTextViewText(R.id.widgetItemTitle, titleText)
        views.setTextViewText(R.id.widgetItemContent, contentText)

        // Set color manually (overwriting ripple if colored)
        if (note.color != 0) {
            views.setInt(R.id.widgetItemContainer, "setBackgroundColor", note.color)
        } else {
            views.setInt(R.id.widgetItemContainer, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        }

        // Set fill-in intent to trigger the list item click
        val fillInIntent = Intent().apply {
            putExtra(AlarmReceiver.EXTRA_NOTE_ID, note.id)
        }
        views.setOnClickFillInIntent(R.id.widgetItemContainer, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = recentNotes.getOrNull(position)?.note?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
