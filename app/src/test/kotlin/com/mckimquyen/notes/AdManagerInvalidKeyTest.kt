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
 * Split out of [AdManagerUnitTest]: this test deliberately fails an `activateVipByKey` call, which
 * leaves `object AdManager`'s internal provider/consent state machine in a way that the other tests
 * in that class observed as a stuck `WAITING_FOR_CONSENT` when run in the same JVM afterward —
 * Robolectric does NOT reset that singleton between @Test methods within one class, only between
 * separate test classes/files. Keeping this alone in its own file sidesteps the contamination
 * instead of fighting the shared-singleton state from outside the SDK module.
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
                allowLegacyPlaintextVipKey = true,
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
