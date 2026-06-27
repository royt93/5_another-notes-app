package com.mckimquyen.notes.ui.note.adt

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemAnimator that adds a spring (overshoot) bounce when an item moves upward in the list,
 * e.g. when a note is pinned and jumps to the top.
 */
class SpringItemAnimator : DefaultItemAnimator() {

    private val overshoot = OvershootInterpolator(1.6f)
    private val runningAnimations = mutableMapOf<RecyclerView.ViewHolder, View>()

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int, fromY: Int,
        toX: Int, toY: Int,
    ): Boolean {
        val view = holder.itemView
        val deltaX = toX - fromX - view.translationX.toInt()
        val deltaY = toY - fromY - view.translationY.toInt()

        if (deltaY >= 0) {
            // Moving down or staying — use default animator (no spring needed)
            return super.animateMove(holder, fromX, fromY, toX, toY)
        }

        // Cancel any pending animations on this view
        endAnimation(holder)

        // Moving up — apply spring overshoot
        view.translationX = -deltaX.toFloat()
        view.translationY = -deltaY.toFloat()
        runningAnimations[holder] = view
        
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(480)
            .setInterpolator(overshoot)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    dispatchMoveStarting(holder)
                }

                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    if (runningAnimations.remove(holder) != null) {
                        dispatchMoveFinished(holder)
                    }
                }
            })
            .start()
        return true
    }

    override fun endAnimation(holder: RecyclerView.ViewHolder) {
        val view = runningAnimations.remove(holder)
        if (view != null) {
            view.animate().cancel()
            view.translationX = 0f
            view.translationY = 0f
            dispatchMoveFinished(holder)
        }
        super.endAnimation(holder)
    }

    override fun isRunning(): Boolean {
        return runningAnimations.isNotEmpty() || super.isRunning()
    }

    override fun endAnimations() {
        val keys = runningAnimations.keys.toList()
        for (holder in keys) {
            endAnimation(holder)
        }
        super.endAnimations()
    }
}
