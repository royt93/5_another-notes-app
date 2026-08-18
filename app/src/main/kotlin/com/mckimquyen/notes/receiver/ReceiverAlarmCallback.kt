package com.mckimquyen.notes.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        // setExactAndAllowWhileIdle() needs SCHEDULE_EXACT_ALARM on API 31+ (granted via a
        // Settings screen, not a runtime dialog — see SettingsFrm's exact_alarm_permission
        // preference). Below API 31 the permission doesn't exist and exact alarms are always
        // allowed. Falls back to the inexact set() (can be delayed tens of minutes in Doze)
        // when the user hasn't granted it. FIX-H04.
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time,
                alarmIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                time,
                alarmIntent
            )
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
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
