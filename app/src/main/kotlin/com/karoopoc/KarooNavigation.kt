package com.karoopoc

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/** Keeps the Karoo-style Back control at the physical bottom-left, clear of content. */
internal object KarooNavigation {
    fun wrap(activity: Activity, content: View): View {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DjiTheme.appPalette(activity).appBackground)
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = FrameLayout(activity)
        footer.addView(backButton(activity), FrameLayout.LayoutParams(dp(68), dp(68), Gravity.LEFT or Gravity.BOTTOM))
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(68)))
        return root
    }

    /** Reuse the same artwork, shape, hit area and back dispatch in custom footers. */
    fun backButton(activity: Activity): ImageButton {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val shape = GradientDrawable().apply {
            setColor(activity.getColor(R.color.karoo_back_background))
            cornerRadii = floatArrayOf(0f, 0f, dp(34).toFloat(), dp(34).toFloat(),
                dp(34).toFloat(), dp(34).toFloat(), 0f, 0f)
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
}
