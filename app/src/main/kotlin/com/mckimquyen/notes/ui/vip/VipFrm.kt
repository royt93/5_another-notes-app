package com.mckimquyen.notes.ui.vip

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import com.mckimquyen.notes.BuildConfig
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.DlgVipActivateBinding
import com.mckimquyen.notes.databinding.FVipBinding
import com.roy.sdkadbmob.AdError
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.RewardedAdListener
import com.roy.sdkadbmob.SafeLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VipFrm : Fragment() {

    private var _binding: FVipBinding? = null
    private val binding get() = _binding!!

    private val animators = mutableListOf<AnimatorSet>()

    // Cleared in onDestroyView alongside `animators` — see FIX-L03.
    private var confettiClearRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward = */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward = */ false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FVipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyEdgeToEdgeInsets()

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.btnActivate.setOnClickListener {
            playButtonPress(it)
            showActivateDialog()
        }
        setupWatchAdButton()
        binding.btnReset.setOnClickListener {
            playButtonPress(it)
            showResetConfirm()
        }

        // Preload ads once when entering screen, only when needed (non-VIP).
        // Avoids spamming load() from renderState() which can fire many times per session.
        if (!AdManager.isVipByKeyActive()) {
            AdManager.loadRewarded(requireContext())
            AdManager.loadInterstitial(requireContext())
        }

        renderState()
        startDecorativeAnimations()
        playEntranceAnimation()
    }

    private var isWaitingForAdReward = false
    private var hasEarnedReward = false

    override fun onResume() {
        super.onResume()
        if (isWaitingForAdReward) {
            isWaitingForAdReward = false
            if (!hasEarnedReward) {
                SafeLogger.w(TAG, "onResume: Callback dropped AND no reward — user closed early.")
                Toast.makeText(requireContext(), R.string.vip_ad_no_reward, Toast.LENGTH_SHORT).show()
            }
        }
        renderState()
    }

    private fun setupWatchAdButton() {
        binding.btnWatchAd.setOnClickListener {
            playButtonPress(it)
            isWaitingForAdReward = true
            hasEarnedReward = false

            // Listener captures `this@VipFrm` — clear in onDestroyView and at end of flow to avoid leak
            // through the AdManager singleton. SDK auto-clear only fires when listener===Activity.
            AdManager.rewardedListener = object : RewardedAdListener {
                override fun onUserEarnedReward(type: String, amount: Int) {
                    SafeLogger.d(TAG, "onUserEarnedReward type=$type amount=$amount")
                    if (hasEarnedReward) return  // dedupe with showRewarded callback
                    hasEarnedReward = true
                    isWaitingForAdReward = false
                    if (_binding == null) return  // fragment view destroyed before reward landed
                    val ctx = context ?: return
                    grantVip3Days(ctx)
                }
            }

            AdManager.showRewarded(requireActivity()) { rewardedSuccess ->
                // Always swap to a non-capturing no-op listener after the flow ends,
                // so the singleton doesn't keep this fragment alive.
                AdManager.rewardedListener = NO_OP_REWARDED_LISTENER
                if (hasEarnedReward) return@showRewarded
                isWaitingForAdReward = false
                if (_binding == null) return@showRewarded  // fragment view destroyed before ad closed
                val ctx = context ?: return@showRewarded

                if (rewardedSuccess) {
                    hasEarnedReward = true
                    grantVip3Days(ctx)
                    return@showRewarded
                }
                // Rewarded ad wasn't ready or user dismissed early.
                // Important: do NOT fall back to interstitial — granting VIP after a
                // non-rewarded ad violates AdMob's "incentivized non-rewarded" policy.
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.vip_ad_not_ready_title)
                    .setMessage(R.string.vip_ad_not_ready_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        // Drop the rewarded listener even if showRewarded callback never fires (process killed
        // mid-ad, etc.) — the SDK's auto-clear only catches Activity-typed listeners.
        AdManager.rewardedListener = null
        animators.forEach { it.cancel() }
        animators.clear()
        // celebrateActivation()/launchConfetti() are one-shot animations fired on successful
        // activation, not tracked before — if the user backed out right after activating,
        // these kept running (and this delayed cleanup runnable kept a pending callback) for
        // up to ~1.7s after the view was gone. FIX-L03.
        confettiClearRunnable?.let { binding.confettiOverlay.removeCallbacks(it) }
        confettiClearRunnable = null
        super.onDestroyView()
        _binding = null
    }

    /** Add bottom inset for navigation bar so the last button doesn't disappear behind it. */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = sysBars.bottom)
            insets
        }
    }

    private fun renderState() {
        val isActive = AdManager.isVipByKeyActive()
        if (isActive) {
            val expiryMs = AdManager.getVipByKeyExpiry()
            val expiryFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(expiryMs))

            binding.statusPill.setBackgroundResource(R.drawable.bg_vip_pill_active)
            binding.statusDot.isVisible = true
            binding.statusText.text = getString(R.string.vip_status_active)
            binding.expiryText.isVisible = true
            binding.expiryText.text = getString(R.string.vip_expires_until, expiryFormatted)
            binding.btnReset.isVisible = true
            binding.btnActivate.isVisible = false
            binding.btnWatchAd.isVisible = false
        } else {
            binding.statusPill.setBackgroundResource(R.drawable.bg_vip_pill_inactive)
            binding.statusDot.isVisible = false
            binding.statusText.text = getString(R.string.vip_status_inactive)
            binding.expiryText.isVisible = false
            binding.btnReset.isVisible = false
            binding.btnActivate.isVisible = true
            binding.btnWatchAd.isVisible = true
            // (loadRewarded/loadInterstitial called once in onViewCreated, not here)
        }
    }

    private fun startDecorativeAnimations() {
        runLoopAnim(R.animator.anim_vip_pulse, binding.crownIcon)
        runLoopAnim(R.animator.anim_vip_glow, binding.glowRing)
        listOf(binding.sparkle1, binding.sparkle2, binding.sparkle3).forEachIndexed { index, sparkle ->
            runLoopAnim(R.animator.anim_vip_sparkle_drift, sparkle, startDelayMs = index * 400L)
        }
    }

    private fun runLoopAnim(animRes: Int, target: View, startDelayMs: Long = 0L) {
        AnimatorInflater.loadAnimator(requireContext(), animRes).apply {
            setTarget(target)
            startDelay = startDelayMs
            (this as AnimatorSet).also { animators += it }
            start()
        }
    }

    /** Hero card slide + benefits rows staggered fade-in. */
    private fun playEntranceAnimation() {
        binding.heroContainer.alpha = 0f
        binding.heroContainer.translationY = 40f
        ObjectAnimator.ofFloat(binding.heroContainer, "alpha", 0f, 1f).apply {
            duration = 400
            start()
        }
        ObjectAnimator.ofFloat(binding.heroContainer, "translationY", 40f, 0f).apply {
            duration = 480
            interpolator = OvershootInterpolator(0.85f)
            start()
        }
        // Benefits rows enter with stagger.
        val rows = listOf(
            binding.benefitRow1, binding.benefitRow2,
            binding.benefitRow3, binding.benefitRow4,
        )
        rows.forEachIndexed { i, row ->
            row.alpha = 0f
            row.translationX = -30f
            row.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(380)
                .setStartDelay(180L + i * 80L)
                .start()
        }
        // Buttons soft fade-in.
        listOf(binding.btnActivate, binding.btnWatchAd, binding.btnReset).forEach {
            it.alpha = 0f
            it.animate().alpha(1f).setStartDelay(560).setDuration(280).start()
        }
    }

    private fun playButtonPress(v: View) {
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }.start()
    }

    private fun showActivateDialog() {
        val dialogBinding = DlgVipActivateBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.vip_dialog_btn_activate, null)
            .setNegativeButton(R.string.vip_dialog_btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val key = dialogBinding.keyInput.text?.toString().orEmpty().trim()
                val ok = AdManager.activateVipByKey(requireContext(), key, days = ACTIVATION_DAYS)
                if (ok) {
                    dialog.dismiss()
                    onActivationSuccess(days = ACTIVATION_DAYS)
                } else {
                    dialogBinding.keyInputLayout.error = getString(R.string.vip_msg_invalid)
                }
            }
            dialogBinding.keyInput.requestFocus()
        }
        dialog.show()
    }

    /**
     * Add 3 days of VIP. ACCUMULATE on top of any remaining time — `activateVipByKey` overwrites
     * the expiry, so we must compute `remainingDays + 3` before calling it. Otherwise a user
     * with 25 days left would shrink down to 3.
     */
    private fun grantVip3Days(context: Context) {
        val now = System.currentTimeMillis()
        val currentExpiry = AdManager.getVipByKeyExpiry().coerceAtLeast(now)
        val remainingDays = ((currentExpiry - now + ONE_DAY_MS - 1) / ONE_DAY_MS).toInt()
        val totalDays = remainingDays + REWARD_DAYS
        val validKey = decodedVipKey()
        SafeLogger.d(TAG, "grantVip3Days: remaining=$remainingDays + reward=$REWARD_DAYS = $totalDays")
        if (AdManager.activateVipByKey(context, validKey, days = totalDays)) {
            // Show "earned 3 days" — user-facing message reflects what was earned, not total.
            onActivationSuccess(days = REWARD_DAYS)
        } else {
            Toast.makeText(context, R.string.vip_msg_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodedVipKey(): String =
        String(Base64.decode(BuildConfig.VIP_KEY_ENCODED, Base64.NO_WRAP))

    private fun onActivationSuccess(days: Int) {
        animateStatusPillToActive()
        renderState()
        celebrateActivation()
        launchConfetti()
        Snackbar.make(
            binding.root,
            getString(R.string.vip_msg_success, days),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showResetConfirm() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vip_reset_title)
            .setMessage(R.string.vip_reset_message)
            .setNegativeButton(R.string.vip_dialog_btn_cancel, null)
            .setPositiveButton(R.string.vip_reset_confirm) { _, _ ->
                AdManager.clearVipByKey()
                renderState()
                Snackbar.make(binding.root, R.string.vip_reset_title, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    /** Hero scale celebration burst on successful activation. */
    private fun celebrateActivation() {
        val card = binding.heroContainer
        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.04f, 1f).apply { duration = 520 }
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.04f, 1f).apply { duration = 520 }
        val crownSpin = ObjectAnimator.ofFloat(binding.crownIcon, "rotation", 0f, 360f).apply {
            duration = 720
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, crownSpin)
            interpolator = OvershootInterpolator(2.5f)
            animators += this // cancelled in onDestroyView if still running. FIX-L03.
            start()
        }
    }

    /** Status pill morph: dot pops in + label crossfade — no jarring snap. */
    private fun animateStatusPillToActive() {
        binding.statusDot.scaleX = 0f
        binding.statusDot.scaleY = 0f
        binding.statusDot.isVisible = true
        binding.statusDot.animate()
            .scaleX(1f).scaleY(1f)
            .setStartDelay(120)
            .setDuration(360)
            .setInterpolator(OvershootInterpolator(3f))
            .start()
        // Pill crossfade animator (text fades to active color).
        val pill = binding.statusPill
        val flash = ObjectAnimator.ofFloat(pill, "alpha", 1f, 0.5f, 1f).apply {
            duration = 480
        }
        flash.start()
    }

    /** Spawn N confetti sparkles flying outward from the hero center. */
    private fun launchConfetti() {
        val overlay: FrameLayout = binding.confettiOverlay
        overlay.removeAllViews()
        val centerX = binding.heroContainer.x + binding.heroContainer.width / 2f
        val centerY = binding.heroContainer.y + binding.heroContainer.height / 2f - 40f

        repeat(CONFETTI_COUNT) { i ->
            val piece = ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_vip_sparkle)
                val size = (16 + Random.nextInt(12))
                layoutParams = FrameLayout.LayoutParams(dp(size), dp(size))
                x = centerX - dp(size) / 2f
                y = centerY - dp(size) / 2f
            }
            overlay.addView(piece)

            val angle = (Random.nextDouble() * 2 * Math.PI).toFloat()
            val distance = dp(120 + Random.nextInt(80)).toFloat()
            val targetX = piece.x + cos(angle) * distance
            val targetY = piece.y + sin(angle) * distance + dp(40)  // bias downward (gravity)

            val moveX = ObjectAnimator.ofFloat(piece, "translationX", 0f, targetX - piece.x)
            val moveY = ObjectAnimator.ofFloat(piece, "translationY", 0f, targetY - piece.y)
            val rotate = ObjectAnimator.ofFloat(piece, "rotation", 0f, (Random.nextFloat() * 720f - 360f))
            val fade = ObjectAnimator.ofFloat(piece, "alpha", 1f, 0f).apply {
                startDelay = 600
            }
            val scale = ValueAnimator.ofFloat(0.4f, 1.2f, 1f).apply {
                duration = 400
                addUpdateListener {
                    val s = it.animatedValue as Float
                    piece.scaleX = s
                    piece.scaleY = s
                }
            }
            AnimatorSet().apply {
                playTogether(moveX, moveY, rotate, fade, scale)
                duration = 1200
                startDelay = i * 25L
                animators += this // cancelled in onDestroyView if still running. FIX-L03.
                start()
            }
        }
        val clearRunnable = Runnable { overlay.removeAllViews() }
        confettiClearRunnable = clearRunnable
        overlay.postDelayed(clearRunnable, 1700)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "roy93~VipFrm"
        private const val ACTIVATION_DAYS = 30
        private const val REWARD_DAYS = 3
        private const val CONFETTI_COUNT = 22
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

        // Top-level non-capturing listener — replaces the per-click anonymous emptyListener that
        // captured the fragment instance and pinned it inside the AdManager singleton.
        private val NO_OP_REWARDED_LISTENER = object : RewardedAdListener {
            override fun onAdLoaded() {}
            override fun onAdFailedToLoad(error: AdError) {}
            override fun onAdShowed() {}
            override fun onAdDismissed() {}
            override fun onAdClicked() {}
            override fun onAdFailedToShow(error: AdError) {}
            override fun onAdNotAvailable() {}
            override fun onUserEarnedReward(type: String, amount: Int) {}
        }
    }
}
