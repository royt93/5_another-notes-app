package com.mckimquyen.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.mckimquyen.notes.di.DaggerAppComponent
import com.mckimquyen.notes.model.NotesDb
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.AppTheme
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import javax.inject.Inject

//done
//applovin
//admob
//review in app
//120hz
//font scale
//keystore
//switch ios, SwitchPreferenceCompat
//change icon launcher
//double to exit app
//leakcanary
//permission ad_id
//proguard
//rate app, share app, more app
//policy

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
        setupAds()
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

    // SDK orchestrates: earlyInit → MobileAds.initialize OR AppLovinSdk.initialize → init (GAID/VIP) → registerAppOpenAdLifecycle.
    // App Open from background is auto-wired via ProcessLifecycle inside initialize().
    private fun setupAds() {
        AdManager.setConfig(
            AdSdkConfig(
                isEnableAdmob          = BuildConfig.IS_ENABLE_ADMOB,
                isDebug                = BuildConfig.DEBUG,
                admobAppOpenId         = BuildConfig.ADMOB_APP_OPEN_ID,
                admobInterstitialId    = BuildConfig.ADMOB_INTERSTITIAL_ID,
                admobBannerId          = BuildConfig.ADMOB_BANNER_ID,
                admobRewardedId        = BuildConfig.ADMOB_REWARDED_ID,
                applovinAppOpenId      = BuildConfig.APPLOVIN_APP_OPEN_ID,
                applovinInterstitialId = BuildConfig.APPLOVIN_INTERSTITIAL_ID,
                applovinBannerId       = BuildConfig.APPLOVIN_BANNER_ID,
                applovinRewardedId     = BuildConfig.APPLOVIN_REWARDED_ID,
                applovinSdkKey         = BuildConfig.APPLOVIN_SDK_KEY,
                vipKeySecret           = decodeVipKey(BuildConfig.VIP_KEY_ENCODED),
            )
        )
        AdManager.initialize(this) { success, gaid ->
            Log.d("roy93~", "AdManager init success=$success, gaid=$gaid")
        }
    }

    // Light obfuscation — Base64 hides the plain key from a casual `strings` dump on the APK.
    // Reversible by anyone who decompiles, which is acceptable for an offline activation key.
    private fun decodeVipKey(encoded: String): String =
        String(Base64.decode(encoded, Base64.NO_WRAP))

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "reminders"
    }
}
