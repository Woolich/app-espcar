package com.example.esp32control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnMoveListener {
        /**
         * x,y normalizados en [-1,1]. norm = distancia 0..1 desde el centro.
         */
        fun onMove(x: Float, y: Float, norm: Float, isPressed: Boolean)
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 30 }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; alpha = 120 }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; alpha = 160 }

    private val center = PointF()
    private var radius = 0f
    private var knobRadius = 0f

    private var knobPos = PointF()
    private var listener: OnMoveListener? = null

    private val deadZone = 0.08f  // 8%

    fun setOnMoveListener(l: OnMoveListener) { listener = l }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h)
        center.set(w / 2f, h / 2f)
        radius = size * 0.48f
        knobRadius = radius * 0.28f
        resetKnob()
    }

    override fun onDraw(canvas: Canvas) {
        // no especificar colores exactos: alpha ya set
        canvas.drawCircle(center.x, center.y, radius, basePaint)
        canvas.drawCircle(center.x, center.y, radius, ringPaint)
        canvas.drawCircle(knobPos.x, knobPos.y, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - center.x
                val dy = event.y - center.y
                val dist = sqrt(dx*dx + dy*dy)
                val clampedDist = min(dist, radius)
                val angle = atan2(dy, dx)
                knobPos.set(center.x + clampedDist * cos(angle), center.y + clampedDist * sin(angle))
                invalidate()

                val norm = (clampedDist / radius).coerceIn(0f, 1f)
                var nx = (dx / radius).coerceIn(-1f, 1f)
                var ny = (dy / radius).coerceIn(-1f, 1f)

                // zona muerta
                if (norm < deadZone) { nx = 0f; ny = 0f }
                listener?.onMove(nx, ny, norm, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetKnob()
                invalidate()
                listener?.onMove(0f, 0f, 0f, false)
            }
        }
        return true
    }

    private fun resetKnob() {
        knobPos.set(center.x, center.y)
    }
}
