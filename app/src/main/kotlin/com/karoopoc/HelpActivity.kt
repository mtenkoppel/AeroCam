package com.karoopoc

import android.app.Activity
import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.view.Window
import android.widget.LinearLayout

/** A real activity: platform back stack, transitions, and scroll restoration. */
open class DetailActivity : Activity() {
    internal lateinit var ui: SettingsViews
    internal lateinit var body: LinearLayout

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DjiTheme.activityContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        super.onCreate(savedInstanceState)
        window.enterTransition = Slide(Gravity.END)
        window.returnTransition = Slide(Gravity.END)
        ui = SettingsViews(this)
        val root = ui.column().apply { setBackgroundColor(ui.palette.appBackground) }
        body = ui.column().apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(16)) }
        root.addView(ui.scroll(body, R.id.article_scroll), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(KarooNavigation.wrap(this, root))
    }
}

class HelpActivity : DetailActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileName = intent.getStringExtra(EXTRA_FILE)
            ?.takeIf { candidate -> candidate == "recommended-camera-setup.md" || HelpContent.articles.any { it.fileName == candidate } }
            ?: HelpContent.articles.first().fileName
        body.addView(HelpContent.render(this, HelpContent.load(this, fileName), ui.palette, ui::dp))
    }

    companion object {
        private const val EXTRA_FILE = "help_file"
        fun open(activity: Activity, fileName: String) {
            activity.currentFocus?.clearFocus()
            activity.startActivity(Intent(activity, HelpActivity::class.java).putExtra(EXTRA_FILE, fileName),
                ActivityOptions.makeSceneTransitionAnimation(activity).toBundle())
        }
    }
}
