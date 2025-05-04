package com.mckimquyen.notes.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.mckimquyen.notes.BuildConfig
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.sdkadbmob.AdMobManager
import com.mckimquyen.notes.ui.main.BaseAct
import com.mckimquyen.notes.ui.main.MainAct
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

        AdMobManager.loadAppOpenAd(
            context = this@SplashAct,
            adUnitId = BuildConfig.ADMOB_APP_OPEN_ID,
            onAdLoaded = { result ->
                Log.d("roy93~", "onAdLoaded result $result")
                goToMain()
                AdMobManager.showAppOpenAd(this@SplashAct)
            },
        )
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
        finish()
    }
}
