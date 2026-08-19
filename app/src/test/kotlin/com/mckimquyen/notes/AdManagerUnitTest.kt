package com.mckimquyen.notes

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.InternalAdApi
import com.roy.sdkadbmob.configureTestHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(InternalAdApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AdManagerUnitTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val encodedKey = BuildConfig.VIP_KEY_ENCODED
        val decodedKey = String(Base64.decode(encodedKey, Base64.NO_WRAP))

        // Initialize AdManager with test config matching the production keys
        val config = AdSdkConfig(
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
            // SDK 1.6.x defaults this off; production RApp.kt sets it too (same reason —
            // VipFrm relies on the legacy activateVipByKey path for both VIP flows).
            allowLegacyPlaintextVipKey = true,
            safety = com.roy.sdkadbmob.AdSafetyLimits.TEST
        )
        AdManager.setConfig(config)
        // SDK 1.6.x gates activateVipByKey behind a real network check (V-03) — Robolectric has no
        // network, so activation would fail regardless of allowLegacyPlaintextVipKey without this.
        AdManager.configureTestHooks(network = { true })
        AdManager.initialize(app) { _, _ -> }
    }

    @Test
    fun testVipDefaultStateIsInactive() {
        // By default, VIP should be inactive
        assertFalse(AdManager.isVipByKeyActive())
    }

    @Test
    fun testVipActivationAndBypass() {
        // Decode the production VIP key from Base64
        val encodedKey = BuildConfig.VIP_KEY_ENCODED
        val decodedKey = String(Base64.decode(encodedKey, Base64.NO_WRAP))

        // Activate VIP using the valid key for 30 days
        val success = AdManager.activateVipByKey(app, decodedKey, days = 30)
        assertTrue("VIP activation should succeed with valid decoded key", success)

        // Verify that VIP is now active
        assertTrue("VIP state should be active after successful activation", AdManager.isVipByKeyActive())

        // Verify expiry time is set
        val expiry = AdManager.getVipByKeyExpiry()
        assertTrue("Expiry time should be set", expiry > 0)

        // Clear VIP key
        AdManager.clearVipByKey()
        assertFalse("VIP state should be inactive after clearing VIP key", AdManager.isVipByKeyActive())
    }

    @Test
    fun testInvalidVipKeyFails() {
        // Attempt to activate VIP with an invalid key
        val success = AdManager.activateVipByKey(app, "INVALID_SECRET_KEY", days = 10)
        assertFalse("VIP activation should fail with an invalid key", success)
        assertFalse("VIP state should remain inactive", AdManager.isVipByKeyActive())
    }

    @Test
    fun testAdSafetyLimitsConfiguration() {
        // Construct AdSafetyLimits with custom values for interval and session caps
        // minTimeBetweenFullscreenAds = 5000L (AD-005)
        // maxFullscreenAdsPerSession = 3 (AD-006)
        // Named args — SDK 1.6.x inserted new params (ctrWindowMs, newSessionAfterBackgroundMs,
        // maxAppOpenAds*) ahead of where positional args used to end, which silently mis-bound
        // maxRapidResumesPerMinute's value onto ctrWindowMs after the SDK bump.
        val safety = com.roy.sdkadbmob.AdSafetyLimits(
            minTimeBetweenFullscreenAds = 5000L,
            maxFullscreenAdsPerSession = 3,
            minTimeAppOpenResume = 10000L,
            maxClicksPerMinute = 10,
            maxFullscreenAdsPerDay = 15,
            maxFullscreenAdsPerHour = 5,
            minSessionDurationBeforeAd = 2000L,
            suspiciousCtrThreshold = 0.05f,
            minImpressionsForCtrCheck = 100,
            maxRapidResumesPerMinute = 2,
        )

        assertEquals(5000L, safety.minTimeBetweenFullscreenAds)
        assertEquals(3, safety.maxFullscreenAdsPerSession)
        assertEquals(10000L, safety.minTimeAppOpenResume)
        assertEquals(10, safety.maxClicksPerMinute)
        assertEquals(15, safety.maxFullscreenAdsPerDay)
        assertEquals(5, safety.maxFullscreenAdsPerHour)
        assertEquals(2000L, safety.minSessionDurationBeforeAd)
        assertEquals(0.05f, safety.suspiciousCtrThreshold, 0.001f)
        assertEquals(100, safety.minImpressionsForCtrCheck)
        assertEquals(2, safety.maxRapidResumesPerMinute)
    }
}
