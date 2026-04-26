package com.mckimquyen.notes.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.color.DynamicColors
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.main.BaseAct
import com.mckimquyen.notes.ui.main.MainAct
import com.roy.sdkadbmob.AdManager
import javax.inject.Inject

// Class name kept as "SplashActivity" so AdManager ProcessLifecycle.onStart can match
// `simpleName == "SplashActivity"` and skip showing App Open Resume here while
// initSplashScreen flow is running its own App Open.
class SplashActivity : BaseAct() {

    @Inject
    lateinit var prefs: PrefsManager

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable {
        if (!isDestroyed && !isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)
        super.onCreate(savedInstanceState)
        (applicationContext as RApp).appComponent.inject(this)
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        setContentView(R.layout.a_splash)

        // UMP Consent (Google Play 2024+ requirement for EEA / UK / CH).
        // Must run BEFORE loading any ad — initSplashScreen kicks off App Open load internally.
        AdManager.requestConsentInfoUpdate(this, tagForUnderAgeOfConsent = false) { canRequestAds ->
            if (canRequestAds) {
                AdManager.initSplashScreen(activity = this, onAdLoaded = { goToMain() })
            } else {
                // User declined consent → skip ad, navigate immediately.
                goToMain()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainAct::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Delay finish so the cross-fade animation finishes before the activity goes away.
        handler.postDelayed(finishRunnable, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(finishRunnable)
    }
}
