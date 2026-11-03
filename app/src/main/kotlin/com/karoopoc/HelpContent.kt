package com.karoopoc

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class HelpArticle(
    val fileName: String,
    val titleRes: Int,
    val summaryRes: Int,
)

object HelpContent {
    val articles = listOf(
        HelpArticle("get-ready-for-a-ride.md", R.string.help_ride, R.string.help_ride_summary),
        HelpArticle("prevent-standby.md", R.string.help_standby, R.string.help_standby_summary),
        HelpArticle("auto-highlights.md", R.string.help_highlights, R.string.help_highlights_summary),
        HelpArticle("different-widgets.md", R.string.help_widgets, R.string.help_widgets_summary),
        HelpArticle("gps-telemetry.md", R.string.help_gps, R.string.help_gps_summary),
    )

    fun load(context: Context, fileName: String): String {
        val language = context.resources.configuration.locales[0].language
            .takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE
        val localizedPath = "help/$language/$fileName"
        val path = if (runCatching { context.assets.open(localizedPath).close() }.isSuccess) {
            localizedPath
        } else {
            "help/$DEFAULT_LANGUAGE/$fileName"
        }
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    fun render(
        context: Context,
        markdown: String,
        palette: DjiPalette,
        dp: (Int) -> Int,
    ): LinearLayout {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        parseBlocks(markdown).forEach { block ->
            if (block.kind == BlockKind.IMAGE) {
                content.addView(ImageView(context).apply {
                    setImageResource(R.drawable.dji_lock_menu)
                    contentDescription = context.getString(R.string.lock_menu_caption)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)).apply {
                    topMargin = dp(8)
                })
                content.addView(TextView(context).apply {
                    text = context.getString(R.string.lock_menu_caption)
                    textSize = 12f
                    setTextColor(palette.textSecondary)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(12)
                })
                return@forEach
            }
            val view = TextView(context).apply {
                text = inlineMarkdown(block.text)
                setTextColor(if (block.kind == BlockKind.CALLOUT) palette.textPrimary else block.color(palette))
                textSize = block.textSize
                setLineSpacing(0f, 1.12f)
                if (block.bold) setTypeface(Typeface.DEFAULT_BOLD)
                if (block.kind == BlockKind.CALLOUT) {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(palette.previewBackground)
                    }
                }
            }
            content.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(block.topMargin)
                    bottomMargin = dp(block.bottomMargin)
                },
            )
        }
        return content
    }

    private fun parseBlocks(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block(BlockKind.BODY, paragraph.joinToString(" ").trim())
                paragraph.clear()
            }
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> flushParagraph()
                line.matches(Regex("!\\[.*]\\(dji-lock-menu\\)")) -> {
                    flushParagraph()
                    blocks += Block(BlockKind.IMAGE, "")
                }
                line.startsWith("### ") -> {
                    flushParagraph()
                    blocks += Block(BlockKind.SUBHEADING, line.removePrefix("### "))
                }
                line.startsWith("## ") -> {
                    flushParagraph()
                    blocks += Block(BlockKind.HEADING, line.removePrefix("## "))
                }
                line.startsWith("# ") -> {
                    flushParagraph()
                    blocks += Block(BlockKind.TITLE, line.removePrefix("# "))
                }
                line.startsWith("> ") -> {
                    flushParagraph()
                    blocks += Block(BlockKind.CALLOUT, line.removePrefix("> "))
                }
                line.matches(Regex("[-*]\\s+.+")) -> {
                    flushParagraph()
                    blocks += Block(BlockKind.LIST, "\u2022  " + line.drop(2))
                }
                line.matches(Regex("\\d+\\.\\s+.+")) -> {
                    flushParagraph()
                    blocks += Block(BlockKind.LIST, line)
                }
                else -> paragraph += line
            }
        }
        flushParagraph()
        return blocks
    }

    private fun inlineMarkdown(source: String): CharSequence {
        val result = SpannableStringBuilder(source)
        val marker = Regex("\\*\\*(.+?)\\*\\*")
        while (true) {
            val match = marker.find(result) ?: break
            val replacement = match.groupValues[1]
            val start = match.range.first
            result.replace(start, match.range.last + 1, replacement)
            result.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + replacement.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return result
    }

    private enum class BlockKind {
        IMAGE,
        TITLE,
        HEADING,
        SUBHEADING,
        BODY,
        LIST,
        CALLOUT,
    }

    private data class Block(val kind: BlockKind, val text: String) {
        val textSize: Float
            get() = when (kind) {
                BlockKind.TITLE -> 24f
                BlockKind.HEADING -> 19f
                BlockKind.SUBHEADING -> 16f
                else -> 15f
            }
        val bold: Boolean
            get() = kind == BlockKind.TITLE || kind == BlockKind.HEADING || kind == BlockKind.SUBHEADING
        val topMargin: Int
            get() = when (kind) {
                BlockKind.TITLE -> 2
                BlockKind.HEADING -> 18
                BlockKind.SUBHEADING -> 12
                BlockKind.CALLOUT -> 8
                else -> 2
            }
        val bottomMargin: Int
            get() = when (kind) {
                BlockKind.TITLE -> 10
                BlockKind.HEADING -> 6
                BlockKind.SUBHEADING -> 4
                BlockKind.LIST -> 5
                else -> 8
            }

        fun color(palette: DjiPalette): Int = when (kind) {
            BlockKind.BODY, BlockKind.LIST -> palette.textSecondary
            else -> palette.textPrimary
        }
    }

    private const val DEFAULT_LANGUAGE = "en"
}
