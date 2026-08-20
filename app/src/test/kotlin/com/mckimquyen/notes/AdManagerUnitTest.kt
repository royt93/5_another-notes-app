package com.mckimquyen.notes

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AdSdkConfig
import com.roy.sdkadbmob.InternalAdApi
import com.roy.sdkadbmob.clearAppPreferencesForTest
import com.roy.sdkadbmob.configureTestHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(InternalAdApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AdManagerUnitTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        // `AdManager`'s VIP-activation backoff (internal, not resettable from this module) throttles
        // for up to 5 min after just one failed attempt and lives on the `object AdManager` singleton.
        // Verified this survives across *separate test classes*, not just @Test methods in this one —
        // Gradle's test worker JVM runs many Robolectric test classes back to back without restarting,
        // so e.g. AdManagerInvalidKeyTest's deliberate failure can throttle activateVipByKey here too,
        // depending on class execution order within the worker. Confirmed by running the full `test`
        // task with `--rerun-tasks` (not just this class in isolation, which hides the interaction).
        // Fast-forward the fake clock past the cap so every test starts cooled down regardless.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(20))
        app = ApplicationProvider.getApplicationContext()
        // Redeem codes (vipRedeemCodes) are marked "already used" in SharedPreferences, which
        // Robolectric backs with a real file — that can survive across separate `./gradlew test`
        // invocations sharing a Gradle test-worker daemon, making a redeem-code test pass once and
        // then fail as "already used" on a later run. Clear before every test for a clean slate.
        AdManager.clearAppPreferencesForTest(app)
        val encodedKey = BuildConfig.VIP_KEY_ENCODED
        val decodedKey = String(Base64.decode(encodedKey, Base64.NO_WRAP))

        val decoded3DaysKey = String(Base64.decode(BuildConfig.VIP_KEY_3DAYS_ENCODED, Base64.NO_WRAP))

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
            // Off (SDK default) — mirrors RApp.setupAds(): vipRedeemCodes below covers both real
            // codes and is checked first, so the legacy path is never actually reached.
            allowLegacyPlaintextVipKey = false,
            vipRedeemCodes = mapOf(decodedKey to 30, decoded3DaysKey to 3),
            safety = com.roy.sdkadbmob.AdSafetyLimits.TEST
        )
        AdManager.setConfig(config)
        // SDK 1.6.x gates activateVipByKey behind a real network check (V-03) — Robolectric has no
        // network, so activation would fail regardless of allowLegacyPlaintextVipKey without this.
        AdManager.configureTestHooks(network = { true })
        AdManager.initialize(app) { _, _ -> }
        // The provider stays parked WAITING_FOR_CONSENT until consent resolves — reproducibly so once
        // any prior Robolectric test in the same worker JVM (this class or another) has already run an
        // initialize() cycle, for the same shared-singleton reason as the backoff above. Not what these
        // tests are about — force it resolved so every test starts from the same known-good state.
        AdManager.confirmGdprConsent(app, hasConsent = true)
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
    fun testRedeemCode3DaysActivatesThreeDaysNotThirty() {
        // The 3-day code is a distinct entry from the 30-day one in vipRedeemCodes — regression
        // test for the Round-7 wiring (RApp.setupAds()): before this, entering it into the manual
        // key dialog just failed (it never equaled the single legacy vipKeySecret).
        val decoded3DaysKey = String(Base64.decode(BuildConfig.VIP_KEY_3DAYS_ENCODED, Base64.NO_WRAP))
        val before = System.currentTimeMillis()

        val success = AdManager.activateVipByKey(app, decoded3DaysKey, days = 30)
        assertTrue("3-day redeem code should activate", success)

        val expiry = AdManager.getVipByKeyExpiry()
        val grantedDays = (expiry - before) / (24L * 60L * 60L * 1000L)
        // Redeem-code lookup ignores the `days` argument passed to activateVipByKey (30 above) —
        // it always grants exactly what vipRedeemCodes maps the code to (3), which is the whole
        // point of the regression test: passing 30 here must NOT result in 30 days.
        assertEquals("Should grant exactly 3 days, not the `days` argument passed in", 3L, grantedDays)

        AdManager.clearVipByKey()
    }

    @Test
    fun testGrantVipDaysUsedByWatchAdRewardDoesNotCollideWithRedeemCodes() {
        // VipFrm.grantVip3Days() calls AdManager.grantVipDays() (not activateVipByKey) specifically
        // to avoid colliding with the 30-day code now living in vipRedeemCodes — verify that path
        // grants exactly 3 days and accumulates on repeat calls, independent of vipKeySecret/vipRedeemCodes.
        val before = System.currentTimeMillis()

        assertTrue(AdManager.grantVipDays(app, 3))
        val firstExpiry = AdManager.getVipByKeyExpiry()
        val firstGrantedDays = (firstExpiry - before) / (24L * 60L * 60L * 1000L)
        assertEquals(3L, firstGrantedDays)

        assertTrue(AdManager.grantVipDays(app, 3))
        val secondExpiry = AdManager.getVipByKeyExpiry()
        val secondGrantedDays = (secondExpiry - before) / (24L * 60L * 60L * 1000L)
        assertEquals("Second reward should accumulate on top of the first", 6L, secondGrantedDays)

        AdManager.clearVipByKey()
    }

    // testInvalidVipKeyFails moved to AdManagerInvalidKeyTest.kt — see that file's KDoc for why.

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
