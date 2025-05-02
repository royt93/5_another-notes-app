package com.mckimquyen.notes.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.mckimquyen.notes.BuildConfig
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.sdkadbmob.AdMobManager
import com.mckimquyen.notes.ui.main.BaseAct
import com.mckimquyen.notes.ui.main.MainAct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class SplashAct : BaseAct() {

    @Inject
    lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)
        super.onCreate(savedInstanceState)
        (applicationContext as RApp).appComponent.inject(this)
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        setContentView(R.layout.a_splash)

        lifecycleScope.launch {
            var hasCalledGoToMain = false
            val job = launch {
                delay(3_000)
                if (!hasCalledGoToMain) {
                    hasCalledGoToMain = true
                    Log.d("roy93~", "goToMain #1")
                    goToMain()
                }
            }
            AdMobManager.loadAppOpenAd(
                context = this@SplashAct,
                adUnitId = BuildConfig.ADMOB_APP_OPEN_ID,
                onAdLoaded = {
                    if (!hasCalledGoToMain) {
                        hasCalledGoToMain = true
                        job.cancel()
                        Log.d("roy93~", "goToMain #2")
                        goToMain()
                        AdMobManager.showAppOpenAd(this@SplashAct)
                    }
                },
            )
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        finish()
    }
}
