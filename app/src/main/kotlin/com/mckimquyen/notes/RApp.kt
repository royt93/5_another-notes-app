package com.mckimquyen.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.mckimquyen.notes.di.DaggerAppComponent
import com.mckimquyen.notes.model.NotesDb
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.AppTheme
import javax.inject.Inject

//done
//applovin
//review in app
//120hz
//font scale
//keystore
//switch ios, SwitchPreferenceCompat
//roy93~ change icon launcher
//double to exit app
//leakcanary
//permission ad_id
//proguard
//roy93~ rate app, share app, more app
//roy93~ policy

class RApp : Application() {

    val appComponent by lazy {
        DaggerAppComponent.factory().create(applicationContext)
    }

    @Inject
    lateinit var prefs: PrefsManager

    // for UI tests, should be injected in test ideally
    // but this works for a temporary solution.
    @Inject
    lateinit var database: NotesDb

    override fun onCreate() {
        super.onCreate()
//        this.setupApplovinAd()
        //TODO roy93~ admob
        appComponent.inject(this)

        // Initialize shared preferences
        prefs.migratePreferences()
        prefs.setDefaults(this)
        updateTheme(prefs.theme)

        createNotificationChannel()
    }

    fun updateTheme(theme: AppTheme) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun createNotificationChannel() {
        // https://developer.android.com/training/notify-user/build-notification#Priority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                /* id = */ NOTIFICATION_CHANNEL_ID,
                /* name = */ getString(R.string.reminder_notif_channel_title),
                /* importance = */ NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.reminder_notif_channel_descr)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "reminders"
    }
}
