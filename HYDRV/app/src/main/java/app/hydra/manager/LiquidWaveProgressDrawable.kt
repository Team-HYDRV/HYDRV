package app.hydra.manager

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class LiquidWaveProgressDrawable(
    private val trackColor: Int,
    private val fillColor: Int,
    waveColor: Int? = null,
    private val drawTrack: Boolean = true
) : Drawable() {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = waveColor ?: ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(fillColor, Color.WHITE, 0.36f),
            120
        )
    }

    private val clipPath = Path()
    private val fillPath = Path()
    private val wavePath = Path()
    private val boundsF = RectF()
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = (it.animatedValue as Float) * TWO_PI
            invalidateSelf()
        }
    }

    private var phase = 0f
    private var progressFraction = 0f
    private var started = false

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        boundsF.set(bounds)
        val corner = min(boundsF.width(), boundsF.height()) / 2f
        if (drawTrack) {
            canvas.drawRoundRect(boundsF, corner, corner, trackPaint)
        }

        val fillRight = boundsF.left + boundsF.width() * progressFraction.coerceIn(0f, 1f)
        if (fillRight <= boundsF.left) return

        clipPath.reset()
        clipPath.addRoundRect(boundsF, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val fillRect = RectF(boundsF.left, boundsF.top, fillRight, boundsF.bottom)
        fillPath.reset()
        val fillCorner = min(corner, fillRect.width() / 2f)
        if (progressFraction >= 0.999f) {
            fillPath.addRoundRect(fillRect, corner, corner, Path.Direction.CW)
        } else {
            fillPath.addRoundRect(
                fillRect,
                floatArrayOf(
                    fillCorner, fillCorner,
                    0f, 0f,
                    0f, 0f,
                    fillCorner, fillCorner
                ),
                Path.Direction.CW
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        val width = max(1f, fillRect.width())
        val height = max(1f, fillRect.height())
        val baseline = boundsF.top + height * 0.52f
        val amplitude = max(2f, min(height * 0.08f, 6f))
        val wavelength = max(24f, height * 1.8f)
        val step = max(6f, width / 28f)

        wavePath.reset()
        wavePath.moveTo(fillRect.left, fillRect.bottom)
        wavePath.lineTo(fillRect.left, baseline)

        var x = fillRect.left
        while (x <= fillRect.right) {
            val y = baseline + amplitude * sin(((x - fillRect.left) / wavelength) * TWO_PI + phase)
            wavePath.lineTo(x, y)
            x += step
        }

        wavePath.lineTo(fillRect.right, fillRect.bottom)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        trackPaint.alpha = alpha
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        trackPaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        wavePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onLevelChange(level: Int): Boolean {
        progressFraction = (level.coerceIn(0, 10000) / 10000f)
        if (!started && level > 0) {
            started = true
            animator.start()
        }
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

    fun dispose() {
        animator.cancel()
        started = false
    }

    companion object {
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
