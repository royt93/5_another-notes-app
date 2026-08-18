package com.mckimquyen.notes.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mckimquyen.notes.model.ReminderAlarmCallback
import com.mckimquyen.notes.model.ReminderAlarmManager
import javax.inject.Inject

/**
 * Implementation of the alarm callback for [ReminderAlarmManager].
 * Uses the app context to set alarms broadcasted to [AlarmReceiver].
 */
class ReceiverAlarmCallback @Inject constructor(
    private val context: Context,
) : ReminderAlarmCallback {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun addAlarm(noteId: Long, time: Long) {
        val alarmIntent = getAlarmPendingIndent(noteId)
        // Deliberately NOT setExactAndAllowWhileIdle(): that needs SCHEDULE_EXACT_ALARM (API
        // 31+), a Play Console "sensitive permission" gated behind a core-functionality
        // declaration (alarm clock / calendar) this notes app doesn't qualify for — using it
        // risks rejection or removal from the Store. setAndAllowWhileIdle() needs no special
        // permission and still lets the alarm through Doze (rate-limited to roughly once per 9
        // minutes per app), instead of being deferred to the next maintenance window like
        // plain set(). FIX-H04 (reverted exact-alarm approach after review).
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time,
            alarmIntent
        )
    }

    override fun removeAlarm(noteId: Long) {
        getAlarmPendingIndent(noteId).cancel()
    }

    private fun getAlarmPendingIndent(noteId: Long): PendingIntent {
        // Make alarm intent
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra(AlarmReceiver.EXTRA_NOTE_ID, noteId)
        }
        var flags = 0
        flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ noteId.toInt(),
            /* intent = */ receiverIntent,
            /* flags = */ flags
        )
    }
}
