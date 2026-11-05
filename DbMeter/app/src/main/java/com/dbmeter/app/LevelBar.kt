package com.dbmeter.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Green / orange / red scale from 30 to 110 dB with a marker at the current level. */
class LevelBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Current level in dB, or null to hide the marker. */
    var value: Double? = null
        set(v) {
            field = v
            invalidate()
        }

    private val green = ContextCompat.getColor(context, R.color.safe)
    private val orange = ContextCompat.getColor(context, R.color.caution)
    private val red = ContextCompat.getColor(context, R.color.danger)

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF212121.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3
    }
    private val clip = Path()
    private val bar = RectF()

    override fun onDraw(canvas: Canvas) {
        val barHeight = height * 0.45f
        val top = (height - barHeight) / 2f
        val radius = barHeight / 2f
        bar.set(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), top + barHeight)

        clip.reset()
        clip.addRoundRect(bar, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        val x70 = xFor(SoundLevel.CAUTION_DB)
        val x85 = xFor(SoundLevel.DANGER_DB)
        segmentPaint.color = green
        canvas.drawRect(bar.left, bar.top, x70, bar.bottom, segmentPaint)
        segmentPaint.color = orange
        canvas.drawRect(x70, bar.top, x85, bar.bottom, segmentPaint)
        segmentPaint.color = red
        canvas.drawRect(x85, bar.top, bar.right, bar.bottom, segmentPaint)
        canvas.restore()

        value?.let {
            val cx = xFor(it)
            val r = height * 0.32f
            canvas.drawCircle(cx, height / 2f, r, markerFill)
            canvas.drawCircle(cx, height / 2f, r, markerStroke)
        }
    }

    private fun xFor(db: Double): Float {
        val fraction = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0).toFloat()
        return bar.left + fraction * bar.width()
    }

    companion object {
        private const val MIN_DB = 30.0
        private const val MAX_DB = 110.0
    }
}
