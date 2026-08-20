package com.mckimquyen.notes

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.InternalAdApi
import com.roy.sdkadbmob.configureTestHooks
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Split out of [AdManagerUnitTest] for readability: this test deliberately fails an
 * `activateVipByKey` call, which leaves `object AdManager`'s internal provider/consent state
 * machine in a way other tests observed as a stuck `WAITING_FOR_CONSENT`/throttled backoff.
 *
 * Splitting into a separate file does NOT by itself isolate this from other tests — `object
 * AdManager` is a JVM singleton that Gradle's test worker keeps alive across every Robolectric test
 * class it runs in the same process, this file included. [AdManagerUnitTest.setup] has its own
 * defensive clock-advance + forced consent for exactly that reason. Verified via `./gradlew test
 * --rerun-tasks` (the full module, not just this file) after removing those defenses reintroduced
 * flaky failures in [AdManagerUnitTest] — don't remove them assuming this split is sufficient.
 */
@OptIn(InternalAdApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AdManagerInvalidKeyTest {

    @Before
    fun setup() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val decodedKey = String(Base64.decode(BuildConfig.VIP_KEY_ENCODED, Base64.NO_WRAP))
        AdManager.setConfig(
            AdSdkConfig(
                isEnableAdmob = false,
                isDebug = true,
                admobAppOpenId = "test_app_open",
                admobInterstitialId = "test_interstitial",
                admobBannerId = "test_banner",
                admobRewardedId = "test_rewarded",
                applovinAppOpenId = "test_lovin_open",
                applovinInterstitialId = "test_lovin_inter",
                applovinBannerId = "test_lovin_banner",
                applovinRewardedId = "test_lovin_rewarded",
                applovinSdkKey = "test_lovin_key",
                vipKeySecret = decodedKey,
                allowLegacyPlaintextVipKey = false, // mirrors RApp.setupAds()
                safety = com.roy.sdkadbmob.AdSafetyLimits.TEST,
            )
        )
        AdManager.configureTestHooks(network = { true })
        AdManager.initialize(app) { _, _ -> }
        AdManager.confirmGdprConsent(app, hasConsent = true)
    }

    @Test
    fun testInvalidVipKeyFails() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val success = AdManager.activateVipByKey(app, "INVALID_SECRET_KEY", days = 10)
        assertFalse("VIP activation should fail with an invalid key", success)
        assertFalse("VIP state should remain inactive", AdManager.isVipByKeyActive())
    }
}
