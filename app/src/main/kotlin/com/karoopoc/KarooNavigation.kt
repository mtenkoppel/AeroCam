package com.karoopoc

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/** Keeps the Karoo-style Back control at the physical bottom-left, clear of content. */
internal object KarooNavigation {
    fun buttonSize(activity: Activity): Int = (54 * activity.resources.displayMetrics.density + .5f).toInt()

    fun bottomInset(activity: Activity): Int = (10 * activity.resources.displayMetrics.density + .5f).toInt()

    fun wrap(activity: Activity, content: View): View {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val root = FrameLayout(activity).apply {
            setBackgroundColor(DjiTheme.appPalette(activity).appBackground)
        }
        fun insetScrolls(view: View) {
            if (view is android.widget.ScrollView) {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,
                    view.paddingBottom + buttonSize(activity) + bottomInset(activity))
                view.clipToPadding = false
            } else if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) insetScrolls(view.getChildAt(i))
            }
        }
        insetScrolls(content)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(backButton(activity), FrameLayout.LayoutParams(buttonSize(activity), buttonSize(activity), Gravity.LEFT or Gravity.BOTTOM).apply {
            bottomMargin = bottomInset(activity)
        })
        return root
    }

    /** Reuse the same artwork, shape, hit area and back dispatch in custom footers. */
    fun backButton(activity: Activity): ImageButton {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val shape = GradientDrawable().apply {
            setColor(activity.getColor(R.color.karoo_back_background))
            cornerRadii = floatArrayOf(0f, 0f, dp(27).toFloat(), dp(27).toFloat(),
                dp(27).toFloat(), dp(27).toFloat(), 0f, 0f)
        }
        return ImageButton(activity).apply {
            id = R.id.karoo_back
            setImageResource(R.drawable.ob_arrow_back)
            imageTintList = ColorStateList.valueOf(Color.BLACK)
            scaleType = ImageView.ScaleType.CENTER
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape, shape.constantState!!.newDrawable())
            contentDescription = activity.getString(R.string.ob_back)
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                activity.currentFocus?.clearFocus()
                activity.onBackPressed()
            }
        }
    }

    /** Forward actions meet the physical right edge, mirroring the Back tab. */
    fun forwardButton(activity: Activity, label: String, click: () -> Unit): Button {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val radius = dp(27).toFloat()
        val shape = GradientDrawable().apply {
            setColor(activity.getColor(R.color.karoo_forward_background))
            cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        }
        return Button(activity).apply {
            text = label
            isAllCaps = true
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(54)
            minWidth = dp(125)
            maxWidth = (activity.resources.displayMetrics.widthPixels - dp(62)).coerceAtLeast(dp(125))
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(0x99FFFFFF.toInt(), Color.WHITE)))
            backgroundTintList = null
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, shape.constantState!!.newDrawable())
            setPadding(dp(16), 0, dp(12), 0)
            setOnClickListener { click() }
        }
    }

}
