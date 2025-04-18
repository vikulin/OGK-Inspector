package io.github.vikulin.opengammakit.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ClockProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f // 0..100
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 128 // semi-transparent white overlay
        style = Paint.Style.FILL
    }

    private val fullRect = RectF()
    private val roundedRectPath = Path()
    private val revealPath = Path()

    private var cornerRadiusPx = 0f

    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 100f)
        if (clamped != progress) {
            progress = clamped
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val insetX = w * 0.075f
        val insetY = h * 0.075f
        fullRect.set(insetX, insetY, w - insetX, h - insetY)

        cornerRadiusPx = (12f * 0.5f).dp(context) // 10% sharper corners
        roundedRectPath.reset()
        roundedRectPath.addRoundRect(fullRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (progress <= 0f || progress >= 100f) return

        // Draw the full semi-transparent overlay
        canvas.drawPath(roundedRectPath, paint)

        // Create sweeping "reveal" path
        revealPath.reset()
        val sweepAngle = (progress / 100f) * 360f
        val cx = fullRect.centerX()
        val cy = fullRect.centerY()
        val radius = min(fullRect.width(), fullRect.height())

        // Construct a sweeping sector path
        revealPath.moveTo(cx, cy)
        revealPath.arcTo(
            cx - radius, cy - radius,
            cx + radius, cy + radius,
            -90f, sweepAngle, false
        )
        revealPath.close()

        // Use porter-duff to clear this swept sector from the overlay
        val clearPaint = Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        canvas.drawPath(revealPath, clearPaint)
    }

    private fun Float.dp(context: Context): Float =
        this * context.resources.displayMetrics.density
}
