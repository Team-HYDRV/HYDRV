package app.hydra.manager

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class WiggleCircularProgressIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var indicatorSize: Int = dp(24)
        set(value) {
            val clamped = value.coerceAtLeast(dp(12))
            if (field == clamped) return
            field = clamped
            requestLayout()
            invalidate()
        }

    var trackThickness: Int = dp(3)
        set(value) {
            val clamped = value.coerceAtLeast(1)
            if (field == clamped) return
            field = clamped
            trackPaint.strokeWidth = clamped.toFloat()
            indicatorPaint.strokeWidth = clamped.toFloat()
            invalidate()
        }

    var indicatorInset: Int = 0
        set(value) {
            if (field == value) return
            field = value.coerceAtLeast(0)
            requestLayout()
            invalidate()
        }

    var trackColor: Int = 0x33222222
        set(value) {
            field = value
            trackPaint.color = value
            invalidate()
        }

    private var indicatorColor: Int = 0xFF6750A4.toInt()
    private var progressFraction: Float = 0f
    private var indeterminatePhase: Float = 0f
    private var indeterminateAnimator: ValueAnimator? = null
    private val arcBounds = RectF()
    private val beadCount = 4
    private val beadSpacingDegrees = 8.25f
    private val wiggleAmplitudeFactor = 0.26f
    private val beadSizeFalloff = 0.11f
    private val headSizeFactor = 0.82f

    var isIndeterminate: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            syncAnimator()
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackThickness.toFloat()
        color = trackColor
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackThickness.toFloat()
        color = indicatorColor
    }

    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = indicatorColor
    }

    fun setIndicatorColor(color: Int) {
        indicatorColor = color
        indicatorPaint.color = color
        beadPaint.color = color
        invalidate()
    }

    fun setProgressCompat(progress: Int, animated: Boolean) {
        progressFraction = (progress.coerceIn(0, 100) / 100f)
        if (!animated) {
            invalidate()
            return
        }
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        indeterminateAnimator?.cancel()
        indeterminateAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimator()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = indicatorSize + (indicatorInset * 2) + paddingLeft + paddingRight
        val measuredWidth = resolveSize(desired, widthMeasureSpec)
        val measuredHeight = resolveSize(desired, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        val size = min(contentWidth, contentHeight).toFloat()
        val inset = indicatorInset.toFloat() + (trackThickness / 2f)
        val left = paddingLeft + ((contentWidth - size) / 2f) + inset
        val top = paddingTop + ((contentHeight - size) / 2f) + inset
        val right = paddingLeft + ((contentWidth + size) / 2f) - inset
        val bottom = paddingTop + ((contentHeight + size) / 2f) - inset
        arcBounds.set(left, top, right, bottom)

        val startAngle = 132f
        val totalSweep = 276f
        val radius = arcBounds.width() / 2f
        val centerX = arcBounds.centerX()
        val centerY = arcBounds.centerY()

        canvas.drawArc(arcBounds, startAngle, totalSweep, false, trackPaint)

        if (isIndeterminate) {
            val headAngle = startAngle + (indeterminatePhase * totalSweep)
            drawWiggleHead(canvas, centerX, centerY, radius, headAngle, animated = true)
        } else {
            val sweep = totalSweep * progressFraction
            if (sweep > 0.5f) {
                canvas.drawArc(arcBounds, startAngle, sweep, false, indicatorPaint)
                drawWiggleHead(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    radius = radius,
                    headAngle = startAngle + sweep,
                    animated = false
                )
            }
        }
    }

    private fun drawWiggleHead(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        headAngle: Float,
        animated: Boolean
    ) {
        val stroke = trackThickness.toFloat()
        for (index in 0 until beadCount) {
            val trailT = index / (beadCount - 1f)
            val angle = Math.toRadians((headAngle - (index * beadSpacingDegrees)).toDouble())
            val wave = if (animated) {
                sin((indeterminatePhase * Math.PI * 2) - (trailT * 1.05f)).toFloat()
            } else {
                sin((trailT * Math.PI * 0.92f)).toFloat()
            }
            val radialOffset = wave * stroke * wiggleAmplitudeFactor
            val beadRadius = stroke * (headSizeFactor - (trailT * beadSizeFalloff))
            val orbitRadius = radius + radialOffset
            val x = centerX + (cos(angle) * orbitRadius).toFloat()
            val y = centerY + (sin(angle) * orbitRadius).toFloat()
            canvas.drawCircle(x, y, beadRadius, beadPaint)
        }
    }

    private fun syncAnimator() {
        val shouldRun = visibility == VISIBLE && isAttachedToWindow && isIndeterminate
        if (!shouldRun) {
            indeterminateAnimator?.cancel()
            indeterminateAnimator = null
            return
        }
        if (indeterminateAnimator != null) return
        indeterminateAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1560L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                indeterminatePhase = animator.animatedFraction
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
