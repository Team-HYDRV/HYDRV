package app.hydra.manager

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PointF
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.Shader
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
        val width = max(1f, fillRect.width())
        val height = max(1f, fillRect.height())
        val baseline = boundsF.top + height * 0.52f
        val amplitude = max(2f, min(height * 0.11f, 7f))
        val wavelength = max(28f, width / 1.6f)
        buildLiquidBodyPath(
            path = fillPath,
            left = fillRect.left,
            top = fillRect.top,
            right = fillRect.right,
            bottom = fillRect.bottom,
            baseline = baseline,
            amplitude = amplitude,
            wavelength = wavelength
        )

        fillPaint.shader = LinearGradient(
            fillRect.left,
            fillRect.top,
            fillRect.left,
            fillRect.bottom,
            ColorUtils.blendARGB(fillColor, Color.WHITE, 0.18f),
            ColorUtils.blendARGB(fillColor, Color.BLACK, 0.12f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        val sheenBottom = min(fillRect.bottom, fillRect.top + height * 0.26f)
        if (sheenBottom > fillRect.top) {
            wavePaint.shader = LinearGradient(
                fillRect.left,
                fillRect.top,
                fillRect.left,
                sheenBottom,
                ColorUtils.setAlphaComponent(Color.WHITE, 64),
                ColorUtils.setAlphaComponent(Color.WHITE, 0),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipRect(fillRect.left, fillRect.top, fillRect.right, sheenBottom)
            canvas.drawPath(fillPath, wavePaint)
            canvas.restore()
        }

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

    private fun buildLiquidBodyPath(
        path: Path,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        baseline: Float,
        amplitude: Float,
        wavelength: Float
    ) {
        val width = max(1f, right - left)
        val samples = max(10, (width / max(10f, wavelength / 4f)).toInt())
        val step = width / samples
        val points = ArrayList<PointF>(samples + 1)
        var x = left
        while (x <= right + 0.5f) {
            val t = (x - left) / wavelength
            val wave = sin(t * TWO_PI + phase) + 0.22f * sin(t * TWO_PI * 1.92f - phase * 1.28f)
            val y = baseline + amplitude * wave
            points.add(PointF(x, y.coerceIn(top + amplitude * 0.25f, bottom - amplitude * 0.2f)))
            x += step
        }

        if (points.size < 2) {
            path.addRoundRect(RectF(left, top, right, bottom), 0f, 0f, Path.Direction.CW)
            return
        }

        path.moveTo(left, bottom)
        path.lineTo(left, points.first().y)
        for (index in 0 until points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            val midX = (current.x + next.x) / 2f
            val midY = (current.y + next.y) / 2f
            if (index == 0) {
                path.quadTo(current.x, current.y, midX, midY)
            } else {
                path.quadTo(current.x, current.y, midX, midY)
            }
        }
        path.lineTo(right, bottom)
        path.close()
    }

    fun dispose() {
        animator.cancel()
        started = false
    }

    companion object {
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
