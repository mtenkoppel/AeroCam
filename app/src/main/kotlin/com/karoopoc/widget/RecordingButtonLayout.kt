package com.karoopoc.widget

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.karoopoc.R
import kotlin.math.abs
import kotlin.math.ceil

/** Fits the same recording content to the space left inside any video button. */
internal object RecordingButtonLayout {
    private data class Line(
        val text: String,
        val size: Float,
        val top: Int,
        val bottom: Int,
        val height: Int,
        val leadingSpace: Int,
        val trailingSpace: Int,
    )

    /** Returns true when highlight feedback needs the Highlight button instead of a text line. */
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
        val horizontalInset = resources.getDimensionPixelSize(R.dimen.widget_recording_horizontal_padding)
        val width = (widthPx - 2 * horizontalInset).coerceAtLeast(1)
        val height = (heightPx - 2 * resources.getDimensionPixelSize(R.dimen.widget_recording_vertical_padding))
            .coerceAtLeast(1)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val preferred = sp(resources, (preferredTimerSp * 1.3f).toInt().coerceIn(23, 49))
        var secondary = (feedback ?: metadata)?.let { text ->
            var size = sp(resources, 14)
            var fitted = line(regular, text, size)
            while (regular.measureText(text) > width && size > sp(resources, 8)) {
                size -= 1f
                fitted = line(regular, text, size)
            }
            fitted.takeIf { regular.measureText(text) <= width }
        }
        var reservedTimer = secondary?.let {
            fitTimer(bold, timer, preferred, width, height, it)
        }
        // Allow feedback to reduce the timer slightly. Metadata uses spare room only.
        val minimumWithSecondary = sp(resources, 14)
        // Grow labels where space permits, but preserve the established short-slot feedback.
        while (secondary != null && secondary.size > sp(resources, 11) &&
            (reservedTimer == null || reservedTimer.size < maxOf(minimumWithSecondary, secondary.size * 1.25f))) {
            secondary = line(regular, secondary.text, secondary.size - 1f)
            reservedTimer = fitTimer(bold, timer, preferred, width, height, secondary)
        }
        val showSecondary = reservedTimer != null && reservedTimer.size >= minimumWithSecondary
        val timerLine = if (showSecondary) checkNotNull(reservedTimer)
        else checkNotNull(fitTimer(bold, timer, preferred, width, height.coerceAtLeast(4)))
        val groupOffset = if (showSecondary) timerLine.leadingSpace - checkNotNull(secondary).trailingSpace else 0
        with(views) {
            setViewPadding(R.id.widgetVideoRecording, horizontalInset, (-groupOffset).coerceAtLeast(0),
                horizontalInset, groupOffset.coerceAtLeast(0))
            showLine(R.id.widgetVideoTimer, timerLine)
            setViewVisibility(R.id.widgetVideoMeta, if (showSecondary) View.VISIBLE else View.GONE)
            if (showSecondary) showLine(R.id.widgetVideoMeta, checkNotNull(secondary))
            else setTextViewText(R.id.widgetVideoMeta, "")
        }
        return feedback != null && !showSecondary
    }

    private fun fitTimer(
        paint: Paint, text: String, preferred: Float, width: Int, height: Int, secondary: Line? = null,
    ): Line? {
        val timerHeight = height - (secondary?.height ?: 0)
        if (timerHeight < 4) return null
        val initial = line(paint, text, preferred)
        val scale = minOf(1f, width / paint.measureText(text), timerHeight.toFloat() / initial.height)
        var size = (preferred * scale).coerceAtLeast(1f)
        var candidate = line(paint, text, size)
        fun fittedHeight(line: Line): Int =
            line.height + (secondary?.let { abs(line.leadingSpace - it.trailingSpace) } ?: 0)
        // Integer font metrics and optical insets can round up at the fitted size.
        while (size > 1f && (fittedHeight(candidate) > timerHeight || paint.measureText(text) > width)) {
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
        val height = ceil(paint.fontSpacing).toInt().coerceAtLeast(metrics.descent - metrics.ascent) + top + bottom
        val baseline = top - metrics.ascent
        return Line(text, size, top, bottom, height, baseline + ink.top, height - baseline - ink.bottom)
    }

    private fun RemoteViews.showLine(id: Int, line: Line) {
        setTextViewText(id, line.text)
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, line.size)
        setViewPadding(id, 0, line.top, 0, line.bottom)
    }

    private fun sp(resources: Resources, size: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), resources.displayMetrics)
}
