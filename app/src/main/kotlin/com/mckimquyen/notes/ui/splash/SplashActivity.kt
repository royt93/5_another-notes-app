package com.mckimquyen.notes.ui.splash

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.common.SelectorBottomSheet
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
    private var isTransitioningToMain = false

    private val safetyTimeoutRunnable = Runnable {
        Log.w("SplashActivity", "Safety timeout reached! Forcing transition to MainAct.")
        goToMain()
    }

    private val finishRunnable = Runnable {
        if (!isDestroyed && !isFinishing) {
            finish()
        }
    }

    private var onDoneRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)
        super.onCreate(savedInstanceState)
        (applicationContext as RApp).appComponent.inject(this)
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        setContentView(R.layout.a_splash)

        if (!prefs.hasPickedFirstRunLanguage) {
            showFirstRunLanguagePicker { startAdFlow() }
        } else {
            startAdFlow()
        }
    }

    /**
     * SelectorBottomSheet shown only on first launch — picks app language.
     * Not cancelable: user must make a choice before proceeding.
     */
    private fun showFirstRunLanguagePicker(onDone: () -> Unit) {
        val entries = resources.getStringArray(R.array.pref_language_entries)
        val values = resources.getStringArray(R.array.pref_language_values)

        val requestKey = "first_run_language"

        // Listen for the result before showing, so we don't miss it
        supportFragmentManager.setFragmentResultListener(requestKey, this) { _, bundle ->
            val tag = bundle.getString(SelectorBottomSheet.RESULT_KEY).orEmpty()
            val locales = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            prefs.hasPickedFirstRunLanguage = true

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            if (currentLocales == locales) {
                // No recreation will happen, safely call onDone. Named+stored (not an inline
                // lambda) so onDestroy() can remove it if the user exits right after picking a
                // language that happens to equal the current one. FIX-L06.
                onDoneRunnable = Runnable { onDone() }
                handler.post(onDoneRunnable!!)
            } else {
                // This triggers recreation. The new activity will see hasPickedFirstRunLanguage = true
                // and will automatically call startAdFlow() in onCreate(). 
                // Do NOT call onDone() here to prevent crashes and double ad loading.
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        SelectorBottomSheet.newInstance(
            requestKey = requestKey,
            title = getString(R.string.first_run_language_title),
            entries = entries,
            values = values,
            selectedValue = "",     // default = System default (index 0)
            cancelable = false,     // user must pick a language
        ).show(supportFragmentManager, "first_run_language_selector")
    }

    private fun startAdFlow() {
        // Start a 5-second safety timeout in case UMP Consent or AdManager gets stuck
        handler.postDelayed(safetyTimeoutRunnable, 5000)

        // UMP Consent (Google Play 2024+ requirement for EEA / UK / CH).
        // Must run BEFORE loading any ad — initSplashScreen kicks off App Open load internally.
        AdManager.requestConsentInfoUpdate(this, tagForUnderAgeOfConsent = false) { canRequestAds ->
            if (canRequestAds) {
                AdManager.initSplashScreen(activity = this, onAdLoaded = { goToMain() })
            } else {
                goToMain()
            }
        }
    }

    private fun goToMain() {
        if (isTransitioningToMain) return
        isTransitioningToMain = true
        handler.removeCallbacks(safetyTimeoutRunnable)

        startActivity(Intent(this, MainAct::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Delay finish so the cross-fade animation finishes before the activity goes away.
        handler.postDelayed(finishRunnable, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(safetyTimeoutRunnable)
        handler.removeCallbacks(finishRunnable)
        onDoneRunnable?.let { handler.removeCallbacks(it) }
    }
}
