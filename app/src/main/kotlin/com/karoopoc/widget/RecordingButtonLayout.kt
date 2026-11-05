package com.karoopoc.widget

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.karoopoc.R
import kotlin.math.ceil

/** Fits the same recording content to the space left inside any video button. */
internal object RecordingButtonLayout {
    private data class Line(val text: String, val size: Float, val top: Int, val bottom: Int, val height: Int)

    fun render(
        views: RemoteViews,
        resources: Resources,
        widthPx: Int,
        heightPx: Int,
        timer: String,
        preferredTimerSp: Int,
        feedback: String?,
        metadata: String?,
    ): Boolean {
        val width = (widthPx - 2 * resources.getDimensionPixelSize(R.dimen.widget_recording_horizontal_padding))
            .coerceAtLeast(1)
        val height = (heightPx - 2 * resources.getDimensionPixelSize(R.dimen.widget_recording_vertical_padding))
            .coerceAtLeast(1)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val regular = Paint(Paint.ANTI_ALIAS_FLAG)
        val preferred = sp(resources, preferredTimerSp.coerceIn(18, 38))
        val secondary = (feedback ?: metadata)?.let { line(regular, it, sp(resources, 11)) }
            ?.takeIf { regular.measureText(it.text) <= width }
        val reservedTimer = secondary?.let {
            fitTimer(bold, timer, preferred, width, height - it.height)
        }
        // Allow feedback to reduce the timer slightly. Metadata uses spare room only.
        val minimumWithSecondary = if (feedback != null) sp(resources, 18) else preferred
        val showSecondary = reservedTimer != null && reservedTimer.size >= minimumWithSecondary
        val timerLine = if (showSecondary) checkNotNull(reservedTimer)
        else checkNotNull(fitTimer(bold, timer, preferred, width, height.coerceAtLeast(4)))
        with(views) {
            showLine(R.id.widgetVideoTimer, timerLine)
            setViewVisibility(R.id.widgetVideoMeta, if (showSecondary) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widgetVideoMeta, "")
            if (showSecondary) showLine(R.id.widgetVideoMeta, checkNotNull(secondary))
        }
        return feedback != null && !showSecondary
    }

    private fun fitTimer(paint: Paint, text: String, preferred: Float, width: Int, height: Int): Line? {
        if (height < 4) return null
        val initial = line(paint, text, preferred)
        val scale = minOf(1f, width / paint.measureText(text), height.toFloat() / initial.height)
        var size = (preferred * scale).coerceAtLeast(1f)
        var candidate = line(paint, text, size)
        // Integer font metrics and optical insets can round up at the fitted size.
        while (size > 1f && (candidate.height > height || paint.measureText(text) > width)) {
            size = (size - 1f).coerceAtLeast(1f)
            candidate = line(paint, text, size)
        }
        return candidate
    }

    private fun line(paint: Paint, text: String, size: Float): Line {
        paint.textSize = size
        val metrics = paint.fontMetricsInt
        val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        // Center the visible glyphs inside the font's ascent/descent box.
        val correction = ink.top + ink.bottom - metrics.ascent - metrics.descent
        val top = (-correction).coerceAtLeast(0)
        val bottom = correction.coerceAtLeast(0)
        return Line(text, size, top, bottom, ceil(paint.fontSpacing).toInt()
            .coerceAtLeast(metrics.descent - metrics.ascent) + top + bottom)
    }

    private fun RemoteViews.showLine(id: Int, line: Line) {
        setTextViewText(id, line.text)
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, line.size)
        setViewPadding(id, 0, line.top, 0, line.bottom)
    }

    private fun sp(resources: Resources, size: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), resources.displayMetrics)
}
