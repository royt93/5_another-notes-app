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
import com.mckimquyen.notes.ui.splash.SplashActivity
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.ErrorReporter
import com.roy.sdkadbmob.PaidEventListener
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
        val vip30DaysKey = decodeVipKey(BuildConfig.VIP_KEY_ENCODED)
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
                // App-internal anti-tamper secret — deliberately NOT vip30DaysKey (that value is
                // handed to end users as the activation code; reusing it here would let anyone who
                // knows the public code also forge the HMAC signature on VIP prefs). See
                // VIP_SECRET_KEY_ENCODED comment in app/build.gradle for the audit finding this fixes.
                vipKeySecret           = decodeVipKey(BuildConfig.VIP_SECRET_KEY_ENCODED),
                // Deliberately left off (SDK 1.6.x default). Both real codes are covered by
                // vipRedeemCodes below, which is checked FIRST inside activateVipByKey() — the
                // legacy plaintext fallback this flag would unlock never actually gets reached for
                // any input we care about, so there's no reason to opt back into a path the SDK
                // docs say will be removed in a future major version. Verified live: both codes log
                // "redeem code +N ngày", never the legacy-path log line.
                allowLegacyPlaintextVipKey = false,
                // Two user-facing redeem codes (doc/ad/id.MD) — checked before the legacy plaintext
                // fallback above (which is off), so this is the only path either code activates
                // through. showActivateDialog() passes whatever the user typed straight to
                // activateVipByKey(), so either code activates for its own mapped duration. The
                // watch-ad reward flow does NOT go through this map — it calls
                // AdManager.grantVipDays() instead (see VipFrm.grantVip3Days), which is why reusing
                // the 30-day code's value here doesn't affect reward-ad day counts.
                vipRedeemCodes = mapOf(
                    vip30DaysKey to VIP_KEY_30DAYS_DAYS,
                    decodeVipKey(BuildConfig.VIP_KEY_3DAYS_ENCODED) to VIP_KEY_3DAYS_DAYS,
                ),
                appOpenExcludedActivities = listOf(SplashActivity::class.java),
                safety                 = if (BuildConfig.DEBUG) {
                    com.roy.sdkadbmob.AdSafetyLimits.TEST
                } else {
                    com.roy.sdkadbmob.AdSafetyLimits()
                }
            )
        )
        // Set right after setConfig(), still in Application.onCreate() — the SDK ties a paidEventListener's
        // lifetime to whichever Activity is foreground when it's set and auto-clears it on that Activity's
        // destroy. Setting it here (no Activity exists yet) keeps it alive for the whole app process instead
        // of silently losing revenue tracking after the first screen rotates/closes.
        AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, adSource ->
            Log.d("roy93~AdsRevenue", "$adType $valueMicros $currency $precision $adSource")
        }
        AdManager.errorReporter = ErrorReporter { throwable, context ->
            Log.e("roy93~AdsError", "context=$context", throwable)
        }
        AdManager.initialize(this, ::onAdManagerInitialized)
    }

    private fun onAdManagerInitialized(success: Boolean, gaid: String?) {
        when {
            success -> Log.d("roy93~", "AdManager init success, gaid=$gaid")
            AdManager.isWaitingForConsent() -> Log.d(
                "roy93~",
                "AdManager init: waiting for consent (not an error) — provider will " +
                    "auto-init once SplashActivity's requestConsentInfoUpdate() resolves."
            )
            else -> Log.w("roy93~", "AdManager init FAILED (not a consent wait) — see getDiagnostics()")
        }
        val isTesting = try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (e: Exception) {
            false
        }
        if (isTesting) {
            // grantVipDays(), not activateVipByKey(): the 30-day key is now also a vipRedeemCodes
            // entry (see setupAds() above), and activateVipByKey() checks that map BEFORE the
            // `days` argument is honored — passing 365 here would silently grant only 30.
            AdManager.grantVipDays(this, VIP_TEST_BYPASS_DAYS)
            Log.d("roy93~", "Bypassed ads for UI tests by activating VIP")
        }
        // Registered QA device test-device IDs so clicking ads during manual testing counts as test
        // traffic, not invalid traffic (see AD_PROMPT_AOS.MD Bước 3b — protects the AdMob account
        // from an invalid-traffic ban). Deliberately NOT gated behind BuildConfig.DEBUG: debug builds
        // use Google's demo ad unit IDs (see app/build.gradle debug buildType), which never count as
        // invalid traffic regardless of test-device registration — the real risk window is RELEASE
        // builds, which use the live production ad unit IDs.
        //
        // IMPORTANT — this is NOT the GAID. AdMob's RequestConfiguration.setTestDeviceIds() expects
        // the opaque hex device-fingerprint string that the Google Ads SDK itself prints to logcat
        // (tag "Ads": "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList(\"<HEX>\"))
        // to get test ads on this device."), NOT the raw GAID (UUID). Passing a GAID here is silently
        // ignored by AdMob — the device is never recognized as a test device, so every ad it sees is
        // live production inventory. Confirmed live 2026-08-20 (3-way independent audit) after this
        // exact mistake shipped: GAID was passed here for weeks with zero effect. GAID IS the right
        // value for AppLovin's setTestDeviceAdvertisingIds (SDK does that automatically when
        // isDebug=true) and for this SDK's own vipDeviceGaids/addVIPMember whitelist — just not here.
        //
        // ALSO IMPORTANT — the hash is NOT stable across build variants on the same physical device.
        // Confirmed live 2026-08-20: Samsung A50s produced a DIFFERENT hash for the signed release
        // build (35F8696D52502118270BCE2A66946380) than for the debug build
        // (813DCF48B3E486F15A60676D49A2AB09) — same device, same install, different APK signature.
        // Collect and register a separate hash per build variant you QA on a given device; don't
        // assume the debug-collected value carries over to release testing.
        AdManager.setTestDeviceIds(
            SAMSUNG_A50S_TEST_DEVICE_HASH,
            SAMSUNG_A50S_RELEASE_TEST_DEVICE_HASH,
            OPPO_CPH1989_TEST_DEVICE_HASH,
        )
    }

    // Light obfuscation — Base64 hides the plain key from a casual `strings` dump on the APK.
    // Reversible by anyone who decompiles, which is acceptable for an offline activation key.
    private fun decodeVipKey(encoded: String): String =
        String(Base64.decode(encoded, Base64.NO_WRAP))

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "reminders"

        private const val VIP_KEY_30DAYS_DAYS = 30
        private const val VIP_KEY_3DAYS_DAYS = 3
        private const val VIP_TEST_BYPASS_DAYS = 365

        // QA devices — see setTestDeviceIds() call in setupAds(). These are AdMob's opaque
        // hex test-device hash (from logcat tag "Ads"), NOT the GAID — see the comment at the
        // call site for why that distinction matters.
        //
        // Samsung SM-A507FN (A50s), collected live from logcat while connected via USB (ENH audit
        // round, 2026-08-20) — confirmed stable across multiple app launches of the DEBUG build on
        // this device. The signed RELEASE build produces a different hash (see below) — the hash is
        // keyed to the APK signature, not just the physical device.
        private const val SAMSUNG_A50S_TEST_DEVICE_HASH = "813DCF48B3E486F15A60676D49A2AB09"
        // Same physical device (Samsung A50s), but collected from the signed PRODUCTION RELEASE
        // build (assembleProductionRelease), 2026-08-20 — confirmed different from the debug hash
        // above. Register both if you QA both build types on this device.
        private const val SAMSUNG_A50S_RELEASE_TEST_DEVICE_HASH = "35F8696D52502118270BCE2A66946380"
        // OPPO CPH1989 (Reno2 series), collected live from logcat while connected via USB
        // (ENH audit round, 2026-08-20).
        private const val OPPO_CPH1989_TEST_DEVICE_HASH = "E165942547A491D06E43E24870B990B2"
        // Pixel 7 Pro entry removed 2026-08-20: the old value here was a GAID
        // (be39dfe0-67f5-4da4-afb3-8407cd481df4), which never worked for this API (see call-site
        // comment). Device isn't connected in this session to re-collect the real hash from logcat —
        // re-add it as a TEST_DEVICE_HASH (not GAID) next time that device is available for QA.
    }
}
