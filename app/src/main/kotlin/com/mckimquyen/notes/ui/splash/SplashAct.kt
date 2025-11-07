package com.mckimquyen.notes.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.color.DynamicColors
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

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)
        super.onCreate(savedInstanceState)
        (applicationContext as RApp).appComponent.inject(this)
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        setContentView(R.layout.a_splash)

        AdMobManager.initSplashScreen(activity = this, onAdLoaded = {
            goToMain()
        })
    }

    private fun goToMain() {
        val intent = Intent(this, MainAct::class.java)
        startActivity(intent)
//        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Trì hoãn finish để đợi animation hoàn tất
        handler.postDelayed(finishRunnable, 300) // delay khoảng 300ms (hoặc đúng thời gian của animation)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(finishRunnable)
    }
}
