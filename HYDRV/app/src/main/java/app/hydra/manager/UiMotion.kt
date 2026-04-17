package app.hydra.manager

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object UiMotion {

    fun attachPress(
        target: View,
        scaleDown: Float = 0.982f,
        pressedAlpha: Float = 0.96f,
        pressedTranslationYDp: Float = 1f,
        downDuration: Long = 80L,
        upDuration: Long = 170L,
        releaseOvershoot: Float = 0.55f
    ) {
        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate()
                        .scaleX(scaleDown)
                        .scaleY(scaleDown)
                        .alpha(pressedAlpha)
                        .translationY(dp(view, pressedTranslationYDp))
                        .setDuration(downDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().cancel()
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(upDuration)
                        .setInterpolator(OvershootInterpolator(releaseOvershoot))
                        .start()
                }
            }
            false
        }
    }

    fun pulse(
        target: View,
        scaleUp: Float = 1.08f,
        alphaDip: Float = 0.9f,
        riseDp: Float = 0f,
        expandDuration: Long = 110L,
        settleDuration: Long = 180L,
        settleOvershoot: Float = 0.55f
    ) {
        target.animate().cancel()
        target.animate()
            .scaleX(scaleUp)
            .scaleY(scaleUp)
            .alpha(alphaDip)
            .translationY(-dp(target, riseDp))
            .setDuration(expandDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(settleDuration)
                    .setInterpolator(OvershootInterpolator(settleOvershoot))
                    .start()
            }
            .start()
    }

    private fun dp(view: View, value: Float): Float {
        return value * view.resources.displayMetrics.density
    }
}
